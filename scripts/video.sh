#!/usr/bin/env bash
# Capsules vidéo du site (§128, §136, §140 ; issues #56, #57, #58) — scènes de tournage et production des capsules. Outillage de
# développement : build `embedded-db` (jamais le jar de production), conteneurs locaux, jamais en CI. À lancer depuis la
# racine du dépôt.
#
#   scripts/video.sh build                construit le jar de démo (comme scripts/demo.sh build)
#   scripts/video.sh mail-servers         démarre GreenMail (boîte courrier@demo.example, SMTP 3025, IMAP 3143) et Mailpit
#                                         (e-mails envoyés par Myrmacta, http://localhost:8025) : podman compose, profil video
#   scripts/video.sh start [capsule]      démarre Myrmacta sur la scène de la capsule :
#                                           l1-mailroom (défaut) — profil demo-video-mailroom : mail room sans courriers
#                                             d'exemple, boîte IMAP sur GreenMail, agent IA dans la file de tri, factures à
#                                             échéance proche (mail-servers d'abord) ;
#                                           l2-onboarding — profil demo-video-onboarding : exemple onboarding sans arrivée,
#                                             faux SIRH, IAM et ITSM pilotables (http://localhost:18090/_demo/faults) ;
#                                           c05-…, c06-… (capsules courtes riverain) — profil demo-video-riverain :
#                                             exemple autorisation riverain, portail activé, pré-vérification IA par le
#                                             modèle local d'Ollama (APP_PRECHECK_MODEL, défaut gemma4:12b) ;
#                                           c13-… (signature) — profil demo-video-signature : exemple convention
#                                             fournisseur, portail activé, bac à sable Yousign (clés dans .env ;
#                                             VIDEO_SIGNATURE_PROVIDER=stub pour répéter sans Yousign) ;
#                                           autres cNN-… (capsules courtes) — profil demo : la démo complète de
#                                             scripts/demo.sh
#   scripts/video.sh mail <scénario>      envoie un e-mail du tournage L1 : facture | inconnu | reponse
#   scripts/video.sh capsule [capsule]    sur une scène fraîche (start) : voix off (edge-tts), tournage (Playwright), montage
#                                         (ffmpeg) → frontend/capture/out/film/<capsule>/<langue>/<capsule>-<langue>.mp4,
#                                         .vtt, .srt ; capsule courte (cNN-…) : sans voix, sous-titres incrustés, sans son
#   scripts/video.sh shorts               sur une démo fraîche (start c01-dossier) : les capsules courtes de la démo
#                                         complète, dans l'ordre de leur numéro (certaines modifient la démo : on ne les
#                                         rejoue pas seules sans reset) ; C5 et C6 se tournent sur leur scène, à part
#   scripts/video.sh cards                cartons du teaser t00-teaser (§148) rendus par Remotion (scripts/video/remotion, Node
#                                         et le Chromium headless de Playwright ; pas de démo à lancer)
#                                         → frontend/capture/out/film/t00-teaser/<langue>/cards/<carton>.mp4
#   scripts/video.sh teaser               teaser t00-teaser (§148) : les cartons, puis les coupes prises dans les plans des
#                                         capsules déjà tournées dans la langue (pas de démo à lancer), muet, sous-titres
#                                         incrustés → frontend/capture/out/film/t00-teaser/<langue>/t00-teaser-<langue>.mp4
#   scripts/video.sh reset [--yes]        vide la base embarquée (application ARRÊTÉE) : le prochain start rejoue la scène
#
# Langue : APP_DEMO_LOCALE=fr (défaut) ou en ; entre deux langues ou deux scènes, reset. Connexion : comme
# scripts/demo.sh (Keycloak et variables OIDC dans .env). M2M_JWT_SECRET (dev seulement) : secret HMAC du client MCP de
# l'agent IA de la capsule L1.
set -euo pipefail
cd "$(dirname "$0")/.."

capsule_of() {
  local capsule="${1:-l1-mailroom}"
  [[ -f "scripts/video/capsules/$capsule.json" ]] || {
    echo "capsule inconnue : $capsule (scripts/video/capsules/*.json)" >&2
    exit 2
  }
  echo "$capsule"
}

case "${1:-}" in
  build)
    exec scripts/demo.sh build
    ;;
  mail-servers)
    exec podman compose --profile video up -d greenmail mailpit
    ;;
  start)
    capsule="$(capsule_of "${2:-}")"
    case "$capsule" in
      l1-mailroom) profile=demo-video-mailroom ;;
      l2-onboarding) profile=demo-video-onboarding ;;
      c05-*|c06-*) profile=demo-video-riverain ;;
      c13-*) profile=demo-video-signature ;;
      c[0-9][0-9]-*) profile=demo ;;
      *) echo "pas de scène pour la capsule $capsule" >&2; exit 2 ;;
    esac
    # Variables locales (OIDC, clé maîtresse des connecteurs) : .env s'il existe ; celles qui suivent priment.
    if [[ -f .env ]]; then set -a; . ./.env; set +a; fi
    # Scène riverain (TODO §143) : portail (liens envoyés à Mailpit) et pré-vérification par le modèle local d'Ollama.
    # Lue après .env : un APP_PRECHECK_* ou APP_PORTAL_* qui y figure l'emporte sur ces valeurs par défaut.
    # C5 (et S13) : variante du processus avec portail ; C6 : variante avec pré-vérification (même clé).
    scene_env=()
    if [[ "$profile" == demo-video-riverain ]]; then
      variant=portal
      [[ "$capsule" == c06-* ]] && variant=preverification
      scene_env=(APP_DEMO_RIVERAIN_VARIANT="$variant"
        APP_PORTAL_ENABLED=true APP_PORTAL_PUBLIC_BASE_URL="${APP_PORTAL_PUBLIC_BASE_URL:-http://localhost:8080}"
        APP_PRECHECK_ENABLED=true APP_PRECHECK_PROVIDER=openai-compatible
        APP_PRECHECK_BASE_URL="${APP_PRECHECK_BASE_URL:-http://localhost:11434/v1}"
        APP_PRECHECK_MODEL="${APP_PRECHECK_MODEL:-gemma4:12b}"
        # Un modèle local sur un poste traite les demandes une à une : C6 en envoie trois, chacune de une à deux minutes.
        APP_PRECHECK_TIMEOUT_SECONDS="${APP_PRECHECK_TIMEOUT_SECONDS:-600}"
        # gemma4 raisonne avant de répondre et épuise sinon les 2000 tokens de sortie : réponse coupée, JSON invalide
        # (docs/guides/DEPLOIEMENT.md, APP_PRECHECK_REASONING_EFFORT).
        APP_PRECHECK_REASONING_EFFORT="${APP_PRECHECK_REASONING_EFFORT:-none}")
    fi
    # Scène signature (§148) : exemple convention fournisseur, signataire externe (portail, liens envoyés à Mailpit),
    # bac à sable Yousign (YOUSIGN_SANDBOX_API_KEY, YOUSIGN_WEBHOOK_SECRET et YOUSIGN_SANDBOX_SIGNER_EMAIL dans .env) ;
    # VIDEO_SIGNATURE_PROVIDER=stub
    # pour répéter sans Yousign. Pas APP_SIGNATURE_PROVIDER : le .env d'un poste le fixe souvent à none (.env.local.example).
    if [[ "$profile" == demo-video-signature ]]; then
      scene_env=(APP_PORTAL_ENABLED=true APP_PORTAL_PUBLIC_BASE_URL="${APP_PORTAL_PUBLIC_BASE_URL:-http://localhost:8080}"
        APP_SIGNATURE_PROVIDER="${VIDEO_SIGNATURE_PROVIDER:-yousign}"
        APP_DEMO_SIGNATURE_DIR="${APP_DEMO_SIGNATURE_DIR:-exemples/convention-fournisseur}")
    fi
    jar="$(ls target/myrmacta-*.jar 2>/dev/null | grep -v '\.original$' | head -n1 || true)"
    [[ -n "$jar" ]] || { echo "jar introuvable : lancer d'abord « scripts/video.sh build »" >&2; exit 2; }
    # Comme scripts/demo.sh : profils fixés, variables DB_* écartées (base embarquée). Les e-mails sortants vont à Mailpit,
    # signés par un expéditeur de démo (D-MAIL-FROM) : jamais utilisateur@nom-du-poste à l'écran.
    # Échéances évaluées 30 s après le démarrage (au lieu de 2 min), puis toutes les 15 min : le rappel et l'escalade des
    # factures semées sont là au tournage.
    exec env -u DB_URL -u DB_USERNAME -u DB_PASSWORD -u DB_NAME -u DB_PORT \
      APP_ENVIRONMENT="${APP_ENVIRONMENT:-local}" \
      SPRING_PROFILES_ACTIVE="local,embedded-db,$profile" \
      APP_DEMO_MAILROOM_DIR="${APP_DEMO_MAILROOM_DIR:-exemples/digital-mail-room}" \
      APP_DEMO_ONBOARDING_DIR="${APP_DEMO_ONBOARDING_DIR:-exemples/onboarding-collaborateur}" \
      APP_DEMO_RIVERAIN_DIR="${APP_DEMO_RIVERAIN_DIR:-exemples/autorisation-riverain}" \
      APP_DEMO_LOCALE="${APP_DEMO_LOCALE:-fr}" \
      APP_I18N_DEFAULT_LOCALE="${APP_I18N_DEFAULT_LOCALE:-${APP_DEMO_LOCALE:-fr}}" \
      MAIL_HOST=localhost MAIL_PORT="${MAILPIT_SMTP_PORT:-1025}" MAIL_SMTP_AUTH=false MAIL_SMTP_STARTTLS=false \
      MAIL_FROM="${VIDEO_MAIL_FROM:-noreply@myrmacta.example}" \
      "${scene_env[@]}" \
      java -jar "$jar" --app.deadlines.initial-delay-ms=30000 --app.inbound.initial-delay=10s
    ;;
  mail)
    shift
    exec uv run scripts/video/send_mail.py "$@" --lang "${APP_DEMO_LOCALE:-fr}"
    ;;
  capsule)
    # La langue de la scène (APP_DEMO_LOCALE) est celle de la capsule. ffmpeg et uv dans le PATH ; .env pour l'agent IA.
    capsule="$(capsule_of "${2:-}")"
    lang="${APP_DEMO_LOCALE:-fr}"
    out="frontend/capture/out/film/$capsule"
    if [[ -f .env ]]; then set -a; . ./.env; set +a; fi
    uv run scripts/video/voice.py "scripts/video/capsules/$capsule.json" --lang "$lang" --out "$out"
    (cd frontend && CAPTURE_LANG="$lang" FILM_CAPSULE="$capsule" FILM_OUT="capture/out/film/$capsule" \
      npx playwright test -c playwright.film.config.ts)
    exec uv run scripts/video/assemble.py "scripts/video/capsules/$capsule.json" --lang "$lang" --out "$out"
    ;;
  shorts)
    for file in scripts/video/capsules/c[0-9][0-9]-*.json; do
      case "$(basename "$file")" in c05-*|c06-*) continue ;; esac
      "$0" capsule "$(basename "$file" .json)"
    done
    ;;
  cards)
    exec uv run scripts/video/cards.py scripts/video/capsules/t00-teaser.json --lang "${APP_DEMO_LOCALE:-fr}"
    ;;
  teaser)
    exec uv run scripts/video/teaser.py scripts/video/capsules/t00-teaser.json --lang "${APP_DEMO_LOCALE:-fr}"
    ;;
  reset)
    shift
    exec scripts/embedded-db.sh reset "$@"
    ;;
  *)
    sed -n '2,40p' "$0"
    exit 2
    ;;
esac
