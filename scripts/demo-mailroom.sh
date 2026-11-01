#!/usr/bin/env bash
# Démo reproductible de la Digital Mail Room (#217, §113) — profil Spring `demo-mailroom`, présent seulement dans le build
# `embedded-db` (jamais dans le jar de production). À lancer depuis la racine du dépôt.
#
#   scripts/demo-mailroom.sh build          construit le jar de démo (./mvnw -Pembedded-db package)
#   scripts/demo-mailroom.sh start          démarre ; sur une base vide : installe l'exemple et fait arriver 3 courriers
#                                           (facture par e-mail → comptabilité, amende par dépôt → file de tri,
#                                           e-mail inconnu → file de tri)
#   scripts/demo-mailroom.sh reset [--yes]  vide la base embarquée (application ARRÊTÉE) : le prochain `start` rejoue la démo
#
# Connexion : comme tout démarrage local (docs/guides/GETTING-STARTED.md §3, Keycloak et variables OIDC dans .env).
# Répertoire de la base : APP_EMBEDDED_DB_DATA_DIR (voir scripts/embedded-db.sh).
set -euo pipefail
cd "$(dirname "$0")/.."

case "${1:-}" in
  build)
    ./mvnw -Pembedded-db -DskipTests package
    ;;
  start)
    jar="$(ls target/myrmacta-*.jar 2>/dev/null | grep -v '\.original$' | head -n1 || true)"
    [[ -n "$jar" ]] || { echo "jar introuvable : lancer d'abord « scripts/demo-mailroom.sh build »" >&2; exit 2; }
    exec env APP_ENVIRONMENT="${APP_ENVIRONMENT:-local}" \
      SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local,embedded-db,demo-mailroom}" \
      APP_DEMO_MAILROOM_DIR="${APP_DEMO_MAILROOM_DIR:-exemples/digital-mail-room}" \
      java -jar "$jar"
    ;;
  reset)
    shift
    exec scripts/embedded-db.sh reset "$@"
    ;;
  *)
    sed -n '2,13p' "$0"
    exit 2
    ;;
esac
