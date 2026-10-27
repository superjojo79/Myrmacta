#!/usr/bin/env bash
# Tests de mutation sur une PR (TODO §107) : classes à muter, déduites des fichiers modifiés depuis la base.
# Une classe de production modifiée est ciblée ; un test modifié cible la classe qu'il porte (FooTest → Foo).
# Seules les classes du périmètre du profil Maven `mutation` (propriété pit.targetClasses du pom) sont gardées.
# Sortie : motifs PIT séparés par des virgules (Foo et Foo$*), vide si rien n'est à muter.
# Usage : scripts/mutation-targets.sh <ref-de-base>
#         scripts/mutation-targets.sh --self-test
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"

# be.myrmacta.domain.*,be.myrmacta.infrastructure.expression.* → src/main/java/be/myrmacta/domain/ …
scope_prefixes() {
  local pom="$1" scope
  scope="$(sed -n 's:.*<pit.targetClasses>\(.*\)</pit.targetClasses>.*:\1:p' "$pom")"
  [[ -n "$scope" ]] || { echo "pit.targetClasses introuvable dans $pom" >&2; return 1; }
  tr ',' '\n' <<< "$scope" | sed -e 's/\*$//' -e 's:\.:/:g' -e 's:^:src/main/java/:'
}

targets() {
  local repo="$1" base="$2" prefixes file main fqcn
  prefixes="$(scope_prefixes "$repo/pom.xml")"
  git -C "$repo" diff --name-only --diff-filter=AMR "$base"...HEAD -- src/main/java src/test/java \
    | while IFS= read -r file; do
        case "$file" in
          src/main/java/*.java) main="$file" ;;
          src/test/java/*Test.java) main="src/main/java/${file#src/test/java/}"; main="${main%Test.java}.java" ;;
          src/test/java/*Tests.java) main="src/main/java/${file#src/test/java/}"; main="${main%Tests.java}.java" ;;
          *) continue ;;
        esac
        [[ -f "$repo/$main" ]] || continue
        while IFS= read -r prefix; do
          if [[ "$main" == "$prefix"* ]]; then
            fqcn="${main#src/main/java/}"; fqcn="${fqcn%.java}"; fqcn="${fqcn//\//.}"
            printf '%s\n%s$*\n' "$fqcn" "$fqcn"
            break
          fi
        done <<< "$prefixes"
      done \
    | sort -u | paste -sd, -
}

self_test() {
  local tmp out expected
  tmp="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '$tmp'" RETURN
  (
    cd "$tmp"
    git init -q -b main
    git config user.email test@example.invalid
    git config user.name test
    printf '<project><properties>\n<pit.targetClasses>be.x.domain.*,be.x.infrastructure.expression.*</pit.targetClasses>\n</properties></project>\n' > pom.xml
    mkdir -p src/main/java/be/x/domain/a src/main/java/be/x/infrastructure/expression src/main/java/be/x/infrastructure/web \
      src/test/java/be/x/domain/a src/test/java/be/x/infrastructure/web
    for f in domain/a/Kept domain/a/FromTest domain/a/Gone infrastructure/expression/Engine infrastructure/web/Api; do
      echo "class $(basename "$f") {}" > "src/main/java/be/x/$f.java"
    done
    git add . && git commit -qm base
    git checkout -qb pr
    echo '// changé' >> src/main/java/be/x/domain/a/Kept.java
    echo '// changé' >> src/main/java/be/x/infrastructure/expression/Engine.java
    echo '// changé' >> src/main/java/be/x/infrastructure/web/Api.java
    echo 'class FromTestTest {}' > src/test/java/be/x/domain/a/FromTestTest.java
    echo 'class OrphanTest {}' > src/test/java/be/x/domain/a/OrphanTest.java
    echo 'class ApiTest {}' > src/test/java/be/x/infrastructure/web/ApiTest.java
    git rm -q src/main/java/be/x/domain/a/Gone.java
    git add . && git commit -qm pr
  )
  out="$(targets "$tmp" main)"
  expected='be.x.domain.a.FromTest,be.x.domain.a.FromTest$*,be.x.domain.a.Kept,be.x.domain.a.Kept$*,be.x.infrastructure.expression.Engine,be.x.infrastructure.expression.Engine$*'
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
  echo "self-test OK"
}

case "${1:-}" in
  --self-test) self_test ;;
  "" | -*) echo "usage : $0 <ref-de-base> | --self-test" >&2; exit 2 ;;
  *) targets "$root" "$1" ;;
esac
