# Reste à faire — features et lots différés

> Synthèse du travail **reporté** hors des tranches déjà livrées
> (`architecture-hexagonale`, `multi-tenant`, `authentification`, `dossiers`).
>
> - Les **points ouverts à trancher** restent dans [TODO.md](TODO.md).
> - Le détail par feature reste dans `docs/ai/**/2026-07-31-feature-*.md`
>   (sections Non-goals, Différé, Follow-ups).
> - Ce fichier consolide le **chemin d’implémentation restant** pour ne pas le
>   perdre à la clôture de lifecycle.

**Statut lifecycle (check-status) :** `architecture-hexagonale`, `multi-tenant`,
`authentification`, `dossiers`, `workflow-engine`, `autorisation-conformite`,
`connecteurs` sont **Complete** (mergés). Prochaine feature démarrée :
`templates-documents` (phase 1 requirements draft). Les features
`generation-documents`, `notifications`, `visualisation-workflow`, `frontend`
restent en docs skeleton / planning non exécuté.

---

## 0. Stockage objet (contrainte transversale)

**Ne pas coupler à MinIO.** Quand le stockage objet arrivera (`generation-documents`,
pièces jointes, purge §22), introduire un **port abstrait** avec adaptateur
**S3** (cible) et **filesystem** (alternative). Aucun port code dans
`autorisation-conformite` (doc seule).

---

## 1. Features livrées — follow-ups techniques

### 1.1 `architecture-hexagonale`

| Item | Notes |
|---|---|
| Remplacer stubs in-memory | `WorkflowInstanceRepository` / `WorkflowEventPublisher` → JPA + Flowable (`workflow-engine`) |
| Retirer exclusions autoconfig restantes | Flowable (et tout résidu) feature par feature |
| Sync lexicale `com.monapp` → `com.flowforge` | Chore dans `analysis doc/` |
| Validation blank `processKey` / `tenantId` | Nice-to-have modèle |

### 1.2 `multi-tenant`

| Item | Notes |
|---|---|
| Activation `server.servlet.context-path` | Routage par chemin (décision n° 14) — yaml encore commenté |
| Datasource + search_path runtime | Partiellement amorcé avec `dossiers` ; hardening ops |
| Bootstrap Liquibase / schéma tenant | Ops + contextes `tenant-init` / `tenant-migration` (décision n° 10) |
| Outillage provisioning | Schéma, instance, bloc reverse-proxy, surcharge obligatoire `app.tenant.id` (voir TODO §31 / docs feature) |
| `platform.tenant_migration_status` | Observabilité migration (lié TODO §29) |
| Injecter `TenantProperties.id()` aux adaptateurs HTTP | Dès premiers controllers métier (déjà fait pour dossiers) |

### 1.3 `authentification`

| Item | Notes |
|---|---|
| Chaîne `@Order(1)` M2M `client_credentials` | Décision n° 14 — resource server JWT |
| Chaîne `@Order(2)` webhooks | Sans session / CSRF (décision n° 15) |
| Checklist IdP | Claims `tenant_id` + `realm_access.roles` sur **ID token** |
| Smoke Keycloak réel | Hors CI |
| IT MockMvc via claim `realm_access` | Nice-to-have (aujourd’hui unitaire + `oidcLogin()`) |
| Spring Session / multi-répliques | Hors V1 (décision n° 13) |

### 1.4 `dossiers` (tranche B)

| Item | Notes |
|---|---|
| Éléments de dossier (`DossierElement`) | Lot suivant — fichiers / MinIO |
| Relations entre dossiers | Lot suivant |
| `DossierWorkflowLink` + AttachWorkflow | Lot `workflow-engine` |
| PATCH fields dossier | Extension API (point 24) |
| Liste / recherche / pagination dossiers | Point 24 |
| Index Liquibase `(tenant_id, dossier_type_code)` | Nice-to-have |
| CHECK SQL `permission ∈ {VIEW,EDIT}` | Nice-to-have |
| `Long version` / `Persistable` pour `isNew()` | Nice-to-have JPA |
| Smoke PostgreSQL réel | Hors CI (IT actuelles = H2 MODE=PostgreSQL) |
| OpenAPI formel | Point 24 |
| Rétention / purge / `sensitive` | TODO §21–23 |
| Observabilité MDC dossiers | TODO §29 |

**Déjà couvert en B (ne pas replanifier) :** types + dossiers CRUD minimal,
validation champs, références séquentielles, `@Version`, ACL type, API REST
minimale, `ProblemDetail`, gate `TENANT_ADMIN`.

---

## 2. Features lifecycle non livrées

Ordre indicatif aligné sur l’analyse et les dépendances techniques :

| Ordre | Feature | Dépendances / livrable attendu |
|---|---|---|
| — | `workflow-engine` | **Complete** (main) |
| — | `autorisation-conformite` | **Complete** (main) |
| — | `connecteurs` | **Complete** (main) |
| 1 | `templates-documents` | Templates Thymeleaf versionnés — **phase 1 draft** (CRUD + resolve) |
| 2 | `generation-documents` | PDF + stockage objet via port abstrait (S3/FS — pas MinIO en dur) |
| 3 | `notifications` | Outbox notifications, webhooks sortants, SSE |
| 4 | `visualisation-workflow` | Diagramme BPMN / état runtime |
| 5 | `frontend` | Vue 3 + Pinia + Axios dans `static/` (décision n° 27) |

---

## 3. Points TODO encore ouverts (mapping)

Voir le détail et les options dans [TODO.md](TODO.md) et
[REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md).

| Point | Titre | État après tranches livrées |
|---|---|---|
| 19 | Granularité rôles RBAC | **Clos V1** — cinq rôles + mapping `ROLE_*` ; ACL type |
| 21 | Rétention par `DossierType` | **Ouvert** — hors tranche authz |
| 22 | Purge PostgreSQL / object store | **Ouvert** — port S3/FS à venir ; pas MinIO en dur |
| 23 | `FieldDefinition.sensitive` | **Partiel** — flag meta livré ; masquage/exports différés |
| 24 | Contrat d’API complet | **Partiel** — surface dossiers minimale + `ProblemDetail` ; reste listes, pagination, OpenAPI, autres ressources |
| 25 | Modèle physique consolidé (ERD) | **Partiel** — tables dossiers livrées ; reste ERD global, `platform.*`, ACT_*, outbox, etc. |
| 26 | Concurrence dossier / référence | **Partiel / quasi clos en B** — `@Version` + `dossier_reference_seq` + format UTC ; reste smoke PG multi-pods |
| 28 | Stratégie de test | **Partiel** — ArchUnit + Jacoco ≥ 80 % + H2 ; reste Testcontainers / charge Flowable / outbox |
| 29 | Observabilité | **Ouvert** — monitoring features = `deferred` |
| 30 | Durcissement entrées exposées | **Ouvert** — rate limit, SSRF, antivirus PJ… |
| 31 | Découpage en lots / provisioning | **Partiel** — lots lifecycle démarrés ; outillage provisioning ops encore à produire |

---

## 4. Ops & qualité transverses

- Provisioning tenant : schéma PG, instance Boot, bloc proxy, secrets OIDC
- Surcharge prod de `app.tenant.id` (ne jamais laisser `tenant_dev`)
- Smoke manuel : Keycloak réel, PostgreSQL réel
- Observabilité : MDC `tenantId` / `dossierId` / `processInstanceId`, métriques Micrometer (TODO §29)
- Chore docs : `com.monapp` résiduel dans `analysis doc/`

---

## 5. Sources

| Source | Usage |
|---|---|
| [TODO.md](TODO.md) | Points ouverts + décisions actées |
| `docs/ai/**/2026-07-31-feature-{architecture-hexagonale,multi-tenant,authentification,dossiers}.md` | Non-goals, Différé, Follow-ups phase 9 |
| `docs/ai/**/2026-07-31-feature-{workflow-engine,autorisation-conformite,connecteurs,…}.md` | Features non démarrées |
| `analysis doc/` | Cible architecture |
