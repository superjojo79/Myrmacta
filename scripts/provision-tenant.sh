#!/usr/bin/env bash
set -euo pipefail

# scripts/provision-tenant.sh — génère (et applique en option) le schéma PostgreSQL et le
# rôle LOGIN dédié d'un nouveau tenant FlowForge (isolation par schéma, TODO.md D-41 —
# option A déjà tranchée ; gabarit : docker/postgres/init/01-init-tenant-schema.sql).
#
# Voir DEPLOIEMENT.md §4 pour le runbook de provisioning complet (ce script couvre l'étape
# "créer le schéma et le rôle PostgreSQL dédiés", pas le démarrage de l'instance applicative
# ni le bloc reverse-proxy).

usage() {
  cat <<'USAGE'
Usage: scripts/provision-tenant.sh <tenant_id> [-o <fichier.sql>] [--apply]

  <tenant_id>       Identifiant de tenant (schéma + rôle PostgreSQL). Doit matcher
                     ^[a-z][a-z0-9_]{2,52}$ (3 à 53 caractères) — le rôle généré
                     ("flowforge_<tenant_id>") reste ainsi <= 63 octets, la limite
                     NAMEDATALEN des identifiants PostgreSQL non cités.

  -o <fichier.sql>  Écrit le SQL généré dans <fichier.sql> au lieu de stdout.
                     Ignoré avec --apply.

  --apply           Applique directement le SQL contre PostgreSQL au lieu de l'imprimer.
                     Requiert les variables psql standard (PGHOST, PGPORT, PGDATABASE,
                     PGUSER, PGPASSWORD — le superuser de provisioning, cf. DEPLOIEMENT.md
                     §4) et TENANT_DB_PASSWORD (mot de passe du rôle applicatif créé pour
                     ce tenant). Échoue explicitement si le schéma <tenant_id> existe déjà
                     — ne réécrase jamais un tenant déjà provisionné.

Variables d'environnement :
  TENANT_DB_PASSWORD  Mot de passe du rôle applicatif du tenant (requis dans tous les
                       modes : le SQL généré contient toujours "CREATE ROLE ... PASSWORD").
  PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD
                       Variables psql standard, utilisées uniquement avec --apply, pour se
                       connecter en tant que superuser de provisioning.

Exemples :
  # SQL sur stdout, pour revue avant application
  TENANT_DB_PASSWORD='...' scripts/provision-tenant.sh tenant_b

  # SQL écrit dans un fichier
  TENANT_DB_PASSWORD='...' scripts/provision-tenant.sh tenant_b -o tenant_b.sql

  # Application directe contre PostgreSQL
  TENANT_DB_PASSWORD='...' PGHOST=localhost PGPORT=5432 PGDATABASE=flowforge \
    PGUSER=flowforge PGPASSWORD='...' \
    scripts/provision-tenant.sh tenant_b --apply
USAGE
}

tenant_id=""
output_file=""
apply=0

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

: "${TENANT_DB_PASSWORD:?TENANT_DB_PASSWORD doit être défini (mot de passe du rôle ${role_name})}"

# Échappement SQL standard (guillemet simple doublé) — le mot de passe vient uniquement de
# la variable d'environnement, jamais interpolé dans une requête construite depuis une
# entrée libre.
escaped_password="${TENANT_DB_PASSWORD//\'/\'\'}"

generate_sql() {
  cat <<SQL
-- Généré par scripts/provision-tenant.sh pour le tenant '${tenant_id}'.
-- Gabarit : docker/postgres/init/01-init-tenant-schema.sql (D-41, TODO.md point 41).
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
