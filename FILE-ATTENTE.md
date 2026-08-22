# File d’attente produit — FlowForge

> Ordre recommandé des **prochaines features** après les lots lifecycle déjà
> livrés. À relire **avant** de choisir la feature suivante (même critères
> qu’aux analyses 2026-08-17 / 2026-08-18).
>
> - Points ouverts à trancher : [TODO.md](TODO.md)
> - Inventaire follow-ups / lots : [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md)
> - Séquencement « avant prod » : [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md) §9
>
> **Dernière mise à jour :** 2026-08-22 (`deploiement-bpmn` livrée, PR
> #67 mergée sur `main` — TODO §35 clos).

## Comment s’en servir

1. Ne pas relancer une feature marquée **Livré** / **En revue**.
2. Prendre le premier item **Prochain** (ou l’alternative si le prochain est
   bloqué par un point TODO « à décider »).
3. Ne **pas** trancher silencieusement un point ouvert de `TODO.md`.
4. Après clôture d’une feature : déplacer la ligne vers *Récemment livré*,
   recalculer *Prochain*, dater ce fichier.

## Critères de sélection (stables)

Même grille que pour `preview-documents` puis `edition-champs-dossier` :

| Priorité du critère | Règle |
|---|---|
| 1 | Lot **nommé** déjà spécifié (analyse / DEFERRED « lot suivant ») |
| 2 | **Pas** de décision produit encore ouverte (`TODO.md` « À décider ») |
| 3 | Parcours **métier utilisateur** (API + UI) plutôt qu’un contrat transverse |
| 4 | REVUE §9 « avant prod » (rétention §21, purge §22, sensible §23) — seulement si les arbitrages sont tranchés ou acceptés en phase requirements |

## Récemment livré (ne pas replanifier)

| Feature | Réf. | Notes |
|---|---|---|
| `preview-documents` | PR #52, lot D-PREVIEW | Aperçu HTML/PDF admin templates |
| `edition-champs-dossier` | PR #53 | UI `PATCH /api/dossiers/{id}/fields` sur le détail (D-DIFF, D-DEFAULTS) |
| `retention-dossier-type` | PR #55, TODO §21 | Défaut tenant + override type ; job ARCHIVE + `purge_outbox` ; UI admin |
| `purge-stockage` | PR #56, TODO §22 | Job `purge_outbox` store+PG ; `DocumentStoragePort.delete` ; file admin lecture + enqueue |
| `openapi-pagination-admin` | PR #58, TODO §24 | Envelope 6 GET admin ; OpenAPI golden + flag off ; pagers SPA ; écran webhooks |
| `observabilite` | PR #59, TODO §29 | ECS + MDC ; Micrometer port management ; snapshot + SPA admin |
| `durcissement-donnees-sensibles` | PR #62, TODO §23 (reste réduit)/§30 (clos) | Magic-bytes + `AttachmentScanPort` no-op sur upload ; export CSV masqué (`GET /api/dossiers/export.csv`) ; skeleton `HttpRestServiceConnector` non enregistré ; garde-fous anti-fuite secrets ; injection de formule CSV trouvée et corrigée en revue |
| `contrat-api-client` | PR #65, TODO §24 (clos) | 6 listes non-admin migrées vers `PageResponse` (elements, dossiers/workflows, workflow-tasks/mine, connector-definitions, workflow-links history+tasks) ; 3 endpoints `@Hidden` documentés (SSE, multipart, webhook) ; pagination rendue visible en UI pour 5/6 listes (`WorkflowLinksList.vue` nouveau — liste jamais affichée avant) ; codegen client resté hors V1 ; 2 bugs réels trouvés et corrigés en Phase 7 (tri déclaré/réel) ; 2 régressions e2e réelles trouvées et corrigées en Phase 8 ; D-SPA-ADAPT rejouée avec l'utilisateur après un manquement Phase 3 (aucune question matérielle posée) |
| `fiabilisation-tests-plateforme` | PR #66, TODO §26 (clos), §28 (clos), §37 (clos) | Testcontainers ciblé (isolation `search_path`, séquence multi-pods — écrits/compilés, non exécutés dans ce bac à sable sans Docker, CI seulement) ; verrous Flowable + réservation workflow concurrente sous H2 + vrais threads (bug réel trouvé et corrigé : `DataIntegrityViolationException` brute traitée comme retry transitoire au lieu de dead-letter) ; migration Liquibase en échec partiel mono-schéma (portée corrigée — pas de job multi-schéma en réalité) ; règle ArchUnit `usecase.*Impl`→`port.in` ; cascade de résolution de templates (JPA réel) ; seuils Jacoco relevés (LINE 85%, nouvelle règle BRANCH 75%) — écart de couverture réel trouvé (73%) et comblé par 8 classes jamais testées (`StorageConfig`, adaptateurs idempotence webhook/workflow, delegate Flowable, etc.) |
| `deploiement-bpmn` | PR #67, TODO §35 (clos) | Écran d'upload BPMN admin (`/admin/workflow-types`, option (c) — pas de Flowable Modeler/Cockpit) avec prévisualisation obligatoire avant import (`BpmnCanvas.vue` extrait de `BpmnViewer.vue`) ; endpoint `WorkflowBpmnDeploymentController` (déploiement réel `RepositoryService`, clé de processus sans `WorkflowTypeConfig` → rejet strict 404) ; `BpmnParserPort` extrait en Phase 5 après violation ArchUnit détectée (domaine dépendait de `org.flowable..`) ; 2 vrais bugs trouvés et corrigés en Phase 7 (`NullPointerException` potentielle sur `originalFilename` null, regex d'extraction trop strict côté preview) ; `README.md`/`05-workflow-engine.md` corrigés (mentions non livrées retirées) |

Lots lifecycle antérieurs (`workflow-engine` … `frontend`, pagination, relations, PATCH fields API, éléments) : voir [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md) §2.

## File d’attente

Plus de lot produit V1 nommé après `observabilite` : les points TODO hygiène
restants (§23 reste, §24 reste, §25, §26 reste, §28, §30 reste, §35, §37)
ont été regroupés en 5 features candidates (regroupement validé 2026-08-20).
§31 (« découpage en lots ») est clos ci-dessous plutôt que transformé en
feature — le process `dev-lifecycle` + ce fichier + `DEFERRED-FEATURES.md`
le remplit déjà en pratique à chaque lot livré. `durcissement-donnees-sensibles`
(PR #62), `contrat-api-client` (PR #65), `fiabilisation-tests-plateforme`
(PR #66) et `deploiement-bpmn` (PR #67) livrées — file renumérotée.

| Priorité | Feature / lot kebab-case | TODO couverts | Contenu | Pourquoi cet ordre |
|---|---|---|---|---|
| 1 | `modele-physique-consolide` | §25 | ERD/DDL consolidé, répartition schéma tenant / `platform`, description `TenantRegistry` + politique de rafraîchissement du cache | Doc pure (aucune décision ouverte) mais sans parcours métier utilisateur direct (critère 3) — reste en dernier |

Seul `modele-physique-consolide` reste dans la file — c'est donc le
**prochain** item à prendre.

**Hors V1 (reporté, pas dans cette file) :** Rapports / SLA (maquette UI de
référence uniquement) ; connecteur `http-rest` en rôle **SERVICE** lui-même
(V1 = TRIGGER uniquement — le hardening SSRF est pré-positionné depuis
`durcissement-donnees-sensibles`, pas le connecteur).

## Hors file produit (ne pas traiter comme « prochaine feature »)

- Provisioning tenant (schéma PG, instance Boot, bloc proxy, `app.tenant.id`)
- Smoke Keycloak réel (environnement manuel — distinct du smoke PostgreSQL/Testcontainers
  automatisé désormais dans `fiabilisation-tests-plateforme`, §26/§28)
- Follow-ups ops (ex. DEV-PURGE : rétention outbox / TTL idempotency non schedulées ; recovery `PROCESSING` coincé)
- Chore `com.monapp` résiduel dans `analysis doc/`

## Décisions §21 / §22 (rappel)

**§21 — Rétention par `DossierType` — acté 2026-08-19** (voir TODO §21 et
`docs/ai/requirements/2026-08-19-feature-retention-dossier-type.md`).

**§22 — Purge PostgreSQL / object store — livré 2026-08-19** (feature
`purge-stockage`, PR #56) :

- Job `ProcessPurgeOutboxUseCase` consomme `purge_outbox` (Q1=B).
- Port `DocumentStoragePort.delete` S3 + filesystem — jamais MinIO (Q2, DEFERRED §0).
- Ordre store puis PG ; graphe complet ; Flowable best-effort (Q3–Q5).
- UI lecture `/admin/purge-outbox` ; enqueue `POST` `CLOTURE`/`ARCHIVE` (Q6, Q7, Q11).
- Suppression de tenant = runbook ops (Q8, décision n° 13) : pas d’API destroy.

## Historique court des choix « prochaine feature »

| Date | Choix | Motif |
|---|---|---|
| 2026-08-17 | `preview-documents` | Dernier lot nommé D-PREVIEW ; pas de point TODO bloquant |
| 2026-08-18 | `edition-champs-dossier` | Plus de lot nommé ; trou API-sans-UI ; §21 toujours « à décider » |
| 2026-08-19 | file ci-dessus | `edition-champs-dossier` en revue (PR #53) ; prochain = §21 si on tranche, sinon §24 |
| 2026-08-19 | `retention-dossier-type` Phase 9 READY | PR #55 ; prochain = `purge-stockage` §22 |
| 2026-08-19 | `purge-stockage` Phase 9 READY | PR #56 ; prochain = `openapi-pagination-admin` §24 |
| 2026-08-19 | `openapi-pagination-admin` Phase 5 | PR #58 ; §24 partiel clos ; prochain = `observabilite` §29 |
| 2026-08-20 | `observabilite` Phase 1 | Stakeholder : UI admin (D-FE) ; Rapports/SLA hors V1 |
| 2026-08-20 | `observabilite` Phase 2 reviewed | Q1=B ; Q2=A ; Q3=B ; Q4=A ; Q5=A ; Q6=A |
| 2026-08-20 | `observabilite` Phase 3 reviewed | DD-JSON=ecs ; DD-PROM-NAMES ; DD-FLOWABLE-API ; DD-THRESHOLDS |
| 2026-08-20 | `observabilite` Phase 5 done | M1–M5 ; verify 906 + Vitest 135 + Playwright 39 |
| 2026-08-20 | `observabilite` Phase 9 READY | PR #59 ; file V1 sans lot nommé suivant |
| 2026-08-20 | §36 `canEditDossier` clos | PR #60 ; frontend aligné sur `DossierAccessPolicy` via `canEdit` |
| 2026-08-20 | Regroupement TODO restants en 5 features | `durcissement-donnees-sensibles`, `contrat-api-client`, `fiabilisation-tests-plateforme`, `deploiement-bpmn`, `modele-physique-consolide` ; §31 clos (process déjà en pratique) |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 1+2 reviewed | D-AV=B (magic-bytes + port no-op) ; D-CSV-SCOPE=A (export construit) ; D-CSV-ROLE=lecture liste ; D-SERVICE-HTTP=skeleton non enregistré ; D-SECRETS-AUDIT=exhaustif |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 3 reviewed | AttachmentContentSniffer+AttachmentScanPort ; DossierExportPort streamé + ExportDossiersUseCase ; HttpRestServiceConnector non-`ConnectorDefinition` (double garde registry) ; garde-fou secrets = test unitaire ciblé |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 4 reviewed | 5 milestones (M1 upload, M2 export CSV, M3 skeleton connecteur, M4 audit secrets, M5 qualité) ; M1–M4 sans dépendance croisée ; D-CSV-UI=pas d'écran SPA V1 (feature backend seul) |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 5 done | M1–M5 en TDD ; `./mvnw verify` 960 tests (0 échec) + Jacoco OK ; Vitest 136 + Playwright 39 (feature backend-only, régression complète) ; TODO §23 reste réduit, §30 clos |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 7 done | Revue fichier par fichier ; injection de formule CSV trouvée sur `CsvWriter` (labels/champs dynamiques exportés non protégés) et corrigée en TDD ; `./mvnw verify` 964 tests (0 échec) |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 9 READY | Revue finale : traçage appelants, cohérence modules similaires, pas de duplication, pas de migration à risque ; aucun blocage ; PR #62 (commit `284345c`) prête pour revue humaine |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 8 rejouée | Relecture Jacoco classe par classe (pas juste le total bundle) ; 2 lacunes réelles comblées (`HttpRestServiceConnector` chemin d'erreur, `AttachmentContentSniffer` texte vide) ; `./mvnw verify` 969 tests (0 échec) |
| 2026-08-21 | `durcissement-donnees-sensibles` Phase 9 re-vérifiée | Utilisateur : « la phase 9 a-t-elle été correctement exécutée ? » → non totalement ; balayage Jacoco corrigé (motif CSS `pc bpc` raté au 1er passage) sur les 15 fichiers modifiés, 4 lacunes réelles trouvées et comblées (`DossierExportController` closedAt, `AttachmentContentSniffer` null, `parseStatus` blanc, `CsvWriter` \r isolé) ; `./mvnw verify` 973 tests (0 échec) |
| 2026-08-21 | `durcissement-donnees-sensibles` livrée | PR #62 mergée sur `main` (commit `a4443e5`) ; TODO §23 reste réduit, §30 clos ; prochain = `contrat-api-client` §24 (reste) |
| 2026-08-21 | `contrat-api-client` Phase 1+2 reviewed | D-CODEGEN=exclu (contradiction FILE-ATTENTE/TODO résolue en faveur de TODO) ; D-RELATIONS=pas de pagination (décision existante respectée) ; D-PAGINATION-SCOPE=6 listes (elements, workflow-tasks/mine, connector-definitions, dossiers/{id}/workflows, workflow-links history+tasks) ; D-HIDDEN-SPEC=documentation complète SSE/multipart/webhook |
| 2026-08-21 | `contrat-api-client` Phase 3 reviewed | Mécanisme par source : JPA `Pageable` (elements, dossiers/{id}/workflows), Flowable `listPage`/`count` natif (workflow-tasks/mine, workflow-links history+tasks — remplace `DEFAULT_LIMIT=100`), slice mémoire `PageResult.slice` (connector-definitions) ; `AdminListSorts` renommé `ListSorts` (générique) ; SPA adapté via `listAllPages` existant, zéro changement de composant Vue ; 3 endpoints `@Hidden` documentés sans changement de comportement |
| 2026-08-21 | `contrat-api-client` Phase 4 reviewed | 7 milestones (M1 `ListSorts`, M2 pagination JPA, M3 pagination Flowable native, M4 pagination mémoire, M5 doc OpenAPI `@Hidden`, M6 adaptation SPA, M7 qualité), 9 tâches, testing doc scénarisé (aucun scénario orphelin) ; toutes les tâches `not started` — Phase 5 pas encore démarrée |
| 2026-08-21 | `contrat-api-client` Phase 5 exécutée (TDD) | 9 tâches (T1.1–T7.2) implémentées, rouge confirmé avant chaque nouvelle méthode de port/adaptateur ; `./mvnw verify` 986 tests (0 échec), Jacoco met ; golden OpenAPI régénéré (6 endpoints paginés + 3 `@Hidden` retirés) |
| 2026-08-21 | `contrat-api-client` Phase 7 (check implementation) | Relecture fichier par fichier : 2 bugs réels trouvés et corrigés — `ListSorts.MY_WORKFLOW_TASKS` déclarait `createdAt,asc` alors que l'adaptateur trie `.desc()` (constante corrigée, comportement préservé) ; `listActiveTasks` n'appliquait aucun `ORDER BY` avant `.listPage` (pagination non déterministe, vrai bug — `.orderByTaskCreateTime().asc()` ajouté) |
| 2026-08-21 | `contrat-api-client` Phase 8 (régression complète) | `npm run test` 136/136, `typecheck`/`lint` propres, `npm run test:e2e` 39/39 après correction de 2 régressions SPA réelles dans `frontend/e2e/pilotage.spec.ts` (mocks non enveloppés `PageResponse` + pattern glob Playwright ne matchant plus l'URL avec `?page=&size=`) ; non-déterminisme du golden OpenAPI trouvé et corrigé (`HttpHeaders` exposé par le webhook, `@Parameter(hidden = true)` ajouté) |
| 2026-08-21 | `contrat-api-client` Phase 9 revue | Revue holistique fichier par fichier : alignement design confirmé, breaking changes de ports tous traités atomiquement (appelants en repo mis à jour), aucun changement de sécurité/autorisation, rollback sûr (aucune migration DB). Aucun problème bloquant nouveau (les 2 trouvés en Phase 7 déjà corrigés). Implémentation complète, TODO §24 clos, en attente de PR |
| 2026-08-21 | `contrat-api-client` — D-SPA-ADAPT rejouée | Utilisateur : Phase 3 n'avait posé aucune question matérielle (violation du contrat `dev-design`). 3 décisions rejouées via `AskUserQuestion` : D-TASK-LIMIT et D-SORT-CONST confirmées, **D-SPA-ADAPT inversée** — pagination visible ajoutée pour 5/6 listes (`connector-definitions` reste `listAllPages`, menu déroulant pas une liste parcourue) ; `dossiers/{id}/workflows` gagne un affichage qui n'existait pas avant (constat fait en creusant, ajout validé explicitement). 9 tâches TDD supplémentaires, `npm run test` 153/153, `test:e2e` 40/40, aucun fichier backend touché |
| 2026-08-22 | `fiabilisation-tests-plateforme` Phase 1+2 reviewed | 6 décisions actées via `AskUserQuestion` (une à la fois, conformément au contrat `dev-requirements`) : D-SCOPE=une feature, D-TESTCONTAINERS=adoption ciblée (isolation `search_path` + séquence multi-pods uniquement), D-TESTCONTAINERS-EXEC=tests écrits mais étiquetés « non exécutés ici » (Docker inaccessible dans ce bac à sable, vérifié par `docker info`), D-COVERAGE=LINE 85%/BRANCH 75% BUNDLE, D-TENANT-ID-37=alignement `tenantId` explicite, D-ARCHUNIT-RULE=`usecase.*Impl`→`port.in`. 3 corrections factuelles trouvées avant de demander : `@Version` déjà en place sur `Dossier`, bug `Instant`/`%tY` cité au TODO §26 inexistant dans le code actuel, couverture outbox déjà substantielle — évite de refaire un travail déjà fait |
| 2026-08-22 | `fiabilisation-tests-plateforme` Phase 3 reviewed | 4 décisions techniques actées via `AskUserQuestion` (mécanisme, périmètre déjà figé en Phase 2) : D-FLOWABLE-LOCK-TEST=H2+threads réels, D-PARALLELISM-TEST=H2+threads réels, D-LIQUIBASE-TEST=échec partiel mono-schéma (la prémisse multi-schéma du TODO §28 ne correspond pas à l'architecture réelle), D-CONTAINER-REUSE=conteneur Testcontainers singleton statique |
| 2026-08-22 | `fiabilisation-tests-plateforme` Phase 4 (planning) | 15 tâches sur 5 milestones ; testing doc scénarisé (traçabilité requirements→tests complète) |
| 2026-08-22 | `fiabilisation-tests-plateforme` Phase 5 (implémentation) | 15 tâches en TDD (H2 exécuté et vert ; Testcontainers écrit/compilé, SKIPPED proprement ici via `Assumptions.assumeTrue`/`isDockerAvailable()`, CI seulement) ; 1 bug réel trouvé et corrigé (`DataIntegrityViolationException` brute au lieu de `WorkflowAlreadyRunningException`, traitée à tort comme retry transitoire par `OutboxRelayScheduler`) ; JDK 25 installé dans le bac à sable (requis par `pom.xml`, absent au démarrage du container) ; couverture BRANCH réelle 73% trouvée sous le seuil D-COVERAGE 75% décidé en Phase 1 — question posée à l'utilisateur (`AskUserQuestion`), réponse = combler par des tests ciblés ; 8 classes jamais testées comblées en 3 vagues (73%→74%→75.03%) ; `./mvnw verify` final : 1050 tests (0 échec), LINE 93.7%, BRANCH 75.0% ; TODO §26/§28/§37 clos |
| 2026-08-22 | `fiabilisation-tests-plateforme` livrée | PR #66 mergée sur `main` ; TODO §26/§28/§37 clos ; prochain = `deploiement-bpmn` §35 |
| 2026-08-22 | `deploiement-bpmn` Phase 1+2 reviewed | Décision produit débloquée via `AskUserQuestion` : D-BPMN-TARGET=option (c) écran d'upload minimal (pas Modeler ni Cockpit) ; précision demandée explicitement par l'utilisateur → D-BPMN-PREVIEW=prévisualisation obligatoire du diagramme avant import, réutilisant le rendu `bpmn-js` de `BpmnViewer.vue` ; D-BPMN-PLACEMENT=nouvelle section dans `/admin/workflow-types` existant (rôles `TENANT_ADMIN`/`WORKFLOW_ADMIN` déjà en place), pas de nouvel écran. Précédents de code identifiés avant de trancher (`DossierElementUploadController` comme patron d'upload multipart) |
| 2026-08-22 | `deploiement-bpmn` Phase 3 reviewed | 5 décisions design actées via `AskUserQuestion` : D-BPMN-PREVIEW-COMPONENT=extraction `BpmnCanvas.vue` partagé (utilisateur n'a **pas** suivi la recommandation, a choisi l'extraction plutôt que la réutilisation directe de `BpmnViewer.vue`) ; D-BPMN-KEY-VALIDATION=rejet strict clé inconnue ; D-BPMN-REDEPLOY-INFO=afficher version+instances actives avant confirmation ; D-BPMN-UPLOAD-SIZE=réutilise le seuil global (revirement après qu'une contradiction avec la convention « source de vérité unique » déjà établie pour les pièces jointes ait été trouvée et signalée avant de trancher) ; D-BPMN-UPLOAD-CONTENT-TYPE=nouvelle classe dédiée sans champ de taille |
| 2026-08-22 | `deploiement-bpmn` Phase 4 (planning) | 6 milestones, 20 tâches ; testing doc scénarisé (traçabilité requirements→tests) |
| 2026-08-22 | `deploiement-bpmn` Phase 5 (implémentation) | 20 tâches en TDD, M1→M5 (backend domaine/Flowable/endpoint, frontend `BpmnCanvas.vue`+`BpmnUploadPreview.vue`+intégration admin, doc). Écart réel trouvé en implémentation (pas en revue) : `BpmnXMLConverter` appelé directement dans le use case domaine violait `ArchitectureRulesTest.domain_has_no_framework_dependency` — extrait en `BpmnParserPort`/`FlowableBpmnParserAdapter`, pas prévu au design initial. Backend : `./mvnw verify` ciblé vert (ArchUnit, OpenAPI golden régénéré, tous les nouveaux tests) ; frontend : 162 tests Vitest (0 échec), typecheck/lint propres, coverage au-dessus des seuils. TODO §35 clos |
| 2026-08-22 | `deploiement-bpmn` M6 (vérification globale) | `./mvnw verify` 1075 tests (0 échec), seuils Jacoco tenus (écart BRANCH réel trouvé à 74.92% au 1er passage, comblé par 1 test ciblé) ; 163 Vitest, typecheck/lint propres ; 43/43 Playwright (3 nouveaux `E2E-BPMN-*`) |
| 2026-08-22 | `deploiement-bpmn` Phase 7 rejouée | Utilisateur : « toutes les phases ont-elles été exécutées ? pas d'autres questions en Phase 3 ? » → vérifié dans l'historique brut de session (pas la mémoire résumée) : Phase 3 complète (5 `AskUserQuestion` réels, tous documentés — le doc affirmait à tort « 7 », corrigé) ; en revanche Phase 7 (check implementation) n'avait pas été faite sérieusement — rejouée fichier par fichier, 2 vrais bugs trouvés et corrigés (`NullPointerException` potentielle sur `originalFilename` null dans `FlowableWorkflowDeploymentAdapter`, regex d'extraction trop strict côté preview) ; doc d'implémentation resté le squelette vide, rédigé ; plusieurs cases testing doc jamais cochées, corrigées |
| 2026-08-22 | `deploiement-bpmn` livrée | PR #67 mergée sur `main` (CI hébergé rouge depuis plusieurs jours pour une raison d'infrastructure sans rapport avec le diff — confirmé rouge aussi sur les merges précédents dont #66 — mergé sur la base des vérifications locales complètes) ; TODO §35 clos ; prochain = `modele-physique-consolide` §25 |
