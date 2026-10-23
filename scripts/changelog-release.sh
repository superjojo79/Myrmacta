#!/usr/bin/env bash
# Ajoute à CHANGELOG.md la section d'une release, générée par git-cliff (cliff.toml) à partir des PR
# mergées sur main (TODO §95, D-REMED-CHANGELOG). La section est insérée avant la première section
# existante (« ## … »), sous l'en-tête du fichier.
#
#   scripts/changelog-release.sh 0.2.0                 depuis le dernier tag
#   scripts/changelog-release.sh 0.1.0 <sha>..HEAD     plage explicite (première release : depuis le merge
#                                                      de la PR #182, dernière entrée écrite à la main)
#   scripts/changelog-release.sh --preview             PR mergées depuis le dernier tag, sans rien écrire
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

GIT_CLIFF=(npx --yes git-cliff@2.10.1 --config cliff.toml)

if [[ "${1:-}" == "--preview" ]]; then
  exec "${GIT_CLIFF[@]}" --unreleased
fi

version="${1:?usage: scripts/changelog-release.sh <version> [<plage git>] | --preview}"
if [[ $# -ge 2 ]]; then
  range="$2"
elif last_tag="$(git describe --tags --abbrev=0 2>/dev/null)"; then
  range="${last_tag}..HEAD"
else
  echo "aucun tag : préciser la plage, par exemple <sha du merge de la PR #182>..HEAD" >&2
  exit 1
fi

section="$("${GIT_CLIFF[@]}" --tag "$version" "$range")"
SECTION="$section" python3 -I - <<'PY'
import os
path = "CHANGELOG.md"
text = open(path, encoding="utf-8").read()
marker = text.find("\n## ")
section = os.environ["SECTION"].strip() + "\n\n"
text = text + "\n" + section if marker < 0 else text[: marker + 1] + section + text[marker + 1:]
open(path, "w", encoding="utf-8").write(text)
PY
echo "CHANGELOG.md : section ${version} ajoutée (${range})"
