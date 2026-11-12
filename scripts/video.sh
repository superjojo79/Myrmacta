#!/usr/bin/env bash
# Capsules vidéo du site (§128, issue #56) — scène de tournage de la capsule « Digital Mail Room ». Outillage de
# développement : build `embedded-db` (jamais le jar de production), conteneurs locaux, jamais en CI. À lancer depuis la
# racine du dépôt.
#
#   scripts/video.sh build           construit le jar de démo (comme scripts/demo.sh build)
#   scripts/video.sh mail-servers    démarre GreenMail (boîte courrier@demo.example, SMTP 3025, IMAP 3143) et Mailpit
#                                    (e-mails envoyés par Myrmacta, http://localhost:8025) : podman compose, profil video
#   scripts/video.sh start           démarre Myrmacta, profil demo-video-mailroom : mail room sans courriers d'exemple,
#                                    boîte IMAP sur GreenMail, agent IA dans la file de tri, factures à échéance proche
#   scripts/video.sh mail <scénario> envoie un e-mail du tournage : facture | inconnu | reponse (scripts/video/send_mail.py)
#   scripts/video.sh reset [--yes]   vide la base embarquée (application ARRÊTÉE) : le prochain start rejoue la scène
#
# Langue : APP_DEMO_LOCALE=fr (défaut) ou en ; entre deux langues, reset. Connexion : comme scripts/demo.sh (Keycloak et
# variables OIDC dans .env). M2M_JWT_SECRET (dev seulement) : secret HMAC du client MCP de l'agent IA (lot 3).
set -euo pipefail
cd "$(dirname "$0")/.."

case "${1:-}" in
  build)
    exec scripts/demo.sh build
    ;;
  mail-servers)
    exec podman compose --profile video up -d greenmail mailpit
    ;;
  start)
    jar="$(ls target/myrmacta-*.jar 2>/dev/null | grep -v '\.original$' | head -n1 || true)"
    [[ -n "$jar" ]] || { echo "jar introuvable : lancer d'abord « scripts/video.sh build »" >&2; exit 2; }
    # Variables locales (OIDC, clé maîtresse des connecteurs) : .env s'il existe ; celles qui suivent priment.
    if [[ -f .env ]]; then set -a; . ./.env; set +a; fi
    # Comme scripts/demo.sh : profils fixés, variables DB_* écartées (base embarquée). Les e-mails sortants vont à Mailpit.
    # Échéances évaluées 30 s après le démarrage (au lieu de 2 min), puis toutes les 15 min : le rappel et l'escalade des
    # factures semées sont là au tournage.
    exec env -u DB_URL -u DB_USERNAME -u DB_PASSWORD -u DB_NAME -u DB_PORT \
      APP_ENVIRONMENT="${APP_ENVIRONMENT:-local}" \
      SPRING_PROFILES_ACTIVE="local,embedded-db,demo-video-mailroom" \
      APP_DEMO_MAILROOM_DIR="${APP_DEMO_MAILROOM_DIR:-exemples/digital-mail-room}" \
      APP_DEMO_LOCALE="${APP_DEMO_LOCALE:-fr}" \
      APP_I18N_DEFAULT_LOCALE="${APP_I18N_DEFAULT_LOCALE:-${APP_DEMO_LOCALE:-fr}}" \
      MAIL_HOST=localhost MAIL_PORT="${MAILPIT_SMTP_PORT:-1025}" MAIL_SMTP_AUTH=false MAIL_SMTP_STARTTLS=false \
      java -jar "$jar" --app.deadlines.initial-delay-ms=30000 --app.inbound.initial-delay=10s
    ;;
  mail)
    shift
    exec uv run scripts/video/send_mail.py "$@" --lang "${APP_DEMO_LOCALE:-fr}"
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
