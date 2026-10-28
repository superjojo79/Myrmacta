#!/usr/bin/env bash
# Tests de mutation du frontend sur une PR (TODO §108) : fichiers à muter, déduits des fichiers modifiés depuis la base.
# Un fichier .ts du périmètre modifié est ciblé ; une spec modifiée cible le fichier qu'elle teste (foo.spec.ts → foo.ts)
# ou, sans source homonyme, les fichiers du périmètre qu'elle importe.
# Seuls les fichiers du périmètre de frontend/stryker.config.mjs (ligne `const scope = [...]`) sont gardés.
# Sortie : chemins relatifs à frontend/, séparés par des virgules (argument de `stryker run --mutate`), vide si rien.
# Usage : scripts/mutation-targets-frontend.sh <ref-de-base>
#         scripts/mutation-targets-frontend.sh --self-test
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"

# const scope = ['src/stores', 'portal'] → src/stores, portal (un répertoire par ligne)
scope_dirs() {
  local config="$1" line dirs
  line="$(grep -E "^const scope = \[" "$config" || true)"
  dirs="$(grep -oE "'[^']+'" <<< "$line" | tr -d "'" || true)"
  [[ -n "$dirs" ]] || { echo "aucun répertoire lu sur une ligne « const scope = ['…'] » de $config" >&2; return 1; }
  printf '%s\n' "$dirs"
}

# Fichiers .ts que vise une spec (chemins relatifs à frontend/) : sa source homonyme si elle existe (foo.spec.ts → foo.ts),
# sinon les modules qu'elle importe en relatif ou par l'alias @/ (spec agrégée, comme src/api/apiCoverage.spec.ts).
spec_sources() {
  local front="$1" spec="$2" source spec_dir module path
  source="${spec%.*.ts}.ts"
  if [[ -f "$front/$source" ]]; then
    printf '%s\n' "$source"
    return
  fi
  spec_dir="$(dirname "$spec")"
  grep -oE "(from|import)[[:space:]]*\(?[[:space:]]*['\"](\.{1,2}/|@/)[^'\"]+['\"]" "$front/$spec" \
    | grep -oE "['\"][^'\"]+['\"]" | tr -d "'\"" \
    | while IFS= read -r module; do
        case "$module" in
          @/*) path="src/${module#@/}" ;;
          *) path="$(realpath -m --relative-to="$front" "$front/$spec_dir/$module")" ;;
        esac
        path="${path%.ts}"
        if [[ -f "$front/$path.ts" ]]; then
          printf '%s\n' "$path.ts"
        elif [[ -f "$front/$path/index.ts" ]]; then
          printf '%s\n' "$path/index.ts"
        fi
      done
}

targets() {
  local repo="$1" base="$2" front="$1/frontend" dirs file source dir
  dirs="$(scope_dirs "$front/stryker.config.mjs")"
  git -C "$repo" -c core.quotePath=false diff -z --name-only --diff-filter=AMR "$base"...HEAD -- frontend \
    | while IFS= read -r -d '' file; do
        file="${file#frontend/}"
        case "$file" in
          *.d.ts) continue ;;
          *.spec.ts | *.test.ts) spec_sources "$front" "$file" ;;
          *.ts) printf '%s\n' "$file" ;;
        esac
      done \
    | while IFS= read -r source; do
        case "$source" in
          *.spec.ts | *.test.ts | *.d.ts | portal/main.ts) continue ;;
          *[,\[\]{}*?]*)
            # --mutate sépare la liste aux virgules et lit chaque entrée comme un glob : un tel nom ne serait pas muté.
            echo "ignoré (nom illisible pour stryker --mutate) : frontend/$source" >&2
            continue ;;
        esac
        [[ -f "$front/$source" ]] || continue
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
    for f in src/stores/kept.ts src/stores/fromSpec.ts src/stores/viaImport.ts src/stores/viaAlias.ts \
      src/views/outside.ts portal/main.ts portal/client.ts; do
      echo "export const x = 1" > "frontend/$f"
    done
    git add . && git commit -qm base
    git checkout -qb pr
    echo '// changé' >> frontend/src/stores/kept.ts
    echo '// changé' >> frontend/src/views/outside.ts
    echo '// changé' >> frontend/portal/main.ts
    echo 'it("x", () => {})' > frontend/src/stores/fromSpec.spec.ts
    echo 'it("x", () => {})' > frontend/src/stores/orphan.spec.ts
    # Spec agrégée, sans source homonyme : elle vise les fichiers du périmètre qu'elle importe.
    printf "import { a } from './viaImport'\nimport { b } from '@/stores/viaAlias.ts'\nimport { c } from '../views/outside'\nimport { mount } from '@vue/test-utils'\n" \
      > frontend/src/stores/aggregate.spec.ts
    echo 'export type T = 1' > frontend/src/stores/types.d.ts
    echo 'export const y = 1' > 'frontend/src/stores/a,b.ts'
    echo '<template />' > frontend/src/stores/View.vue
    git add . && git commit -qm pr
  )
  out="$(targets "$tmp" main 2> "$tmp/stderr")"
  grep -q "ignoré (nom illisible pour stryker --mutate) : frontend/src/stores/a,b.ts" "$tmp/stderr" \
    || { echo "self-test KO : un nom avec virgule n'est pas signalé" >&2; exit 1; }
  expected='src/stores/fromSpec.ts,src/stores/kept.ts,src/stores/viaAlias.ts,src/stores/viaImport.ts'
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
