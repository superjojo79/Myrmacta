#!/usr/bin/env bash
# Refresh AI DevKit skills as real files (no absolute symlinks to ~/.ai-devkit).
# Cloud Agents and fresh clones do not have the local ai-devkit cache, so
# committing portable copies keeps .cursor/skills and .claude/skills readable.
#
# Supports:
#   - registry skills declared in .ai-devkit.json (any registry under ~/.ai-devkit/skills)
#   - project-local skills under .project-skills/<name>/ (copied into every env)
#   - skills CLI installs under .agents/skills/<name>/ (npx skills add …)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "Installing/refreshing AI DevKit cache and project config..."
npx --yes ai-devkit@latest install --overwrite

CACHE_ROOT="${HOME}/.ai-devkit/skills"
PROJECT_SKILLS_DIR="${ROOT}/.project-skills"
AGENTS_SKILLS_DIR="${ROOT}/.agents/skills"

if [[ ! -d "$CACHE_ROOT" ]]; then
  echo "error: expected skill cache at $CACHE_ROOT" >&2
  exit 1
fi

resolve_cached_skill() {
  local registry="$1"
  local name="$2"
  local candidate

  for candidate in \
    "${CACHE_ROOT}/${registry}/skills/${name}" \
    "${CACHE_ROOT}/${registry}/${name}" \
    "${CACHE_ROOT}/${registry}/.cursor/skills/${name}" \
    "${CACHE_ROOT}/${registry}/.claude/skills/${name}" \
    "${CACHE_ROOT}/${registry}/.agents/skills/${name}" \
    "${CACHE_ROOT}/${registry}/.github/skills/${name}"
  do
    if [[ -d "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

copy_skill() {
  local src="$1"
  local dest="$2"
  local label="$3"

  rm -rf "$dest"
  cp -a "$src" "$dest"
  rm -rf "${dest}/.git"
  echo "vendored ${dest}  (${label})"
}

vendor_env() {
  local env_dir="$1"
  local skills_dir="$env_dir/skills"
  mkdir -p "$skills_dir"

  local registry name src
  while IFS=$'\t' read -r registry name; do
    [[ -z "${registry:-}" || -z "${name:-}" ]] && continue

    if ! src="$(resolve_cached_skill "$registry" "$name")"; then
      echo "error: missing cached skill for ${registry}/${name}" >&2
      echo "hint: run: npx ai-devkit@latest skill add ${registry} ${name}" >&2
      exit 1
    fi
    copy_skill "$src" "${skills_dir}/${name}" "from ${registry}"
  done < <(python3 - <<'PY'
import json
with open(".ai-devkit.json") as f:
    cfg = json.load(f)
for s in cfg.get("skills", []):
    print(f"{s['registry']}\t{s['name']}")
PY
)

  # Project-local skills (not managed by ai-devkit registries).
  if [[ -d "$PROJECT_SKILLS_DIR" ]]; then
    local skill_dir
    for skill_dir in "$PROJECT_SKILLS_DIR"/*/; do
      [[ -d "$skill_dir" ]] || continue
      name="$(basename "$skill_dir")"
      [[ -f "${skill_dir}/SKILL.md" ]] || {
        echo "error: local skill missing SKILL.md: ${skill_dir}" >&2
        exit 1
      }
      copy_skill "$skill_dir" "${skills_dir}/${name}" "from .project-skills"
    done
  fi

  # Skills installed via `npx skills add` (universal .agents/skills tree).
  if [[ -d "$AGENTS_SKILLS_DIR" ]]; then
    local skill_dir
    for skill_dir in "$AGENTS_SKILLS_DIR"/*/; do
      [[ -d "$skill_dir" ]] || continue
      name="$(basename "$skill_dir")"
      [[ -f "${skill_dir}/SKILL.md" ]] || {
        echo "error: agents skill missing SKILL.md: ${skill_dir}" >&2
        exit 1
      }
      copy_skill "$skill_dir" "${skills_dir}/${name}" "from .agents/skills"
    done
  fi
}

vendor_env ".cursor"
vendor_env ".claude"

# Preserve repo-local patches that upstream skill refreshes must not clobber.
preserve_local_patches() {
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    local patched=(
      ".cursor/skills/dev-lifecycle/scripts/check-status.sh"
      ".claude/skills/dev-lifecycle/scripts/check-status.sh"
    )
    local path
    for path in "${patched[@]}"; do
      if git cat-file -e "HEAD:${path}" 2>/dev/null; then
        git checkout HEAD -- "$path"
        echo "restored local patch: ${path}"
      fi
    done
  fi
}
preserve_local_patches

echo "Done. Skills are portable copies under .cursor/skills and .claude/skills."
