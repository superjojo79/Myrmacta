#!/usr/bin/env bash
# Refresh AI DevKit skills as real files (no absolute symlinks to ~/.ai-devkit).
# Cloud Agents and fresh clones do not have the local ai-devkit cache, so
# committing portable copies keeps .cursor/skills and .claude/skills readable.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "Installing/refreshing AI DevKit cache and project config..."
npx --yes ai-devkit@latest install --overwrite

SRC="${HOME}/.ai-devkit/skills/codeaholicguy/ai-devkit/skills"
if [[ ! -d "$SRC" ]]; then
  echo "error: expected skill cache at $SRC" >&2
  exit 1
fi

vendor_env() {
  local env_dir="$1"
  local skills_dir="$env_dir/skills"
  mkdir -p "$skills_dir"

  # Skills declared in .ai-devkit.json
  local names
  names="$(python3 - <<'PY'
import json
with open(".ai-devkit.json") as f:
    cfg = json.load(f)
for s in cfg.get("skills", []):
    print(s["name"])
PY
)"

  while IFS= read -r name; do
    [[ -z "$name" ]] && continue
    if [[ ! -d "$SRC/$name" ]]; then
      echo "error: missing cached skill: $SRC/$name" >&2
      exit 1
    fi
    rm -rf "$skills_dir/$name"
    cp -a "$SRC/$name" "$skills_dir/$name"
    echo "vendored $skills_dir/$name"
  done <<< "$names"
}

vendor_env ".cursor"
vendor_env ".claude"

echo "Done. Skills are portable copies under .cursor/skills and .claude/skills."
