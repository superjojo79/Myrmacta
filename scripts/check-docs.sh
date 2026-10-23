#!/usr/bin/env bash
# Contrôles de cohérence de la documentation (TODO §95) : liens, copies de skills, références
# « TODO §N » et identifiants de décision cités par le code, versions du README.
#
#   scripts/check-docs.sh                 tous les contrôles
#   scripts/check-docs.sh links skills    une partie seulement
#   scripts/check-docs.sh --self-test     prouve que chaque contrôle détecte sa faute
#
# Hors ligne, rapide, même résultat en local et en CI (job « docs » de .github/workflows/ci.yml).
set -euo pipefail
exec python3 -I "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/check_docs.py" "$@"
