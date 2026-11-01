#!/usr/bin/env bash
# Garde-fou CI : le jar de production ne doit contenir NI zonky, NI ses binaires PostgreSQL, NI le
# lanceur embarqué, NI la démo mail room (profil Maven `embedded-db` uniquement). Usage : scripts/check-no-embedded-db-in-jar.sh [jar]
set -euo pipefail
jar="${1:-$(ls target/myrmacta-*.jar 2>/dev/null | grep -v '\.original$' | head -n1)}"
[[ -f "$jar" ]] || { echo "jar introuvable (lancer ./mvnw package d'abord)" >&2; exit 2; }
if unzip -l "$jar" | grep -iE 'zonky|embedded-postgres|ZonkyEmbeddedDbLauncher|db/embedded/|MailRoomDemoSeeder'; then
  echo "ERREUR : $jar embarque le PostgreSQL embarqué (démo/QA) — interdit en production." >&2
  exit 1
fi
echo "OK : $jar ne contient pas le PostgreSQL embarqué."
