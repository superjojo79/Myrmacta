#!/usr/bin/env bash
# Gestion de la base PostgreSQL embarquée persistante (profil Spring `embedded-db`, démos/QA).
#
#   scripts/embedded-db.sh status                    état (arrêtée / en cours) et emplacement
#   scripts/embedded-db.sh backup <archive.tar.gz>   sauvegarde à froid (application ARRÊTÉE)
#   scripts/embedded-db.sh restore <archive.tar.gz>  restauration à froid (répertoire cible vide ou --force)
#   scripts/embedded-db.sh dump <fichier.sql>        pg_dump à chaud du schéma tenant (application démarrée, pg_dump requis)
#   scripts/embedded-db.sh reset [--yes]             supprime toutes les données (application ARRÊTÉE)
#
# Répertoire : APP_EMBEDDED_DB_DATA_DIR (défaut ~/.flowforge/embedded-db). Port pour `dump` :
# APP_EMBEDDED_DB_PORT (défaut 15432 ; avec le port 0 aléatoire, le dump à chaud n'est pas possible).
# Voir docs/guides/GETTING-STARTED.md § « Base embarquée persistante ».
set -euo pipefail

BASE="${APP_EMBEDDED_DB_DATA_DIR:-$HOME/.flowforge/embedded-db}"
PORT="${APP_EMBEDDED_DB_PORT:-15432}"

die() { echo "embedded-db.sh : $*" >&2; exit 1; }

is_running() {
  local pidfile="$BASE/pgdata/postmaster.pid" pid
  [[ -f "$pidfile" ]] || return 1
  pid="$(head -n1 "$pidfile" | tr -d '[:space:]')"
  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  kill -0 "$pid" 2>/dev/null && [[ "$(cat "/proc/$pid/comm" 2>/dev/null || echo postgres)" == postgres* ]]
}

require_stopped() {
  if is_running; then
    die "une base embarquée tourne sur $BASE : arrêter l'application d'abord."
  fi
}

# Garde-fou : on ne supprime/écrase jamais autre chose qu'un répertoire de base embarquée.
safe_base() {
  case "$BASE" in
    ""|"/"|"$HOME"|"$HOME/") die "répertoire dangereux : '$BASE'" ;;
  esac
  if [[ -d "$BASE" && -n "$(ls -A "$BASE" 2>/dev/null)" && ! -d "$BASE/pgdata" ]]; then
    die "'$BASE' n'est pas un répertoire de base embarquée (pas de pgdata/) : refus."
  fi
}

cmd="${1:-}"
case "$cmd" in
  status)
    echo "répertoire : $BASE"
    if [[ ! -d "$BASE/pgdata" ]]; then echo "état : aucune donnée"; elif is_running; then echo "état : en cours d'exécution"; else echo "état : arrêtée"; fi
    ;;
  backup)
    archive="${2:-}"; [[ -n "$archive" ]] || die "usage : backup <archive.tar.gz>"
    [[ -d "$BASE/pgdata" ]] || die "rien à sauvegarder dans $BASE"
    require_stopped
    umask 077
    tar -C "$(dirname "$BASE")" -czf "$archive" "$(basename "$BASE")"
    echo "sauvegarde écrite : $archive (contient le secret du rôle applicatif : la protéger)"
    ;;
  restore)
    archive="${2:-}"; [[ -n "$archive" && -f "$archive" ]] || die "usage : restore <archive.tar.gz>"
    require_stopped
    if [[ -d "$BASE" && -n "$(ls -A "$BASE" 2>/dev/null)" && "${3:-}" != "--force" ]]; then
      die "$BASE n'est pas vide : relancer avec --force pour l'écraser (ou lancer reset)."
    fi
    safe_base
    rm -rf "$BASE"
    mkdir -p "$(dirname "$BASE")"
    tar -C "$(dirname "$BASE")" -xzf "$archive"
    echo "restauré dans $BASE"
    ;;
  dump)
    file="${2:-}"; [[ -n "$file" ]] || die "usage : dump <fichier.sql>"
    command -v pg_dump >/dev/null || die "pg_dump introuvable (installer le client PostgreSQL)"
    is_running || die "la base n'est pas en cours d'exécution (dump à chaud) — utiliser backup pour une copie à froid."
    umask 077
    PGPASSWORD="$(cat "$BASE/app-role-password")" pg_dump -h 127.0.0.1 -p "$PORT" \
      -U flowforge_tenant_dev -n tenant_dev --no-owner flowforge > "$file"
    echo "dump écrit : $file"
    ;;
  reset)
    safe_base
    require_stopped
    if [[ "${2:-}" != "--yes" ]]; then
      read -r -p "Supprimer TOUTES les données de $BASE ? [oui/N] " answer
      [[ "$answer" == "oui" ]] || die "annulé"
    fi
    rm -rf "$BASE"
    echo "base embarquée réinitialisée : $BASE"
    ;;
  *)
    sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
    ;;
esac
