# PostgreSQL embarqué (zonky) — tests et démos/QA

> **Statut : livré** (TODO §91). Remplace la décision de test **D-TESTCONTAINERS-EXEC** et la partie
> PostgreSQL de **D-TESTCONTAINERS** (feature `fiabilisation-tests-plateforme`). Docs SDD :
> [requirements](../docs/ai/requirements/2026-10-02-feature-zonky-embedded-postgres.md),
> [design](../docs/ai/design/2026-10-02-feature-zonky-embedded-postgres.md),
> [implementation](../docs/ai/implementation/2026-10-02-feature-zonky-embedded-postgres.md).

## Décision

| Sujet | Choix |
|---|---|
| Moteur de test | PostgreSQL **réel et embarqué** (`io.zonky.test:embedded-postgres`), version majeure **17** alignée sur docker-compose. H2 et son mode `PostgreSQL` sont supprimés. |
| Isolation | Une instance par JVM de test, **une base par contexte Spring**, initialisée par `docker/postgres/init/01-init-tenant-schema.sql` (schéma `tenant_dev` + rôle applicatif non-superuser). |
| Exécution | Sans Docker. `initdb` refuse root : utilisateur non-root, ou `FLOWFORGE_TEST_PG_URL` pour un PostgreSQL externe. |
| `*IT` | Inclus dans `./mvnw verify` (ils n'y étaient pas : pas de Failsafe). |
| Changelogs | Plus aucune branche `dbms: h2` ; checksums PostgreSQL inchangés, gardés par `ChangelogChecksumStabilityIT`. |
| Démo/QA | Profil Spring `embedded-db` + profil Maven `embedded-db` : base **persistante** (`APP_EMBEDDED_DB_DATA_DIR`), absente du jar de production, interdite avec un profil `prod`/`production` ou `APP_ENVIRONMENT=production` (défaut). |

## Pourquoi pas Supabase / un PostgreSQL managé

Supabase est PostgreSQL + services (Auth, PostgREST, Realtime, Storage) ; seule la base serait utile et
FlowForge garde Keycloak (BFF), un domaine Java hexagonal et un stockage S3 abstrait. Pour la CI, une base
distante partagée n'est ni hermétique ni parallélisable, et l'absence de superuser / le pooler
(`SET search_path` par connexion) s'écartent du modèle réel de production (rôle et schéma par tenant).

## Limites assumées

- Le mode embarqué écoute 127.0.0.1 en authentification `trust` (zonky) : démos/QA sur machine ou
  conteneur non partagé uniquement, jamais en production.
- Un seul tenant (`tenant_dev`) en mode embarqué.
- Changer la version majeure des binaires exige une migration des données (`pg_dump`/restauration) :
  le lanceur refuse un cluster d'une autre version.
