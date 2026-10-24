#!/usr/bin/env bash
set -euo pipefail

# scripts/keycloak-realm-smoke.sh — recette du realm par tenant sur un vrai Keycloak (TODO §103, D-BEAUTH-KC-SMOKE).
#
# Démarre Keycloak (distribution zip, Java 21+), importe le simulateur FAS (docker/keycloak/import/fas-simulateur-realm.json),
# provisionne deux tenants avec scripts/provision-tenant.sh --realm-per-tenant (un en idp-only, un en both), applique
# leur <tenant>.keycloak.sh deux fois (idempotence), puis joue les connexions avec scripts/keycloak_realm_smoke.py.
#
# Variables :
#   KEYCLOAK_HOME        distribution Keycloak dézippée (sinon téléchargée dans le dossier de travail)
#   KEYCLOAK_VERSION     défaut 26.7.1 (version de docker-compose.yml)
#   SMOKE_KEYCLOAK_PORT  défaut 18081
#   SMOKE_WORK_DIR       dossier de travail (défaut : mktemp -d) ; SMOKE_KEEP=1 le conserve
#
# Sortie : une ligne « ok » ou « ÉCHEC » par vérification ; code 0 si tout passe.

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
version="${KEYCLOAK_VERSION:-26.7.1}"
port="${SMOKE_KEYCLOAK_PORT:-18081}"
work="${SMOKE_WORK_DIR:-$(mktemp -d)}"
kc_url="http://localhost:${port}"
failures=0
kc_pid=""

cleanup() {
  if [[ -n "$kc_pid" ]]; then
    kill "$kc_pid" 2>/dev/null || true
    wait "$kc_pid" 2>/dev/null || true
  fi
  if [[ "${SMOKE_KEEP:-0}" != "1" ]]; then
    rm -rf "$work"
  else
    echo "Dossier de travail conservé : $work" >&2
  fi
}
trap cleanup EXIT

check() {
  local label="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    echo "ok      $label"
  else
    echo "ÉCHEC   $label"
    failures=$((failures + 1))
  fi
}

# --- Keycloak -------------------------------------------------------------------------------------------------
mkdir -p "$work"
if [[ -z "${KEYCLOAK_HOME:-}" ]]; then
  echo "Téléchargement de Keycloak ${version}…" >&2
  curl -fsSL -o "$work/keycloak.zip" \
    "https://github.com/keycloak/keycloak/releases/download/${version}/keycloak-${version}.zip"
  (cd "$work" && unzip -q keycloak.zip)
  KEYCLOAK_HOME="$work/keycloak-${version}"
else
  # Copie : la recette part d'une base vide et ne touche pas à la distribution fournie.
  cp -r "$KEYCLOAK_HOME" "$work/keycloak"
  KEYCLOAK_HOME="$work/keycloak"
  rm -rf "$KEYCLOAK_HOME/data"
fi
mkdir -p "$KEYCLOAK_HOME/data/import"
cp "$root/docker/keycloak/import/fas-simulateur-realm.json" "$KEYCLOAK_HOME/data/import/"

KC_BOOTSTRAP_ADMIN_USERNAME=admin KC_BOOTSTRAP_ADMIN_PASSWORD=admin KEYCLOAK_PORT="$port" \
  "$KEYCLOAK_HOME/bin/kc.sh" start-dev --http-port="$port" --import-realm >"$work/keycloak.log" 2>&1 &
kc_pid=$!
for _ in $(seq 1 180); do
  grep -q "Listening on" "$work/keycloak.log" && break
  kill -0 "$kc_pid" 2>/dev/null || { cat "$work/keycloak.log" >&2; exit 1; }
  sleep 1
done
grep -q "Listening on" "$work/keycloak.log" || { echo "Keycloak n'a pas démarré." >&2; exit 1; }

cat >"$work/kcadm" <<KCADM
#!/usr/bin/env bash
exec "$KEYCLOAK_HOME/bin/kcadm.sh" "\$@" --config "$work/kcadm.config"
KCADM
chmod 700 "$work/kcadm"
kcadm="$work/kcadm"
"$kcadm" config credentials --server "$kc_url" --realm master --user admin --password admin >/dev/null

# --- Deux tenants provisionnés par le vrai script ----------------------------------------------------------------
provision() {
  local tenant="$1" mode="$2"
  TENANT_DB_PASSWORD=recette KEYCLOAK_URL="$kc_url" PUBLIC_BASE_URL=http://localhost:8080 \
    bash "$root/scripts/provision-tenant.sh" "$tenant" --instance-dir "$work/instances" --port 8099 \
    --realm-per-tenant --idp fas --password-login "$mode" >/dev/null 2>&1

  # Le simulateur connaît un client par realm consommateur : private_key_jwt vérifié sur la JWKS de ce realm.
  "$kcadm" get clients -r fas-simulateur -q clientId=keycloak-flowforge | jq --arg t "$tenant" --arg kc "$kc_url" '
    .[0] | del(.id) | .clientId = "keycloak-\($t)"
    | .redirectUris = ["\($kc)/realms/flowforge-\($t)/broker/fas/endpoint"]
    | .attributes["jwks.url"] = "\($kc)/realms/flowforge-\($t)/protocol/openid-connect/certs"
    | .protocolMappers |= map(del(.id))' | "$kcadm" create clients -r fas-simulateur -f - >/dev/null

  for _ in 1 2; do
    KCADM="$kcadm" FAS_CLIENT_ID="keycloak-${tenant}" SECOURS_PASSWORD=Secours-temporaire-1 \
      FAS_DISCOVERY_URL="$kc_url/realms/fas-simulateur/.well-known/openid-configuration" \
      bash "$work/instances/${tenant}.keycloak.sh" >>"$work/provision.log" 2>&1
  done
}

bff_secret() {
  local realm="$1" id
  id="$("$kcadm" get clients -r "$realm" -q clientId=mon-app --fields id --format csv --noquotes)"
  "$kcadm" get "clients/$id/client-secret" -r "$realm" | jq -r .value
}

password_user() {
  local realm="$1" tenant="$2"
  "$kcadm" create users -r "$realm" -s username=agent.mdp -s enabled=true -s email=agent.mdp@example.be \
    -s firstName=Agent -s lastName=Mdp -s "attributes.tenant_id=[\"$tenant\"]" >/dev/null 2>&1
  "$kcadm" set-password -r "$realm" --username agent.mdp --new-password agent.mdp >/dev/null
  "$kcadm" add-roles -r "$realm" --uusername agent.mdp --rolename DOSSIER_MANAGER >/dev/null
}

smoke() {
  python3 -I "$root/scripts/keycloak_realm_smoke.py" "$kc_url" "$@"
}

check "provisionnement idp-only appliqué deux fois sans erreur" provision recette_idp idp-only
check "provisionnement both appliqué deux fois sans erreur" provision recette_both both
password_user flowforge-recette_idp recette_idp
password_user flowforge-recette_both recette_both

for tenant in recette_idp recette_both; do
  realm="flowforge-${tenant}"
  secret="$(bff_secret "$realm")"
  redirect="http://localhost:8080/${tenant}/login/oauth2/code/oidc"
  out="$(smoke agent-fas realm="$realm" secret="$secret" redirect="$redirect" user=agent.dupont \
    password=agent.dupont idp=fas)"
  check "$tenant : agent connecté par FAS (simulé)" jq -e '.result == "logged-in"' <<<"$out"
  # Chaque vérification exige d'abord la connexion : sans claims, rien n'est « absent » par construction.
  check "$tenant : compte créé sans tenant_id (A2)" jq -e '.claims and (.claims | has("tenant_id") | not)' <<<"$out"
  check "$tenant : aucun NRN dans l'ID token (A5)" \
    jq -e '.claims and ([.claims | .. | strings | select(test("^[0-9]{11}$"))] | length == 0)' <<<"$out"
  check "$tenant : aucun rôle FlowForge par défaut (A2)" \
    jq -e '.claims and ((.claims.realm_access.roles // []) - ["default-roles-'"$realm"'", "offline_access", "uma_authorization"] == [])' \
    <<<"$out"
done

out="$(smoke mot-de-passe realm=flowforge-recette_both secret="$(bff_secret flowforge-recette_both)" \
  redirect=http://localhost:8080/recette_both/login/oauth2/code/oidc user=agent.mdp password=agent.mdp)"
check "both : connexion directe par mot de passe Keycloak" jq -e '.result == "logged-in" and .claims.tenant_id == "recette_both"' <<<"$out"

secret="$(bff_secret flowforge-recette_idp)"
redirect=http://localhost:8080/recette_idp/login/oauth2/code/oidc
out="$(smoke mot-de-passe realm=flowforge-recette_idp secret="$secret" redirect="$redirect" user=agent.mdp password=agent.mdp)"
check "idp-only : mot de passe refusé à un compte ordinaire" jq -e '.result == "refused"' <<<"$out"

out="$(smoke secours realm=flowforge-recette_idp secret="$secret" redirect="$redirect" user=secours \
  password=Secours-temporaire-1 new_password=Secours-definitif-2)"
check "idp-only : secours connecté (mot de passe changé, OTP enrôlé)" \
  jq -e '.result == "logged-in" and .claims.tenant_id == "recette_idp" and (.claims.realm_access.roles | index("TENANT_ADMIN"))' \
  <<<"$out"
otp_secret="$(jq -r '.otp_secret // ""' <<<"$out")"
out="$(smoke secours-otp realm=flowforge-recette_idp secret="$secret" redirect="$redirect" user=secours \
  password=Secours-definitif-2 otp_secret="$otp_secret")"
check "idp-only : secours reconnecté par mot de passe et OTP" jq -e '.result == "logged-in"' <<<"$out"
out="$(smoke secours-sans-otp realm=flowforge-recette_idp secret="$secret" redirect="$redirect" user=secours \
  password=Secours-definitif-2)"
check "idp-only : secours sans OTP arrêté à l'OTP" jq -e '.result == "otp-required"' <<<"$out"

if (( failures > 0 )); then
  echo "$failures vérification(s) en échec. Journaux : $work (SMOKE_KEEP=1 pour les garder)." >&2
  exit 1
fi
echo "Recette Keycloak ${version} : tout est vert."
