#!/usr/bin/env bash
# Tests de mutation du frontend sur une PR (TODO §108) : fichiers à muter, déduits des fichiers modifiés depuis la base.
# Un fichier .ts du périmètre modifié est ciblé ; une spec modifiée cible le fichier qu'elle teste (foo.spec.ts → foo.ts).
# Seuls les fichiers du périmètre de frontend/stryker.config.mjs (ligne `const scope = [...]`) sont gardés.
# Sortie : chemins relatifs à frontend/, séparés par des virgules (argument de `stryker run --mutate`), vide si rien.
# Usage : scripts/mutation-targets-frontend.sh <ref-de-base>
#         scripts/mutation-targets-frontend.sh --self-test
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"

# const scope = ['src/stores', 'portal'] → src/stores, portal (un répertoire par ligne)
scope_dirs() {
  local config="$1" line
  line="$(grep -E "^const scope = \[" "$config" || true)"
  [[ -n "$line" ]] || { echo "ligne « const scope = [...] » introuvable dans $config" >&2; return 1; }
  grep -oE "'[^']+'" <<< "$line" | tr -d "'"
}

targets() {
  local repo="$1" base="$2" dirs file source dir
  dirs="$(scope_dirs "$repo/frontend/stryker.config.mjs")"
  git -C "$repo" diff --name-only --diff-filter=AMR "$base"...HEAD -- frontend \
    | while IFS= read -r file; do
        file="${file#frontend/}"
        case "$file" in
          *.d.ts | portal/main.ts) continue ;;
          *.spec.ts) source="${file%.spec.ts}.ts" ;;
          *.test.ts) source="${file%.test.ts}.ts" ;;
          *.ts) source="$file" ;;
          *) continue ;;
        esac
        [[ -f "$repo/frontend/$source" && "$source" != portal/main.ts ]] || continue
        while IFS= read -r dir; do
          if [[ "$source" == "$dir"/* ]]; then
            printf '%s\n' "$source"
            break
          fi
        done <<< "$dirs"
      done \
    | sort -u | paste -sd, -
}

self_test() {
  local tmp out expected dirs dir
  tmp="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '$tmp'" RETURN
  (
    cd "$tmp"
    git init -q -b main
    git config user.email test@example.invalid
    git config user.name test
    mkdir -p frontend/src/stores frontend/src/views frontend/portal
    printf "// x\nconst scope = ['src/stores', 'portal']\n" > frontend/stryker.config.mjs
    for f in src/stores/kept.ts src/stores/fromSpec.ts src/views/outside.ts portal/main.ts portal/client.ts; do
      echo "export const x = 1" > "frontend/$f"
    done
    git add . && git commit -qm base
    git checkout -qb pr
    echo '// changé' >> frontend/src/stores/kept.ts
    echo '// changé' >> frontend/src/views/outside.ts
    echo '// changé' >> frontend/portal/main.ts
    echo 'it("x", () => {})' > frontend/src/stores/fromSpec.spec.ts
    echo 'it("x", () => {})' > frontend/src/stores/orphan.spec.ts
    echo 'export type T = 1' > frontend/src/stores/types.d.ts
    echo '<template />' > frontend/src/stores/View.vue
    git add . && git commit -qm pr
  )
  out="$(targets "$tmp" main)"
  expected='src/stores/fromSpec.ts,src/stores/kept.ts'
  if [[ "$out" != "$expected" ]]; then
    printf 'self-test KO\n  attendu : %s\n  obtenu  : %s\n' "$expected" "$out" >&2
    exit 1
  fi
  git -C "$tmp" checkout -q main
  out="$(targets "$tmp" main)"
  if [[ -n "$out" ]]; then
    printf 'self-test KO : aucune modification, obtenu %s\n' "$out" >&2
    exit 1
  fi
  # Le périmètre réel se lit dans la configuration du dépôt et chaque répertoire existe.
  dirs="$(scope_dirs "$root/frontend/stryker.config.mjs")"
  while IFS= read -r dir; do
    [[ -d "$root/frontend/$dir" ]] || { echo "self-test KO : répertoire du périmètre absent : frontend/$dir" >&2; exit 1; }
  done <<< "$dirs"
  echo "self-test OK"
}

case "${1:-}" in
  --self-test) self_test ;;
  "" | -*) echo "usage : $0 <ref-de-base> | --self-test" >&2; exit 2 ;;
  *) targets "$root" "$1" ;;
esac
