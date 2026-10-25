#!/usr/bin/env bash
set -euo pipefail

# scripts/provision-tenant.sh — génère (et applique en option) le schéma PostgreSQL et le
# rôle LOGIN dédié d'un nouveau tenant Myrmacta (isolation par schéma, D-41 —
# option A déjà tranchée ; gabarit : docker/postgres/init/01-init-tenant-schema.sql).
#
# Voir docs/guides/DEPLOIEMENT.md §4 pour le runbook de provisioning complet. Avec --instance-dir, le
# script génère aussi la configuration de l'instance (docker/tenant/compose.tenant.yml), son bloc
# reverse-proxy Apache et le script Keycloak de son client machine-to-machine (TODO §95).

usage() {
  cat <<'USAGE'
Usage: scripts/provision-tenant.sh <tenant_id> [-o <fichier.sql>] [--apply]
                                   [--instance-dir <dossier> --port <port>
                                    [--realm-per-tenant [--idp fas|itsme]... [--password-login both|idp-only]]]

  <tenant_id>       Identifiant de tenant (schéma + rôle PostgreSQL). Doit matcher
                     ^[a-z][a-z0-9_]{2,52}$ (3 à 53 caractères) — le rôle généré
                     ("flowforge_<tenant_id>") reste ainsi <= 63 octets, la limite
                     NAMEDATALEN des identifiants PostgreSQL non cités.

  -o <fichier.sql>  Écrit le SQL généré dans <fichier.sql> au lieu de stdout.
                     Ignoré avec --apply.

  --apply           Applique directement le SQL contre PostgreSQL au lieu de l'imprimer.
                     Requiert les variables psql standard (PGHOST, PGPORT, PGDATABASE,
                     PGUSER, PGPASSWORD — le superuser de provisioning, cf. docs/guides/DEPLOIEMENT.md
                     §4) et TENANT_DB_PASSWORD (mot de passe du rôle applicatif créé pour
                     ce tenant). Échoue explicitement si le schéma <tenant_id> existe déjà
                     — ne réécrase jamais un tenant déjà provisionné.

  --instance-dir <dossier>
                    Écrit aussi dans <dossier> la configuration de l'instance (TODO §95) :
                     <tenant_id>.env          variables de l'instance (droits 600 : contient
                                              DB_PASSWORD ; les secrets restés vides empêchent
                                              le démarrage tant qu'ils ne sont pas renseignés) ;
                     <tenant_id>.compose.env  paramètres de docker/tenant/compose.tenant.yml ;
                     <tenant_id>.apache.conf  bloc reverse-proxy (docs/guides/DEPLOIEMENT.md §3) ;
                     proxy-headers.conf       en-têtes X-Forwarded-* communs (écrit une fois) ;
                     <tenant_id>.keycloak.sh  client client_credentials du tenant, idempotent
                                              (docs/guides/DEPLOIEMENT.md §7).
                     Refuse d'écraser des fichiers existants pour ce tenant.

  --port <port>     Port local (127.0.0.1) de l'instance. Requis avec --instance-dir.

  --realm-per-tenant
                    Avec --instance-dir : realm Keycloak propre au tenant (flowforge-<tenant_id>,
                     TODO §104, modèle par défaut en production). Écrit aussi
                     <tenant_id>.realm.json (déclaratif, sans secret), que <tenant_id>.keycloak.sh
                     applique avant de créer le client machine-to-machine. Requiert jq.
  --idp fas|itsme   Fournisseur d'identité belge du realm (répétable) : FAS de BOSA (eID, itsme,
                     eIDAS) ou itsme en direct. Requiert --realm-per-tenant.
  --password-login both|idp-only
                    both (défaut) : mot de passe Keycloak et fournisseurs côte à côte. idp-only :
                     fournisseurs seuls pour Myrmacta ; seuls les comptes de rôle « secours » gardent
                     le mot de passe et l'OTP. Requiert --realm-per-tenant et au moins un --idp.

Variables d'environnement :
  TENANT_DB_PASSWORD  Mot de passe du rôle applicatif du tenant (requis dans tous les
                       modes : le SQL généré contient toujours "CREATE ROLE ... PASSWORD").
  PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD
                       Variables psql standard, utilisées uniquement avec --apply, pour se
                       connecter en tant que superuser de provisioning.
  Avec --instance-dir, valeurs reprises si elles sont définies (sinon à compléter) :
  TENANT_DB_URL, OIDC_ISSUER_URI, OIDC_CLIENT_ID, FLOWFORGE_IMAGE (défaut flowforge:latest),
  KEYCLOAK_REALM (défaut flowforge).
  Avec --realm-per-tenant (requis) : KEYCLOAK_URL (ex. https://auth.example.be) et PUBLIC_BASE_URL
  (URL publique des instances, sans le préfixe du tenant). Facultatif : FAS_CLAIM_GIVEN_NAME,
  FAS_CLAIM_FAMILY_NAME, FAS_CLAIM_EMAIL, ITSME_CLAIM_GIVEN_NAME, ITSME_CLAIM_FAMILY_NAME,
  ITSME_CLAIM_EMAIL (noms des claims, à confirmer auprès du fournisseur).

Exemples :
  # SQL sur stdout, pour revue avant application
  TENANT_DB_PASSWORD='...' scripts/provision-tenant.sh tenant_b

  # SQL écrit dans un fichier
  TENANT_DB_PASSWORD='...' scripts/provision-tenant.sh tenant_b -o tenant_b.sql

  # Application directe contre PostgreSQL
  TENANT_DB_PASSWORD='...' PGHOST=localhost PGPORT=5432 PGDATABASE=flowforge \
    PGUSER=flowforge PGPASSWORD='...' \
    scripts/provision-tenant.sh tenant_b --apply

  # Schéma et rôle appliqués, plus la configuration de l'instance dans ./tenants
  TENANT_DB_PASSWORD='...' PGHOST=... scripts/provision-tenant.sh tenant_b --apply \
    --instance-dir ./tenants --port 8082
USAGE
}

tenant_id=""
output_file=""
apply=0
instance_dir=""
port=""
realm_per_tenant=0
idps=()
password_login="both"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -o)
      [[ $# -ge 2 ]] || { echo "-o requiert un argument" >&2; exit 1; }
      output_file="$2"
      shift 2
      ;;
    --apply)
      apply=1
      shift
      ;;
    --instance-dir)
      [[ $# -ge 2 ]] || { echo "--instance-dir requiert un argument" >&2; exit 1; }
      instance_dir="$2"
      shift 2
      ;;
    --port)
      [[ $# -ge 2 ]] || { echo "--port requiert un argument" >&2; exit 1; }
      port="$2"
      shift 2
      ;;
    --realm-per-tenant)
      realm_per_tenant=1
      shift
      ;;
    --idp)
      [[ $# -ge 2 ]] || { echo "--idp requiert un argument" >&2; exit 1; }
      idps+=("$2")
      shift 2
      ;;
    --password-login)
      [[ $# -ge 2 ]] || { echo "--password-login requiert un argument" >&2; exit 1; }
      password_login="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      echo "Option inconnue : $1" >&2
      usage >&2
      exit 1
      ;;
    *)
      if [[ -n "$tenant_id" ]]; then
        echo "Un seul identifiant de tenant est attendu (déjà reçu : $tenant_id)" >&2
        exit 1
      fi
      tenant_id="$1"
      shift
      ;;
  esac
done

if [[ -z "$tenant_id" ]]; then
  echo "Identifiant de tenant manquant." >&2
  usage >&2
  exit 1
fi

# Identifiant PostgreSQL non cité, borné à 53 caractères — validé avant toute génération
# SQL pour ne jamais interpoler une entrée non maîtrisée dans le SQL produit (tenant_id est
# la seule entrée utilisateur injectée ci-dessous, hors mot de passe). La borne n'est pas
# 63 (NAMEDATALEN) mais 53 : le rôle généré préfixe "flowforge_" (10 caractères) au
# tenant_id, et le nom de rôle résultant doit lui aussi rester <= 63 octets.
if ! [[ "$tenant_id" =~ ^[a-z][a-z0-9_]{2,52}$ ]]; then
  echo "Identifiant de tenant invalide : '$tenant_id'" >&2
  echo "Attendu : minuscules/chiffres/underscore, commence par une lettre, 3 à 53 caractères." >&2
  exit 1
fi

role_name="flowforge_${tenant_id}"

if [[ -n "$instance_dir" ]]; then
  if ! [[ "$port" =~ ^[0-9]+$ ]] || (( port < 1024 || port > 65535 )); then
    echo "--port requis avec --instance-dir (entier entre 1024 et 65535)." >&2
    exit 1
  fi
fi

# --- Realm par tenant (TODO §104) : options validées avant toute écriture ---------------------------------------
if (( realm_per_tenant == 0 )); then
  if (( ${#idps[@]} > 0 )) || [[ "$password_login" != "both" ]]; then
    echo "--idp et --password-login requièrent --realm-per-tenant." >&2
    exit 1
  fi
else
  [[ -n "$instance_dir" ]] || { echo "--realm-per-tenant requiert --instance-dir." >&2; exit 1; }
  command -v jq >/dev/null || { echo "--realm-per-tenant requiert jq." >&2; exit 1; }
  for idp in "${idps[@]}"; do
    [[ "$idp" == "fas" || "$idp" == "itsme" ]] || { echo "Fournisseur inconnu : '$idp' (fas ou itsme)." >&2; exit 1; }
  done
  [[ "$password_login" == "both" || "$password_login" == "idp-only" ]] \
    || { echo "--password-login : both ou idp-only (reçu : '$password_login')." >&2; exit 1; }
  if [[ "$password_login" == "idp-only" ]] && (( ${#idps[@]} == 0 )); then
    echo "--password-login idp-only requiert au moins un --idp." >&2
    exit 1
  fi
  : "${KEYCLOAK_URL:?KEYCLOAK_URL doit être défini avec --realm-per-tenant (ex. https://auth.example.be)}"
  : "${PUBLIC_BASE_URL:?PUBLIC_BASE_URL doit être défini avec --realm-per-tenant (URL publique des instances)}"
  KEYCLOAK_URL="${KEYCLOAK_URL%/}"
  PUBLIC_BASE_URL="${PUBLIC_BASE_URL%/}"
  KEYCLOAK_REALM="flowforge-${tenant_id}"
  OIDC_ISSUER_URI="${OIDC_ISSUER_URI:-${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}}"
  OIDC_CLIENT_ID="${OIDC_CLIENT_ID:-mon-app}"
fi
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

new_uuid() {
  if [[ -r /proc/sys/kernel/random/uuid ]]; then
    cat /proc/sys/kernel/random/uuid
  else
    uuidgen | tr '[:upper:]' '[:lower:]'
  fi
}

generate_realm_json() {
  local idps_json claims_json
  idps_json="$(printf '%s\n' "${idps[@]}" | jq -R . | jq -cs 'map(select(. != ""))')"
  claims_json="$(jq -cn \
    --arg fg "${FAS_CLAIM_GIVEN_NAME:-givenName}" --arg ff "${FAS_CLAIM_FAMILY_NAME:-surname}" \
    --arg fe "${FAS_CLAIM_EMAIL:-mail}" --arg ig "${ITSME_CLAIM_GIVEN_NAME:-given_name}" \
    --arg if "${ITSME_CLAIM_FAMILY_NAME:-family_name}" --arg ie "${ITSME_CLAIM_EMAIL:-email}" \
    '{fas: {given: $fg, family: $ff, email: $fe}, itsme: {given: $ig, family: $if, email: $ie}}')"
  jq -n -f "${script_dir}/tenant-realm.jq" \
    --arg tenant "$tenant_id" --arg base "$PUBLIC_BASE_URL" --arg mode "$password_login" \
    --arg flowId "$(new_uuid)" --argjson idps "$idps_json" --argjson claims "$claims_json"
}

# Section « realm » de <tenant>.keycloak.sh (TODO §104) : variables figées au provisionnement, puis logique littérale.
generate_realm_section() {
  cat <<SECTION

# --- Realm du tenant (TODO §104, D-BEAUTH-PROVISION) -------------------------------------------------------------
# Créé depuis ${tenant_id}.realm.json s'il est absent ; sinon complété par import partiel (clients, rôles,
# fournisseurs existants conservés ; les flux d'authentification ne sont posés qu'à la création).
REALM_FILE="\$(cd "\$(dirname "\${BASH_SOURCE[0]}")" && pwd)/${tenant_id}.realm.json"
IDPS="${idps[*]:-}"
PASSWORD_LOGIN="${password_login}"
SECTION
  cat <<'SECTION'
command -v jq >/dev/null || { echo "jq est requis." >&2; exit 1; }

if "$KCADM" get "realms/$REALM" >/dev/null 2>&1; then
  "$KCADM" create partialImport -r "$REALM" -s ifResourceExists=SKIP -f "$REALM_FILE" >/dev/null
  echo "Realm $REALM déjà présent : complété (import partiel, flux d'authentification inchangés)." >&2
else
  "$KCADM" create realms -f "$REALM_FILE"
fi

# Identifiants d'un fournisseur : URL de découverte et client enregistré chez lui, depuis l'environnement.
# FAS : FAS_DISCOVERY_URL, FAS_CLIENT_ID, et FAS_CLIENT_SECRET si BOSA a émis un secret (sinon private_key_jwt,
# signé par la clé du realm). itsme : ITSME_DISCOVERY_URL, ITSME_CLIENT_ID, toujours private_key_jwt.
configure_idp() {
  local alias="$1" prefix="$2" discovery client secret imported current
  discovery="$(printenv "${prefix}_DISCOVERY_URL" || true)"
  client="$(printenv "${prefix}_CLIENT_ID" || true)"
  secret="$(printenv "${prefix}_CLIENT_SECRET" || true)"
  if [[ -z "$discovery" || -z "$client" ]]; then
    echo "Fournisseur $alias : ${prefix}_DISCOVERY_URL et ${prefix}_CLIENT_ID requis — non configuré." >&2
    return 1
  fi
  imported="$("$KCADM" create identity-provider/import-config -r "$REALM" -s providerId=oidc -s fromUrl="$discovery" -o)"
  current="$("$KCADM" get "identity-provider/instances/$alias" -r "$REALM")"
  jq --argjson imported "$imported" --arg client "$client" --arg secret "$secret" --arg alias "$alias" '
    .config += ($imported | with_entries(select(.key | IN("authorizationUrl", "tokenUrl", "userInfoUrl",
                                                       "jwksUrl", "issuer", "logoutUrl"))))
    | .config.clientId = $client | .config.validateSignature = "true" | .config.useJwksUrl = "true"
    | if $secret != "" and $alias == "fas"
      then .config.clientSecret = $secret | .config.clientAuthMethod = "client_secret_post"
      else .config.clientAuthMethod = "private_key_jwt" end' <<<"$current" \
    | "$KCADM" update "identity-provider/instances/$alias" -r "$REALM" -f -
  echo "Fournisseur $alias configuré. URI à enregistrer chez lui : <keycloak>/realms/$REALM/broker/$alias/endpoint" >&2
}

for idp in $IDPS; do
  case "$idp" in
    fas) configure_idp fas FAS ;;
    itsme) configure_idp itsme ITSME ;;
  esac
done

# Mode « fournisseurs seuls » : compte de secours (mot de passe temporaire, OTP à la première connexion).
if [[ "$PASSWORD_LOGIN" == "idp-only" ]]; then
  : "${SECOURS_PASSWORD:?SECOURS_PASSWORD doit être défini (mot de passe temporaire du compte de secours)}"
  SECOURS_USER="${SECOURS_USERNAME:-secours}"
  if [[ -z "$("$KCADM" get users -r "$REALM" -q exact=true -q username="$SECOURS_USER" --fields id --format csv --noquotes)" ]]; then
    "$KCADM" create users -r "$REALM" -s username="$SECOURS_USER" -s enabled=true \
      -s "attributes.tenant_id=[\"$TENANT\"]" -s 'requiredActions=["CONFIGURE_TOTP"]'
    "$KCADM" set-password -r "$REALM" --username "$SECOURS_USER" --new-password "$SECOURS_PASSWORD" --temporary
    "$KCADM" add-roles -r "$REALM" --uusername "$SECOURS_USER" --rolename secours --rolename TENANT_ADMIN
    echo "Compte de secours $SECOURS_USER créé (mot de passe temporaire, OTP à configurer)." >&2
  fi
fi
SECTION
}

: "${TENANT_DB_PASSWORD:?TENANT_DB_PASSWORD doit être défini (mot de passe du rôle ${role_name})}"

# Échappement SQL standard (guillemet simple doublé) — le mot de passe vient uniquement de
# la variable d'environnement, jamais interpolé dans une requête construite depuis une
# entrée libre.
escaped_password="${TENANT_DB_PASSWORD//\'/\'\'}"

generate_sql() {
  cat <<SQL
-- Généré par scripts/provision-tenant.sh pour le tenant '${tenant_id}'.
-- Gabarit : docker/postgres/init/01-init-tenant-schema.sql (D-41, TODO §41).
CREATE SCHEMA IF NOT EXISTS ${tenant_id};

DO \$\$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${role_name}') THEN
        CREATE ROLE ${role_name} LOGIN PASSWORD '${escaped_password}';
    END IF;
END
\$\$;

DO \$\$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO ${role_name}', current_database());
END
\$\$;

REVOKE ALL ON SCHEMA ${tenant_id} FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM ${role_name};
GRANT USAGE ON SCHEMA public TO ${role_name};
GRANT USAGE, CREATE ON SCHEMA ${tenant_id} TO ${role_name};
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA ${tenant_id} TO ${role_name};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA ${tenant_id} TO ${role_name};
ALTER DEFAULT PRIVILEGES IN SCHEMA ${tenant_id}
    GRANT ALL ON TABLES TO ${role_name};
ALTER DEFAULT PRIVILEGES IN SCHEMA ${tenant_id}
    GRANT ALL ON SEQUENCES TO ${role_name};
SQL
}

# --- Configuration de l'instance (--instance-dir), TODO §95 -------------------------------------------
generate_instance_files() {
  local dir="$1" env_file compose_env apache keycloak headers
  mkdir -p "$dir"
  dir="$(cd "$dir" && pwd)"
  env_file="${dir}/${tenant_id}.env"
  compose_env="${dir}/${tenant_id}.compose.env"
  apache="${dir}/${tenant_id}.apache.conf"
  keycloak="${dir}/${tenant_id}.keycloak.sh"
  local realm_file="${dir}/${tenant_id}.realm.json"
  headers="${dir}/proxy-headers.conf"
  local f
  for f in "$env_file" "$compose_env" "$apache" "$keycloak" "$realm_file"; do
    if [[ -e "$f" ]]; then
      echo "$f existe déjà — génération refusée (pas d'écrasement)." >&2
      exit 1
    fi
  done
  local m2m_client="flowforge-m2m-${tenant_id}"

  (umask 077 && cat > "$env_file" <<ENV
# Instance Myrmacta du tenant '${tenant_id}' — généré par scripts/provision-tenant.sh (TODO §95).
# Gabarit commenté : .env.example ; détail : docs/guides/DEPLOIEMENT.md §2. Les variables laissées vides
# sont des secrets à renseigner : l'instance refuse de démarrer tant qu'ils manquent (fail-fast).
APP_ENVIRONMENT=production
SPRING_PROFILES_ACTIVE=prod
APP_TENANT_ID=${tenant_id}

DB_URL=${TENANT_DB_URL:-}
DB_USERNAME=${role_name}
DB_PASSWORD=${TENANT_DB_PASSWORD}

APP_CONNECTOR_MASTER_KEY=

OIDC_ISSUER_URI=${OIDC_ISSUER_URI:-}
OIDC_CLIENT_ID=${OIDC_CLIENT_ID:-}
OIDC_CLIENT_SECRET=

# Machine-to-machine : client ${m2m_client} (script ${tenant_id}.keycloak.sh).
OAUTH2_ISSUER_URI=${OIDC_ISSUER_URI:-}
M2M_TRUSTED_CLIENT_IDS=${m2m_client}

APP_DOSSIER_ATTACHMENT_SCAN_TYPE=clamav
APP_DOSSIER_ATTACHMENT_SCAN_CLAMAV_HOST=
APP_DOSSIER_ATTACHMENT_SCAN_CLAMAV_PORT=3310

APP_STORAGE_TYPE=s3
APP_STORAGE_S3_BUCKET=
APP_STORAGE_S3_REGION=
APP_STORAGE_S3_ACCESS_KEY=
APP_STORAGE_S3_SECRET_KEY=

MANAGEMENT_ADDRESS=127.0.0.1
ENV
  )

  cat > "$compose_env" <<ENV
# Paramètres de docker/tenant/compose.tenant.yml pour le tenant '${tenant_id}' (TODO §95).
#   docker compose -p flowforge-${tenant_id} --env-file ${compose_env} -f docker/tenant/compose.tenant.yml up -d
TENANT_ID=${tenant_id}
TENANT_PORT=${port}
TENANT_ENV_FILE=${env_file}
FLOWFORGE_IMAGE=${FLOWFORGE_IMAGE:-flowforge:latest}
ENV

  if [[ ! -e "$headers" ]]; then
    cat > "$headers" <<'APACHE'
# En-têtes communs à tous les tenants (docs/guides/DEPLOIEMENT.md §3) — à inclure une seule fois dans le
# VirtualHost HTTPS, avant les blocs <tenant_id>.apache.conf. Écrase les X-Forwarded-* venus du client.
RequestHeader unset X-Forwarded-Proto early
RequestHeader unset X-Forwarded-For early
RequestHeader unset X-Forwarded-Host early
RequestHeader unset X-Forwarded-Port early
RequestHeader unset Forwarded early
RequestHeader unset X-Forwarded-Prefix early
RequestHeader set X-Forwarded-Proto "https"
RequestHeader set X-Forwarded-For "%{REMOTE_ADDR}s"
APACHE
  fi

  cat > "$apache" <<APACHE
# Tenant '${tenant_id}' — généré par scripts/provision-tenant.sh (TODO §95). Le préfixe n'est jamais
# réécrit : /${tenant_id} côté client = context-path /${tenant_id} de l'instance (décision n° 14).
# Inclure proxy-headers.conf une fois par VirtualHost avant ce bloc.
ProxyPass        "/${tenant_id}" "http://127.0.0.1:${port}/${tenant_id}"
ProxyPassReverse "/${tenant_id}" "http://127.0.0.1:${port}/${tenant_id}"
APACHE

  if (( realm_per_tenant == 1 )); then
    generate_realm_json > "$realm_file"
  fi

  cat > "$keycloak" <<KEYCLOAK
#!/usr/bin/env bash
# Client machine-to-machine du tenant '${tenant_id}' (docs/guides/DEPLOIEMENT.md §7) — généré par
# scripts/provision-tenant.sh (TODO §95). Idempotent : relançable sans doublon.
# Prérequis : kcadm.sh authentifié sur le Keycloak de production, par exemple
#   kcadm.sh config credentials --server https://keycloak.example --realm master --user <admin>
set -euo pipefail
KCADM="\${KCADM:-kcadm.sh}"
REALM="\${KEYCLOAK_REALM:-${KEYCLOAK_REALM:-flowforge}}"
CLIENT="${m2m_client}"
TENANT="${tenant_id}"
KEYCLOAK
  if (( realm_per_tenant == 1 )); then
    generate_realm_section >> "$keycloak"
  fi
  cat >> "$keycloak" <<KEYCLOAK

client_id() {
  "\$KCADM" get clients -r "\$REALM" -q clientId="\$CLIENT" --fields id --format csv --noquotes
}

id="\$(client_id)"
if [[ -z "\$id" ]]; then
  "\$KCADM" create clients -r "\$REALM" -s clientId="\$CLIENT" -s enabled=true -s publicClient=false \\
    -s serviceAccountsEnabled=true -s standardFlowEnabled=false -s directAccessGrantsEnabled=false \\
    -s clientAuthenticatorType=client-secret
  id="\$(client_id)"
fi

has_mapper() {
  "\$KCADM" get "clients/\$id/protocol-mappers/models" -r "\$REALM" --fields name --format csv --noquotes \\
    | grep -qx "\$1"
}

# Claim tenant_id figé : ce client ne peut appeler que l'instance de ce tenant (M2mTenantValidationFilter).
if ! has_mapper tenant_id; then
  "\$KCADM" create "clients/\$id/protocol-mappers/models" -r "\$REALM" -s name=tenant_id \\
    -s protocol=openid-connect -s protocolMapper=oidc-hardcoded-claim-mapper \\
    -s 'config."claim.name"=tenant_id' -s "config.\"claim.value\"=\$TENANT" \\
    -s 'config."jsonType.label"=String' -s 'config."access.token.claim"=true'
fi
# Rôles réalm projetés dans un claim plat « roles » (lu par M2mJwtAuthoritiesConverter).
if ! has_mapper roles; then
  "\$KCADM" create "clients/\$id/protocol-mappers/models" -r "\$REALM" -s name=roles \\
    -s protocol=openid-connect -s protocolMapper=oidc-usermodel-realm-role-mapper \\
    -s 'config."claim.name"=roles' -s 'config.multivalued=true' \\
    -s 'config."jsonType.label"=String' -s 'config."access.token.claim"=true'
fi

"\$KCADM" get roles/API_SERVICE -r "\$REALM" >/dev/null 2>&1 \\
  || "\$KCADM" create roles -r "\$REALM" -s name=API_SERVICE
"\$KCADM" add-roles -r "\$REALM" --uusername "service-account-\$CLIENT" --rolename API_SERVICE

echo "Client \$CLIENT prêt (realm \$REALM). Secret : \$KCADM get clients/\$id/client-secret -r \$REALM"
KEYCLOAK
  chmod 700 "$keycloak"
  if (( realm_per_tenant == 1 )); then
    chmod 644 "$realm_file"
  fi

  echo "Configuration de l'instance écrite dans ${dir} (${tenant_id}.env, .compose.env, .apache.conf, .keycloak.sh$( \
    (( realm_per_tenant == 1 )) && printf ', .realm.json'))." >&2
}

if [[ -n "$instance_dir" ]]; then
  generate_instance_files "$instance_dir"
fi

if [[ "$apply" -eq 1 ]]; then
  existing="$(psql -X -A -t -v ON_ERROR_STOP=1 \
    -c "SELECT 1 FROM information_schema.schemata WHERE schema_name = '${tenant_id}'")" || {
      echo "Échec de connexion à PostgreSQL (vérifier PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD)." >&2
      exit 1
    }
  if [[ "$existing" == "1" ]]; then
    echo "Le schéma '${tenant_id}' existe déjà — provisioning refusé (pas d'écrasement)." >&2
    exit 1
  fi

  generate_sql | psql -X -v ON_ERROR_STOP=1
  echo "Tenant '${tenant_id}' provisionné (schéma + rôle ${role_name})." >&2
elif [[ -n "$output_file" ]]; then
  generate_sql > "$output_file"
  echo "SQL écrit dans $output_file — à revoir avant application." >&2
else
  generate_sql
fi
