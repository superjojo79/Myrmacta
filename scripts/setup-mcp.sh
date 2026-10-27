#!/usr/bin/env bash
# Bootstrap local/Cloud Agent dependencies for Myrmacta MCP servers (Serena).
# The @ai-devkit/memory server was dropped (TODO §107): project memory lives in the repo.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export PATH="${HOME}/.local/bin:${PATH}"

echo "==> Installing uv (if needed)"
if ! command -v uv >/dev/null 2>&1; then
  curl -LsSf https://astral.sh/uv/install.sh | sh
  # shellcheck disable=SC1091
  source "${HOME}/.local/bin/env"
fi

echo "==> Installing serena-agent"
uv tool install -p 3.13 serena-agent

link_bin() {
  local src="$1" dest="$2"
  if [[ -w "$(dirname "${dest}")" ]]; then
    ln -sfn "${src}" "${dest}" || true
  elif command -v sudo >/dev/null 2>&1; then
    sudo ln -sfn "${src}" "${dest}" || true
  fi
}

link_bin "${HOME}/.local/bin/uvx" /usr/local/bin/uvx
link_bin "${HOME}/.local/bin/serena" /usr/local/bin/serena

# Compatibility shim for a broken Cloud Agent Serena config that passes a single
# malformed arg to `uv` instead of launching `serena start-mcp-server`.
UV_WRAPPER=/usr/local/bin/uv
UV_WRAPPER_CONTENT=$(cat <<EOF
#!/usr/bin/env bash
set -euo pipefail
REAL_UV="${HOME}/.local/bin/uv"
if [[ \$# -eq 1 && "\$1" == *serena-agent* && "\$1" == *'"tool"'* ]]; then
  exec "${HOME}/.local/bin/serena" start-mcp-server \\
    --context ide \\
    --project "${ROOT}" \\
    --enable-web-dashboard false \\
    --open-web-dashboard false
fi
exec "\$REAL_UV" "\$@"
EOF
)
if [[ -w /usr/local/bin ]]; then
  printf '%s\n' "${UV_WRAPPER_CONTENT}" > "${UV_WRAPPER}"
  chmod +x "${UV_WRAPPER}"
elif command -v sudo >/dev/null 2>&1; then
  printf '%s\n' "${UV_WRAPPER_CONTENT}" | sudo tee "${UV_WRAPPER}" >/dev/null
  sudo chmod +x "${UV_WRAPPER}"
fi

echo "==> Initializing Serena"
serena init -b LSP >/dev/null || true

echo "==> MCP bootstrap complete"
echo "Project config: ${ROOT}/.cursor/mcp.json"
echo "Cloud Agents: paste the same mcpServers entries in cursor.com/agents MCP settings"
echo "  (Cloud Agents do not load .cursor/mcp.json automatically)."
