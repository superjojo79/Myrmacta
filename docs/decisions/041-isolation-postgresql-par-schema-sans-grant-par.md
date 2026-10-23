# §41 — Isolation PostgreSQL par schéma sans GRANT par tenant

> **Statut :** clos · **Section :** [Points ouverts › Alignement post-implémentation et sécurité (2026-08-25)](section-points-alignement-post-implementation-et-securite.md) · ancien `TODO.md` §41 · [registre](README.md)

**Clos** (2026-08-25, `durcissement-securite-post-revue`, D-41 = **A** / scope **S**,
[PR #76](https://github.com/superjojo79/FlowForge/pull/76)) — rôle LOGIN
`flowforge_tenant_dev` dans l'init Compose, superuser `POSTGRES_USER` distinct,
IT `TenantRoleIsolationIT` (SKIPPED sans Docker).

**Outillage produit (2026-09-08, lot A1 `provisioning-tenant`)** :
`scripts/provision-tenant.sh` génère/applique le schéma + rôle LOGIN dédié d'un tenant, paramétré
depuis le même gabarit que l'init Compose. Isolation cross-tenant vérifiée avec un vrai PostgreSQL
pendant ce cycle (rôle A refusé sur le schéma de B — `permission denied`, reproduisant
`TenantRoleIsolationIT` sans Testcontainers). Voir point 31 et
[docs/ai/implementation/2026-09-08-feature-provisioning-tenant.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-08-feature-provisioning-tenant.md).

**Énoncé :** `search_path` n'est pas une barrière. Un seul rôle PG (Compose :
superuser de la base `flowforge`) peut lire les autres schémas du cluster
partagé. Combiné au point 40 : RCE sur une instance → données des autres
tenants.

**Options :** (A) un rôle PG par tenant au provisioning — recommandée ;
(B) une database par tenant ; (C) assumer la confiance réseau d'instances.

**À produire :** décision + runbook provisioning (point 31) + IT « user A ne
`SELECT` pas `tenant_b` ».

**Impacte :** [02-multi-tenant.md](../specs/02-multi-tenant.md),
`docker/postgres/init/`, point 31.

**Détail :** [docs/analyses/REVUE-ALIGNEMENT-SECURITE.md](../analyses/REVUE-ALIGNEMENT-SECURITE.md) §4 S-2.
