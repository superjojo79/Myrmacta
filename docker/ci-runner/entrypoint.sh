#!/usr/bin/env bash
# Démarre le runner auto-hébergé de Myrmacta (TODO §110).
# Premier démarrage : télécharge la dernière version du runner dans le volume et l'enregistre avec RUNNER_TOKEN.
# Démarrages suivants : reprend l'enregistrement conservé dans le volume, sans jeton.
# `remove` : désenregistre le runner (RUNNER_TOKEN = jeton de suppression donné par GitHub).
set -euo pipefail

runner_dir="$HOME/actions-runner"
repo_url="${REPO_URL:-https://github.com/superjojo79/Myrmacta}"
runner_name="${RUNNER_NAME:-myrmacta-ci}"
runner_labels="${RUNNER_LABELS:-myrmacta-ci}"

require_token() {
  if [[ -z "${RUNNER_TOKEN:-}" ]]; then
    echo "RUNNER_TOKEN manquant : jeton de $1 donné par GitHub (Settings > Actions > Runners), valable une heure." >&2
    exit 1
  fi
}

if [[ ! -x "$runner_dir/run.sh" ]]; then
  version="${RUNNER_VERSION:-$(curl -fsSL https://api.github.com/repos/actions/runner/releases/latest | jq -r .tag_name)}"
  version="${version#v}"
  case "$(uname -m)" in
    x86_64) arch=x64 ;;
    aarch64) arch=arm64 ;;
    *) echo "Architecture non prise en charge : $(uname -m)" >&2; exit 1 ;;
  esac
  echo "Téléchargement du runner $version ($arch)"
  mkdir -p "$runner_dir"
  curl -fsSL "https://github.com/actions/runner/releases/download/v$version/actions-runner-linux-$arch-$version.tar.gz" \
    | tar -xz -C "$runner_dir"
fi

cd "$runner_dir"

if [[ "${1:-}" == "remove" ]]; then
  require_token "suppression"
  exec ./config.sh remove --token "$RUNNER_TOKEN"
fi

if [[ ! -f .runner ]]; then
  require_token "création"
  ./config.sh --unattended --replace --url "$repo_url" --token "$RUNNER_TOKEN" \
    --name "$runner_name" --labels "$runner_labels" --work _work
fi

exec ./run.sh
