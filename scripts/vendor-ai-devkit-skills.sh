#!/usr/bin/env bash
# Refresh AI DevKit skills as real files (no absolute symlinks to ~/.ai-devkit).
# Cloud Agents and fresh clones do not have the local ai-devkit cache, so
# committing portable copies keeps .cursor/skills and .claude/skills readable.
#
# Supports:
#   - registry skills declared in .ai-devkit.json (any registry under ~/.ai-devkit/skills)
#   - project-local skills under .project-skills/<name>/ (copied into every env)
#   - skills CLI installs under .agents/skills/<name>/ (npx skills add …)
#
# Usage: vendor-ai-devkit-skills.sh [--local]
#   --local  no network: registry skill copies are left as they are; only project-local
#            and .agents skills are recopied, and undeclared skills are removed.
#
# The ai-devkit version is pinned by the "version" field of .ai-devkit.json (TODO §107).
# Skills that are no longer declared anywhere are removed from both copies.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

LOCAL=0
case "${1:-}" in
  "") ;;
  --local) LOCAL=1 ;;
  *) echo "usage: $0 [--local]" >&2; exit 2 ;;
esac

PYTHON="$(command -v python3 || command -v python)"
CACHE_ROOT="${HOME}/.ai-devkit/skills"
PROJECT_SKILLS_DIR="${ROOT}/.project-skills"
AGENTS_SKILLS_DIR="${ROOT}/.agents/skills"
ENVS=(".cursor" ".claude")

registry_skills() {
  "$PYTHON" - <<'PY'
import json
with open(".ai-devkit.json") as f:
    cfg = json.load(f)
for s in cfg.get("skills", []):
    print(f"{s['registry']}\t{s['name']}")
PY
}

if [[ "$LOCAL" -eq 0 ]]; then
  version="$("$PYTHON" -c 'import json; print(json.load(open(".ai-devkit.json"))["version"])')"
  # The installer also writes its own slash commands; they belong to the dropped
  # dev-lifecycle workflow, so remove whatever it adds.
  before="$(find "${ENVS[@]/%//commands}" -type f 2>/dev/null | sort || true)"
  # The installer rewrites .ai-devkit.json and silently drops a skill its registry no longer
  # provides; keep the declared list authoritative and fail instead of losing a skill.
  config_backup="$(mktemp)"
  cp .ai-devkit.json "$config_backup"
  declared_before="$(registry_skills | tr -d '\r' | sort)"
  echo "Installing/refreshing AI DevKit ${version} cache and project config..."
  npx --yes "ai-devkit@${version}" install --overwrite
  declared_after="$(registry_skills | tr -d '\r' | sort)"
  cp "$config_backup" .ai-devkit.json
  rm -f "$config_backup"
  after="$(find "${ENVS[@]/%//commands}" -type f 2>/dev/null | sort || true)"
  comm -13 <(printf '%s\n' "$before") <(printf '%s\n' "$after") | while IFS= read -r added; do
    [[ -n "$added" ]] && rm -f "$added" && echo "removed installer command ${added}"
  done
  for env_dir in "${ENVS[@]}"; do
    rmdir "${env_dir}/commands" 2>/dev/null || true
  done
  dropped="$(comm -23 <(printf '%s\n' "$declared_before") <(printf '%s\n' "$declared_after"))"
  if [[ -n "$dropped" ]]; then
    echo "error: the installer dropped skills its registry no longer provides:" >&2
    printf '%s\n' "$dropped" | tr '\t' '/' | sed 's/^/  /' >&2
    echo "hint: find their new source, or remove them from .ai-devkit.json; copies are left untouched" >&2
    exit 1
  fi

  if [[ ! -d "$CACHE_ROOT" ]]; then
    echo "error: expected skill cache at $CACHE_ROOT" >&2
    exit 1
  fi
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
  local declared=()

  local registry name src
  while IFS=$'\t' read -r registry name; do
    name="${name%$'\r'}"  # Python on Windows ends lines with CRLF
    [[ -z "${registry:-}" || -z "${name:-}" ]] && continue
    declared+=("$name")

    if [[ "$LOCAL" -eq 1 ]]; then
      [[ -d "${skills_dir}/${name}" ]] || echo "warning: ${skills_dir}/${name} missing; rerun without --local" >&2
      continue
    fi
    if ! src="$(resolve_cached_skill "$registry" "$name")"; then
      echo "error: missing cached skill for ${registry}/${name}" >&2
      echo "hint: run: npx ai-devkit skill add ${registry} ${name}" >&2
      exit 1
    fi
    copy_skill "$src" "${skills_dir}/${name}" "from ${registry}"
  done < <(registry_skills)

  # Project-local skills (not managed by ai-devkit registries), then skills installed via
  # `npx skills add` (universal .agents/skills tree).
  local source_dir skill_dir
  for source_dir in "$PROJECT_SKILLS_DIR" "$AGENTS_SKILLS_DIR"; do
    [[ -d "$source_dir" ]] || continue
    for skill_dir in "$source_dir"/*/; do
      [[ -d "$skill_dir" ]] || continue
      name="$(basename "$skill_dir")"
      [[ -f "${skill_dir}/SKILL.md" ]] || {
        echo "error: skill missing SKILL.md: ${skill_dir}" >&2
        exit 1
      }
      declared+=("$name")
      copy_skill "$skill_dir" "${skills_dir}/${name}" "from ${source_dir#"$ROOT"/}"
    done
  done

  # Skills no longer declared anywhere.
  local existing
  for existing in "$skills_dir"/*/; do
    [[ -d "$existing" ]] || continue
    name="$(basename "$existing")"
    if [[ ! " ${declared[*]} " == *" ${name} "* ]]; then
      rm -rf "$existing"
      echo "removed ${skills_dir}/${name}  (no longer declared)"
    fi
  done
}

for env_dir in "${ENVS[@]}"; do
  vendor_env "$env_dir"
done

echo "Done. Skills are portable copies under .cursor/skills and .claude/skills."
