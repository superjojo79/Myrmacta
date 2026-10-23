#!/usr/bin/env bash
set -euo pipefail

# scripts/provision-tenant.sh — génère (et applique en option) le schéma PostgreSQL et le
# rôle LOGIN dédié d'un nouveau tenant FlowForge (isolation par schéma, D-41 —
# option A déjà tranchée ; gabarit : docker/postgres/init/01-init-tenant-schema.sql).
#
# Voir docs/guides/DEPLOIEMENT.md §4 pour le runbook de provisioning complet. Avec --instance-dir, le
# script génère aussi la configuration de l'instance (docker/tenant/compose.tenant.yml), son bloc
# reverse-proxy Apache et le script Keycloak de son client machine-to-machine (TODO §95).

usage() {
  cat <<'USAGE'
Usage: scripts/provision-tenant.sh <tenant_id> [-o <fichier.sql>] [--apply]
                                   [--instance-dir <dossier> --port <port>]

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

Variables d'environnement :
  TENANT_DB_PASSWORD  Mot de passe du rôle applicatif du tenant (requis dans tous les
                       modes : le SQL généré contient toujours "CREATE ROLE ... PASSWORD").
  PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD
                       Variables psql standard, utilisées uniquement avec --apply, pour se
                       connecter en tant que superuser de provisioning.
  Avec --instance-dir, valeurs reprises si elles sont définies (sinon à compléter) :
  TENANT_DB_URL, OIDC_ISSUER_URI, OIDC_CLIENT_ID, FLOWFORGE_IMAGE (défaut flowforge:latest),
  KEYCLOAK_REALM (défaut flowforge).

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
  headers="${dir}/proxy-headers.conf"
  local f
  for f in "$env_file" "$compose_env" "$apache" "$keycloak"; do
    if [[ -e "$f" ]]; then
      echo "$f existe déjà — génération refusée (pas d'écrasement)." >&2
      exit 1
    fi
  done
  local m2m_client="flowforge-m2m-${tenant_id}"

  (umask 077 && cat > "$env_file" <<ENV
# Instance FlowForge du tenant '${tenant_id}' — généré par scripts/provision-tenant.sh (TODO §95).
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

  echo "Configuration de l'instance écrite dans ${dir} (${tenant_id}.env, .compose.env, .apache.conf, .keycloak.sh)." >&2
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
