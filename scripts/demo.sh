#!/usr/bin/env bash
# Démo complète de Myrmacta (§127, issue #55) — profil Spring `demo`, présent seulement dans le build `embedded-db`
# (jamais dans le jar de production). Installe les paquets de démo par les vrais cas d'usage. À lancer depuis la racine du
# dépôt.
#
#   scripts/demo.sh build          construit le jar de démo (./mvnw -Pembedded-db package)
#   scripts/demo.sh start          démarre ; sur une base vide, installe chaque paquet : la mail room (3 courriers),
#                                  l'onboarding (3 arrivées, faux SIRH/IAM/ITSM sur le port 18090), les états (SLA,
#                                  factures antidatées, incident, brouillon MCP). Un paquet déjà installé n'est pas touché.
#   scripts/demo.sh reset [--yes]  vide la base embarquée (application ARRÊTÉE) : le prochain `start` rejoue la démo
#
# Langue : APP_DEMO_LOCALE=fr (défaut) ou en ; elle fixe aussi la langue par défaut de l'interface. Entre deux langues,
# remettre la base à zéro (reset) : un paquet déjà installé n'est pas réinstallé.
#
# Connexion : comme tout démarrage local (docs/guides/GETTING-STARTED.md §3, Keycloak et variables OIDC dans .env).
# Répertoire de la base : APP_EMBEDDED_DB_DATA_DIR (voir scripts/embedded-db.sh). Démo de la seule mail room :
# scripts/demo-mailroom.sh.
set -euo pipefail
cd "$(dirname "$0")/.."

case "${1:-}" in
  build)
    ./mvnw -Pembedded-db -DskipTests package
    ;;
  start)
    jar="$(ls target/myrmacta-*.jar 2>/dev/null | grep -v '\.original$' | head -n1 || true)"
    [[ -n "$jar" ]] || { echo "jar introuvable : lancer d'abord « scripts/demo.sh build »" >&2; exit 2; }
    exec env APP_ENVIRONMENT="${APP_ENVIRONMENT:-local}" \
      SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local,embedded-db,demo}" \
      APP_DEMO_MAILROOM_DIR="${APP_DEMO_MAILROOM_DIR:-exemples/digital-mail-room}" \
      APP_DEMO_ONBOARDING_DIR="${APP_DEMO_ONBOARDING_DIR:-exemples/onboarding-collaborateur}" \
      APP_DEMO_LOCALE="${APP_DEMO_LOCALE:-fr}" \
      APP_I18N_DEFAULT_LOCALE="${APP_I18N_DEFAULT_LOCALE:-${APP_DEMO_LOCALE:-fr}}" \
      java -jar "$jar"
    ;;
  reset)
    shift
    exec scripts/embedded-db.sh reset "$@"
    ;;
  *)
    sed -n '2,17p' "$0"
    exit 2
    ;;
esac
