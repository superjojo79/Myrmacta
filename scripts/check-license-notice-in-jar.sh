#!/usr/bin/env bash
# Garde-fou CI : le jar de production embarque LICENSE (PolyForm-Noncommercial-1.0.0) et NOTICE, octets
# identiques aux fichiers de la racine du dépôt, aux entrées META-INF/LICENSE et
# META-INF/NOTICE (racine du jar, à côté du manifeste — pas la licence OFL d'une police).
# Usage : scripts/check-license-notice-in-jar.sh [jar]
#         scripts/check-license-notice-in-jar.sh --self-test
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"

# Noms d'entrées exacts. unzip -p traite le motif comme un glob : on exige une ligne
# zipinfo identique, puis on extrait cette seule entrée.
entries_for() {
  unzip -Z1 "$1"
}

extract_exact() {
  local jar="$1" entry="$2" dest="$3"
  local count
  count="$(entries_for "$jar" | grep -c -x -F "$entry" || true)"
  if [[ "$count" -ne 1 ]]; then
    return 1
  fi
  unzip -p "$jar" "$entry" > "$dest"
}

check_jar() {
  local jar="$1"
  [[ -f "$jar" ]] || { echo "jar introuvable : $jar (lancer ./mvnw package d'abord)" >&2; exit 2; }
  local tmp failed=0 name entry src
  tmp="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '$tmp'" RETURN
  for name in LICENSE NOTICE; do
    entry="META-INF/$name"
    src="$root/$name"
    if ! extract_exact "$jar" "$entry" "$tmp/$name" || [[ ! -s "$tmp/$name" ]]; then
      echo "ERREUR : $jar ne contient pas $entry" >&2
      failed=1
      continue
    fi
    if ! cmp -s "$src" "$tmp/$name"; then
      echo "ERREUR : $entry dans $jar diffère de $name à la racine du dépôt" >&2
      failed=1
    fi
  done
  if [[ "$failed" -ne 0 ]]; then
    exit 1
  fi
  echo "OK : $jar contient META-INF/LICENSE et META-INF/NOTICE, identiques à la racine du dépôt."
}

self_test() {
  local tmp bad only_license decoy mismatch status
  tmp="$(mktemp -d)"
  # shellcheck disable=SC2064
  trap "rm -rf '$tmp'" RETURN
  command -v jar >/dev/null || { echo "self-test : commande jar introuvable" >&2; exit 1; }

  mkdir -p "$tmp/bad/fonts" "$tmp/only/META-INF" "$tmp/decoy/META-INF" "$tmp/decoy/BOOT-INF/classes/META-INF" \
    "$tmp/good/META-INF" "$tmp/mismatch/META-INF"
  echo "SIL OFL" > "$tmp/bad/fonts/LICENSE-LiberationSans.txt"
  cp "$root/LICENSE" "$tmp/only/META-INF/LICENSE"
  cp "$root/LICENSE" "$tmp/decoy/BOOT-INF/classes/META-INF/LICENSE"
  cp "$root/NOTICE" "$tmp/decoy/BOOT-INF/classes/META-INF/NOTICE"
  printf 'pas la licence\n' > "$tmp/decoy/META-INF/LICENSE"
  cp "$root/NOTICE" "$tmp/decoy/META-INF/NOTICE"
  cp "$root/LICENSE" "$root/NOTICE" "$tmp/good/META-INF/"
  cp "$root/LICENSE" "$tmp/mismatch/META-INF/LICENSE"
  cp "$root/NOTICE" "$tmp/mismatch/META-INF/NOTICE"
  printf '\n' >> "$tmp/mismatch/META-INF/NOTICE"

  # -C ne s'applique qu'au fichier qui suit : le répéter pour chaque entrée.
  jar --create --file "$tmp/bad.jar" -C "$tmp/bad" fonts/LICENSE-LiberationSans.txt
  jar --create --file "$tmp/only.jar" -C "$tmp/only" META-INF/LICENSE
  jar --create --file "$tmp/decoy.jar" -C "$tmp/decoy" BOOT-INF/classes/META-INF/LICENSE \
    -C "$tmp/decoy" BOOT-INF/classes/META-INF/NOTICE \
    -C "$tmp/decoy" META-INF/LICENSE \
    -C "$tmp/decoy" META-INF/NOTICE
  jar --create --file "$tmp/good.jar" -C "$tmp/good" META-INF/LICENSE -C "$tmp/good" META-INF/NOTICE
  jar --create --file "$tmp/mismatch.jar" -C "$tmp/mismatch" META-INF/LICENSE -C "$tmp/mismatch" META-INF/NOTICE

  expect_fail() {
    local label="$1" subject="$2"
    set +e
    "$0" "$subject" >"$tmp/out" 2>"$tmp/err"
    status=$?
    set -e
    if [[ "$status" -eq 0 ]]; then
      echo "self-test : $label aurait dû échouer" >&2
      cat "$tmp/err" >&2
      exit 1
    fi
    if [[ "$status" -eq 2 ]]; then
      echo "self-test : $label a échoué pour une jar introuvable (code 2)" >&2
      exit 1
    fi
  }

  expect_fail "un jar qui n'a que la licence OFL" "$tmp/bad.jar"
  expect_fail "un jar sans NOTICE" "$tmp/only.jar"
  expect_fail "un META-INF/LICENSE qui n'est pas le fichier de la racine" "$tmp/decoy.jar"
  expect_fail "un NOTICE différent de la racine" "$tmp/mismatch.jar"

  "$0" "$tmp/good.jar" >"$tmp/out"
  echo "OK : auto-test du contrôle LICENSE/NOTICE."
}

if [[ "${1:-}" == "--self-test" ]]; then
  self_test
  exit 0
fi

jar_path="${1:-}"
if [[ -z "$jar_path" ]]; then
  shopt -s nullglob
  candidates=("$root"/target/flowforge-*.jar)
  shopt -u nullglob
  for candidate in "${candidates[@]}"; do
    [[ "$candidate" == *.original ]] && continue
    jar_path="$candidate"
    break
  done
fi
[[ -n "${jar_path:-}" ]] || { echo "jar introuvable (lancer ./mvnw package d'abord)" >&2; exit 2; }
check_jar "$jar_path"
