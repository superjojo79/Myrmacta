-- Exécuté une seule fois, au premier démarrage du conteneur PostgreSQL
-- (répertoire /docker-entrypoint-initdb.d, uniquement si le volume de données est vide).
--
-- L'application ne crée jamais le schéma tenant elle-même : elle exécute uniquement
-- `SET search_path TO <app.tenant.id>, public` à la connexion (voir application.yml,
-- hikari.connection-init-sql). Si le schéma n'existe pas encore, PostgreSQL retombe
-- silencieusement sur `public` et Liquibase y crée les tables — ce qui fonctionne, mais
-- ne démontre pas l'isolation par schéma. On crée donc explicitement le schéma par défaut
-- (tenant_dev, cf. application.yml) pour un getting-started fidèle au modèle multi-tenant.
--
-- D-41 : le superuser du conteneur (`POSTGRES_USER`, défaut `flowforge`) sert au bootstrap.
-- L'application se connecte avec `flowforge_tenant_dev` (USAGE/CREATE sur tenant_dev seulement).
-- Volume existant : `docker compose down -v` pour rejouer ce script.
--
-- Pour ajouter un second tenant local : CREATE SCHEMA + CREATE ROLE + GRANT (voir
-- analysis doc/02-multi-tenant.md), puis une seconde instance APP_TENANT_ID / DB_USERNAME.
CREATE SCHEMA IF NOT EXISTS tenant_dev;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flowforge_tenant_dev') THEN
        CREATE ROLE flowforge_tenant_dev LOGIN PASSWORD 'flowforge';
    END IF;
END
$$;

DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO flowforge_tenant_dev', current_database());
END
$$;

REVOKE ALL ON SCHEMA tenant_dev FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM flowforge_tenant_dev;
GRANT USAGE ON SCHEMA public TO flowforge_tenant_dev;
GRANT USAGE, CREATE ON SCHEMA tenant_dev TO flowforge_tenant_dev;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA tenant_dev TO flowforge_tenant_dev;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA tenant_dev TO flowforge_tenant_dev;
ALTER DEFAULT PRIVILEGES IN SCHEMA tenant_dev
    GRANT ALL ON TABLES TO flowforge_tenant_dev;
ALTER DEFAULT PRIVILEGES IN SCHEMA tenant_dev
    GRANT ALL ON SEQUENCES TO flowforge_tenant_dev;

