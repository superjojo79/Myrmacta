# Reste à faire — features et lots différés

> Synthèse du travail **reporté** hors des tranches déjà livrées
> (`architecture-hexagonale`, `multi-tenant`, `authentification`, `dossiers`).
>
> - Les **points ouverts à trancher** restent dans [TODO.md](TODO.md).
> - L’**ordre de la prochaine feature** (et les vagues parallèles) est dans [FILE-ATTENTE.md](FILE-ATTENTE.md).
> - Le détail par feature reste dans `docs/ai/**/2026-*-feature-*.md`
>   (sections Non-goals, Différé, Follow-ups).
> - Ce fichier consolide le **chemin d’implémentation restant** pour ne pas le
>   perdre à la clôture de lifecycle.

**Statut lifecycle (check-status) :** `architecture-hexagonale`, `multi-tenant`,
`authentification`, `dossiers`, `workflow-engine`, `autorisation-conformite`,
`connecteurs`, `templates-documents`, `generation-documents`, `notifications`,
`visualisation-workflow`, `frontend`, `pagination-dossiers`, `preview-documents`,
`edition-champs-dossier`, `retention-dossier-type`, `purge-stockage`,
`openapi-pagination-admin`, `observabilite`, `durcissement-donnees-sensibles`,
`contrat-api-client`, `fiabilisation-tests-plateforme`, `deploiement-bpmn`,
`modele-physique-consolide`, `masquage-donnees-sensibles-logs-webhooks`
(PR #70), `edition-bpmn` (PR #71), `reporting-sla` (PR #80), `serveur-mcp`
(PR #82), `schema-champs-dynamiques` (PR #87), `connecteur-http-rest-service`
(PR #95, TODO §48), `assainissement-dette-technique` (PR #97, TODO §49),
`connecteurs-hors-v1` (PR #99, TODO §50), `connecteur-callback` (PR #102,
TODO §51), `formulaires-dynamiques-taches` (TODO §52),
`questions-utilisateur-taches` (TODO §55), `extension-mcp-flux-metier`
(PR #111, TODO §56), `revue-adversariale-securite` (PR #113, TODO §57),
contre-revue post-merge (PR #115, TODO §58)
sont **Complete** (mergées sur `main` sauf
mention contraire — `frontend` via PR #38
`cursor/feature-frontend-21f2`, vague 1 instructeur + vague 2 admin
types/templates/connectors/workflow-configs ; packaging Maven→`static/` ;
maquette
[FlowForge - Maquettes UI.html](analysis%20doc/FlowForge%20-%20Maquettes%20UI.html)
= référence UX). Détail des lots livrés en §2.
**TODO.md entièrement clos** (points 1–58). **File d'attente (2026-09-08) :**
reliquat V1.x / ops cadré en **vagues parallèles** — voir
[FILE-ATTENTE.md](FILE-ATTENTE.md) § *File d’attente* et **§6** ci-dessous.
[REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md) intégralement
close (§40–46).

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
| Sync lexicale `com.monapp` → `com.flowforge` | **Clos** (2026-08-25) — 3 occurrences dans `analysis doc/01-architecture-hexagonale.md` corrigées |
| Validation blank `processKey` / `tenantId` | **Clos** — `tenantId` déjà validé (`TenantProperties.id` `@NotBlank`, code préexistant) ; `processKey`/`workflowKey` désormais gardés blank dans les records domaine (`WorkflowTypeConfig`, `TriggerOutboxPayload`, `WorkflowStartedEvent`, `DossierType`) — `assainissement-dette-technique` (TODO §49) |

### 1.2 `multi-tenant`

| Item | Notes |
|---|---|
| Activation `server.servlet.context-path` | Routage par chemin (décision n° 14) — yaml encore commenté |
| Datasource + search_path runtime | Partiellement amorcé avec `dossiers` ; hardening ops |
| Bootstrap Liquibase / schéma tenant | Ops (bootstrap du schéma au provisioning). **Obsolète :** les contextes `tenant-init`/`tenant-migration` séparés évoqués ici (décision n° 10) n'existent pas dans le code — la décision n° 1 porte une note explicite disant qu'ils « n'ont plus lieu d'être » depuis la décision n° 13 (instance dédiée par tenant, changelog unique appliqué à chaque démarrage). Confirmé par `modele-physique-consolide` (PR #68, TODO §25). |
| Outillage provisioning | Schéma, instance, bloc reverse-proxy, surcharge obligatoire `app.tenant.id` — jamais numéroté en TODO.md à ce jour (TODO §31 porte sur le découpage en lots, **clos**, pas sur cet outillage) |
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
| Index Liquibase `(tenant_id, dossier_type_code)` | **Déjà couvert** (vérifié 2026-08-25) — `idx_dossier_type_updated_at ON dossier (dossier_type_code, updated_at DESC)` existe depuis `008-dossier-list-indexes.yaml` ; `tenant_id` en tête serait redondant (cardinalité 1 par schéma, une instance = un tenant, décision n° 13) |
| CHECK SQL `permission ∈ {VIEW,EDIT}` | **Clos** — migration `014-dossier-type-acl-permission-check.yaml` (PR #72), livré hors cycle formel, jamais répercuté ici avant `assainissement-dette-technique` (TODO §49) |
| `Long version` / `Persistable` pour `isNew()` | **Clos, vérifié non nécessaire** — `@Version private long` déjà sur `DossierJpaEntity`, comportement standard Spring Data JPA (`JpaMetamodelEntityInformation.isNew()`) suffit à distinguer persist/merge malgré l'UUID assigné côté application ; preuve documentée dans `docs/ai/design/2026-09-01-feature-assainissement-dette-technique.md` — `assainissement-dette-technique` (TODO §49) |
| Smoke PostgreSQL réel | Hors CI (IT actuelles = H2 MODE=PostgreSQL) |
| OpenAPI formel | **Clos** — `openapi-pagination-admin` (PR #58) : springdoc + YAML CI ; complété par `contrat-api-client` (PR #65, TODO §24 clos) : 6 listes non-admin paginées, golden OpenAPI régénéré. Reste hors scope assumé : codegen client (décision D-CODEGEN) |
| Rétention / purge / `sensitive` | §21/§22 livrés ; §23 reste réduit (voir §3 ci-dessous) |
| Observabilité MDC dossiers | **Livré** — `observabilite` PR #59 |

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
| Écrans Rapports / SLA | **Livré** — feature `reporting-sla` ([PR #80](https://github.com/superjojo79/FlowForge/pull/80), BC `reporting`, SPA `/rapports`) |
| Liste / recherche / pagination dossiers (Supervision complet) | **Livré** — `pagination-dossiers` |
| `npm run test` / `lint` / `test:e2e` en CI | Câblé (`.github/workflows/ci.yml`, job `frontend`) |

### 1.7 `openapi-pagination-admin` (PR #58)

| Item | Notes |
|---|---|
| Codegen client OpenAPI (typescript-axios, etc.) | Hors V1 — SPA reste écrite à la main ; golden YAML = contrat |
| Pagination nested lists | `GET /api/connector-definitions`, relations, tasks, éléments restent des arrays |
| Spec SSE / multipart / webhook ingress | Volontairement `@Hidden` (D-SPEC) |
| Fusion `DossierPage` → `PageResult` | Non — A-PAGE-TYPE |

### 1.8 `observabilite` (PR #59)

| Item | Notes |
|---|---|
| Wrapper MDC async executor Flowable Community 8 | Hors V1 — fallback jobs applicatifs ; PI après start outbox |
| `dossierId` MDC par item de purge | **Livré** — callback `Function<UUID, Runnable>` exposé par `ProcessPurgeOutboxUseCase`, `PurgeOutboxJob` ouvre/ferme `MdcScope.dossier(...)` par item sans dépendance MDC dans le domaine — `assainissement-dette-technique` (TODO §49) |
| IT stdout ECS `tenantId` | Config `ecs` livrée ; smoke manuel |
| OTel / Grafana-as-code / Alertmanager | Hors V1 |

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
| — | `observabilite` | **Complete** Phase 9 READY (PR #59) — ECS + MDC ; Micrometer port management ; snapshot + SPA `/admin/observabilite`. |
| — | `durcissement-donnees-sensibles` | **Complete** (PR #62) — magic-bytes + `AttachmentScanPort` no-op sur upload ; export CSV masqué + anti-injection formule ; skeleton `HttpRestServiceConnector` ; garde-fous anti-fuite secrets. TODO §23 reste réduit, §30 clos. |
| — | `contrat-api-client` | **Complete** (PR #65) — 6 listes non-admin paginées (elements, workflow-tasks/mine, connector-definitions, dossiers/{id}/workflows, workflow-links history+tasks) ; golden OpenAPI régénéré. TODO §24 clos. |
| — | `fiabilisation-tests-plateforme` | **Complete** (PR #66) — Testcontainers PostgreSQL, `SequentialReferenceMultiPodIT`, `TenantSchemaIsolationIT`, `LiquibasePartialFailureIT`, seuils Jacoco relevés. TODO §26/§28/§37 clos. |
| — | `deploiement-bpmn` | **Complete** (PR #67) — écran admin d'upload BPMN avec prévisualisation (`BpmnUploadPreview.vue`), `DeployBpmnProcessUseCase`, `FlowableWorkflowDeploymentAdapter`. TODO §35 clos. |
| — | `modele-physique-consolide` | **Complete** (PR #68) — ERD consolidé [13-modele-physique.md](analysis%20doc/13-modele-physique.md) (17 tables, 4 index uniques partiels, 2 FK DB) ; correction de la prémisse obsolète `TenantRegistry`/contextes Liquibase séparés. TODO §25 clos. |
| — | `masquage-donnees-sensibles-logs-webhooks` | **Complete** (PR #70) — audit préalable : aucune fuite active trouvée (logs/webhooks), protection préventive ajoutée : `toString()` sûr sur `Dossier`/`FieldDefinition` + liste blanche ArchUnit ; `SendNotificationUseCaseImpl` masque automatiquement les champs sensibles avant écriture outbox. TODO §23 clos. |
| — | `edition-bpmn` | **Complete** (PR #71) — éditeur `bpmn-js` Modeler + panel Flowable custom ; brouillon persisté ; TODO §39 clos. |
| — | `reporting-sla` | **Complete** (PR #80) — snapshot + summary SLA, cible par type, settings tenant, SPA `/rapports`, inbox remaining. |
| — | `serveur-mcp` | **Complete** (PR #82) — serveur MCP interne, 9 tools (`get_dossier`, `search_dossiers`, `query_sla_summary`, `list_document_templates`, `get_dossier_type`, `list_dossier_types`, `list_workflow_types`, `define_dossier_type`, `deploy_workflow`) sur `POST /api/m2m/mcp` (Streamable HTTP, Spring AI 2.0.0), JWT M2M `API_SERVICE` réutilisé ; zéro fichier frontend touché. |
| — | `schema-champs-dynamiques` | **Complete** (PR #87) — écran Admin → Types édite le schéma `fieldDefinitions` d'un `DossierType` (création + édition), blocage 409 sur schéma incompatible avec des dossiers existants. TODO §47 clos. |
| — | `connecteur-http-rest-service` | **Complete** ([PR #95](https://github.com/superjojo79/FlowForge/pull/95)) — connecteur `http-rest` utilisable en rôle SERVICE (capability `BOTH`), fusion avec le skeleton `HttpRestServiceConnector` (supprimé) dans `HttpRestConnector` renommé. GET/POST/PUT dynamiques, `authHeaderValue` chiffré, nouveau bean `HttpClient`. TODO §48 clos. |
| — | `connecteurs-hors-v1` | **Complete** ([PR #99](https://github.com/superjojo79/FlowForge/pull/99)) — 3 derniers connecteurs du catalogue cible : `scheduler` (TRIGGER, cron via job `@Scheduled` + table `connector_schedule_state`), `smtp` (SERVICE, réutilise `EmailPort`), `document-storage` (SERVICE, généralisé depuis `s3-minio`, réutilise `DocumentStoragePort`). TODO §50 clos. |
| — | `connecteur-callback` | **Complete** ([PR #102](https://github.com/superjojo79/FlowForge/pull/102), correctifs #103/#104) — capability `CALLBACK`, `POST /api/callbacks/{instanceId}`. TODO §51/§53/§54 clos. |
| — | `formulaires-dynamiques-taches` | **Complete** (TODO §52) — `flowable:formKey` `dossier-fields` / `document-generation`. |
| — | `questions-utilisateur-taches` | **Complete** (TODO §55) — kind `user-question:<schemaKey>`. |
| — | `extension-mcp-flux-metier` | **Complete** ([PR #111](https://github.com/superjojo79/FlowForge/pull/111)) — 9 → 16 tools MCP. TODO §56 clos. |
| — | `revue-adversariale-securite` | **Complete** ([PR #113](https://github.com/superjojo79/FlowForge/pull/113) + [PR #115](https://github.com/superjojo79/FlowForge/pull/115)) — High/Medium §57 + contre-revue §58. |

---

## 3. Points TODO encore ouverts (mapping)

Voir le détail et les options dans [TODO.md](TODO.md),
[REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md) (points 1–39) et
[REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md) (points 40–46).

| Point | Titre | État après tranches livrées |
|---|---|---|
| 19 | Granularité rôles RBAC | **Clos V1** — cinq rôles + mapping `ROLE_*` ; ACL type |
| 21 | Rétention par `DossierType` | **Livré** (2026-08-19) — `retention-dossier-type` |
| 22 | Purge PostgreSQL / object store | **Livré** (2026-08-19) — `purge-stockage` PR #56 ; Q1–Q11 |
| 23 | `FieldDefinition.sensitive` | **Clos** (2026-08-24, `masquage-donnees-sensibles-logs-webhooks`) — en plus du masquage serveur déjà livré (`DossierFieldRedactor` sur `GET /api/dossiers/**`, panneau visualisation, export CSV), audit préalable : aucune fuite active trouvée dans les logs ni les webhooks (risque latent, pas un incident). Protection préventive ajoutée : `toString()` sûr sur `Dossier`/`FieldDefinition`, liste blanche ArchUnit sur `Dossier.fields()`/`FieldDefinition.defaultValue()`, masquage automatique dans `SendNotificationUseCaseImpl` avant écriture outbox (webhook + browser) |
| 24 | Contrat d’API complet | **Clos** (2026-08-21, `contrat-api-client` PR #65) — en plus de `openapi-pagination-admin` (PR #58), 6 listes non-admin paginées, golden OpenAPI régénéré. Codegen client explicitement exclu (décision D-CODEGEN) |
| 25 | Modèle physique consolidé (ERD) | **Clos** (2026-08-22, `modele-physique-consolide` PR #68) — [13-modele-physique.md](analysis%20doc/13-modele-physique.md) : ERD des 17 tables réelles, 4 index uniques partiels, répartition tenant/`platform`. L'énoncé d'origine était en partie obsolète (`TenantRegistry`, contextes Liquibase séparés — n'existent plus depuis la décision n° 13), corrigé dans le nouveau document |
| 26 | Concurrence dossier / référence | **Clos** (2026-08-22, `fiabilisation-tests-plateforme` PR #66) — `SequentialReferenceMultiPodIT` (Testcontainers PostgreSQL, 8 pods simulés, 80 générations concurrentes) prouve l'absence de doublon/trou sous charge réelle |
| 28 | Stratégie de test | **Clos** (2026-08-22, `fiabilisation-tests-plateforme` PR #66) — Testcontainers PostgreSQL ajoutés (`TenantSchemaIsolationIT`, `LiquibasePartialFailureIT`, `SequentialReferenceMultiPodIT`), seuils Jacoco relevés ; FE = Vitest + Playwright, câblés en CI (job `frontend`, `.github/workflows/ci.yml`) |
| 29 | Observabilité | **Livré** (2026-08-20) — `observabilite` PR #59 Phase 9 READY ; Q1–Q6 |
| 30 | Durcissement entrées exposées | **Clos** (2026-08-21, `durcissement-donnees-sensibles` PR #62) — en plus du rate limit/taille max/SSRF déjà livrés, validation magic-bytes des pièces jointes + port `AttachmentScanPort` pluggable (adaptateur no-op, pas d'antivirus dans la stack figée — décision explicite, contrat prêt pour un futur adaptateur type ClamAV) + audit anti-fuite de secrets déchiffrés |
| 31 | Découpage en lots / provisioning | **Partiel** — lots lifecycle V1 livrés ; outillage provisioning ops = lot **A1** ([FILE-ATTENTE.md](FILE-ATTENTE.md)) |
| 40 | SSTI Thymeleaf / SpEL | **Clos** (2026-08-25, `durcissement-securite-post-revue` PR #76) — évaluateur allowlist (`#vars`/`#dates`/`#numbers`/`#strings`/`#lists` + chemins modèle), D-40 = B/B2 |
| 41 | Isolation PG sans GRANT par tenant | **Clos** (2026-08-25, PR #76) — GRANT PG `LOGIN` par tenant |
| 42 | Actuator `permitAll` / port 8081 | **Clos** (2026-08-25, PR #76) — actuator bindé `127.0.0.1:8090` |
| 43 | Secrets défaut + HMAC M2M | **Clos** (2026-08-25, PR #76) — secrets fail-fast hors profils `local`/`test` |
| 44 | SSRF sortant DNS TOCTOU | **Clos** (2026-08-25, PR #76) — pin IP résolue sur le webhook sortant |
| 45 | Dérive documentaire README / analyse | **Clos** (2026-08-25, `derive-documentaire` [PR #75](https://github.com/superjojo79/FlowForge/pull/75)) |
| 46 | ArchUnit couche `application` | **Clos** (2026-08-31) — port `WorkflowStartConfirmationPort` extrait, règle ArchUnit `application_never_depends_on_infrastructure` ajoutée |
| 49 | Assainissement dette technique | **Clos** (2026-09-01, `assainissement-dette-technique` [PR #97](https://github.com/superjojo79/FlowForge/pull/97)) — validation blank domaine (4 records), `dossierId` en MDC par item de purge, `Persistable` confirmé redondant |
| 50 | Nouveaux connecteurs hors V1 (`scheduler`/`smtp`/`document-storage`) | **Clos** (2026-09-01, `connecteurs-hors-v1`, [PR #99](https://github.com/superjojo79/FlowForge/pull/99)) — `scheduler` (TRIGGER, cron), `smtp` (SERVICE, réutilise `EmailPort`), `document-storage` (SERVICE, généralisé depuis `s3-minio` vers `DocumentStoragePort`, pas de couplage MinIO) |
| 48 | Connecteur `http-rest` en rôle SERVICE | **Clos** (2026-08-31, `connecteur-http-rest-service`) — capability `BOTH`, fusion `HttpRestConnector`, `authHeaderValue`, nouveau bean `HttpClient` |

---

## 4. Ops & qualité transverses

- Provisioning tenant : **lot A1** — schéma PG, instance Boot, bloc proxy, secrets OIDC, Dockerfile (aucun aujourd’hui)
- Surcharge prod de `app.tenant.id` (ne jamais laisser `tenant_dev`)
- Smoke manuel : Keycloak réel, PostgreSQL réel — **hors file produit**
- Observabilité : **livré** — MDC + Micrometer + UI admin (TODO §29, PR #59)
- Chore docs : `com.monapp` dans `analysis doc/01` — **clos** (2026-08-25, PR #72) ;
  dérive README / `analysis doc/` (TODO §45) — **clos** (PR #75)
- Build FE : `npm run build` → copie vers `src/main/resources/static/` (Maven plugin ou CI)
- Reliquats sécu V1 assumés (pas rouverts **sauf** lots A4 headers / C ClamAV) : AV no-op, rate-limit JVM, headers HTTP —
  [REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md) §4 Low

---

## 5. Sources

| Source | Usage |
|---|---|
| [FILE-ATTENTE.md](FILE-ATTENTE.md) | Prochaine feature + **vagues parallèles** (surfaces exclusives) |
| [TODO.md](TODO.md) | Points ouverts + décisions actées |
| [REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md) | Revue 2026-08-25 alignement × sécurité (points 40–46) |
| `docs/ai/**/2026-*-feature-*.md` | Non-goals, Différé, Follow-ups lifecycle |
| [11-frontend.md](analysis%20doc/11-frontend.md) | Stack FE + packaging + maquette |
| [FlowForge - Maquettes UI.html](analysis%20doc/FlowForge%20-%20Maquettes%20UI.html) | Référence UX / écrans |
| `analysis doc/` | Cible architecture |

---

## 6. Lots V1.x — lancement parallèle (2026-09-08)

> **Source opérationnelle :** [FILE-ATTENTE.md](FILE-ATTENTE.md) § *File d’attente*
> (tables des surfaces exclusives). Ne pas dupliquer le brief ici — ce § est
> l’inventaire pour ne pas reperdre les lots après clôture du V1 nommé.

| Vague | Feature | Statut | Notes |
|---|---|---|---|
| **A1** | `provisioning-tenant` | **Livré** ([PR #119](https://github.com/superjojo79/FlowForge/pull/119)) | Dockerfile + runbook/CLI ; mergé 2026-09-08 |
| **A2** | `admin-connector-form-schema` | **Livré** ([PR #117](https://github.com/superjojo79/FlowForge/pull/117)) | FE seulement ; `configSchema` déjà exposé par l’API ; D-WEBHOOK-URL = C ; mergé 2026-09-08 |
| **A3** | `mcp-ecritures-metier` | **Livré** ([PR #118](https://github.com/superjojo79/FlowForge/pull/118)) | 4 tools dans **un** cycle ; cadrage [15-serveur-mcp.md](analysis%20doc/15-serveur-mcp.md) § Backlog ; TODO §60 clos ; mergé 2026-09-08 |
| **A4** | `headers-http-globaux` | **Livré** ([PR #128](https://github.com/superjojo79/FlowForge/pull/128), TODO §63 clos) | S-9 ; CSP/HSTS/XFO ; remédiation A4-HSTS-001 ; mergé sur `main` 2026-09-20 |
| **B1** | `connecteurs-cible` (Kafka + import batch + ERP) | **Livré** ([PR #129](https://github.com/superjojo79/FlowForge/pull/129), TODO §62 clos) | `batch-import` (SFTP) + `kafka`, 1 seul cycle comme prévu ; « ERP » = cas d'usage de `batch-import`, pas un 3ème connecteur ; mergé sur `main` 2026-09-20 |
| **C** | Adaptateur AV (ClamAV) | **Livré** ([PR #131](https://github.com/superjojo79/FlowForge/pull/131), mergé 2026-09-21) | Cycle `scan-antivirus-clamav` Phases 1-9 et revue adversariale clos. Port `AttachmentScanPort` inchangé, adaptateur `INSTREAM`, profil Compose `clamav`. |
| — | `enrichissement-reporting` | **Livré** ([PR #132](https://github.com/superjojo79/FlowForge/pull/132), mergé 2026-09-22, TODO §65 clos) | Alerting SLA, widgets opérationnels et export BI CSV planifié (pas de JDBC/Power BI). |
| — | `inbox-notifications-persistee` | **Livré** ([PR #135](https://github.com/superjojo79/FlowForge/pull/135), mergé 2026-09-23, TODO §66 clos) | Historique `user_notification`, badge serveur, backfill REST + SSE, purge par rétention. |
| — | `outillage-dmn` | **Livré** ([PR #134](https://github.com/superjojo79/FlowForge/pull/134), TODO §67 clos) | Doc 28. Évaluation, jeux de tests, historique et redéploiement de version. |
| — | `portail-externe` | **Livré** ([PR #138](https://github.com/superjojo79/FlowForge/pull/138), mergé 2026-09-24, TODO §68 clos) | Doc [26-portail-externe.md](analysis%20doc/26-portail-externe.md). Opt-in ; revue adversariale close. |
| — | `signature-electronique` | **Livré** ([PR #149](https://github.com/superjojo79/FlowForge/pull/149), mergé 2026-09-29, TODO §72 clos ; stub, sans provider) | Doc [30-signature-electronique.md](analysis%20doc/30-signature-electronique.md). Recalibré le 2026-09-29 : socle générique + stub, aucun provider désigné ; eIDAS reporté au premier provider réel. |
| — | `palette-metier-bpmn` (LC1) | **Livré** ([PR #151](https://github.com/superjojo79/FlowForge/pull/151), mergé 2026-09-30, TODO §73 clos) | Doc [39-palette-metier-bpmn.md](analysis%20doc/39-palette-metier-bpmn.md). Onze briques, catalogue serveur (API + MCP), attributs littéraux ; revue adversariale close. |

**Interdits :** Kafka ∥ import-batch ∥ ERP ; import batch ∥ A3 ; ClamAV ∥ A1 (Compose) ;
codegen OpenAPI ∥ tout FE ; `signature-electronique` avant `portail-externe`. Hors vague : Redis/SSE multi-pod, Chart.js SLA, Cockpit.
