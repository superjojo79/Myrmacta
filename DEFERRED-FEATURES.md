# Reste à faire — features et lots différés

> Synthèse du travail **reporté** hors des tranches déjà livrées
> (`architecture-hexagonale`, `multi-tenant`, `authentification`, `dossiers`).
>
> - Les **points ouverts à trancher** restent dans [TODO.md](TODO.md).
> - L’**ordre de la prochaine feature** est dans [FILE-ATTENTE.md](FILE-ATTENTE.md).
> - Le détail par feature reste dans `docs/ai/**/2026-*-feature-*.md`
>   (sections Non-goals, Différé, Follow-ups).
> - Ce fichier consolide le **chemin d’implémentation restant** pour ne pas le
>   perdre à la clôture de lifecycle.

**Statut lifecycle (check-status) :** `architecture-hexagonale`, `multi-tenant`,
`authentification`, `dossiers`, `workflow-engine`, `autorisation-conformite`,
`connecteurs`, `templates-documents`, `generation-documents`, `notifications`,
`visualisation-workflow`, `frontend`, `pagination-dossiers` (en cours de merge) sont **Complete**/lifecycle advanced (mergés — `frontend` via
PR #38 `cursor/feature-frontend-21f2`, vague 1 instructeur + vague 2 admin
types/templates/connectors/workflow-configs ; packaging Maven→`static/` ;
maquette
[FlowForge - Maquettes UI.html](analysis%20doc/FlowForge%20-%20Maquettes%20UI.html)
= référence UX ; pas Rapports/SLA ni liste dossiers tant que TODO §24).

---

## 0. Stockage objet (contrainte transversale)

**Ne pas coupler à MinIO.** Port abstrait `DocumentStoragePort` (adaptateurs
**S3** cible + **filesystem**) déjà livré (`generation-documents`, pièces
jointes). `delete` livré avec `purge-stockage` (§22). Aucun couplage MinIO.

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
| Éléments de dossier (`DossierElement`) | **Livré** — feature `elements-dossier` (extension `PRODUCED_DOC`→`ATTACHMENT`/`INFORMATION`/`NOTE`/`LINK`, API + UI, réutilisation stricte de `DocumentStoragePort`/`DossierAccessPolicy` existants, aucun couplage MinIO en dur — conforme §0) |
| Relations entre dossiers | **Livré** — feature `relations-dossiers` (API + UI + ServiceTask) |
| `DossierWorkflowLink` + AttachWorkflow | Lot `workflow-engine` |
| PATCH fields dossier | **Livré** — feature `patch-dossier-fields` (fusion + audit `sensitive`) ; **UI détail** — feature `edition-champs-dossier` |
| Liste / recherche / pagination dossiers | **Livré** — feature `pagination-dossiers` (API + Supervision) |
| Index Liquibase `(tenant_id, dossier_type_code)` | Nice-to-have |
| CHECK SQL `permission ∈ {VIEW,EDIT}` | Nice-to-have |
| `Long version` / `Persistable` pour `isNew()` | Nice-to-have JPA |
| Smoke PostgreSQL réel | Hors CI (IT actuelles = H2 MODE=PostgreSQL) |
| OpenAPI formel | **Partiel clos** — `openapi-pagination-admin` (PR #58) : springdoc + YAML CI ; reste codegen |
| Rétention / purge / `sensitive` | TODO §21–23 |
| Observabilité MDC dossiers | TODO §29 |

**Déjà couvert en B (ne pas replanifier) :** types + dossiers CRUD minimal,
validation champs, références séquentielles, `@Version`, ACL type, API REST
minimale, `ProblemDetail`, gate `TENANT_ADMIN`.

### 1.5 `visualisation-workflow` (mergé)

| Item | Notes |
|---|---|
| Cockpit Flowable | Hors scope produit |
| SSE multi-pod | Hors V1 (session mono-réplique) |

**Déjà couvert (ne pas replanifier) :** viewer Vue / `bpmn-js`
(`frontend/src/components/workflow/BpmnViewer.vue`, feature `frontend`,
consomme `GET …/diagram`).

### 1.6 `frontend` (mergé)

| Item | Notes |
|---|---|
| Écrans Rapports / SLA | Hors scope V1 (mock UI de référence uniquement) |
| Liste / recherche / pagination dossiers (Supervision complet) | **Livré** — `pagination-dossiers` |
| `npm run test` / `lint` / `test:e2e` en CI | Câblé (`.github/workflows/ci.yml`, job `frontend`) |

### 1.7 `openapi-pagination-admin` (PR #58)

| Item | Notes |
|---|---|
| Codegen client OpenAPI (typescript-axios, etc.) | Hors V1 — SPA reste écrite à la main ; golden YAML = contrat |
| Pagination nested lists | `GET /api/connector-definitions`, relations, tasks, éléments restent des arrays |
| Spec SSE / multipart / webhook ingress | Volontairement `@Hidden` (D-SPEC) |
| Fusion `DossierPage` → `PageResult` | Non — A-PAGE-TYPE |

---

## 2. Features lifecycle

Ordre indicatif aligné sur l’analyse et les dépendances techniques :

| Ordre | Feature | Dépendances / livrable attendu |
|---|---|---|
| — | `workflow-engine` | **Complete** (main) |
| — | `autorisation-conformite` | **Complete** (main) |
| — | `connecteurs` | **Complete** (main) |
| — | `templates-documents` | **Complete** (main) |
| — | `generation-documents` | **Complete** (main) |
| — | `notifications` | **Complete** (main) |
| — | `visualisation-workflow` | **Complete** (main) — API diagram/history/tasks/reassign + participants + SSE |
| — | `frontend` | **Complete** (main, PR #38) — vague 1 : socle + auth(`sub`) + CRUD parcours dossier/viz/SSE ; vague 2 : admin types/ACL/templates/connectors/WF configs ; UX = maquette HTML (décision n° 27) |
| — | `preview-documents` | **Complete** (`cursor/preview-documents-dbc9`) — lot D-PREVIEW livré : HTML temps réel + PDF inline, admin templates, D-AUTHZ B, D-RATE-SPLIT C. |
| — | `edition-champs-dossier` | **Complete** (PR #53) — UI `PATCH /api/dossiers/{id}/fields` sur le détail (D-DIFF, D-DEFAULTS). |
| — | `retention-dossier-type` | **Complete** (PR #55) — settings tenant + override type ; job ARCHIVE + `purge_outbox` ; UI admin. |
| — | `purge-stockage` | **Complete** Phase 9 READY (PR #56) — job `purge_outbox` store+PG ; `DocumentStoragePort.delete` ; UI file lecture + enqueue admin. |
| — | `openapi-pagination-admin` | **Complete** Phase 5 (PR #58) — envelope 6 GET admin ; OpenAPI golden ; pagers + webhooks admin ; catalogues `listAllPages`. |

---

## 3. Points TODO encore ouverts (mapping)

Voir le détail et les options dans [TODO.md](TODO.md) et
[REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md).

| Point | Titre | État après tranches livrées |
|---|---|---|
| 19 | Granularité rôles RBAC | **Clos V1** — cinq rôles + mapping `ROLE_*` ; ACL type |
| 21 | Rétention par `DossierType` | **Livré** (2026-08-19) — `retention-dossier-type` |
| 22 | Purge PostgreSQL / object store | **Livré** (2026-08-19) — `purge-stockage` PR #56 ; Q1–Q11 |
| 23 | `FieldDefinition.sensitive` | **Quasi clos** — flag meta + masquage serveur livrés (`DossierFieldRedactor`, D-SENSITIVE = A) sur `GET /api/dossiers/**` et le panneau dossier de la visualisation ; reste masquage sur d'éventuels futurs exports (CSV, etc.) |
| 24 | Contrat d’API complet | **Partiel clos** (2026-08-19) — OpenAPI + pagination admin + webhooks UI (`openapi-pagination-admin` PR #58). Reste codegen, nested lists, spec SSE/multipart/ingress |
| 25 | Modèle physique consolidé (ERD) | **Partiel** — tables dossiers livrées ; reste ERD global, `platform.*`, ACT_*, outbox, etc. |
| 26 | Concurrence dossier / référence | **Partiel / quasi clos en B** — `@Version` + `dossier_reference_seq` + format UTC ; reste smoke PG multi-pods |
| 28 | Stratégie de test | **Partiel** — ArchUnit + Jacoco ≥ 80 % + H2 ; reste Testcontainers / charge Flowable / outbox ; FE = Vitest + Playwright, câblés en CI (job `frontend`, `.github/workflows/ci.yml`) |
| 29 | Observabilité | **Phase 2 en cours** (PR #59) — Q1 = B ; Q2–Q6 à valider ; D-FE acté |
| 30 | Durcissement entrées exposées | **Partiel** — rate limit + taille max sur webhooks entrants et `POST /api/documents/generate` ; SSRF (PDF + webhooks sortants) fermé ; reste antivirus PJ, upload générique… |
| 31 | Découpage en lots / provisioning | **Partiel** — lots lifecycle démarrés ; outillage provisioning ops encore à produire |

---

## 4. Ops & qualité transverses

- Provisioning tenant : schéma PG, instance Boot, bloc proxy, secrets OIDC
- Surcharge prod de `app.tenant.id` (ne jamais laisser `tenant_dev`)
- Smoke manuel : Keycloak réel, PostgreSQL réel
- Observabilité : MDC + Micrometer + UI admin (TODO §29, requirements reviewed PR #59)
- Chore docs : `com.monapp` résiduel dans `analysis doc/`
- Build FE : `npm run build` → copie vers `src/main/resources/static/` (Maven plugin ou CI)

---

## 5. Sources

| Source | Usage |
|---|---|
| [FILE-ATTENTE.md](FILE-ATTENTE.md) | Prochaine feature + critères de sélection |
| [TODO.md](TODO.md) | Points ouverts + décisions actées |
| `docs/ai/**/2026-*-feature-*.md` | Non-goals, Différé, Follow-ups lifecycle |
| [11-frontend.md](analysis%20doc/11-frontend.md) | Stack FE + packaging + maquette |
| [FlowForge - Maquettes UI.html](analysis%20doc/FlowForge%20-%20Maquettes%20UI.html) | Référence UX / écrans |
| `analysis doc/` | Cible architecture |
