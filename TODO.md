# Points à traiter — FlowForge

> Registre de suivi du projet. Il comporte deux parties :
>
> - **[Points ouverts](#points-ouverts)** — ils ne sont **pas** tranchés. Le contexte détaillé
>   (constat, extraits concernés, options envisageables, documents impactés) est dans :
>   - [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md) pour les points **1–39**
>     (revue de la documentation d'architecture avant le premier code) ;
>   - [REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md) pour les points **40–46**
>     (revue post-implémentation 2026-08-25 : alignement spec → AI-devkit → code, et sécurité).
> - **[Décisions actées](#décisions-actées)** — décisions prises lors de la revue de conception
>   initiale, déjà répercutées dans `analysis doc/`. Elles font office de journal ; ne les rouvrir
>   qu'avec une raison explicite.
>
> **Reste à implémenter (features / lots différés) :** synthèse consolidée dans
> [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md) — follow-ups des tranches livrées, features
> lifecycle non démarrées, et mapping vers les points ouverts ci-dessous.
>
> **Prochaine feature (file d’attente) :** [FILE-ATTENTE.md](FILE-ATTENTE.md) — ordre
> recommandé et critères de sélection.

---

# Points ouverts

> Pour le backlog d’implémentation reporté (hors décisions à trancher), voir
> [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md).

## Bloquants — à trancher avant le premier commit applicatif

> Les trois points initialement classés bloquants sont désormais tranchés — propagation du tenant
> (décision n° 13), résolution du tenant pour les appels non authentifiés (décision n° 14), et
> sécurité de la session BFF (décision n° 15). Voir la section *Décisions actées* ci-dessous. Cette
> section n'a plus d'élément à trancher à ce niveau de priorité. Les points 17 (entités JPA) et 27
> (versions, build, packaging frontend) ont également été tranchés — voir *Décisions actées*.

## Contradictions internes à lever

> Les points 16 et 18 ont été tranchés — voir *Décisions actées*. Cette section n'a plus d'élément
> ouvert.

---

## Autorisation et conformité

Points identifiés par [12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md),
qui prévoyait leur fusion dans ce registre.

### [x] 19. Granularité des rôles RBAC

**Clos V1 (2026-08-03 — feature `autorisation-conformite`) :** les cinq rôles
`TENANT_ADMIN`, `WORKFLOW_ADMIN`, `DOSSIER_MANAGER`, `DOSSIER_VIEWER`,
`API_SERVICE` sont la liste définitive V1. Mapping `realm_access.roles` →
`ROLE_*` déjà livré (`authentification`). Restriction par type = ACL
(décision n° 20). Peut être rouvert si les premiers clients réels exigent
d’autres rôles.

**Impacte :** [12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

### [x] 21. Politique de rétention par `DossierType`

**Implémenté (2026-08-19 — feature `retention-dossier-type`, PR #55) :**

| Sujet | Choix livré |
|---|---|
| Durée | Défaut **tenant obligatoire** (> 0 jours) + override **par type** (jours \| `NEVER` \| héritage) |
| À expiration | `ARCHIVE` + `purgeEligibleAt` + event **`purge_outbox`** (`DOSSIER_PURGE_REQUESTED`) ; **pas** de purge physique |
| Horloge | Depuis **`closedAt`** à l’entrée en **`CLOTURE`** ; non clos = pas d’expiration |
| UI | Config défaut tenant + champs sur admin `DossierType` |
| Bootstrap | Pas de seed ; GET 404 ; job no-op jusqu’au PUT |

**Suite livrée :** purge physique PG + object store → point **22** /
`purge-stockage` (PR #56).

**Impacte :** [04-dossiers.md](analysis%20doc/04-dossiers.md),
[12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

### [x] 22. Purge cohérente PostgreSQL / object store

**Implémenté (2026-08-19 — feature `purge-stockage`, PR #56) :**

| Sujet | Choix livré |
|---|---|
| Mécanisme (Q1) | Job consomme `purge_outbox` (`ProcessPurgeOutboxUseCase`) — pas XA, pas soft-delete-only |
| Port (Q2) | `DocumentStoragePort.delete` (S3 + filesystem) ; **jamais MinIO** en dur |
| Ordre (Q3) | Store puis PostgreSQL |
| Graphe (Q4) | Éléments, relations src\|tgt, workflow links, puis dossier |
| Flowable (Q5) | `WorkflowEnginePort.deleteProcessInstanceBestEffort` ; ACT_HI résiduel OK |
| UI (Q6) | Admin **lecture** `/admin/purge-outbox` ; enqueue sur détail dossier |
| HTTP (Q7) | `POST /api/dossiers/{id}/purge-requests` async ; pas de purge dans le thread HTTP |
| Tenant (Q8) | Destroy tenant = **runbook ops** (`docs/ai/deployment/2026-08-19-feature-purge-stockage.md`) |
| Orphelins (Q9) | Pas de GC store hors outbox |
| E2E (Q10) | Playwright E2E-PURGE-1..5 + suite existante |
| Enqueue (Q11) | Seulement `CLOTURE`/`ARCHIVE` (ouvert → 409) ; job ignore l’horloge si `origin=ADMIN` |

**Reste hors V1 :** recovery d’une ligne `PROCESSING` coincée après crash JVM ;
backoff `nextAttemptAt` ; GC orphelins store.

**Impacte :** [07-generation-documents.md](analysis%20doc/07-generation-documents.md),
[12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

### [x] 23. Classification des champs dynamiques sensibles

**Partiel (2026-08-03 — feature `autorisation-conformite`) :** flag
`FieldDefinition.sensitive` livré (métadonnée JSON, défaut false, API types).
**Partiel (2026-08-21 — feature `durcissement-donnees-sensibles`) :**
masquage à l'affichage déjà en place (`DossierFieldRedactor`) désormais
également appliqué à l'export CSV (`GET /api/dossiers/export.csv`, même
règle : masqué pour tout acteur non `TENANT_ADMIN`).

**Résolu (2026-08-24, feature `masquage-donnees-sensibles-logs-webhooks`) :**
audit avant implémentation — **aucune fuite active trouvée**, ni dans les
logs (aucun code de production ne loggue un `Dossier`/sa map `fields`
entière) ni dans les webhooks (aucun producteur Java ni process BPMN ne
peuple aujourd'hui le payload de notification avec des champs de dossier).
Le point restait un risque latent sans garde-fou, pas un incident à
corriger — traité comme protection préventive en défense en profondeur :
`Dossier`/`FieldDefinition` ont désormais un `toString()` sûr
(n'imprime plus jamais `fields`/`defaultValue`) ; une règle ArchUnit
restreint l'appel à `Dossier.fields()`/`FieldDefinition.defaultValue()` à
une liste blanche de classes (protection plus large qu'une détection de
logging, qui s'est avérée irréalisable avec ArchUnit — pas d'analyse de
flux de valeurs) ; `SendNotificationUseCaseImpl` masque automatiquement
les valeurs sensibles d'un payload de notification dès lors qu'un
producteur lui fournit le contexte dossier (`DossierFieldsSnapshot`),
avant toute écriture outbox — protège les canaux webhook et browser d'un
seul point de contrôle, sans dépendre de la discipline d'un futur
producteur. Prouvé bout en bout (masquage → sérialisation outbox →
désérialisation → corps HTTP réel) et par contrôle mutant manuel (le
masquage désactivé fait échouer les tests). `./mvnw verify` : 1088 tests,
0 échec.

**Impacte :** [04-dossiers.md](analysis%20doc/04-dossiers.md),
[12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

## Compléments de conception

Ces points ne bloquent pas le démarrage, mais ils doivent être planifiés explicitement : aucun n'est
couvert aujourd'hui, même partiellement.

### [x] 24. Contrat d'API — surface incomplète et format d'erreur non normalisé

**Partiel (2026-08-13 — feature `pagination-dossiers`) :** liste/recherche/pagination
dossiers + envelope page + Supervision FE livrés. **Partiel (2026-08-15 — feature
`patch-dossier-fields`) :** `PATCH /api/dossiers/{id}/fields` (fusion, verrou optimiste,
gate statut, audit `sensitive`) livré. **Partiel (2026-08-16 — feature `elements-dossier`) :**
`POST /api/dossiers/{id}/elements` (JSON, `INFORMATION`/`NOTE`/`LINK`) et
`POST /api/dossiers/{id}/elements/attachment` (multipart, `ATTACHMENT`) livrés, avec la
même gate de statut `CLOTURE`/`ARCHIVE` que le point précédent (scopée au chemin humain).
**Partiel clos (2026-08-19 — feature `openapi-pagination-admin`) :** OpenAPI formel
(springdoc 3.1.0, golden `openapi/openapi.yaml`, fail-on-diff CI) ; pagination envelope
des 6 GET admin (types, templates, connectors, workflow-type-configs, webhooks, purge) ;
pagers SPA + écran `/admin/notification-webhooks` ; catalogues via `listAllPages`.
`ProblemDetail` déjà livré (tranches dossiers).

**Clos (2026-08-21 — feature `contrat-api-client`) :** les 6 listes non-admin restantes
migrées vers `PageResponse<T>` (`dossiers/{id}/elements`, `dossiers/{id}/workflows`,
`workflow-tasks/mine`, `connector-definitions`, `workflow-links/{linkId}/history`,
`workflow-links/{linkId}/tasks`) — mécanisme JPA `Pageable`, Flowable `listPage`/`count`
natif ou slice mémoire selon la source ; `DEFAULT_LIMIT=100` de `ListMyTasksUseCaseImpl`
supprimé au profit du contrat `page`/`size`. Les 3 endpoints `@Hidden` (SSE, multipart
attachment, webhook ingress) documentés dans le golden sans changement de comportement.
`dossiers/{id}/relations` volontairement non touché (décision `relations-dossiers`
respectée). SPA adapté via `listAllPages` existant, zéro composant Vue modifié. Codegen
client reste explicitement hors V1 (décision assumée, pas un gap).

**Énoncé :** aucun endpoint de création, recherche, liste ou mise à jour de dossier n'est documenté,
pas plus que l'administration des `DossierType`, `ConnectorInstance` et templates. Les listes
impliquent pagination, tri et filtres, non abordés. Les exceptions métier existent déjà avec des
sémantiques HTTP précises, mais sans format pivot.

**À produire :** la surface d'API complète avec schémas de requête et de réponse ; un format d'erreur
normalisé (`ProblemDetail` / RFC 7807 est le candidat naturel) et le `@RestControllerAdvice` associé ;
une décision sur la génération OpenAPI et son statut de contrat vis-à-vis du frontend.

**Énoncé :** aucun endpoint de création, recherche, liste ou mise à jour de dossier n'est documenté,
pas plus que l'administration des `DossierType`, `ConnectorInstance` et templates. Les listes
impliquent pagination, tri et filtres, non abordés. Les exceptions métier existent déjà avec des
sémantiques HTTP précises, mais sans format pivot.

**À produire :** la surface d'API complète avec schémas de requête et de réponse ; un format d'erreur
normalisé (`ProblemDetail` / RFC 7807 est le candidat naturel) et le `@RestControllerAdvice` associé ;
une décision sur la génération OpenAPI et son statut de contrat vis-à-vis du frontend.

---

### [x] 25. Modèle physique de données consolidé

**Énoncé :** une dizaine de tables métier, deux index uniques partiels critiques, la table
`outbox_event`, `dossier_type_acl` (décision n° 20), le stockage des DEK chiffrées, `platform.tenant_migration_status` et les tables
`ACT_*` de Flowable coexistent sans vue consolidée ni règle de répartition entre le schéma du tenant
et le schéma `platform`. En particulier, la table des tenants reflétée par le `TenantRegistry` —
deuxième barrière de sécurité de la décision n° 1 — n'est décrite nulle part, non plus que la
politique de rafraîchissement de son cache.

**À produire :** schéma consolidé (ERD ou DDL), répartition tenant / `platform`, et changelogs
Liquibase correspondants avec leurs contextes `tenant-init` / `tenant-migration`.

**Résolu (2026-08-22, feature `modele-physique-consolide`) :** écart trouvé avant de commencer —
l'énoncé lui-même était en partie obsolète. Ni le `TenantRegistry` ni les contextes Liquibase
`tenant-init`/`tenant-migration` séparés n'existent dans le code : la décision n° 1 porte une note
de mise à jour explicite disant que les deux mécanismes « n'ont plus lieu d'être » depuis la
décision n° 13 (une instance dédiée par tenant, schéma fixé au démarrage, changelog unique
appliqué à chaque démarrage). Nouveau document
[13-modele-physique.md](analysis%20doc/13-modele-physique.md) : ERD mermaid des **17** tables
métier réelles (pas « une dizaine »), **4** index uniques partiels critiques avec leur invariant
métier (pas « deux » — `uq_dossier_workflow_link_active`, `uq_template_active`,
`uq_connector_instance_tenant_name_active`, `uq_notification_webhook_tenant_name_active`),
`dossier_type_acl`/`tenant_connector_dek`/`outbox_event` documentées, répartition tenant/`platform`
clarifiée (un seul schéma par tenant aujourd'hui, `tenant_migration_status` documentée comme option
non retenue plutôt qu'implémentée). Feature strictement documentaire — aucun changelog, aucune
classe Java modifiée (confirmé par `git diff --stat`).

---

### [x] 26. Concurrence sur le dossier et unicité de la référence

**Constat révisé (feature `fiabilisation-tests-plateforme`, 2026-08-22) :** relecture du code avant
implémentation a montré que 2 des 3 points listés ici étaient déjà résolus et jamais mis à jour :
verrouillage optimiste `@Version` déjà présent sur `Dossier` (`DossierJpaEntity.java`) ; le bug
allégué (`Instant` ne supporte pas `%tY`) n'existe pas dans `SequentialReferenceGenerator.java`
(utilise `clock.instant().atZone(ZoneOffset.UTC).getYear()`, un `int`). Seul le 3ᵉ point —
`Dossier.reference` sous accès concurrent multi-pods réel — restait ouvert.

**Livré (2026-08-22 — feature `fiabilisation-tests-plateforme`) :** `SequentialReferenceMultiPodIT`
(Testcontainers PostgreSQL, 8 pods simulés par 8 `DataSource`/`TransactionTemplate` indépendants,
80 générations concurrentes) prouve l'absence de doublon et de trou dans la séquence sous charge
réelle. Écrit et compilé ; non exécuté dans le bac à sable de développement (Docker absent) —
exécution/vérification en CI (`ubuntu-latest`, Docker natif). Voir
`docs/ai/testing/2026-08-22-feature-fiabilisation-tests-plateforme.md`.

**Impacte :** [04-dossiers.md](analysis%20doc/04-dossiers.md).

---

### [x] 28. Stratégie de test

**Livré (2026-08-22 — feature `fiabilisation-tests-plateforme`) :**

- Étanchéité `search_path` par tenant : `TenantSchemaIsolationIT` (Testcontainers PostgreSQL réel,
  2 schémas). Écrit/compilé, non exécuté ici (Docker absent) — CI seulement.
- Dimensionnement des verrous Flowable sous charge : `FlowableLockDimensioningTest` — H2 + threads
  réels (le verrouillage Flowable est un mécanisme SQL portable, pas Postgres-spécifique), exercice
  direct de `AcquireJobsCmd` et `ResetExpiredJobsCmd`. Exécuté et vert.
- Migration Liquibase en échec partiel : `LiquibasePartialFailureIT` — portée mono-schéma (pas
  multi-schéma : l'architecture réelle est « un schéma par instance », voir
  `02-multi-tenant.md`), H2, reprise idempotente via `DATABASECHANGELOG`. Exécuté et vert.
- ArchUnit : règle `..usecase..*Impl` → doit implémenter une interface de `..port.in..` ajoutée à
  `ArchitectureRulesTest`. Exécutée et verte.
- Relais outbox (rejeu, idempotence, crash) : couverture déjà substantielle avant cette feature
  (`OutboxRelaySchedulerTest`, `OutboxRelayIT`) — pas un trou réel, pas de nouveau test nécessaire.
- Résolution en cascade des templates : `ResolveDocumentTemplateUseCaseImplTest` (JPA réel, pas de
  mock — la cascade est implémentée dans la requête native `findBestActive`). Exécuté et vert.
- « Un seul workflow actif » sous parallélisme réel : `ConcurrentWorkflowReservationTest` — H2 +
  threads réels sur `AttachWorkflowUseCase.reserve`. A révélé et corrigé un bug réel : le perdant
  de la course recevait une `DataIntegrityViolationException` brute au lieu de
  `WorkflowAlreadyRunningException`, traitée par erreur comme un échec transitoire par
  `OutboxRelayScheduler` (retry) au lieu d'un dead-letter. Exécuté et vert.
- Seuils de couverture : Jacoco `BUNDLE` LINE 85% (était 80%), nouvelle règle BRANCH 75% (n'existait
  pas). `./mvnw verify` vert avec 1050 tests (0 échec), LINE 93.7%, BRANCH 75.0%.

Détails complets (décisions techniques, requirements coverage) :
`docs/ai/design/2026-08-22-feature-fiabilisation-tests-plateforme.md`.

---

### [x] 29. Observabilité

**Livré (2026-08-20 — feature `observabilite`, PR #59, Phase 9 READY) :**
logs ECS + MDC (`tenantId` / `dossierId` / `processInstanceId`) ;
Micrometer `flowforge.*` (3 âges outbox, Flowable DLQ+exception, timer
PDF, webhooks DEAD, SSE) sur le **port management** ; audit lecture
`SECURITY_AUDIT` Q1=B ; `GET /api/observability` + SPA
`/admin/observabilite` (`TENANT_ADMIN`). Voir
`docs/ai/implementation/2026-08-20-feature-observabilite.md`.

Follow-ups non bloquants : wrapper MDC async Flowable Community 8 ;
`dossierId` MDC par item de purge.

Périmètre figé : logs ECS + MDC ; Micrometer (`flowforge.*`) ; écran
admin `/admin/observabilite`. Détail : requirements + design.

---

### [x] 30. Durcissement des points d'entrée exposés

**Livré (audit sécurité, session 2026-08-20) :** rate limiting + plafond de taille en mémoire sur
`/api/webhooks/**` (`WebhookRequestRateLimiter`, `WebhookBodySizeLimitFilter`) et sur
`POST /api/documents/generate` (`DocumentGenerationRateLimiter`,
`DocumentGenerationBodySizeLimitFilter`) ; protection SSRF sur le rendu PDF
(`RestrictedResourceUserAgent`) et les webhooks sortants (`OutboundUrlGuard`, résolution DNS
incluse) ; journal d'audit sécurité structuré sur les 403 (`SecurityAuditLog`).

**Livré (feature `durcissement-donnees-sensibles`, 2026-08-21) :**
- Pièces jointes : validation du contenu réel (magic-bytes,
  `AttachmentContentSniffer`) en complément de l'allowlist MIME déclarative
  existante ; port `AttachmentScanPort` pluggable avec adaptateur no-op
  (aucun moteur antivirus dans la stack figée — le contrat est prêt pour un
  futur adaptateur, ex. ClamAV, décision explicite de ne pas l'intégrer
  maintenant, cf. `durcissement-donnees-sensibles` design D-AV).
- SSRF sur `http-rest` en rôle SERVICE : skeleton `HttpRestServiceConnector`
  réutilisant `OutboundUrlGuard`, non enregistré dans le `ConnectorRegistry`
  (connecteur SERVICE lui-même hors V1, cf. FILE-ATTENTE) — prouve la
  réutilisabilité du guard, prêt à activer sans redévelopper.
- Fuites de secrets déchiffrés : audit des endpoints admin connecteurs et
  webhooks de notification (déjà masqués, confirmé sain) + garde-fou
  anti-régression (`ConnectorResponseSecretMaskingTest`,
  `NotificationWebhookResponseSecretMaskingTest`).

**Décision : rate limiter distribué (Redis ou équivalent) non ajouté.** Les
deux limiteurs ci-dessus sont en mémoire locale (`ConcurrentHashMap` par
JVM) — corrects pour le modèle retenu (décision n° 13, une instance par
tenant), mais chaque réplique aurait son propre compteur si un tenant était
un jour scalé horizontalement. Aucun déploiement multi-réplique n'est prévu
à court terme, et Redis introduirait une dépendance d'infrastructure hors
stack figée sans besoin actuel. À réactiver si/quand un tenant est
effectivement scalé horizontalement.

**Impacte :** [06-connecteurs.md](analysis%20doc/06-connecteurs.md),
[09-notifications.md](analysis%20doc/09-notifications.md),
[12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

### [x] 35. Modélisation et déploiement des diagrammes BPMN — écart vision / implémentation

**Constat :** [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md) retient « Designer visuel :
Flowable UI (web, embarqué) » et « Monitoring instances : Flowable UI Cockpit » ; le `README.md`
reprend cette vision (« designer visuel intégré », « déployés sans redémarrage », « supervisés via le
cockpit Flowable »). En pratique, `pom.xml` ne dépend que de `flowable-spring-boot-starter` (le moteur
d'exécution) — aucune des applications web Flowable UI (Modeler, Cockpit, Task, IDM) n'est intégrée, et
`application.yml` exclut explicitement leurs autoconfigurations (REST API, sécurité, app engine).

Le volet **Cockpit** a déjà une décision tracée : `DEFERRED-FEATURES.md` le classe « Hors scope
produit », remplacé par le viewer lecture seule Vue/`bpmn-js` (`BpmnViewer.vue`, déjà livré). Le volet
**Modeler / déploiement** n'a en revanche **aucune décision équivalente** — c'est un écart de fait, pas
un choix assumé. Le déploiement réel des diagrammes se fait par scan classpath au démarrage
(`TenantProcessDeployment`, `classpath*:/processes/**/*.bpmn20.xml`) : pas de hot-deploy, pas d'écran
d'upload ou de design dans l'UI FlowForge — le `.bpmn20.xml` est modélisé en externe puis déposé dans le
dépôt backend par l'équipe technique (documenté comme limite actuelle dans
[GUIDE-UTILISATEUR.md](GUIDE-UTILISATEUR.md)).

**À décider :** trois options, non exclusives dans le temps (V1 minimaliste puis cible plus tard) :

- **(a) Acter l'état actuel comme cible V1.** Modélisation externe (Flowable Modeler autonome ou tout
  éditeur BPMN 2.0) + dépôt du fichier dans le repo backend + déploiement au build/redémarrage reste le
  mécanisme définitif. Impose de corriger `README.md` / `05-workflow-engine.md` (retirer « designer
  visuel intégré » et « sans redémarrage », qui décrivent une fonctionnalité non livrée).
- **(b) Intégrer Flowable Modeler** (webapp séparée) pour permettre le design et le déploiement en base
  depuis un navigateur, avec rechargement à chaud des déploiements Flowable
  (`RepositoryService.createDeployment()`) — récupère la vision initiale, mais ajoute une app tierce à
  opérer/sécuriser par tenant (probablement hors scope au vu de la décision Cockpit ci-dessus).
- **(c) Écran d'upload minimal dans FlowForge.** Upload d'un `.bpmn20.xml` validé (schéma BPMN 2.0,
  `processDefinitionKey` cohérent avec la config workflow) via l'API FlowForge existante, déploiement
  via `RepositoryService` — sans réintroduire toute la stack Flowable UI ni son coût opérationnel.

**Impacte :** [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md), `README.md` (sections
Proposition de valeur et Fonctionnalités principales), [GUIDE-UTILISATEUR.md](GUIDE-UTILISATEUR.md),
`TenantProcessDeployment.java`.

**Résolu (2026-08-22, feature `deploiement-bpmn`) :** option **(c)** retenue — écran d'upload
minimal dans FlowForge, sans Flowable Modeler ni Cockpit (décision produit tranchée directement,
cette session) — plus prévisualisation obligatoire du diagramme avant tout import (réutilisation du
rendu `bpmn-js`, décision complémentaire non envisagée par les 3 options initiales).

- Backend : `DeployBpmnProcessUseCase`/`GetProcessDeploymentInfoUseCase` (domaine),
  `BpmnParserPort`/`FlowableBpmnParserAdapter` (parsing/validation BPMN 2.0, extraits en port pour
  respecter la règle ArchUnit domaine-sans-framework), `WorkflowDeploymentPort`/
  `FlowableWorkflowDeploymentAdapter` (`RepositoryService`/`RuntimeService`),
  `WorkflowBpmnDeploymentController` (`GET .../info`, `POST` multipart,
  `@PreAuthorize` `TENANT_ADMIN`/`WORKFLOW_ADMIN`). Clé de processus sans `WorkflowTypeConfig`
  correspondante → rejet strict (`UnknownWorkflowKeyException` → 404, symétrique
  `WorkflowTypeConfigNotFoundException`).
- Frontend : `BpmnCanvas.vue` (rendu bpmn-js bas niveau, extrait de `BpmnViewer.vue` qui le
  réutilise désormais en interne, contrat public inchangé) ; `BpmnUploadPreview.vue` (lecture
  locale, prévisualisation, info de redéploiement, confirmation explicite avant tout appel de
  déploiement) ; nouvelle section dans `/admin/workflow-types` (pas de nouvel écran dédié).
- Le scan classpath au démarrage (`TenantProcessDeployment`) n'est **pas remplacé** — l'upload
  admin est un mécanisme complémentaire de déploiement à chaud.
- Taille d'upload : pas de nouvelle propriété — réutilise le seuil global
  `spring.servlet.multipart.max-file-size` existant (convention « source de vérité unique » déjà
  établie pour les pièces jointes dossier, découverte en implémentation et signalée avant de
  trancher plutôt que silencieusement contournée).
- `README.md` et `05-workflow-engine.md` corrigés (retrait des mentions non livrées : designer
  visuel intégré, cockpit Flowable) ; `GUIDE-UTILISATEUR.md` §3 documente les deux mécanismes de
  déploiement qui coexistent.

---

### [x] 36. `canEditDossier` (frontend) ne reflète pas la vraie règle d'autorisation backend

**Constat (revue PR #53, `edition-champs-dossier`) :** le frontend calculait `canEditDossier`
comme `isTenantAdmin || isWorkflowAdmin || roles.includes('DOSSIER_MANAGER')` — un simple rôle
`DOSSIER_MANAGER` suffisait à afficher les actions d'édition (champs dynamiques depuis PR #53,
« Ajouter » élément, « Lier » dossier). Or `DossierAccessPolicy.assertCanEdit`, le contrôle
réellement appliqué côté serveur, ne laissait passer automatiquement que `TENANT_ADMIN` et
`WORKFLOW_ADMIN` — un `DOSSIER_MANAGER` seul devait en plus détenir une entrée
`DossierTypeAclEntry(EDIT)` explicite pour ce type de dossier (décision n° 20). Sans cette ACL, le
frontend affichait donc une UI éditable qui échouait systématiquement en 403 à l'enregistrement.

**Pas une faille de sécurité** — le backend refusait proprement et aucune donnée n'était exposée ou
modifiée sans autorisation — mais un écart de fidélité UI/backend, présent avant PR #53
(« Ajouter »/« Lier ») et étendu à l'édition des champs.

**Corrigé (2026-08-20, cette session) :** première option retenue — la permission effective est
désormais exposée par le backend plutôt que redocumentée côté UI.

- `DossierAccessPolicy.canEdit(Dossier, AccessContext)` (nouveau, domaine pur) : miroir
  non-throwing de `assertCanEdit`, même logique (`hasPermission` factorisé), pour les appelants qui
  veulent le résultat plutôt qu'une exception.
- `DossierResponse` porte désormais un champ `canEdit: boolean`, calculé par `DossierController`
  via `accessPolicy.canEdit(dossier, actor)` sur les 5 réponses concernées (`create`, `getById`,
  `getByReference`, `updateStatus`, `updateFields`).
- Frontend : `DossierDetailView.vue` calcule `canEditDossier` depuis `dossier.value?.canEdit`
  (réponse serveur), plus depuis le rôle local — supprime la dépendance à `roles`/`DOSSIER_MANAGER`
  dans ce composant.
- OpenAPI golden régénéré (`openapi/openapi.yaml`) — ce passage a aussi rattrapé un golden
  périmé : la feature `observabilite` (PR #59) avait ajouté `GET /api/observability` sans
  régénérer le fichier, malgré sa propre revue Phase 9 affirmant le contraire.
- Tests : `DossierAccessPolicyTest` (nouveaux cas `canEdit`, y compris le contexte `Service` qui
  retourne `false` au lieu de lever) ; `DossierApiTest` (`canEdit` vrai/faux selon ACL et rôle,
  y compris `DOSSIER_MANAGER` sans grant) ; front Vitest + Playwright mettent à jour les fixtures
  `Dossier` avec `canEdit` et un test dédié prouve le découplage rôle/serveur.

**Impacte :** `frontend/src/views/pilotage/DossierDetailView.vue` (`canEditDossier`),
`DossierAccessPolicy.java`, `DossierController.java`, `DossierApiDtos.java`,
[12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

### [x] 37. `findClosedForRetention` sans `tenantId` explicite — rupture du pattern « ceinture et bretelles »

**Constat (revue PR #55, `retention-dossier-type`) :** `SpringDataDossierRepository` scope déjà
certaines requêtes par isolation de schéma **et** par `tenantId` explicite en paramètre (ex.
`findByTenantIdAndReference(tenantId, reference)`) — double barrière conforme à la décision n° 1.
La nouvelle requête `findClosedForRetention(status, pageable)`, ajoutée pour le job de rétention,
ne prend **pas** de `tenantId` et s'appuie uniquement sur l'isolation par schéma
(`search_path`, décision n° 13/14). Non exploitable aujourd'hui — une instance Spring Boot = un
tenant, donc aucune fuite inter-tenant possible en pratique — mais l'incohérence de pattern dans le
même fichier est un piège si ce postulat d'architecture évolue un jour (ex. pooling de connexions
multi-tenant).

**Livré (2026-08-22 — feature `fiabilisation-tests-plateforme`) :** `findClosedForRetention` aligné
sur le pattern existant — `tenantId` explicite ajouté au port (`DossierRepository`), à l'adaptateur
JPA (`JpaDossierRepositoryAdapter`) et à la requête (`SpringDataDossierRepository`, `and
d.tenantId = :tenantId`) ; `EvaluateDossierRetentionUseCaseImpl` propage le `tenantId` déjà en
paramètre de `evaluate(...)`. Test de non-régression (seul appelant existant) + nouveau test
d'isolation multi-tenant dans `DossierPersistenceTest`.

**Impacte :** `SpringDataDossierRepository.java`, `JpaDossierRepositoryAdapter.java`,
[02-multi-tenant.md](analysis%20doc/02-multi-tenant.md).

---

### [x] 38. `PurgeOutboxPayloads.json` — échappement JSON incomplet + `DossierType.code` sans validation de format

**Constat (revue PR #55, `retention-dossier-type`) :** `EvaluateDossierRetentionUseCaseImpl.payloadJson`
construisait le JSON de `purge_outbox.payload_json` à la main, avec un échappement limité à `\` et `"`
— les caractères de contrôle (ex. saut de ligne) que RFC 8259 impose d'échapper dans une chaîne
JSON ne le sont pas. Or `DossierType.code`, injecté tel quel dans ce payload, n'a **aucune
validation de format/charset** dans le domaine (`DossierType` ne fait qu'un `requireNonNull`).
`purge_outbox.payload_json` est une colonne `json` PostgreSQL : un `code` contenant un caractère de
contrôle brut ferait échouer l'`INSERT`.

**Mise à jour (revue PR #56, `purge-stockage`) :** le constructeur JSON manuel a été mutualisé dans
`PurgeOutboxPayloads.json()` (`domain/dossier/model/`), désormais utilisé à la fois par le job de
rétention (§21) **et** par le nouvel enqueue admin `EnqueueDossierPurgeUseCaseImpl` (§22,
`POST /api/dossiers/{id}/purge-requests`) — l'échappement lui-même n'a **pas** été corrigé lors de
cette mutualisation (`escape()` copié tel quel). Le rayon d'impact est donc plus large qu'au constat
initial : un `code` mal formé bloque désormais aussi l'enqueue admin, pas seulement le job planifié.
Le job de rétention reste en outre le plus exposé : il tourne dans une seule transaction
(`EvaluateDossierRetentionJob.evaluate`), donc un `INSERT` en échec y annule **tout le lot**,
bloquant silencieusement la rétention pour l'ensemble du tenant jusqu'à correction du type fautif.
`PurgeOutboxPayloads` fait aussi de l'extraction JSON manuelle en lecture (`parseEligibleAt`,
`parseTenantId` — `indexOf`/`substring`, pas un vrai parseur), même famille de fragilité.

Auto-infligé (seul un `TENANT_ADMIN` peut définir `code`), donc pas un vecteur d'attaque externe —
mais un point de robustesse réel, susceptible de stopper silencieusement un mécanisme de
conformité RGPD (rétention **et**, désormais, droit à l'effacement à la demande).

**Corrigé (2026-08-19, cette session) :** les deux options envisagées ont été retenues ensemble,
sans introduire Jackson dans le domaine — `PurgeOutboxPayloads` porte déjà le commentaire délibéré
« no Jackson in domain » et `mvn dependency:tree` confirme qu'aucun module Jackson n'est même
résolu dans ce projet (Spring Boot 4.1 / `spring-boot-starter-web` ne l'entraîne pas ici) ; le
remplacer aurait ajouté une dépendance, pas seulement corrigé un bug.

- `PurgeOutboxPayloads.escape()` réécrit pour échapper tous les caractères de contrôle RFC 8259
  (formes courtes `\n \r \t \b \f`, repli `\u00XX` pour le reste de la plage `< 0x20`) — la
  lecture (`parseEligibleAt`/`parseTenantId`) n'a pas eu besoin de changer, l'écriture était le
  seul point de rupture.
- `DossierType` valide désormais `code` contre `^[A-Za-z0-9_-]+$` dans le constructeur compact du
  record (même point que `validateRetention`) — défense en profondeur, cohérent avec l'usage réel
  (`CLAIM`, `LEGAL`, `LOG`…) et avec son usage en `@PathVariable` (`DossierTypeController`).
- Tests neufs : `PurgeOutboxPayloadsTest` (échappement `\`/`"`, formes courtes, repli `\u00XX`,
  absence de tout caractère de contrôle brut en sortie, round-trip lecture) ;
  `DossierTypeTest` (code valide accepté, code avec espace/contrôle/vide rejeté).
- `./mvnw verify -Dmaven.compiler.release=21` : 848 tests (839 + 9 nouveaux), 0 échec, ArchUnit et
  couverture Jacoco au vert — aucune régression, aucun `code` existant en base/tests hors charset.

**Impacte :** `PurgeOutboxPayloads.java`, `DossierType.java`,
[04-dossiers.md](analysis%20doc/04-dossiers.md).

---

### [x] 39. Construction de diagrammes BPMN dans FlowForge — écart restant après §35

**Constat :** §35 (`deploiement-bpmn`, PR #67) a livré l'upload + prévisualisation d'un
`.bpmn20.xml` **déjà modélisé en externe**, avec comme non-goal explicite « pas d'édition/design
de diagramme dans FlowForge ». Le besoin métier sous-jacent (pouvoir construire/modifier un
diagramme BPMN *depuis* FlowForge) reste donc un écart réel, pas un choix assumé — distinct du
volet Cockpit (`DEFERRED-FEATURES.md` §1.5, hors scope produit, non remis en cause ici).

**Options analysées (2026-08-24, discussion utilisateur) :**

- **(a) `bpmn-js` Modeler seul.** Bascule de `NavigatedViewer` vers le bundle `Modeler` du même
  package déjà en dépendance (`bpmn-js` `^18.24.0`, `frontend/package.json`) — palette
  drag-and-drop, `saveXML()`. Zéro nouvelle dépendance, mais pas d'édition des attributs
  `flowable:*` (assignee, `formKey`, listeners…) utilisés par les process réels
  (`smoke-process.bpmn20.xml` etc.) via la palette générique.
- **(b) `bpmn-js` Modeler + `bpmn-js-properties-panel` avec provider custom Flowable — retenue.**
  Même socle que (a), plus le panel de propriétés officiel de l'écosystème bpmn.io (MIT, même
  éditeur que Camunda Modeler mais embarqué en lib, pas d'app tierce) étendu par un provider
  custom pour exposer les extensions `flowable:*` réellement utilisées dans les diagrammes du
  dépôt.
- **(c) `diagram-js` bas niveau, éditeur maison.** Écarté — réinvente ce que `bpmn-js` fait déjà.
- **(d) Flowable Modeler (webapp séparée).** Dernier recours explicitement écarté par
  l'utilisateur — app tierce à opérer/sécuriser par tenant, contradictoire avec la décision déjà
  actée d'exclure les apps Flowable UI (§35, `DEFERRED-FEATURES.md` §1.5).

**Recommandation actée :** option **(b)**. Analyse détaillée :
[analysis doc/14-edition-bpmn.md](analysis%20doc/14-edition-bpmn.md) et
[docs/ai/requirements/2026-08-24-feature-edition-bpmn.md](docs/ai/requirements/2026-08-24-feature-edition-bpmn.md).

**Cycle `dev-lifecycle` lancé (2026-08-24), Phase 2 (requirements) close** — 7 décisions actées via
`AskUserQuestion` (une à la fois) : D-EDIT-SCOPE=provider custom limité aux attributs `flowable:*`
réellement utilisés (`async`/`delegateExpression`/`expression`/`scope`) ; D-EDIT-PLACEMENT=nouvel
écran dédié (pas une section de `/admin/workflow-types`) ; D-EDIT-ENTRYPOINT=3 points d'entrée
(création vierge, import fichier, édition d'un diagramme déjà déployé) ; D-EDIT-SAVE=brouillon
**persisté côté backend** (écart par rapport à la recommandation initiale d'export fichier local —
élargit le scope à un nouveau composant backend) ; D-EDIT-DRAFT-MODEL=un brouillon par clé de
process, écrasé à chaque sauvegarde, pas d'historique ; D-EDIT-DEPLOY-FLOW=bouton déployer
intégré, logique de confirmation/déploiement de `BpmnUploadPreview.vue` extraite et partagée (pas
dupliquée) ; D-EDIT-UNSAVED=sauvegarde explicite + confirmation navigateur `beforeunload`, pas
d'autosave.

**Phase 3 (design) close** — 3 décisions techniques actées via `AskUserQuestion` : D-DESIGN-DRAFT-KEY=clé
composite `(tenant_id, process_definition_key)` via `@IdClass` sur `BpmnDraftJpaEntity` (même pattern que
`WorkflowIdempotencyJpaEntity`/`WebhookIdempotencyJpaEntity`, déjà utilisé 2x dans le dépôt) ;
D-DESIGN-ROUTE=une seule route SPA `/admin/bpmn-editor` avec `processDefinitionKey` en query param
optionnel ; D-DESIGN-DRAFT-LIFECYCLE=le brouillon est supprimé automatiquement après un déploiement
réussi. Gap réel trouvé en exploration (pas supposé) : aucun mécanisme existant ne renvoyait le XML brut
d'une définition de process par clé — nouvelle méthode `WorkflowDeploymentPort.findDeployedXml`. Détail :
[docs/ai/design/2026-08-24-feature-edition-bpmn.md](docs/ai/design/2026-08-24-feature-edition-bpmn.md).

**Phase 4 (planning) close** — testing doc créé (comblant l'étape sautée en Phase 1 initiale) puis plan en
8 milestones / ~20 tâches : M1 domaine backend brouillon, M2 persistence (migration
`013-bpmn-draft-tables.yaml`), M3 `findDeployedXml`, M4 endpoint brouillon + suppression post-déploiement,
M5 canvas éditeur (`bpmn-js` Modeler + `bpmn-js-properties-panel`), M6 composables partagés
(`useBpmnDraft`/`useBpmnDeployment`), M7 écran dédié + intégration admin, M8 vérification globale
(4 nouveaux scénarios E2E). Couverture testing→planning vérifiée exhaustive. Détail :
[docs/ai/testing/2026-08-24-feature-edition-bpmn.md](docs/ai/testing/2026-08-24-feature-edition-bpmn.md),
[docs/ai/planning/2026-08-24-feature-edition-bpmn.md](docs/ai/planning/2026-08-24-feature-edition-bpmn.md).

**Résolu (2026-08-24, feature `edition-bpmn`) :** Phase 5 (implémentation) exécutée en TDD, M1-M4
(backend) et M5-M7 (frontend) en parallèle par deux agents dans des worktrees git isolés (contrat
API déjà figé par le design), fusionnés puis M8 (vérification globale) exécuté par l'orchestrateur.

- Backend : `BpmnDraft`/`BpmnDraftRepository`/`SaveBpmnDraftUseCase`/`GetBpmnDraftUseCase`
  (domaine), `BpmnDraftJpaEntity` (clé composite `@IdClass`, même pattern que
  `WorkflowIdempotencyJpaEntity`), `BpmnDraftController` (`GET`/`PUT /api/workflow-bpmn-drafts/{key}`),
  `WorkflowDeploymentPort.findDeployedXml` (+`GET /api/workflow-bpmn-deployments/{key}/xml`),
  `DeployBpmnProcessUseCaseImpl` modifié (supprime le brouillon après déploiement réussi).
- Frontend : `BpmnEditorCanvas.vue` (`bpmn-js` Modeler + `bpmn-js-properties-panel` +
  `flowablePropertiesProvider.ts` custom limité aux attributs réellement utilisés), composables
  `useBpmnDraft.ts`/`useBpmnDeployment.ts` (ce dernier extrait de `BpmnUploadPreview.vue`, contrat
  public inchangé), `BpmnEditorView.vue` (route `/admin/bpmn-editor`), lien « Éditer » dans
  `WorkflowConfigsAdminView.vue`.
- Le mécanisme de déploiement existant (§35) reste inchangé et réutilisé tel quel ; aucune
  application Flowable UI tierce introduite.
- **2 bugs réels trouvés et corrigés en Phase 5/8** : `modeler.get('commandStack').on(...)` —
  `CommandStack` de diagram-js n'a pas de `.on()` propre (événements via l'`eventBus`), masqué par
  des tests Vitest mockant entièrement `bpmn-js`, trouvé en écrivant l'E2E (Playwright, vrai
  navigateur) ; race condition de montage `BpmnEditorCanvas`/`useBpmnDraft.load()`, trouvée et
  corrigée en TDD par l'agent frontend.
- `./mvnw verify` : **1110 tests, 0 échec**, LINE 93.84%/BRANCH 75.27% (≥ seuils). Frontend : **193
  tests Vitest** (0 échec), typecheck/lint propres, **47/47 Playwright** (43 existants non
  régressés + 4 nouveaux `E2E-EDIT-BPMN-1..4`).
- **Phase 7 (check implementation, revue dédiée) : 1 bug réel supplémentaire trouvé et corrigé** —
  `SaveBpmnDraftUseCaseImpl.save()` levait une `NullPointerException` non gérée (→ 500) sur un `xml`
  `null`/manquant dans le corps de la requête `PUT /api/workflow-bpmn-drafts/{key}` ; même classe de
  défaut que la NPE déjà trouvée et corrigée en Phase 7 de §35 (`originalFilename` null), réapparue
  ici indépendamment. Corrigé par une garde explicite → `InvalidBpmnException` → `400`, couvert par
  4 nouveaux tests (2 unitaires, 2 API dont un `PUT` sans rôle admin jamais testé explicitement).
- **Phase 8 (write tests) : coverage vérifiée par classe/fichier, pas seulement à l'agrégat** — écart
  réel trouvé et comblé sur `flowablePropertiesProvider.ts` (33% branch coverage : les tests initiaux
  ne couvraient que `getGroups()`, jamais les fonctions `getValue`/`setValue` de chaque entrée — le
  pont lecture/écriture réel entre le panel et les attributs `flowable:*`, cœur de D-EDIT-SCOPE). 3
  tests ajoutés, tous verts du premier coup (implémentation déjà correcte, juste jamais exercée) →
  100% sur ce fichier. `./mvnw verify` final : 1110 tests (inchangé) ; frontend **196/196 Vitest**
  (193 + 3), `test:coverage` 86.84%/77.59%/86.05%/88.71% (≥ seuils 80/75/70/60), **47/47 Playwright**.
- `README.md`, `05-workflow-engine.md`, `GUIDE-UTILISATEUR.md` corrigés (mentions « pas de designer
  visuel/éditeur BPMN » retirées, nouveau mécanisme documenté).
- **Phase 9 (revue finale) close** : diff complet vérifié (65 fichiers, strictement scopé), contrat
  OpenAPI relu (2 nouveaux chemins + 2 schémas, 1 imprécision cosmétique mineure relevée — `format:
  byte` documenté au lieu de `text/xml` réel, non bloquant), rollback de migration confirmé,
  aucun breaking change, aucun secret/debug résiduel. **4 bugs réels trouvés et corrigés au total**
  sur la feature (Phases 5 et 7). Preuve fraîche re-vérifiée avant PR : 1110 backend + 196 Vitest +
  47 Playwright, tous verts.

Détail complet : [docs/ai/implementation/2026-08-24-feature-edition-bpmn.md](docs/ai/implementation/2026-08-24-feature-edition-bpmn.md).
Phase 9 (revue finale) close, PR ouverte sur demande explicite de l'utilisateur :
[PR #71](https://github.com/superjojo79/FlowForge/pull/71).

**Impacte :** `frontend/src/components/workflow/BpmnCanvas.vue`, `BpmnUploadPreview.vue`,
`WorkflowBpmnDeploymentController` (endpoint de déploiement potentiellement réutilisable tel
quel), [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md), `README.md`.

---

### [x] 47. Édition du schéma des champs dynamiques — écran admin `DossierType`

**Constat :** [`GUIDE-UTILISATEUR.md` §1](GUIDE-UTILISATEUR.md#1-préalable--configurer-un-type-de-dossier)
documente déjà la limite : le formulaire `DossierTypesAdminView.vue` crée toujours un
`DossierType` avec `fieldDefinitions: []` en dur — aucun écran ne permet à un `TENANT_ADMIN` de
définir la **liste des `FieldDefinition`** d'un type. Le backend est déjà prêt (`POST`/`PATCH
/api/dossier-types/{code}` acceptent déjà `fieldDefinitions` en entrée, domaine et validation déjà
en place) : c'est un écran manquant, pas un trou d'architecture. À ne pas confondre avec
`edition-champs-dossier` (PR #53, déjà livrée) qui édite les **valeurs** de champs sur un dossier,
pas la définition du schéma sur le type.

**Découverte notable :** `CheckDossierTypeSchemaCompatibilityUseCase` existe déjà côté domaine
(rejoue `DossierFieldValidator` sur les dossiers existants avec le nouveau schéma proposé) mais
n'est aujourd'hui invoqué que par l'outil MCP `define_dossier_type` — `PATCH
/api/dossier-types/{code}` (REST) accepte silencieusement une modification de schéma qui rendrait
des dossiers existants incohérents, sans avertissement ni blocage. `DossierExceptionHandler` ne
mappe pas non plus `DossierTypeSchemaIncompatibleException` vers un statut HTTP.

**Points ouverts (à trancher en phase requirements/design, pas silencieusement) :**

1. **D-UI-SCHEMA-COMPAT** — comment l'écran s'articule avec le check de compatibilité :
   (a) nouvel endpoint REST de préflight (`POST
   /api/dossier-types/{code}/schema-compatibility`, miroir du `dryRun` MCP) puis confirmation
   explicite avant `PATCH` ; (b) `UpdateDossierTypeUseCaseImpl` invoque le check et bloque par
   défaut (aligne REST sur le comportement MCP, mappe
   `DossierTypeSchemaIncompatibleException` en 409) ; (c) V1 sans avertissement, incohérence §
   ci-dessus non résolue. Choix produit (bloquer vs avertir), pas seulement technique.
2. **Unicité de `name` dans `fieldDefinitions`** — validation formulaire seule, ou garde-fou
   domaine (`DossierType`/`DossierFieldValidator`) pour couvrir aussi MCP/API directe ?
3. **`allowUnknownFields`** — à confirmer dans le périmètre de cette feature (proposé) plutôt
   qu'un lot séparé ; absent de l'écran actuel malgré son exposition API complète.
4. **Alignement maquette** — [11-frontend.md](analysis%20doc/11-frontend.md) impose
   `FlowForge - Maquettes UI.html` comme référence ; aucun écran de ce fichier ne couvre
   aujourd'hui l'édition du schéma de champs — à vérifier en phase design.

**À produire :** décisions ci-dessus, puis écran admin (création + édition), avec mise à jour de
`GUIDE-UTILISATEUR.md` §1 (limite actuelle devient obsolète) une fois livrée.

**Impacte :** `frontend/src/views/admin/DossierTypesAdminView.vue`, `DossierTypeController`,
`UpdateDossierTypeUseCaseImpl`, `DossierExceptionHandler`, `GUIDE-UTILISATEUR.md` §1.

**Détail :** [17-schema-champs-dynamiques.md](analysis%20doc/17-schema-champs-dynamiques.md).

**Implémenté (2026-08-28, feature `schema-champs-dynamiques`, Phase 5 dev-lifecycle) :**
`UpdateDossierTypeUseCaseImpl` branche désormais `CheckDossierTypeSchemaCompatibilityUseCase`
(déjà écrit, jusqu'ici invoqué uniquement par le MCP) avant `save` — bloque (409, nouveau
`@ExceptionHandler(DossierTypeSchemaIncompatibleException.class)`) toute modification de schéma
qui rendrait des dossiers existants incohérents, alignant enfin REST sur le comportement déjà
strict du MCP `define_dossier_type` (lui-même simplifié : ne fait plus son propre appel au check
sur le chemin d'écriture réel, seulement pour `dryRun`). `DossierType` gagne une contrainte
d'unicité de `name` dans `fieldDefinitions` (compact constructor, 400 générique). Nouvel écran :
`FieldDefinitionDefsEditor.vue` (liste éditable de `FieldDefinition`, HTML natif + scoped CSS,
réutilisé création + panneau d'édition inline « Modifier les champs », pas de modale) intégré à
`DossierTypesAdminView.vue`, plus la bascule `allowUnknownFields`. Validation formulaire
(`validateFieldDefinitions.ts`) bloque côté client avant tout appel réseau. Écart trouvé en
implémentation (pas en design) : le panneau d'édition se pré-remplit directement depuis l'objet
déjà chargé par la liste paginée plutôt que par un `GET /api/dossier-types/{code}` dédié envisagé
au design — la réponse de liste porte déjà le schéma complet. `./mvnw verify` 1382 tests (0
échec), Jacoco tenu ; frontend 247 Vitest (0 échec), typecheck/lint propres, 59/59 Playwright
(4 nouveaux `E2E-SCHEMA-*`). 1 bug réel trouvé et corrigé en TDD :
`FieldDefinitionDefsEditor.vue` liait son rendu directement sur la prop `modelValue` au lieu de
l'état local réactif, donc n'affichait pas ses propres modifications avant un aller-retour par le
parent (ex. `type=SELECT` ne révélait pas l'éditeur d'options). Détail :
`docs/ai/implementation/2026-08-28-feature-schema-champs-dynamiques.md`. Phases 7 (check
implementation — 1 écart trouvé et corrigé, ordre validation/check), 8 (write tests — 1383 tests
backend, 247 Vitest + 59 Playwright) et 9 (revue finale, aucun blocage) closes.
[PR #87](https://github.com/superjojo79/FlowForge/pull/87).

---

### [x] 48. Activation du connecteur `http-rest` en rôle SERVICE

**Constat :** [README.md](README.md) et [06-connecteurs.md](analysis%20doc/06-connecteurs.md)
documentent déjà la limite : le catalogue V1 livré pour `http-rest` est **TRIGGER seulement** ; un
skeleton `HttpRestServiceConnector`
(`src/main/java/com/flowforge/infrastructure/connector/builtin/HttpRestServiceConnector.java`)
existe depuis `durcissement-donnees-sensibles` (PR #62, réutilise `OutboundUrlGuard`, anti-fuite de
secrets prouvée, 7 tests) mais n'implémente pas `ConnectorDefinition` et n'est enregistré nulle
part — `ExecuteConnectorUseCaseImpl` rejette toute demande SERVICE sur ce type
(`UnsupportedOperationException`).

**Découverte notable :** toute la chaîne de consommation SERVICE (`ConnectorServiceTask`,
`ConnectorExecuteController`, `M2mConnectorExecuteController`, doc `${connectorServiceTask}` déjà
au catalogue §7 de `GUIDE-CONCEPTION-BPMN.md`) est déjà générique et en production via
`flowable-signal` — rien à construire côté consommation. Le vrai obstacle est une **contrainte de
modélisation** : `ConnectorCatalog` indexe par `ConnectorDefinition.getType()`, un seul bean peut
porter `"http-rest"` — le skeleton (classe séparée) ne peut pas être enregistré tel quel à côté de
`HttpRestTriggerConnector` (`@Component`, capability `TRIGGER`) sans conflit.

**Points ouverts (à trancher en phase requirements/design, pas silencieusement) :**

1. **D-CAPABILITY-SCOPE** — fusionner la capacité SERVICE dans `HttpRestTriggerConnector`
   (capability → `BOTH`, toute instance sert aux deux rôles, cohérent avec `flowable-signal`) ou
   introduire une capacité par instance (nouveau champ de config limitant le rôle, sans précédent
   dans le code, scope plus large) ?
2. **D-HTTP-METHODS** — GET seul (statu quo skeleton) ou élargir à POST/PUT/DELETE avec corps ?
3. **D-HTTP-CONFIG** — champs de config par instance (URL de base, en-têtes par défaut, auth
   sortante) ou tout piloté dynamiquement par `connectorInput` à chaque appel comme aujourd'hui ?
4. **D-HTTP-NAMING** — renommer `HttpRestTriggerConnector` (ex. `HttpRestConnector`) ou garder le
   nom et ajouter l'interface `ServiceConnector` ?
5. **D-HTTP-TESTS** — sort des deux tests qui verrouillent explicitement l'état « non activé »
   (`connector_doesNotImplementConnectorDefinition`, `connector_isNotASpringBean`).

**À produire :** décisions ci-dessus, puis activation (fusion capacité + config schema le cas
échéant), avec mise à jour de `06-connecteurs.md` (tableau catalogue), `README.md` (§ Système de
connecteurs) et `DEFERRED-FEATURES.md`/`FILE-ATTENTE.md` une fois livrée.

**Impacte :** `HttpRestTriggerConnector`, `HttpRestServiceConnector`,
`HttpRestServiceConnectorTest`, `06-connecteurs.md`, `README.md`.

**Implémenté (2026-08-31, feature `connecteur-http-rest-service`, cycle `dev-lifecycle` complet) :**
les 5 décisions ci-dessus tranchées via `AskUserQuestion` (une à la fois) : D-CAPABILITY-SCOPE=fusion
au niveau du type (capability `BOTH`) ; D-HTTP-METHODS=GET+POST/PUT avec corps dynamique ;
D-HTTP-CONFIG=en-tête `Authorization` statique en config (`authHeaderValue`, chiffré) ;
D-HTTP-NAMING=renommage `HttpRestTriggerConnector`→`HttpRestConnector` ; D-HTTP-TESTS=fusion dans
`HttpRestConnectorTest`. Complétées en design par D-HTTP-AUTH-SHAPE=valeur brute de l'en-tête et
D-HTTP-CONTENT-TYPE=fourni dynamiquement par `connectorInput.headers` (pas de défaut).

**2 écarts réels trouvés en implémentation (Phase 5), absents du design :** `ExecuteConnectorRequest`
(DTO REST BFF/M2M) était câblé en dur sur les champs `flowable-signal` — `url`/`method`/`body`/
`headers` n'existaient pas dans le DTO, rendant `http-rest` SERVICE inatteignable via HTTP malgré la
capacité activée côté connecteur ; corrigé, golden OpenAPI régénéré en conséquence. **1 durcissement
trouvé en Phase 7 :** bean `HttpClient` rendu explicitement `Redirect.NEVER` (défaut JDK déjà correct,
mais implicite — une redirection suivie ne serait jamais re-vérifiée contre `OutboundUrlGuard`).

`./mvnw verify` 1420 tests (0 échec), Jacoco bundle tenu (LINE ≥85 %/BRANCH ≥75 %, 3 lacunes réelles
comblées en Phase 8 sur le code touché) ; Vitest 247/247, Playwright 59/59 (feature backend-only,
zéro fichier `frontend/**` touché, régression complète). Doc à jour :
`06-connecteurs.md`, `README.md`, `CHANGELOG.md`.

**Détail :** [18-connecteur-http-rest-service.md](analysis%20doc/18-connecteur-http-rest-service.md).

---

### [x] 49. Assainissement de la dette technique — reliquats `DEFERRED-FEATURES.md` §1

**Clos** (2026-09-01, `assainissement-dette-technique`,
[PR #97](https://github.com/superjojo79/FlowForge/pull/97)) — cycle `dev-lifecycle`
complet (Phases 1-9). Vérification item par item contre le code réel (2026-08-31) : **2 items
étaient déjà clos, jamais répercutés dans le doc** (`CHECK SQL dossier_type_acl.permission` —
migration `014` déjà livrée, PR #72 ; validation blank `tenantId` — `TenantProperties.id` porte
déjà `@NotBlank`) ; **2 réellement ouverts, livrés par cette feature** (validation blank
`processKey`/`workflowKey`, `dossierId` en MDC par item du job de purge) ; **1 confirmé redondant
sans code** (`Persistable`/`isNew()`).

**Décisions actées (`AskUserQuestion`, options recommandées) :**

- **D-BLANK-VALIDATION-SCOPE = défense en profondeur dans le domaine** — garde
  `if (x.isBlank()) throw new IllegalArgumentException(...)` ajoutée dans les compact constructors
  de `WorkflowTypeConfig.workflowKey`, `TriggerOutboxPayload.processKey`,
  `WorkflowStartedEvent.processKey`, `DossierType.workflowKey`, sur le modèle
  `DossierType.code`/`CODE_PATTERN`. Tous les points d'entrée externes (REST, MCP) bloquaient déjà
  le blank en amont (`required()`/`requireString()`) — cette garde est une seconde ligne de
  défense, pas la fermeture d'un bug atteignable.
- **D-MDC-DOSSIER-MECHANISM = callback `Function<UUID, Runnable>`** — `ProcessPurgeOutboxUseCase.
  processBatch` gagne un 3e paramètre invoqué avant `purgeDossier(record)`, fermé dans un `finally`
  englobant tout le traitement de l'item (pas seulement le `catch`). `PurgeOutboxJob` fournit le
  callback via `MdcScope.dossier(...)`. Justifié par une trouvaille concrète en design : le seul
  vrai `log.warn` par item existant (`FlowableWorkflowEngineAdapter.
  deleteProcessInstanceBestEffort`) avale son exception sans la relancer — un scope limité au
  `catch` l'aurait raté. Preuve directe via un test d'intégration multi-items utilisant le vrai
  `ProcessPurgeOutboxUseCaseImpl` (pas mocké).
- **D-PERSISTABLE-CONFIRM = confirmé redondant par preuve documentée** — `DossierJpaEntity.version`
  est un `@Version private long` primitif, `SpringDataDossierRepository` un `JpaRepository`
  standard sans `Persistable` : comportement déjà correct via
  `JpaMetamodelEntityInformation.isNew()`. Aucun code écrit.

**Vérifié en implémentation (Phase 7)** : tous les appelants de production des 4 constructeurs
gardés passent déjà par un DTO REST/MCP qui blank-check en amont — aucun risque de régression
(confirmé par `./mvnw verify`). Trouvaille mineure hors périmètre non corrigée :
`WorkflowTriggerEvent.processKey` porte le même gap, mais son seul appelant construit un
`TriggerOutboxPayload` immédiatement après, déjà gardé par cette feature.

**Validation :** `./mvnw verify` vert (1430 tests, `ArchitectureRulesTest.
domain_has_no_framework_dependency` toujours vert, seuils Jacoco maintenus). Balayage Jacoco classe
par classe (Phase 8) : 4 nouvelles gardes + code du callback 100% couverts, aucun gap introduit.
Feature confirmée backend seule : `npm run test` (247 tests) et `npm run test:e2e` (59 tests) verts
en régression, zéro fichier `frontend/**` touché.

**Impacte :** `WorkflowTypeConfig`, `TriggerOutboxPayload`, `WorkflowStartedEvent`, `DossierType`,
`ProcessPurgeOutboxUseCase`, `ProcessPurgeOutboxUseCaseImpl`, `PurgeOutboxJob`,
`DEFERRED-FEATURES.md`, `CHANGELOG.md`.

**Détail :** [19-assainissement-dette-technique.md](analysis%20doc/19-assainissement-dette-technique.md).

---

### [x] 50. Nouveaux connecteurs hors V1 — `scheduler`, `smtp`, `document-storage`

**Clos** (2026-09-01, `connecteurs-hors-v1`,
[PR #99](https://github.com/superjojo79/FlowForge/pull/99)) — cycle `dev-lifecycle` complet
(Phases 1-9). Les 3 connecteurs restants du catalogue cible livrés, chacun en réutilisant un
mécanisme domaine déjà existant plutôt qu'un nouveau couplage technique — zéro modification de
`ConnectorTriggerRuntimeImpl`/`ConnectorCatalog`/`ConnectorRegistry`.

**Constat :** `06-connecteurs.md` catalogue 5 types de connecteurs cibles, seuls `http-rest` et
`flowable-signal` sont enregistrés dans `ConnectorRegistry` aujourd'hui. Les 3 restants
(`scheduler`, `smtp`, `s3-minio`) étaient explicitement hors V1 à chaque cycle précédent. Choisi
comme prochaine feature (2026-09-01) après épuisement complet de TODO.md (49/49), de la revue
sécurité (§40–46) et du sweep de dette technique (§49), parmi deux pistes proposées à
l'utilisateur (l'autre — outillage provisioning tenant — reste en réserve). Périmètre précisé par
l'utilisateur : les 3 connecteurs, avec `s3-minio` **généralisé** en `document-storage` (réutilise
le port `DocumentStoragePort` déjà agnostique S3/filesystem, cohérent avec le principe
« ne pas coupler à MinIO » de `DEFERRED-FEATURES.md` §0) plutôt que couplé à un client S3 direct.

**Mécanismes identifiés en analyse (pas supposés, vérifiés contre le code) :**

- `scheduler` (TRIGGER) réutilise le pipeline trigger générique existant
  (`ConnectorTriggerRuntimeImpl.onIncomingEvent`, déjà découplé de HTTP) — seule pièce nouvelle :
  un job `@Scheduled` qui décide quand l'appeler pour chaque instance selon son `cronExpression`
  (`org.springframework.scheduling.support.CronExpression`, déjà sur le classpath).
- `smtp` (SERVICE) réutilise le port `EmailPort`/`SmtpEmailAdapter` déjà livré avec `notifications`
  — envoi direct ad hoc depuis un `serviceTask`, distinct du pipeline notification
  structuré/templé/outbox existant (pas une duplication).
- `document-storage` (SERVICE) réutilise `DocumentStoragePort` (`store`/`retrieve`/`exists`/
  `delete`) pour du contenu non lié à un `Dossier`/`DossierElement` (déjà couvert par les pièces
  jointes existantes).

**Décisions actées (`AskUserQuestion`, options recommandées retenues) :**

1. **D-SCHEDULER-LASTFIRED = table dédiée** — `connector_schedule_state` (`connector_instance_id`
   PK+FK, `last_triggered_at`), séparée de `ConnectorInstanceData` (record partagé par tous les
   connecteurs).
2. **D-SCHEDULER-DOSSIER = un dossier par tir, comportement existant** — `scheduler` exige
   `dossierTypeCode` en config comme les autres connecteurs TRIGGER, zéro changement du pipeline
   trigger partagé.
3. **D-SCHEDULER-POLL = 1 minute par défaut, configurable** — `app.connector.scheduler.poll-delay`.
4. **D-SMTP-HTML = texte brut seul** — `EmailPort.send(to, subject, body)` tel quel.
5. **D-SMTP-VALIDATION = garde basique non-blank + « @ »**.
6. **D-STORAGE-SIZE-LIMIT = limite configurable** — `ConnectorProperties.DocumentStorage.
   maxContentSize` (même patron que `Webhook.maxBodyBytes`).
7. **D-STORAGE-REF-LIFECYCLE = choix assumé, documenté** — aucune purge automatique du
   `storageRef`, à la charge du process BPMN.

**Écart de design trouvé et corrigé en planning (pas en Phase 7)** : le config schema initial de
`SchedulerConnector` (`cronExpression`+`dossierTypeCode`) omettait `processKey`, pourtant requis
par `WorkflowTriggerEvent` (`Objects.requireNonNull`) et déjà présent dans `HttpRestConnector`/
`FlowableSignalConnector` — vérifié par grep avant d'écrire le code, pas découvert plus tard.

**Déviations trouvées en implémentation (Phase 5, confirmées en Phase 7)** :
- `ExecuteConnectorRequest` (DTO partagé BFF/M2M) étendu de 6 champs (`to`, `subject`, `operation`,
  `storageRef`, `content`, `contentType`) pour porter les entrées `smtp`/`document-storage` —
  même point de couture déjà rencontré pour `http-rest` (TODO §48), 2ᵉ occurrence. OpenAPI golden
  régénéré.
- `NoopEmailPortTestConfiguration` (nouveau `@TestConfiguration`, patron `OidcClientTestConfiguration`)
  pour tester `smtp` en contexte Spring complet sans serveur SMTP réel (aucun GreenMail dans le
  dépôt).

**Validation :** `./mvnw -Dmaven.compiler.release=21 verify` vert (**1486 tests**, `All coverage
checks have been met.`, seuils Jacoco maintenus sans aucun gap à combler). Feature confirmée
backend seule : `npm run test` (**247 tests**) et `npm run test:e2e` (**59 tests**) verts en
régression, zéro fichier `frontend/**` touché. Revue Phase 7 (fichier par fichier contre le
design) : aucun écart supplémentaire trouvé.

**Impacte :** `ConnectorRegistry` (découverte automatique, aucune modification), `06-connecteurs.md`,
`README.md`, nouveaux fichiers `infrastructure/connector/builtin/{SchedulerConnector,SmtpConnector,
DocumentStorageConnector}.java`, `infrastructure/connector/SchedulerConnectorTriggerJob.java`,
`domain/connector/port/out/ConnectorScheduleStateRepository.java`, migration Liquibase
`018-connector-schedule-state.yaml`, `ConnectorProperties.DocumentStorage`,
`ExecuteConnectorRequest`, `DEFERRED-FEATURES.md`, `CHANGELOG.md`.

**Détail :** [20-connecteurs-hors-v1.md](analysis%20doc/20-connecteurs-hors-v1.md).

---

### [x] 51. Corrélation par clé métier pour callbacks asynchrones — connecteur `flowable-callback`

**Statut (2026-09-02) : clos.** Cycle `dev-lifecycle` Phases 1–9 complet, **mergée sur `main`**
([PR #102](https://github.com/superjojo79/FlowForge/pull/102), sans attendre la CI hébergée, sur
demande explicite utilisateur). `README.md` § « Système de connecteurs » et duplication interne
triviale trouvées et corrigées en Phase 9. `CHANGELOG.md` mis à jour avec le numéro de PR réel.
Implémentation complète (M1–M7) en TDD : `WorkflowEnginePort.correlateMessage`
(`ExecutionQuery`/`messageEventReceived`), `CallbackConnectorRuntime`,
`FlowableMessageCallbackConnector` (capability `CALLBACK`), `CallbackWebhookController`
(`POST /api/callbacks/{instanceId}`). `./mvnw verify` : 1533 tests, 0 échec. Zéro fichier
`frontend/**` touché. **3 écarts réels trouvés et corrigés** : Phase 5 (NPE sur variable de mapping
optionnelle non résolue, `Map.copyOf`) ; **Phase 7** (D-CALLBACK-TYPE — Flowable compare les
variables de process par type ET valeur, un `correlationValue` numérique dans le payload externe
ne matchait jamais une variable de process `String`, mode de défaillance systématique pas un cas
limite ; corrigé par normalisation en `String` dans `FlowableMessageCallbackConnector`, prouvé
contre le vrai moteur) ; **Phase 7** aussi (design promettait un 404 sur la race
`list()`/`messageEventReceived(...)`, le code livré en Phase 5 laissait fuir un
`FlowableException` brut — corrigé). Docs :
`docs/ai/{requirements,design,planning,implementation,testing}/2026-09-02-feature-connecteur-callback.md`.
Rappel Phase 3 : le mécanisme initialement proposé (`RuntimeService.createMessageCorrelation(...)`)
n'existe pas en Flowable 8.0.0 Community (vérifié contre les sources réelles téléchargées, pas
supposé) — remplacé par `ExecutionQuery`/`messageEventReceived` ci-dessus.

**Constat :** ni le rôle SERVICE du connecteur `http-rest` (`HttpRestConnector.execute`, appel
`httpClient.send(...)` bloquant, timeout fixe) ni son rôle TRIGGER (`WebhookController` →
`ConnectorTriggerRuntimeImpl.onIncomingEvent`, qui crée/attache un dossier mais ne réveille jamais
une instance de process en attente précise) ne permettent de recevoir un callback asynchrone
corrélé à un appel sortant précis. Origine : question utilisateur (2026-09-01), vérifiée contre le
code réel avant de répondre.

**Mécanisme retenu (tranché, corrigé en Phase 3) :**
```java
List<Execution> matches = runtimeService.createExecutionQuery()
        .messageEventSubscriptionName(messageName)
        .processVariableValueEquals(correlationKey, correlationValue)
        .list();
// 0 -> erreur (404), >1 -> erreur (409), 1 -> :
runtimeService.messageEventReceived(messageName, matches.get(0).getId(), payload);
```
Déjà disponible sur le `RuntimeService`/`ExecutionQuery` injectés, **jamais utilisé** dans le dépôt
aujourd'hui (confirmé par grep). Résout l'exécution en attente sur un
`intermediateCatchEvent`/`receiveTask` dont une variable de **process** correspond à une clé de
corrélation choisie par le concepteur BPMN — pas le `businessKey` du process (`link.id()`, déjà
utilisé par `WorkflowEnginePort.start`), qui ne distingue pas plusieurs callbacks parallèles pour
un même dossier.

**Points ouverts — tous tranchés (2026-09-02) :**

1. **D-CALLBACK-RACE** — rejet HTTP 404/409, retry côté système externe. Pas de file d'attente
   persistée.
2. **D-CALLBACK-IDEMPOTENCE** — réutilisation telle quelle de `webhook_idempotency` /
   `JpaWebhookIdempotencyStoreAdapter`.
3. **D-CALLBACK-MULTI-MATCH** — erreur remontée (409) sur `matches.size() > 1`, jamais de
   traitement silencieux du premier résultat.
4. **D-CALLBACK-MESSAGE-NAME** — `messageName` fixe par instance de connecteur, pas dynamique par
   appel.
5. **Nom du connecteur/capability** — nouvelle valeur `ConnectorCapability.CALLBACK`, tranché en
   Phase 3 via `AskUserQuestion`.
6. **Vérification déploiement BPMN** — faite en Phase 3 : `DeployBpmnProcessUseCaseImpl`/
   `FlowableWorkflowDeploymentAdapter` déploient de façon générique, aucune restriction sur les
   éléments BPMN.

**À produire (Phase 5, implémentation) :** `WorkflowEnginePort.correlateMessage(...)` +
implémentation Flowable ci-dessus, nouveau connecteur callback + endpoint HTTP authentifié (HMAC,
patron `WebhookController`), doc catalogue à jour.

**Impacte :** `WorkflowEnginePort`, `FlowableWorkflowEngineAdapter`, nouveaux fichiers
`domain/connector/model/CallbackConnector.java` (nom provisoire) + adaptateur infra dédié,
`06-connecteurs.md`, `README.md`.

**Détail :** [21-correlation-callback-async.md](analysis%20doc/21-correlation-callback-async.md).

---

### [x] 52. Formulaires dynamiques rattachés aux tâches BPMN (`userTask`)

**Statut (2026-09-03) : clos.** Cycle `dev-lifecycle` Phases 1-9 complet,
`formulaires-dynamiques-taches`. Décisions tranchées par l'utilisateur au lancement du cycle :
**D-FORM-KIND-SCOPE = mécanisme extensible** (pas seulement 2 kinds figés — `formKey` reste une
chaîne opaque de bout en bout côté backend, interprétée uniquement par un registre
`taskFormRegistry.ts` côté frontend) ; **D-FORM-DOSSIER-FIELDS-PARTIAL = schéma complet** du
`DossierType` (pas de sous-ensemble) ; **D-FORM-DOCGEN-TEMPLATE-SCOPE = filtré par type de
dossier**, nouveau champ `DossierType.documentTemplateKeys` (liste vide = aucun template
disponible, configuration explicite requise — pas de fallback « tous les templates »). Point
technique confirmé (D6, ex-point 6) : `Task.getFormKey()` fonctionne bien sans le module
`flowable-form` sur le moteur Flowable 8.0.0 réellement vendored par ce dépôt, prouvé par un test
d'intégration réel (`FormKeySmokeIT`) avant d'investir dans la plomberie qui en dépend.

**Écart de sécurité réel trouvé et corrigé en Phase 3 (design)** : `GET /api/document-templates`
est `TENANT_ADMIN`-only, inutilisable par un porteur de tâche pour peupler le picker
`document-generation` — corrigé par un nouvel endpoint dédié
`GET /api/dossier-types/{code}/document-templates` (jamais `htmlContent`, même vérification `VIEW`
que `GET /api/dossier-types/{code}`, pas un accès inconditionnel).

**Bug réel latent trouvé et corrigé en Phase 5 (implémentation, tests Playwright réels)** : le tout
premier attribut `flowable:*` posé sur un diagramme qui ne déclare pas encore `xmlns:flowable` —
dont le gabarit BPMN **vierge** de l'éditeur — round-trip correctement en mémoire mais est
silencieusement perdu par `saveXML()` (aucune extension moddle enregistrée pour l'espace de noms
`flowable`, latent depuis `edition-bpmn` TODO §39, jamais détecté faute de test round-trip réel
précédent — les tests Vitest existants mockaient `useService` et ne prouvaient que la logique
get/set, pas la sérialisation XML). Corrigé (`EMPTY_BPMN_XML` déclare désormais l'espace de noms) ;
risque résiduel documenté (`GUIDE-CONCEPTION-BPMN.md` D-FLOWABLE-NS) pour un diagramme importé d'un
outil externe n'ayant jamais utilisé d'attribut `flowable:*`.

`./mvnw verify` (Test-class only — ce dépôt n'a pas de plugin Failsafe, `*IT.java` en est exclu) :
1561 tests, 0 échec, Jacoco LINE/BRANCH tenus. `*IT.java` du périmètre de la feature exécutées
séparément (`FormKeySmokeIT`, `DocumentTemplatePersistenceIT`, `OpenApiContractIT`) : 9 tests,
0 échec. Vitest 269/269 ; Playwright 63/63 (59 existants + 4 nouveaux `E2E-FORM-*`, zéro
régression). `GUIDE-UTILISATEUR.md`/`GUIDE-CONCEPTION-BPMN.md`/`CHANGELOG.md` mis à jour.
Écart de revue trouvé et corrigé en Phase 9 : `openapi/openapi.yaml` (fichier de référence testé
par contrat) n'avait pas été régénéré après l'ajout du nouvel endpoint et des nouveaux champs —
régénéré et vérifié vert. Détail complet :
`docs/ai/implementation/2026-09-03-feature-formulaires-dynamiques-taches.md`.

**Revue adversariale approfondie (subagent dédié, post-Phase 9)** : 6 écarts réels trouvés et
corrigés — le kind `document-generation` était non fonctionnel de bout en bout (mismatch de noms
de variables entre le formulaire et le `serviceTask ${documentGenerationTask}`, jamais détecté car
chaque côté était testé isolément avec le même nom erroné) ; `DossierFieldsTaskForm` pouvait
effacer silencieusement un champ `sensitive` jamais lu par l'utilisateur et rendait la tâche
incomplétable dès qu'un champ `NUMBER` était présent (soumission de l'état complet au lieu d'un
diff) ; `documentTemplateKeys` n'était appliqué qu'à la découverte, jamais à la complétion de tâche
(n'importe quel `documentType` passait) ; le registre de formulaires de tâche était vulnérable aux
clés héritées d'`Object.prototype`. Chacun corrigé avec un test de non-régression dédié
(unitaire + IT/E2E réel selon le cas — dont un nouveau fixture BPMN + IT Flowable réel pour le
premier). Détail complet et écarts mineurs restants (non corrigés, hors périmètre) :
`docs/ai/implementation/2026-09-03-feature-formulaires-dynamiques-taches.md` §M8. Vérification
finale après correctifs : 1566 tests `Test*` (0 échec), 13 tests `*IT.java` (0 échec), Vitest
278/278, Playwright 63/63.

<details>
<summary>Analyse d'origine (avant cycle dev-lifecycle)</summary>


**Constat :** le tuyau backend de complétion de tâche accepte déjà des variables
(`POST /api/workflow-tasks/{taskId}/complete` → `{ variables: {...} }`, `CompleteTaskUseCaseImpl`),
mais rien ne s'en sert : `MobileTasksView.vue` complète les tâches à l'aveugle, sans formulaire.
Aucun concept de `formKey` n'existe sur `MyWorkflowTask`/`WorkflowTaskView`, ni dans l'éditeur BPMN
visuel (`flowablePropertiesProvider.ts`). Origine : question utilisateur (2026-09-01), vérifiée
contre le code réel avant de répondre. Les 2 cas cités (compléter les champs du dossier, déclencher
une génération de documents) ne sont possibles aujourd'hui qu'en dehors du BPMN (fiche dossier pour
l'édition de champs ; génération entièrement automatique via le `serviceTask`
`${documentGenerationTask}`, jamais déclenchée par une saisie utilisateur en cours de process).

**Mécanisme proposé (pas encore tranché) :** réutiliser `flowable:formKey`, attribut BPMN standard
lu via `Task.getFormKey()` sur le moteur **cœur** (`flowable-engine`) — pas le module optionnel
`flowable-form`, absent du classpath (`pom.xml` vérifié). Deux briques déjà réutilisables :
`DynamicFields.vue` (composant générique `FieldDefinition[]` + `v-model`, déjà utilisé pour
l'édition des champs dossier) et `PATCH /api/dossiers/{id}/fields` (permission + concurrence
optimiste déjà gérées). Convention de 2 « kinds » fixes proposée, pas un schéma générique ouvert :
`dossier-fields` (rend `DynamicFields.vue` contre le schéma `DossierType`, soumission via
`PATCH .../fields` existant) et `document-generation` (nouveau petit composant de sélection
template/variante/locale, soumission = variables de complétion de tâche lues par le
`serviceTask` `${documentGenerationTask}` suivant — la génération reste où elle est, le formulaire
ne fait que fournir l'entrée).

**Points ouverts (à trancher en phase requirements/design, pas silencieusement) :**

1. **D-FORM-KIND-SCOPE** — s'en tenir à ces 2 kinds fixes vs. prévoir un mécanisme extensible.
2. **D-FORM-DOSSIER-FIELDS-PARTIAL** — schéma complet du `DossierType` à chaque tâche, ou
   sous-ensemble de champs pertinent à l'étape (non supporté par `DynamicFields.vue` aujourd'hui).
3. **D-FORM-DOCGEN-TEMPLATE-SCOPE** — tous les templates actifs du tenant, ou filtrés par type de
   dossier (aucun lien `DossierType`↔`templateKey` dans le modèle actuel).
4. **D-FORM-VALIDATION** — client seul (le serveur revalide déjà via `DossierFieldValidator` sur
   `PATCH .../fields`, pas de nouvelle garde nécessaire pour `dossier-fields`).
5. **D-FORM-BACKWARD-COMPAT** — tâche sans `formKey` = comportement actuel inchangé (à documenter
   explicitement, pas un vrai point ouvert).
6. **Vérification à faire en design, pas supposée** : confirmer `Task.getFormKey()` accessible sans
   le module `flowable-form` sur la version Flowable 8.0.x réellement utilisée par ce dépôt.

**À produire :** décisions ci-dessus, puis `formKey` sur `MyWorkflowTask`/`WorkflowTaskView` +
lecture dans `FlowableWorkflowTaskInboxAdapter`, champ `flowable:formKey` dans l'éditeur BPMN
(`flowablePropertiesProvider.ts`), rendu des 2 kinds côté frontend tâches.

**Impacte :** `MyWorkflowTask`, `WorkflowTaskView`, `WorkflowTaskController`,
`FlowableWorkflowTaskInboxAdapter`, `flowablePropertiesProvider.ts`, `MobileTasksView.vue`/
`TaskList.vue`, nouveau composant sélection template/variante/locale.

**Détail :** [22-formulaires-dynamiques-taches.md](analysis%20doc/22-formulaires-dynamiques-taches.md).

</details>

---

### [x] 53. `WebhookController` : un événement TRIGGER échoué peut bloquer les rejeux avec la même `Idempotency-Key`

**Statut (2026-09-03) : corrigé.** Même pattern que le correctif F2 de TODO §51 : capture de
`RuntimeException` autour de `readPayload`/`onIncomingEvent`, `idempotencyStore.complete(...)`
appelé avec le statut déterministe (400) pour `ConnectorValidationException`/`IllegalArgumentException`,
`IN_FLIGHT` conservé pour l'imprévu. Tests ajoutés dans `WebhookControllerTest`
(`receive_deterministicValidationFailureReturns400_andCompletesClaim`,
`receive_unmappedRuntimeException_leavesClaimInFlightForFreshRetry`). `./mvnw verify` : 1541
tests, 0 échec.

**Constat (trouvé en revue de sécurité adversariale sur `connecteur-callback`, 2026-09-02) :**
`WebhookController.receive(...)` n'appelle `idempotencyStore.complete(...)` que sur le chemin de
succès. Si `ConnectorTriggerRuntime.onIncomingEvent(...)` lève (config manquante, référence dossier
inconnue, etc.), la ligne d'idempotence reste `IN_FLIGHT` en base — un rejeu avec la **même**
`Idempotency-Key` (pratique standard de retry idempotent côté systèmes externes) lève
`WebhookIdempotencyInFlightException` (409 « delivery in progress ») pendant toute la durée du TTL
(24 h par défaut), au lieu de recevoir la vraie réponse d'échec ou d'être retraité. Exactement le
même défaut a été corrigé sur `CallbackWebhookController` (composant `JpaWebhookIdempotencyStoreAdapter`
partagé) dans le correctif post-merge de TODO §51 — voir
`docs/ai/implementation/2026-09-02-feature-connecteur-callback.md` § « Correctifs post-merge »
pour le détail du mécanisme et le pattern de correction déjà écrit et testé (capturer
`RuntimeException`, appeler `complete(...)` avec le statut déterministe pour les échecs dont
l'issue est stable sur le même contenu de requête, laisser `IN_FLIGHT` pour l'imprévu).

**Impact réel plus faible que sur le callback** : les exceptions typiques de
`ConnectorTriggerRuntimeImpl` (config manquante, `DossierNotFoundException`) sont en général des
erreurs permanentes — un rejeu échouerait de toute façon pour la même raison. Pas un blocage
urgent, mais un vrai bug de fiabilité si un système externe retente légitimement après une erreur
transitoire (ex. `DossierNotFoundException` sur une référence pas encore créée par ailleurs).

**À produire :** même correctif que `CallbackWebhookController` (try/catch autour de l'appel à
`onIncomingEvent`, `complete(...)` avec le statut déterministe, exception non reconnue laissée
`IN_FLIGHT`), test de régression miroir de
`receive_unmappedRuntimeException_leavesClaimInFlightForFreshRetry`.

**Impacte :** `WebhookController.java`.

---

### [x] 54. 2ème revue de sécurité adversariale sur `connecteur-callback` — 4 nouveaux écarts (F5–F8)

**Statut (2026-09-03) : corrigé.** Nouvelle revue adversariale complète, indépendante de la
1ère (TODO §51), demandée explicitement par l'utilisateur. Reconfirme F0–F4 toujours clos, trouve
et corrige 4 nouveaux écarts (« corrige tout maintenant ») :

- **F5 (haute)** — le corps de requête est intégralement bufférisé en mémoire par Spring **avant**
  toute garde applicative (`body.length > maxBodyBytes`, rate limiter, authentification) : DoS
  mémoire non authentifié via `Transfer-Encoding: chunked` (ou tout mensonge sur `Content-Length`)
  sur `/api/callbacks/**`/`/api/webhooks/**`. Corrigé : `WebhookBodySizeLimitFilter` enveloppe la
  requête dans un `ServletInputStream` qui borne les octets réellement lus, indépendamment de ce
  que la requête déclare.
- **F6 (moyenne)** — pas de protection anti-rejeu HMAC (signature du corps seul, `Idempotency-Key`
  non signée) : une paire (corps, signature) capturée une fois était rejouable indéfiniment.
  Corrigé : signature étendue à `timestamp + "." + corps` (`X-Webhook-Timestamp` requis, fenêtre
  de fraîcheur 5 min) — **rupture de contrat pour les appelants HMAC existants**, documentée dans
  les champs de config et `analysis doc/06-connecteurs.md`.
- **F7 (moyenne)** — aucune longueur minimale sur `webhookSecret` : brute-forçable en pratique avec
  la limite de débit par défaut (120 req/min) si l'opérateur choisit un secret court. Corrigé :
  `WebhookSecretPolicy.MIN_LENGTH = 20`, appliqué dans `validate()` des 2 connecteurs concernés.
- **F8 (moyenne)** — échec d'authentification webhook jamais journalisé : une tentative de brute
  force soutenue contre F7 aurait été invisible. Corrigé :
  `SecurityAuditLog.webhookAuthenticationFailed(...)` (WARN), câblé dans
  `ConnectorExceptionHandler`.

Détail complet (root cause, faux positifs écartés) :
`docs/ai/implementation/2026-09-02-feature-connecteur-callback.md` § « Correctifs post-merge
(2ème passe adversariale) ». `./mvnw verify` : **1554 tests, 0 échec** (+13 tests de régression
permanents).

---

### [x] 55. Nouveau kind de tâche générique — questions/choix utilisateur indépendants du dossier

**Statut (2026-09-05) : clos.** Cycle `dev-lifecycle` Phases 1–9 complet,
`questions-utilisateur-taches`, milestones M1–M9 enchaînés en TDD sur demande explicite. Prolonge
le mécanisme `formKey`/`taskFormRegistry.ts` livré par TODO §52 avec un 3ᵉ kind générique
`user-question:<schemaKey>` : un concepteur de workflow définit un mini-formulaire (un ou
plusieurs champs texte/nombre/date/choix, réutilise `FieldDefinition`/`FieldType` tel quel) dans un
nouvel écran admin (**Admin → Questions de tâche**), sans qu'un développeur code un composant
dédié. La réponse part uniquement comme variables de process (nom = `FieldDefinition.name`),
**jamais écrite dans le dossier**, et reste tracée (`TaskQuestionAnswer`).

**Décisions tranchées par l'utilisateur au lancement du cycle** (2 séries de questions en
requirements, revues en Phase 2/3) : réutilisation de `FieldDefinition`/`FieldType`
(dépendance `domain.workflow` → `domain.dossier.model` déjà existante et assumée) ; garde-fou de
collision de noms de variables validé **à la sauvegarde du schéma** (`WorkflowVariables
.isReservedOrKnownKey`, nouvelle méthode) ; traçabilité à portée minimale (réponses brutes, pas de
lien reporting/SLA) ; schéma **non versionné** (modification directe, suppression dégrade comme un
`formKey` inconnu) ; RBAC `TENANT_ADMIN`+`WORKFLOW_ADMIN` (cohérent avec la conception BPMN, pas
`TENANT_ADMIN` seul comme `DossierType`) ; pas d'i18n.

**2 gaps réels trouvés en revue Phase 2 (requirements)**, non anticipés par l'analyse initiale :
`CompleteTaskUseCase.complete(...)` (port existant) ne transmettait aucune identité d'appelant
alors que le contrôleur dispose déjà d'`Authentication` — la signature a évolué (`actorUserId`)
pour renseigner `TaskQuestionAnswer.submittedBy` (D-USERQ-SUBMITTED-BY). L'endpoint runtime de
lecture d'un schéma n'a, par construction, aucun `DossierType`/permission `VIEW` à vérifier —
corrigé en exigeant `taskId` et en vérifiant que le `formKey` de cette tâche référence bien le
`schemaKey` demandé (D-USERQ-RUNTIME-SCOPE).

**1 vrai compromis d'architecture trouvé et tranché en revue Phase 3 (design)**, seul point
explicitement soumis à confirmation (les 2 autres écarts de cette phase — alignement de la
concurrence sur `DossierType` plutôt que `Dossier.fields()`, scission d'un use case couvrant en
réalité 2 endpoints aux autorisations différentes — ont été corrigés directement, l'utilisateur
ayant relevé après coup que je ne l'avais pas signalé assez clairement) : la validation serveur du
kind doit résoudre le `schemaKey` depuis `Task.getFormKey()` lu **côté serveur**
(`WorkflowEnginePort.findFormKeyByTaskId`, nouveau), jamais depuis une variable soumise par le
client — sinon réintroduction de la faille D-TASK-FORM-DOCTYPE-SCOPE déjà corrigée sur
`document-generation` (TODO §52 Phase 9). Accepté comme exception **documentée et bornée** au
principe « `formKey` opaque côté backend » (`TaskQuestionFormKeys`, source de vérité unique
partagée avec son miroir TypeScript).

**Écarts réels trouvés en implémentation (Phase 5), corrigés ou documentés sans les cacher** :
`WorkflowTaskController.complete()` devient `@Transactional` (précédent : `DossierExportController`)
pour que l'écriture de `TaskQuestionAnswer` et `completeTask(...)` roulent back ensemble.
Simplification assumée du component breakdown : un seul `TaskQuestionSchemaUseCaseImpl`
(Save/Get/Delete) au lieu des 5 use cases séparés du design initial — pattern
`WorkflowTypeConfigUseCaseImpl` déjà établi, RBAC entièrement au contrôleur, aucun garde domaine.
`DynamicFields.vue` n'avait déjà aucun binding dossier (le design supposait à tort qu'il fallait
« en retirer » un binding). `FieldDefinitionDefsEditor.vue` réutilisé sans fork (`FieldType.FILE`
reste proposé côté UI, rejeté côté serveur avec message explicite) pour ne pas risquer de
régression sur `DossierTypesAdminView` pour un seul gain UX. `taskFormRegistry.ts` ne passait que
`dossierId` à tout composant résolu — `taskId`/`formKey` ajoutés comme props sur les 2 sites
d'appel (`MobileTasksView.vue`, `TaskList.vue`), nécessaires à l'endpoint runtime scopé par tâche.

**Non fait, documenté comme risque assumé plutôt que silencieusement ignoré** : pas de test de
rollback transactionnel dédié (nécessiterait un mock partiel du moteur Flowable) ; pas de garde
applicative dédiée au `schemaKey` dupliqué (repose sur la contrainte unique DB, comme
`DossierType.code`).

Prouvé contre le **vrai** moteur Flowable (`UserQuestionFormSmokeIT`, fixture
`user-question-smoke.bpmn20.xml`) : la passerelle exclusive en aval lit effectivement la variable
produite par la complétion, pas seulement émise (même classe de bug que celle trouvée en Phase 9
de TODO §52). RBAC et garde-fou de portée runtime vérifiés contre la **vraie** chaîne Spring
Security (`TaskQuestionSchemaApiTest`, pas `standaloneSetup`).

**Écart réel trouvé et corrigé en Phase 7 (revue d'implémentation)** : la validation serveur des
réponses (`assertAndRecordUserQuestionAnswers`) ne vérifiait initialement que `required` et les
`options` d'un champ `SELECT` — un champ `NUMBER`/`DATE`/`BOOLEAN` acceptait silencieusement une
valeur non conforme au type. Corrigé en réutilisant `DossierFieldValidator`
(`domain.dossier.model`, déjà utilisé pour les champs dossier) au lieu d'une validation « maison »
partielle, sans nouvelle classe. Test de régression ajouté.

**Écarts réels trouvés et corrigés en Phase 8 (write tests, balayage Jacoco classe par classe,
pas seulement à l'agrégat)** : 4 trouvailles. (1) `GetTaskQuestionSchemaForTaskUseCaseImpl` —
la branche « le `formKey` référence bien un `schemaKey` connu mais le schéma a été supprimé
entre-temps » n'était pas couverte ; test `getForTask_rejects_whenFormKeyMatchesButSchemaWasDeleted`
ajouté. (2) `TaskQuestionSchemaController.update` — la branche 404 (`schemaKey` inconnu) n'était
pas couverte ; test `update_unknownSchemaKey_returns404` ajouté (au passage,
`create_withDuplicateSchemaKeyForTheSameTenant_returns409` ajouté aussi pour la même raison sur la
contrainte unique DB). (3) `TaskQuestionFormKeys.formKeyFor` était du code mort côté production
(seul `schemaKeyOf` avait des appelants réels) : plutôt que de le supprimer, 3 fichiers de test
(`CompleteTaskUseCaseImplTest`, `GetTaskQuestionSchemaForTaskUseCaseImplTest`,
`UserQuestionFormSmokeIT`) ont été corrigés pour l'appeler au lieu de dupliquer le littéral
`"user-question:orientation-dossier"` en dur — la méthode a désormais de vrais appelants et le
risque de dérive entre préfixe et littéraux de test disparaît. (4) `TaskQuestionAnswerJpaEntity
.toDomain()` : 0% de couverture confirmée (aucun appelant, `TaskQuestionAnswerRepository` est
volontairement write-only — D-USERQ-TRACE-SCOPE). Supprimée plutôt que couverte par un test
artificiel, avec un commentaire expliquant pourquoi et quand la réintroduire (cohérent avec le YAGNI
déjà appliqué ailleurs dans ce dépôt plutôt que du code mort gardé « au cas où »). Deux tests
d'erreur serveur ajoutés côté frontend (`TaskQuestionSchemasAdminView.spec.ts`) pour la même
raison (branches d'erreur de `create`/`update` non exercées).

`./mvnw verify` : 1591 tests, 0 échec, ArchUnit conforme, couverture Jacoco tenue (« All coverage
checks have been met »). `UserQuestionFormSmokeIT` exécutée séparément (`*IT.java` exclu par
défaut, convention du dépôt). Vitest 289/289 (287 existants + 2 nouveaux, 0 régression),
`vue-tsc`/`eslint` propres, Playwright 65/65 (inchangé, aucun scénario E2E supplémentaire en Phase
8). `GUIDE-UTILISATEUR.md` (§0 + nouvelle §10), `GUIDE-CONCEPTION-BPMN.md` (§3bis étendu) mis à
jour. `CHANGELOG.md` différé (aucune PR ouverte à ce stade, même convention que TODO §47).
Détail complet : `docs/ai/implementation/2026-09-05-feature-questions-utilisateur-taches.md`,
`docs/ai/testing/2026-09-05-feature-questions-utilisateur-taches.md`.

**Bug réel trouvé et corrigé après Phase 9, via un subagent dédié à une analyse adversariale
demandée explicitement par l'utilisateur (pas dans le contrat des skills `dev-lifecycle`)** :
`CompleteTaskUseCaseImpl.assertAndRecordUserQuestionAnswers` appelait
`fieldValidator.validate(schema.fields(), submitted, allowUnknownFields=true)` — codé en dur — et
`complete(...)` transmettait ensuite les `variables` **brutes** (pas la map `answers` filtrée aux
champs du schéma) à `workflowEnginePort.completeTask(...)`. Résultat : un détenteur de tâche
authentifié pouvait soumettre, en plus des champs déclarés par le schéma, n'importe quelle autre
clé (`decision`, etc.) — jamais validée, jamais filtrée — qui atteignait quand même le moteur
Flowable comme variable de process, capable de piloter une passerelle exclusive ailleurs dans le
workflow, **sans jamais apparaître dans la trace `TaskQuestionAnswer`** dont c'est pourtant
l'unique raison d'être. Cela allait à l'encontre de la promesse même de la feature (un schéma
admin borne les choix possibles indépendamment du dossier). Corrigé : `allowUnknownFields=false`
(toute clé hors schéma est rejetée en 400) et `complete(...)` transmet désormais la map `answers`
schéma-filtrée (pas `variables` brute) au moteur pour ce kind de tâche — le schéma devient une
vraie liste blanche des variables de process qu'une tâche `user-question` peut produire, pas
seulement un contrôle sur les champs qu'il reconnaît. Test de régression ajouté
(`complete_withUserQuestionFormKey_variableNotDeclaredByTheSchema_rejectsAndNeverReachesTheEngine`).
`./mvnw verify` re-exécuté : **1592 tests**, 0 échec, Jacoco tenu ; `UserQuestionFormSmokeIT`
re-confirmée verte ; Vitest/Playwright inchangés (correctif backend seul).

**Détail :** [23-questions-utilisateur-taches.md](analysis%20doc/23-questions-utilisateur-taches.md).

---

### [ ] 56. Extension du serveur MCP au flux métier complet (connecteurs, association templates, questions de tâche)

**Statut (2026-09-06) : cycle `dev-lifecycle` lancé, Phase 7 (check implementation) close.** Origine :
demande utilisateur — inspecter ce qui a été ajouté à FlowForge depuis `serveur-mcp` (PR #82, 9
tools) et déterminer ce qui manque pour qu'un agent MCP puisse définir un flux métier complet sans
sortir du canal MCP. Analyse initiale :
[24-extension-mcp-flux-metier.md](analysis%20doc/24-extension-mcp-flux-metier.md). Trois lacunes
vérifiées contre le code réel : **(1)** aucun tool ne couvre `ConnectorInstance` (créée
aujourd'hui uniquement via l'écran admin `TENANT_ADMIN`-only, alors qu'un `serviceTask` BPMN la
référence par UUID d'instance) ; **(2)** `DossierType.documentTemplateKeys` (association template
↔ type de dossier, TODO §52) est le seul champ de `DossierType` inaccessible en écriture via
`define_dossier_type` (forcé à `null` en création, préservé tel quel en update) ; **(3)**
`TaskQuestionSchema` (TODO §55) n'a pas de tool MCP, la feature étant postérieure au cadrage MCP.

**6 décisions tranchées en Phase 1 via `AskUserQuestion`** :

- **D-MCP-EXT-SCOPE = un seul cycle** couvrant les 3 lacunes (pas 3 cycles séparés).
- **D-MCP-EXT-SECRET = même flux que l'admin REST** — secret connecteur en clair sur l'appel MCP
  (canal M2M déjà authentifié `API_SERVICE`), chiffré AES-GCM à l'écriture, jamais renvoyé en
  clair en lecture.
- **D-MCP-EXT-DOCTPL = étendre `define_dossier_type`** avec un paramètre optionnel
  `documentTemplateKeys`, plutôt qu'un tool dédié.
- **D-MCP-EXT-DRYRUN = `dryRun` partout** — reconduit sur les 2 nouveaux tools d'écriture, cohérent
  avec `define_dossier_type`/`deploy_workflow`.
- **D-MCP-EXT-NAMING** — 7 nouveaux tools confirmés : `list_connector_definitions`,
  `list_connector_instances`, `get_connector_instance`, `define_connector_instance`,
  `list_task_question_schemas`, `get_task_question_schema`, `define_task_question_schema`.
- **D-MCP-EXT-RBAC-GUARD** — trouvaille réelle faite en Phase 1 (pas anticipée par l'analyse
  initiale) : `ManageConnectorInstanceUseCaseImpl` et `TaskQuestionSchemaUseCaseImpl` n'ont
  **aucune garde de rôle au niveau domaine** (RBAC vérifié seulement au contrôleur REST
  `@PreAuthorize`) — et `WorkflowTypeConfigUseCaseImpl`, **déjà utilisé par `deploy_workflow`
  depuis PR #82**, non plus. Décision : ajouter le garde-fou domaine sur les 3 use cases, y
  compris le correctif sur du code déjà livré (pas seulement sur les 2 nouveaux tools).

**Portée technique désormais plus large que l'analyse initiale** : le garde-fou implique de
changer la signature de 4 interfaces `port.in`
(`ManageConnectorInstanceUseCase`, `SaveTaskQuestionSchemaUseCase`/`DeleteTaskQuestionSchemaUseCase`,
`SaveWorkflowTypeConfigUseCase`) pour y ajouter l'acteur, et de mettre à jour tous leurs appelants
REST existants. Point de conception encore ouvert (Phase 3) : emplacement du garde-fou partagé
(dupliquer un guard par bounded context, le déplacer dans un package neutre, ou étendre
`TenantAdminGuard` en l'acceptant comme dépendance transverse) — voir
[docs/ai/design/2026-09-06-feature-extension-mcp-flux-metier.md](../docs/ai/design/2026-09-06-feature-extension-mcp-flux-metier.md).

**Phase 2 (revue requirements) close** — 1 décision supplémentaire tranchée via `AskUserQuestion` :
**D-MCP-EXT-RBAC-SCOPE**, trouvée en revérifiant le RBAC REST exact de chaque contrôleur (pas
seulement l'existence du garde) — l'asymétrie est réelle et volontairement reproduite au niveau
domaine : `ManageConnectorInstanceUseCaseImpl` (garde sur toutes les opérations, miroir du
`@PreAuthorize` classe de `ConnectorAdminController`) et `TaskQuestionSchemaUseCaseImpl`/
`GetTaskQuestionSchemaUseCaseImpl` (garde sur toutes les opérations, miroir de
`TaskQuestionSchemaController` qui gate explicitement `list`/`get`) contre
`WorkflowTypeConfigUseCaseImpl` (garde **uniquement** sur `create`/`update` — ses `list`/
`getByKey` restent ouverts comme le REST, pour ne pas régresser `list_workflow_types` déjà en
production sans demande fonctionnelle). 2 points que le brouillon design avait laissés incertains
en Phase 1 sont résolus après vérification du code réel (pas des trous) :
`GetTaskQuestionSchemaUseCase.list(PageQuery)` existe déjà (pas de nouveau port nécessaire) ;
`list_connector_definitions` réutilisera `ConnectorCatalog.listAll()` directement, comme le fait
déjà `ConnectorDefinitionController`.

**Phase 3 (design) close** — dernier point de conception ouvert (D2, emplacement du garde-fou
partagé) tranché via `AskUserQuestion` : **option (c)** — `TenantAdminGuard` (`domain.dossier`)
gagne une méthode `requireTenantOrWorkflowAdminOrApiService`, réutilisée par `domain.connector`
(via `requireTenantAdminOrApiService`, déjà existante) et `domain.workflow` (nouvelle méthode),
plutôt que de dupliquer un guard par bounded context ou de déplacer la classe dans un package
neutre. Vérifié avant de trancher : aucune règle ArchUnit n'interdit une dépendance entre
sous-packages du domaine, et un précédent déjà accepté existe (`domain.workflow` dépend déjà de
`domain.dossier.model.FieldDefinition`, TODO §55). En recensant tous les appelants des interfaces
`port.in` à modifier, le compte est passé de **4 à 5 interfaces** : `GetTaskQuestionSchemaUseCase`
a aussi besoin de l'acteur (pas seulement `Save`/`Delete`), car `TaskQuestionSchemaController` gate
également ses lectures (D5). Trouvaille utile : `DeployWorkflowTool` (tool `deploy_workflow`
existant, PR #82) résout déjà l'acteur (`accessContextFactory.currentActor()`) mais ne le transmet
pas à `saveWorkflowTypeConfigUseCase.create`/`update` — changement minimal (un paramètre à ajouter
à un appel déjà en place).

**Phase 4 (planning) close** — plan en **9 milestones / ~26 tâches** (comparable à `serveur-mcp`,
9 milestones / 32 tâches) : M1 nouvelle méthode `TenantAdminGuard` seule d'abord (pattern « gardes
domaine d'abord » déjà utilisé par `serveur-mcp` M1) ; M2/M3/M4 câblage du garde-fou par bounded
context (connecteurs, questions de tâche, correctif `WorkflowTypeConfigUseCase` limité à
`create`/`update`) ; M5 `define_dossier_type` étendu (indépendant, peut être fait en parallèle) ;
M6/M7 les 7 nouveaux tools MCP (lecture puis écriture) ; M8 IT HTTP M2M réelles + scénario bout en
bout (`define_connector_instance` → `define_dossier_type` → `define_task_question_schema` →
`deploy_workflow`, preuve du flux métier complet) ; M9 vérification globale + documentation.
Traçabilité testing → tâches vérifiée exhaustive.

**Phase 5 (implémentation TDD) close** — M1 à M9 enchaînés en TDD dans la même session. 16 tools
MCP au total (9 → 16). **3 écarts réels trouvés en cours d'implémentation, pas anticipés par le
design** : (1) `ConnectorPortsContractTest` (implémentation anonyme du port dans un test de
contrat existant) était un appelant supplémentaire non recensé en design, corrigé en même temps
que `DeployWorkflowTool`/`DeployWorkflowToolTest` ; (2) les méthodes `default`
`TaskQuestionSchemaRepository.page(...)`/`WorkflowTypeConfigRepository.page(...)` ne sont pas
exécutées par un mock Mockito standard (retournent `null`) — les nouveaux tests stubbent
directement `page(...)`, pas `findAll(...)` ; (3) seule régression trouvée par `./mvnw verify` :
`McpEndpointApiTest` figeait le compte de tools à 9, corrigé (16, test renommé). `./mvnw verify` :
**1653 tests, 0 échec**, ArchUnit conforme, couverture Jacoco tenue. Frontend : **289/289 Vitest**,
0 régression (zéro fichier `frontend/**` modifié, confirmé par `git diff --stat`). Playwright non
exécutable dans ce bac à sable (binaire `chrome-headless-shell` absent, pré-existant, sans rapport
avec cette feature) — non bloquant, feature backend-only. Documentation resynchronisée :
[GETTING-STARTED.md](../GETTING-STARTED.md) §12 (9 → 16 tools),
[15-serveur-mcp.md](analysis%20doc/15-serveur-mcp.md) (bandeau d'extension). `CHANGELOG.md`
différé (aucune PR ouverte à ce stade, même convention que TODO §47/§55). Détail complet :
[docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md](../docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md).

**Phase 6 (mise à jour planning)** — déjà reflétée ci-dessus (les 9 milestones étaient marqués
`[x]` en fin de Phase 5).

**Phase 7 (check implementation) close** — revue fichier par fichier (pas par relecture du résumé
Phase 5) de tous les fichiers modifiés, comparés au design et aux requirements. Confirmé conforme :
pattern adaptateur fin sur les 7 nouveaux tools ; garde-fou domaine bien en tête de chaque méthode
concernée sans exception, dans les 3 use case impl ; asymétrie D5 réellement implémentée dans le
code (pas seulement non testée) — vérifiée à l'identique côté domaine et côté REST
(`WorkflowTypeConfigController.list`/`get` toujours sans acteur transmis) ; masquage secrets
(`ConnectorInstanceMcpView`) seul point de sortie des 3 tools connecteur, aucune fuite en clair
dans les messages d'erreur. **1 écart réel trouvé et corrigé** : `DefineConnectorInstanceTool`
sautait toute validation (`name`/`connectorType` requis, `config` contre le schéma connecteur) en
`dryRun=true` sur `create` — le test `if (dryRun)` était placé avant les contrôles, contrairement
au contrat annoncé et incohérent avec `define_dossier_type` (validation structurelle toujours
active en dryRun). Corrigé : contrôles requis avant le test dryRun, ajout d'une validation
`ConnectorCatalog.get(connectorType).validate(...)` dans la branche dryRun (`ConnectorCatalog`
désormais injecté dans le tool) — 3 nouveaux tests unitaires (`DefineConnectorInstanceToolTest` →
10/10) + 1 nouveau test d'intégration HTTP réel (`McpToolCallIntegrationTest` → 14/14). 2 questions
examinées et écartées (pas des bugs) : le garde-fou domaine n'est pas redondant avec la chaîne
Spring Security M2M (rôle métier vs. validité transport du JWT, objets différents) ; la validation
`ConnectorCatalog` en dryRun-create n'a pas besoin de garde propre (opération locale en mémoire,
aucun accès tenant). `./mvnw verify` final (JDK 25 installé dans ce bac à sable, absent au
démarrage du conteneur) : **1657 tests, 0 échec**, ArchUnit conforme, couverture Jacoco tenue.
Détail complet dans
[docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md](../docs/ai/implementation/2026-09-06-feature-extension-mcp-flux-metier.md)
§ « Phase 7 — Check Implementation ».

**Suite :** Phase 8 (dev-testing), Phase 9 (revue finale). Documents lifecycle :
[docs/ai/requirements/2026-09-06-feature-extension-mcp-flux-metier.md](../docs/ai/requirements/2026-09-06-feature-extension-mcp-flux-metier.md),
[docs/ai/design/2026-09-06-feature-extension-mcp-flux-metier.md](../docs/ai/design/2026-09-06-feature-extension-mcp-flux-metier.md),
[docs/ai/testing/2026-09-06-feature-extension-mcp-flux-metier.md](../docs/ai/testing/2026-09-06-feature-extension-mcp-flux-metier.md),
[docs/ai/planning/2026-09-06-feature-extension-mcp-flux-metier.md](../docs/ai/planning/2026-09-06-feature-extension-mcp-flux-metier.md).

**Impacte :** `infrastructure/mcp/tool/**` (7 nouveaux tools + `DefineDossierTypeTool` étendu),
`domain/connector/**`, `domain/workflow/**` (`TaskQuestionSchemaUseCaseImpl`,
`WorkflowTypeConfigUseCaseImpl`), [15-serveur-mcp.md](analysis%20doc/15-serveur-mcp.md),
[06-connecteurs.md](analysis%20doc/06-connecteurs.md).

**Détail :** [24-extension-mcp-flux-metier.md](analysis%20doc/24-extension-mcp-flux-metier.md).

---

## Alignement post-implémentation et sécurité (2026-08-25)

Points identifiés par
[REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md). Ils ne sont **pas**
tranchés. Ne pas les implémenter en choisissant une option silencieusement.

### [x] 40. SSTI Thymeleaf / SpEL dans les templates stockés

**Clos** (2026-08-25, `durcissement-securite-post-revue`, D-40 = **B** / B2,
[PR #76](https://github.com/superjojo79/FlowForge/pull/76)) — évaluateur allowlist
(`#vars` / `#dates` / `#numbers` / `#strings` / `#lists` + chemins modèle), pas
l'option A « sans SpEL » au sens moteur nu. `T()` / `#beans` / `new` / `getClass`
→ 400 generate/preview **et** activation (le POST draft reste 201).

**Durci (revue post-merge, 2026-08-26) :** le finding « Basse » de la Phase 9
(`readProperty` via `setAccessible(true)` + méthode "bare name" arbitraire,
y compris privée) était sans pont vers `Runtime` mais dépassait le contrat
« accès lecture seule aux propriétés » voulu. `readProperty` n'accepte
désormais que les getters JavaBean publics et les accesseurs canoniques de
`record` (via `Class#getRecordComponents()`) — plus de `setAccessible`, plus
de méthode privée/non-conventionnelle atteignable. Détail :
`docs/ai/implementation/2026-08-25-feature-durcissement-securite-post-revue.md`
§ Findings.

**Énoncé :** `ThymeleafHtmlTemplateProcessor` utilise `SpringTemplateEngine`
(SpringEL). Un `TENANT_ADMIN` peut faire exécuter `${T(java.lang.Runtime)…}`
sur la JVM via generate / live preview. L'iframe SPA `sandbox=""` ne couvre
que le XSS navigateur. Le sandboxing Thymeleaf avancé a été reporté dès
`templates-documents`.

**Options :** (A) `StandardTemplateEngine` sans SpEL — recommandée ;
(B) allowlist SpEL ; (C) assumer `TENANT_ADMIN` = opérateur de confiance
(incompatible SaaS si le point 41 reste ouvert).

**À produire :** décision A/B/C puis tests d'exploit qui doivent échouer
(`${T(java.lang.Runtime)}` → 400 / non évalué).

**Impacte :** `ThymeleafHtmlTemplateProcessor`,
[08-templates-documents.md](analysis%20doc/08-templates-documents.md).

**Détail :** [REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md) §4 S-1.

---

### [x] 41. Isolation PostgreSQL par schéma sans GRANT par tenant

**Clos** (2026-08-25, `durcissement-securite-post-revue`, D-41 = **A** / scope **S**,
[PR #76](https://github.com/superjojo79/FlowForge/pull/76)) — rôle LOGIN
`flowforge_tenant_dev` dans l'init Compose, superuser `POSTGRES_USER` distinct,
IT `TenantRoleIsolationIT` (SKIPPED sans Docker). Pas de CLI provisioning tenant.

**Énoncé :** `search_path` n'est pas une barrière. Un seul rôle PG (Compose :
superuser de la base `flowforge`) peut lire les autres schémas du cluster
partagé. Combiné au point 40 : RCE sur une instance → données des autres
tenants.

**Options :** (A) un rôle PG par tenant au provisioning — recommandée ;
(B) une database par tenant ; (C) assumer la confiance réseau d'instances.

**À produire :** décision + runbook provisioning (point 31) + IT « user A ne
`SELECT` pas `tenant_b` ».

**Impacte :** [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md),
`docker/postgres/init/`, point 31.

**Détail :** [REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md) §4 S-2.

---

### [x] 42. Actuator `permitAll` et conflit de port 8081

**Clos** (2026-08-25, `durcissement-securite-post-revue`, D-42 = **L**,
[PR #76](https://github.com/superjojo79/FlowForge/pull/76)) — bind `127.0.0.1`,
port défaut **8090**, `permitAll` conservé (DD-MGMT-SEC) parce que loopback.
Keycloak reste `:8081`.

**Énoncé :** chaîne Security Order 0 `permitAll` sur le port management
(`health` + `prometheus`), décision assumée `observabilite` DD-MGMT-SEC.
Aucun `management.server.address`. En local, Keycloak occupe déjà l'hôte
**8081**.

**Options :** bind localhost / réseau scrape ; mTLS ; ne pas publier 8081 ;
départager `MANAGEMENT_PORT` et `KEYCLOAK_PORT`.

**À produire :** durcissement ops documenté (GETTING-STARTED + runbook) et/ou
changement de défaut yaml.

**Impacte :** `SecurityConfig`, `application.yml`,
`docs/ai/design/2026-08-20-feature-observabilite.md`, `GETTING-STARTED.md`.

**Détail :** [REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md) §4 S-3.

---

### [x] 43. Secrets par défaut et HMAC M2M hors profil local

**Clos** (2026-08-25, `durcissement-securite-post-revue`, D-43 = **F**,
DD-DENYLIST = **L**, [PR #76](https://github.com/superjojo79/FlowForge/pull/76)) —
fail-fast hors `local`/`test` ; denylist vide + `flowforge`/`postgres`/`admin`/
`password` (trim, casse ignorée) ; HMAC M2M interdit hors ces profils.

**Énoncé :** fallback `${DB_PASSWORD:flowforge}` ; Compose Postgres / Keycloak /
MinIO avec mots de passe connus ; `M2M_JWT_SECRET` HMAC HS256 optionnel accepte
des JWT `API_SERVICE` sur `/api/m2m/**`.

**Options :** fail-fast hors `local`/`test` si secrets / issuer absents ;
refuser HMAC hors ces profils ; secrets Compose obligatoires.

**À produire :** décision profil / fail-fast + tests de démarrage.

**Impacte :** `application.yml`, `docker-compose.yml`, `SecurityConfig`.

**Détail :** [REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md) §4 S-4.

---

### [x] 44. SSRF webhook sortant — DNS TOCTOU

**Clos** (2026-08-25, `durcissement-securite-post-revue`, D-44 = **P**,
[PR #76](https://github.com/superjojo79/FlowForge/pull/76)) — pin IP
(`resolveSafe` un lookup, POST URI IP + `Host` / TLS hostname d'origine,
redirects NEVER). Pas de proxy egress.

**Durci (revue post-merge, 2026-08-26) :** le finding « Moyenne » de la
Phase 9 était réel — la JDK `HttpURLConnection` droppe silencieusement le
header `Host` restreint et envoie l'IP pinnée à la place (observé : test
`WebhookRestAdapterUrlConnectionTest` rouge en suite complète, vert en
isolation — la course entre forks Surefire réutilisés déterminait quelle
classe chargeait `HttpURLConnection` en premier). Corrigé par
`sun.net.http.allowRestrictedHeaders=true`, posé au plus tôt à trois
endroits complémentaires (`FlowForgeApplication.main`, static init
`WebhookRestAdapter`, `systemPropertyVariables` Surefire pour les forks de
test) — le pin IP / TOCTOU (D-44 lui-même) n'était pas affecté, seul le
header `Host` du serveur cible l'était. Détail :
`docs/ai/implementation/2026-08-25-feature-durcissement-securite-post-revue.md`
§ Findings.

**Énoncé :** `OutboundUrlGuard` bloque privé/metadata après résolution DNS,
mais le client HTTP n'est pas pinté sur l'IP vérifiée (javadoc explicite).

**Options :** pin IP sur le `HttpClient` ; proxy egress allowlist.

**À produire :** décision + tests de rebinding.

**Impacte :** `OutboundUrlGuard`, `WebhookRestAdapter`,
[09-notifications.md](analysis%20doc/09-notifications.md).

**Détail :** [REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md) §4 S-5.

---

### [x] 45. Dérive documentaire analyse / README / code

**Clos** (2026-08-25, `derive-documentaire`, [PR #75](https://github.com/superjojo79/FlowForge/pull/75)) — chore docs, code = source de vérité. `com.monapp` dans `01` déjà clos PR #72. Ne pas élargir le V1 (DMN, connecteurs SERVICE).

**Énoncé (historique) :** le code suit les décisions 13–15, 17, 27 ; plusieurs docs
décrivaient encore l'ancien modèle ou un périmètre non livré.

Corrigé (liste revue §3.3) :

- `README.md` : tenant instance (`app.tenant.id`) + garde OIDC `tenant_id` ; DMN hors V1 ; catalogue connecteurs V1 vs cible ; stockage port S3 ; jsoup 1.23.1 ; `context-path` cible (yaml commenté)
- `analysis doc/12` : `WORKFLOW_ADMIN` → `/admin/bpmn-editor` (plus « accès au cockpit Flowable »)
- `analysis doc/06` : colonnes Cible / V1 livré
- `analysis doc/00` / `07` : jsoup 1.23.1 ; stockage
- `analysis doc/11` : `context-path` = cible ops
- `application.yml` : commentaire d'en-tête Flowable V1 (plus « until real adapters land »)

**Détail :** [REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md) §3.3.

---

### [x] 46. Couche `application` non gardée par ArchUnit

**Énoncé :** `ManualStartOrchestrator` (`com.flowforge.application`) importe
`infrastructure.workflow.WorkflowStartConfirmer`. ArchUnit ne contraint que
`domain` (pas `application`). Contraint aux conventions
(`infrastructure → domain`, jamais l'inverse) au sens strict.

**Options :** (A) extraire `WorkflowStartConfirmer` derrière un port domaine ;
(B) règle ArchUnit `application` ↛ `infrastructure` + déplacer l'orchestrateur
en infra ; (C) documenter l'exception (orchestration légère assumée).

**Clos (2026-08-31) :** option **A** retenue, complétée par la garde
structurelle envisagée en B (sans déplacer l'orchestrateur) : nouveau port
`WorkflowStartConfirmationPort` (`domain.workflow.port.out`), implémenté par
`WorkflowStartConfirmer` (`infrastructure.workflow`, inchangé sinon).
`ManualStartOrchestrator` et le bean Spring `WorkflowConfig.manualStartOrchestrator`
dépendent désormais du port, plus de la classe infra. Nouvelle règle ArchUnit
`application_never_depends_on_infrastructure` (symétrique à la règle domaine
existante) ajoutée à `ArchitectureRulesTest` — `application` ne compte qu'une
seule classe (`ManualStartOrchestrator`), aucune autre violation trouvée.
`./mvnw verify` vert (ArchUnit + suite complète).

**Impacte :** `ManualStartOrchestrator`, `WorkflowStartConfirmer`,
`WorkflowStartConfirmationPort` (nouveau), `WorkflowConfig`,
`ArchitectureRulesTest`,
[01-architecture-hexagonale.md](analysis%20doc/01-architecture-hexagonale.md).

**Détail :** [REVUE-ALIGNEMENT-SECURITE.md](REVUE-ALIGNEMENT-SECURITE.md) §3.3 / §7.

---

## Processus

### [x] 31. Découpage en lots d'implémentation

**Énoncé initial :** la documentation décrivait une cible, pas un chemin — il n'existait ni lots,
ni séquencement, ni critères d'acceptation.

**Clos (2026-08-20) :** le découpage en lots se fait en pratique, en continu, via le trio
[FILE-ATTENTE.md](FILE-ATTENTE.md) (ordre recommandé + critères de sélection) /
[DEFERRED-FEATURES.md](DEFERRED-FEATURES.md) (inventaire consolidé) / `docs/ai/{requirements,
design, planning, …}` par feature (support `dev-lifecycle`, un lot = un dossier daté) — quatorze
lots lifecycle (`workflow-engine` → `observabilite`) puis six features de suivi livrées à ce jour
(voir `FILE-ATTENTE.md` § Récemment livré, [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md) §2). Pas
de plan statique unique à produire en plus : le séquencement de
[REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md) §9 reste la référence pour l'ordre
initial, `FILE-ATTENTE.md` prend le relais pour la suite et se met à jour à chaque clôture.

**Documenté dans :** [FILE-ATTENTE.md](FILE-ATTENTE.md), [DEFERRED-FEATURES.md](DEFERRED-FEATURES.md).

---

# Décisions actées

> Décisions architecturales et de sécurité identifiées lors de la revue de conception initiale.
> Toutes sont actées et répercutées dans la documentation technique correspondante (`analysis doc/`).

## Sécurité

### [x] 1. Validation du `tenantId` avant injection dans le `search_path`

**Problème :** `SchemaMultiTenantConnectionProvider` concaténait directement le `tenantId` dans la requête SQL `SET search_path TO {tenantId}, public` sans sanitisation.

**Décision retenue :** Double barrière — (1) validation par regex (`^[a-z][a-z0-9_]{2,62}$`) à la création du tenant, alignée sur les contraintes d'identifiant PostgreSQL, et (2) vérification à chaque résolution contre un registre des tenants actifs mis en cache, plus échappement de l'identifiant via `Connection.enquoteIdentifier()` au lieu d'une concaténation brute (défense en profondeur).

> **Mise à jour** — ce mécanisme de bascule dynamique du schéma par requête est remplacé par le
> modèle d'instance dédiée par tenant de la décision n° 13. La validation par regex reste pertinente
> au provisioning (création du schéma) ; le registre des tenants actifs consulté à chaque requête et
> le routage dynamique du `search_path` n'ont plus lieu d'être. Répercuté dans
> [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md).

**Documenté dans :** [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md).

---

### [x] 2. Comportement si le claim `tenant_id` est absent du token OIDC

**Problème :** Le `TenantResolutionFilter` ne rejetait pas la requête si le claim `tenant_id` était absent, et le `TenantIdentifierResolver` tombait sur le fallback `"public"`, rendant le schéma public accessible.

**Décision retenue :** Le `TenantResolutionFilter` rejette la requête (403) si le claim est absent ou vide. Le `TenantIdentifierResolver` ne fait plus de fallback silencieux : il lève une exception si aucun tenant n'est présent dans le contexte. Le schéma `public` reste réservé à un contexte système explicite (jobs internes, migration), jamais atteint via une requête API.

> **Mise à jour** — avec la décision n° 13, le `TenantResolutionFilter` ne sélectionne plus de
> schéma : il vérifie que le claim `tenant_id` du token correspond au tenant configuré de l'instance,
> et rejette (403) sinon ou si le claim est absent. `TenantIdentifierResolver` disparaît avec le SPI
> multi-tenant Hibernate, devenu inutile. Répercuté dans
> [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md).

**Documenté dans :** [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md).

---

### [x] 3. Authentification des webhooks entrants

**Problème :** Le connecteur `http-rest` en mode TRIGGER recevait des webhooks externes sans mécanisme d'authentification documenté.

**Décision retenue :** Signature HMAC-SHA256 par défaut (modèle GitHub/Stripe), secret stocké comme champ `PASSWORD` de la `ConnectorInstance` (donc chiffré au repos comme les autres secrets). Un champ `webhookAuthType` (SELECT : `HMAC_SHA256`, `SHARED_SECRET_TOKEN`, `IP_ALLOWLIST`) permet d'adapter le mécanisme aux capacités du système appelant. Vérification effectuée dans l'adaptateur web (`WebhookController`), avant d'atteindre `ConnectorTriggerRuntime` — c'est un détail HTTP/infrastructure, pas une responsabilité du domaine.

**Documenté dans :** [06-connecteurs.md](analysis%20doc/06-connecteurs.md).

---

### [x] 4. Gestion des clés de chiffrement des secrets de connecteurs

**Problème :** Les champs `PASSWORD` des `ConnectorInstance` sont chiffrés AES-GCM avec une « clé par tenant », mais la gestion de ces clés n'était pas documentée.

**Décision retenue :** Chiffrement en enveloppe — une KEK (Key Encryption Key) unique gérée par un service externe (HashiCorp Vault Transit, Azure Key Vault ou AWS KMS), et une DEK (Data Encryption Key) par tenant, chiffrée par la KEK et stockée en base sous forme chiffrée. La rotation de la KEK est déléguée au vault (versionnage natif) ; la rotation d'une DEK est un job explicite (`RotateTenantEncryptionKeyUseCase`), jamais automatique. Une clé maître unique via variable d'environnement reste un compromis toléré uniquement en mono-tenant/développement, documenté comme limitation temporaire.

**Documenté dans :** [06-connecteurs.md](analysis%20doc/06-connecteurs.md).

---

### [x] 32. Retrait synchronisé des exclusions autoconfig temporaires (Security/OAuth2/Flowable)

**Problème (revue de code, phase architecture-hexagonale) :** `src/main/resources/application.yml`
désactivait Security, OAuth2 client et Flowable via des entrées séparées dans
`spring.autoconfigure.exclude` (3 lignes à elles seules pour la sécurité), documentées comme
temporaires « à retirer feature par feature ». Rien n'empêchait un retrait partiel de ces
exclusions lors de l'implémentation de la feature `authentification` — l'application aurait pu
démarrer sans filtre de sécurité actif sans qu'aucun test ne le détecte.

**Décision retenue :** la feature `authentification` (PR #12) a retiré les 3 lignes Security/OAuth2
d'un seul mouvement, en même temps qu'elle câblait `SecurityConfig` (chaîne BFF `oauth2Login` +
CSRF + `TenantResolutionFilter`). Vérifié en revue de code post-merge : plus aucune exclusion
Security/OAuth2 dans `application.yml` ; `BffSecurityTest` couvre le 401/403 anonyme et mismatch de
tenant, donc un retrait partiel futur (ex. lors du retrait des exclusions JPA/Flowable restantes)
serait immédiatement détecté par la suite de tests existante. Pas de garde dédiée supplémentaire
jugée nécessaire au-delà de cette couverture.

**Documenté dans :** [docs/ai/requirements/2026-07-31-feature-authentification.md](docs/ai/requirements/2026-07-31-feature-authentification.md),
`src/main/java/com/flowforge/infrastructure/security/SecurityConfig.java`.

---

## Architecture

### [x] 5. `ConnectorInstance` — entité JPA ou objet domaine ?

**Problème :** L'entité JPA `ConnectorInstance` était passée directement aux ports `TriggerConnector` et `ServiceConnector`, couplant le domaine à l'infrastructure.

**Décision retenue :** Introduction de `ConnectorInstanceData`, objet domaine pur (config déjà déchiffrée) consommé par les ports `TriggerConnector`/`ServiceConnector`. L'entité JPA est renommée `ConnectorInstanceJpaEntity` et reste confinée à `infrastructure.persistence.connector` ; l'adaptateur assure la conversion (et le déchiffrement) — conforme au pattern déjà appliqué à `WorkflowInstance`/`WorkflowJpaEntity`.

> La généralisation de cette règle aux autres entités du domaine n'a pas été faite — voir le point
> ouvert 17.

**Documenté dans :** [06-connecteurs.md](analysis%20doc/06-connecteurs.md), [01-architecture-hexagonale.md](analysis%20doc/01-architecture-hexagonale.md).

---

### [x] 6. Extensibilité de `WorkflowEventPublisher`

**Problème :** L'interface déclarait une méthode par type d'événement — chaque nouvel état du workflow imposait une modification de l'interface et de tous ses adaptateurs.

**Décision retenue :** Signature générique `void publish(WorkflowEvent event)` avec `WorkflowEvent` en interface scellée (`sealed interface`, Java 21). Un nouvel événement = un nouveau type implémentant l'interface scellée, sans modification de `WorkflowEventPublisher`. Les adaptateurs qui routent par type utilisent un `switch` exhaustif sur les types permis, ce qui conserve la vérification à la compilation malgré la signature générique.

**Documenté dans :** [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md).

---

### [x] 7. Durabilité des événements de déclenchement workflow

**Problème :** Le bus `ApplicationEventPublisher` est synchrone et en mémoire — un crash entre la réception d'un trigger et le démarrage de l'instance Flowable entraînait une perte sans rejeu possible.

**Décision retenue :** Pattern outbox transactionnel — l'événement de trigger est écrit dans une table `outbox_event` (schéma du tenant) dans la même transaction que la création / résolution du `Dossier`, puis un relais (scheduler de polling, avec chemin d'évolution vers Debezium/CDC si le volume l'exige) démarre Flowable et marque l'événement traité. Solution retenue car elle ne nécessite pas de nouvelle brique d'infrastructure immédiate tout en garantissant la durabilité.

> Articulation avec `ConnectorTriggerRuntime` tranchée par la décision n° 16 (option B) : le runtime
> crée/résout le dossier et écrit l'outbox ; le relais réserve le lien, démarre Flowable et confirme.

**Documenté dans :** [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md),
[06-connecteurs.md](analysis%20doc/06-connecteurs.md) (décision n° 16).

---

### [x] 33. Pattern outbox pour les notifications ; `WorkflowEventPublisher` = no-op en production

**Problème :** `09-notifications.md` décrivait un `NotificationDispatcher` synchrone (appel direct
email/webhook/SSE depuis le `ServiceTask` Flowable, retry via Spring Retry `@Retryable`).
L'implémentation livrée a divergé sur les deux points sans jamais repasser par une décision :
`SendNotificationUseCaseImpl` ne fait aucune I/O — il écrit dans `notification_outbox`, et un
relais (`NotificationOutboxRelay`) dispatche réellement avec retry/backoff maison. Par ailleurs
`WorkflowEventPublisher` (décision n° 6) n'a **aucune** implémentation de production câblée sur les
notifications : le bean réel est `NoOpWorkflowEventPublisher`, volontairement inerte.

**Décision retenue :**
- **Notifications = outbox transactionnel**, symétrique à la décision n° 7 (déclenchement workflow) :
  écriture dans `notification_outbox` dans la même transaction que l'action métier, puis relais de
  polling avec `attempts`/backoff. Retenu pour la même raison que la décision n° 7 — durabilité sans
  brique d'infra supplémentaire — et pour uniformiser les deux chemins outbox du projet. La
  dépendance `spring-retry` (jamais utilisée, `@Retryable` absent du code) est retirée du `pom.xml` ;
  le retry reste porté par le relais, pas par une annotation Spring.
- **`WorkflowEventPublisher` reste no-op en V1.** Les notifications déclenchées par un changement
  d'état workflow passent par un `ServiceTask` explicite du process BPMN
  (`notificationDispatchTask`), pas par un listener générique sur `WorkflowEvent`. Le point
  d'extension de la décision n° 6 (signature générique `publish(WorkflowEvent)`) reste disponible si
  un futur besoin (audit trail générique, intégration tierce) justifie un abonné réel — non requis
  en V1.

**Documenté dans :** [09-notifications.md](analysis%20doc/09-notifications.md) (mis à jour pour
refléter l'outbox au lieu du dispatcher synchrone d'origine).

---

### [x] 8. Validation des champs dynamiques des dossiers

**Problème :** `Dossier.fields` n'était pas validé contre le schéma `FieldDefinition` du `DossierType` associé.

**Décision retenue :** Validation aux deux niveaux — couche REST pour le retour d'erreurs par champ (400) à des fins d'UX, et use case (`CreateDossierUseCase`/`CreateDossierBatchUseCase`) comme garde-fou faisant autorité, car les triggers/imports batch contournent la couche REST. Un service domaine pur `DossierFieldValidator` vérifie les champs requis, la conformité de type, et les valeurs `SELECT` hors énumération. Les champs inconnus sont rejetés par défaut, avec un indicateur `allowUnknownFields` sur `DossierType` pour les cas de compatibilité ascendante volontaire.

**Documenté dans :** [04-dossiers.md](analysis%20doc/04-dossiers.md).

---

### [x] 9. Duplication `usecase/` et `port/in/` dans le domaine dossier

**Problème :** La structure de packages de `04-dossiers.md` listait les mêmes noms dans `domain/dossier/usecase/` et dans `domain/dossier/port/in/`, incohérent avec `01-architecture-hexagonale.md`.

**Décision retenue :** Convention uniformisée : `port/in/` contient uniquement les interfaces, `usecase/` contient les implémentations suffixées `Impl` (ex. `CreateDossierUseCaseImpl` implémente `CreateDossierUseCase`). Appliqué à tous les modules du domaine (`workflow`, `document`, `connector`, `notification`, `dossier`) et vérifié en revue de code.

**Documenté dans :** [04-dossiers.md](analysis%20doc/04-dossiers.md).

---

## Opérationnel

### [x] 10. Stratégie de migration Liquibase multi-tenant

**Problème :** Le choix de Liquibase était acté mais la stratégie opérationnelle (onboarding, migration de masse, erreurs partielles) n'était pas documentée.

**Décision retenue :** Deux contextes Liquibase — `tenant-init` (changelog complet exécuté une fois à la création d'un schéma) et `tenant-migration` (changesets incrémentaux appliqués à tous les schémas existants lors d'un déploiement). Exécution via un Job Kubernetes dédié (hook pre-upgrade), qui boucle sur les tenants actifs avant le démarrage des nouveaux pods applicatifs — évite toute migration concurrente entre plusieurs instances. Une table de contrôle `platform.tenant_migration_status` isole les échecs par tenant (statut `MIGRATING_ERROR`, requêtes API bloquées en 503 pour ce tenant uniquement) sans affecter les autres tenants ; le Job est idempotent et rejouable sur les seuls tenants en échec.

> **Mise à jour** — avec la décision n° 13, chaque instance ne migre que son propre schéma à son
> propre démarrage ; le Job Kubernetes orchestrant plusieurs schémas avant le démarrage de pods
> génériques n'est plus nécessaire par défaut. Le suivi par tenant (`platform.tenant_migration_status`)
> et les contextes `tenant-init`/`tenant-migration` restent des idées valables, en version simplifiée
> (une instance n'écrit plus que sa propre ligne, sans coordination inter-schéma). Répercuté dans
> [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md).

**Documenté dans :** [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md).

---

### [x] 11. Scaling horizontal et scheduler Flowable Community

**Problème :** Risque perçu de double exécution des jobs Flowable en déploiement multi-instances Kubernetes.

**Décision retenue :** Le job executor de Flowable Community gère nativement le verrouillage optimiste au niveau ligne (`lockOwner`/`lockExpirationTime`) et supporte le clustering sans contrainte d'instance unique. La configuration des délais de verrou (`lockTimeInMillis`, `defaultAsyncJobAcquireWaitTime`) est validée par un test de charge au niveau cible. Si la contention devient un problème à l'échelle visée, séparation opérationnelle entre pods API (`asyncExecutorActivate: false`) et pods worker (`asyncExecutorActivate: true`, eux-mêmes réplicables pour la haute disponibilité), plutôt qu'une limitation à une seule instance globale.

> **Mise à jour** — avec la décision n° 13, un tenant occupe par défaut une seule instance : aucune
> contention entre pods d'un même tenant n'est possible tant qu'il n'est pas scalé. Le repli
> « pods API / pods worker » ne redevient pertinent que dans le pool de répliques d'un tenant scalé,
> jamais entre tenants. Répercuté dans
> [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md).

**Documenté dans :** [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md).

---

### [x] 12. Fichier `architecture-technique v7.md` — référence ou archive ?

**Problème :** Le fichier `architecture-technique v7.md` coexistait avec les 11 fichiers thématiques numérotés qui en sont le découpage, sans qu'il soit clair lequel faisait référence.

**Décision retenue :** `00-index.md` couvre intégralement et sans écart les 11 fichiers thématiques (déjà la table des matières utilisée par le `README.md` du dépôt). `architecture-technique v7.md` est supprimé du suivi ; les fichiers numérotés de `analysis doc/` sont l'unique source de vérité.

**Documenté dans :** suppression effective du fichier ; voir [00-index.md](analysis%20doc/00-index.md).

---

### [x] 13. Modèle de déploiement multi-tenant — instance dédiée par tenant

**Problème :** `TenantContext` (bean `@RequestScope`) et le resolveur strict introduit par la décision n° 2 rendent inopérants tous les composants qui s'exécutent hors requête HTTP — relais outbox, `JavaDelegate` Flowable exécutés en asynchrone, listeners d'événements, job de migration. Le sujet était renvoyé à `ScopedValue`, resté en *preview* jusqu'à Java 25.

**Décision retenue :** Abandon du modèle « pods génériques + résolution dynamique du tenant par requête ». Chaque tenant est désormais servi par sa **propre instance Spring Boot**, démarrée séparément (port ou serveur distinct), configurée avec son identifiant de tenant en dur au démarrage — une propriété de configuration lue une fois, jamais une donnée résolue par requête. Toutes les instances partagent la même base PostgreSQL multi-schéma (l'isolation par schéma, décision n° 1, ne change pas), mais chaque instance n'ouvre jamais qu'une connexion vers son propre schéma. Le routage vers la bonne instance (sous-domaine, port ou chemin) est délégué à un reverse-proxy, pas à l'application. Le mode « instance mutualisée servant plusieurs tenants dynamiquement » est abandonné — aucun chemin alternatif n'est conservé. Le scaling horizontal d'un tenant donné (plusieurs répliques pour un seul tenant) reste un chemin d'évolution documenté, activé au cas par cas, et ne réintroduit alors les problématiques de session/SSE partagées et de verrouillage Flowable que dans le pool de ce tenant.

Cette décision rend obsolète le mécanisme dynamique décrit par les décisions n° 1, 2, 10 et 11 (voir notes ajoutées sous chacune).

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md), [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md), [06-connecteurs.md](analysis%20doc/06-connecteurs.md), [09-notifications.md](analysis%20doc/09-notifications.md), [04-dossiers.md](analysis%20doc/04-dossiers.md), [08-templates-documents.md](analysis%20doc/08-templates-documents.md) et le `README.md`.

---

### [x] 14. Résolution du tenant pour les appels non authentifiés — routage par chemin + `client_credentials` par tenant

**Problème :** le `WebhookController` est appelé par des systèmes tiers sans token OIDC — donc rejeté en 403 par le `TenantResolutionFilter` d'origine — et commençait par une requête JPA qui avait besoin du tenant pour lire la ligne qui contient justement le tenant : résolution circulaire. Le même trou valait pour l'appel machine-to-machine `/api/connectors/{id}/execute` associé au rôle `API_SERVICE`, sans flux `client_credentials` décrit.

**Décision retenue :** Routage par **chemin** — chaque tenant est adressé sous un préfixe d'URL qui lui est propre (`https://example.com/{tenantId}/...`), identique au `server.servlet.context-path` de son instance dédiée (décision n° 13). La configuration du reverse-proxy est **statique** (Apache `mod_proxy`/`mod_rewrite`, un bloc par tenant ajouté au provisioning), sans annuaire consulté dynamiquement à chaque requête. Ce mécanisme sert uniformément le trafic humain (SPA, redirection OAuth2), les webhooks et l'API machine-to-machine — la requête est déjà routée vers l'instance du bon tenant avant d'atteindre l'application, ce qui lève la circularité initiale.

Pour l'API machine-to-machine, un flux OAuth2 **`client_credentials`** est ajouté : un client Keycloak dédié par tenant, provisionné avec le reste de l'onboarding, dont un protocol mapper attache un claim `tenant_id` fixe à tout token émis. Une chaîne de sécurité Spring distincte (`securityMatcher` sur les endpoints machine-to-machine), en *resource server* JWT, applique la même vérification d'identité que le flux humain — le `tenant_id` du token doit correspondre au tenant configuré de l'instance (`TenantProperties`), sinon rejet.

Effet de bord : le comportement pour un tenant en `MIGRATING_ERROR` est résolu par construction — une instance dont la migration échoue au démarrage (décision n° 13) ne devient jamais disponible, donc n'est jamais atteinte par le routage, sans logique 503 dédiée à écrire côté application.

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [02-multi-tenant.md](analysis%20doc/02-multi-tenant.md) (routage), [03-authentification.md](analysis%20doc/03-authentification.md) (`client_credentials`), [06-connecteurs.md](analysis%20doc/06-connecteurs.md) (`WebhookController`, `ConnectorExecutionController`) et [12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

### [x] 15. Sécurité et cycle de vie de la session BFF — CSRF, déconnexion, session par tenant

**Problème :** le pattern BFF avec cookie de session était retenu et justifié, mais aucune de ses conséquences directes n'était traitée. Spring Security active la protection CSRF par défaut — sans configuration, toutes les requêtes mutantes de la SPA échoueraient en 403. Le `logout` documenté se limitait à `session.invalidate()`, laissant la session du fournisseur OIDC ouverte et permettant une reconnexion silencieuse de l'utilisateur.

**Décision retenue :** Jeton CSRF porté par un cookie lisible en JavaScript (`CookieCsrfTokenRepository`, pattern double-submit), avec `SameSite=Lax` — hypothèse confirmée par la décision n° 27 (frontend embarqué dans `static/`, même origine) ; un filtre force la résolution du token à chaque requête, contournant le rendu paresseux introduit par Spring Security 6 (comportement toujours présent sous Spring Boot 4 / Security 7). Webhooks et API machine-to-machine ne sont pas exemptés via `ignoringRequestMatchers` mais isolés dans leurs propres chaînes de sécurité (`securityMatcher`, sans session ni CSRF), au même titre que la chaîne `client_credentials` de la décision n° 14 — trois chaînes ordonnées (`@Order`), partitionnées, jamais traversées simultanément par une même requête.

Déconnexion via `OidcClientInitiatedLogoutSuccessHandler`, qui appelle l'`end_session_endpoint` du fournisseur avec l'`id_token_hint` pour fermer la session Keycloak en plus de la session locale — la reconnexion silencieuse disparaît. Effet de bord identifié en corrigeant ce point : le routage par chemin de la décision n° 14 fait varier l'URL de callback OAuth2 et de post-logout par tenant ; une entrée à caractère joker (`https://example.com/*`) côté client Keycloak partagé couvre tous les tenants sans provisioning supplémentaire par tenant.

La session partagée entre répliques reste hors périmètre de la V1 (déjà tranché par la décision n° 13) — non requise par défaut, à réexaminer seulement si un tenant précis est scalé sur plusieurs répliques.

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [03-authentification.md](analysis%20doc/03-authentification.md) (protection CSRF, déconnexion, chaînes de sécurité), [11-frontend.md](analysis%20doc/11-frontend.md) (renvoi vers la configuration Axios).

---

### [x] 17. Entités JPA dans le domaine — règle appliquée à toutes les entités

**Problème :** la décision n° 5 avait séparé `ConnectorInstanceData` de son entité de persistance, mais `Dossier`, `DossierType`, `DossierElement`, `DossierRelation`, `DossierWorkflowLink`, `WorkflowTypeConfig` et `DocumentTemplate` restaient annotés `@Entity` tout en étant déclarés dans le domaine et exposés dans les signatures des ports. `GetDossierWorkflowHistoryUseCase` allait jusqu'à renvoyer une entité de persistance comme corps de réponse d'API.

**Décision retenue :** La règle est appliquée strictement, sans exception, aux sept classes concernées. Le modèle domaine garde son nom métier naturel (`Dossier`, `DossierType`…) ; l'entité de persistance reçoit le suffixe `JpaEntity` (`DossierJpaEntity`…) — le principe déjà illustré par `WorkflowInstance`/`WorkflowJpaEntity`. `ConnectorInstanceData`/`ConnectorInstanceJpaEntity` reste une exception de nommage historique de la décision n° 5 (le nom lui-même n'est pas retouché), mais sa forme de déclaration est alignée sur la nouvelle convention.

Le modèle domaine est un `record` Java. Un `record` avait d'abord été écarté au profit de `@Value @Builder` (Lombok) au motif qu'il génère des accesseurs sans préfixe, incompatibles avec le style déjà utilisé dans des extraits de code existants (`type.isAllowUnknownFields()`, `type.getFieldDefinitions()`). **Cet argument a été corrigé** : le projet est en phase d'analyse, sans aucune implémentation — il n'existe pas de code réel dont les appelants seraient figés, et les extraits illustratifs de `analysis doc/` s'éditent au même titre que le reste de cette documentation. Une fois ce coût artificiel écarté, `record` est le choix le mieux justifié (fonctionnalité native du JDK, immuabilité garantie par le langage, cohérent avec l'usage déjà fait de Java 21 moderne — `sealed interface` de la décision n° 6 — et déjà utilisé ailleurs dans ces mêmes documents, `WorkflowDiagramView`). `@Builder` (Lombok) reste utilisé au-dessus du `record` pour la construction des objets à de nombreux champs, sans renoncer aux accesseurs natifs ni à l'immuabilité ; une transition d'état (`Dossier.withStatus(...)`) s'implémente comme une méthode d'instance retournant une nouvelle instance via le constructeur canonique. `ConnectorInstanceData` a été alignée sur cette même convention pour ne pas laisser sa forme de déclaration seule diverger.

Corollaires actés dans le même mouvement : le risque concret de fuite d'un proxy Hibernate en réponse REST (`GetDossierWorkflowHistoryUseCase`) disparaît par construction ; le découplage plus général du contrat d'API reste du ressort du point 24. La règle de dépendance du domaine (et la convention `usecase`/`Impl` de la décision n° 9) est désormais vérifiée par un test ArchUnit en CI plutôt que confiée à la seule revue de code.

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [01-architecture-hexagonale.md](analysis%20doc/01-architecture-hexagonale.md) (règle généralisée, convention de nommage, section ArchUnit), [04-dossiers.md](analysis%20doc/04-dossiers.md), [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md), [06-connecteurs.md](analysis%20doc/06-connecteurs.md) (`ConnectorInstanceData` alignée), [08-templates-documents.md](analysis%20doc/08-templates-documents.md) (entités converties) et [10-visualisation-workflow.md](analysis%20doc/10-visualisation-workflow.md) (note sur la réponse d'API).

---

### [x] 27. Versions figées, structure du build et packaging du frontend

**Problème :** les versions de stack n'étaient pas épinglées (`Flowable 7.x.x`, `Spring Boot 3.x`, Java 21) ; les seules versions figées (`flying-saucer-pdf-openpdf 9.9.4`, `jsoup 1.17.2`) dataient ; ni structure Maven ni packaging du frontend n'étaient définis — ce dernier point conditionnait CORS / `SameSite` / CSRF de la décision n° 15.

**Décision retenue — trois orientations :**

1. **Versions de stack.** Java **25** LTS · Spring Boot **4.1.x** · Flowable **8.0.x** · `flying-saucer-pdf` **10.4.x** (remplace `flying-saucer-pdf-openpdf`, abandonné) · jsoup **1.22.x** · Keycloak **26.7.x**. Points de vigilance actés : Jackson 3 est le défaut de Flowable 8 (compatibilité Jackson 2 encore disponible via `flowable.variable-json-mapper=jackson2` si besoin) ; l'ordre d'initialisation Liquibase / Flowable sous Boot 4 doit être vérifié dès le premier démarrage d'intégration.

2. **Build mono-module Maven.** Un seul module applicatif. La règle « domaine pur » (décision n° 17) est déjà garantie par ArchUnit en CI — un module `domain` séparé n'apporterait qu'une redondance au niveau du build, au prix d'une découpe figée trop tôt.

3. **Frontend embarqué.** Le build Vue 3 est servi depuis `src/main/resources/static/` de chaque instance Spring Boot dédiée (décision n° 13). Même origine API / SPA → confirme `SameSite=Lax` sans CORS de la décision n° 15. Chaque instance embarque le même artefact front (N copies identiques) — acceptable au regard de la simplicité opérationnelle.

> **Mise à jour** — deux angles morts de l'analyse initiale du frontend ont été comblés lors d'une
> revue technique ultérieure : (1) absence de typage statique, corrigée par l'adoption de
> **TypeScript** (`vue-tsc --noEmit` en CI, gabarit `vue-ts`) — d'autant plus pertinent que le point
> ouvert n° 24 (contrat OpenAPI vis-à-vis du frontend) ouvre la voie à un client généré typé ; (2)
> absence d'outillage qualité, corrigée par l'ajout de **Vitest** (unitaire/composants), **Playwright**
> (e2e) et **ESLint/Prettier** (`@vue/eslint-config-typescript`) comme gate CI, sur le même principe que
> `./mvnw verify` côté backend. Répercuté dans [11-frontend.md](analysis%20doc/11-frontend.md) et
> [README.md](README.md).

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [00-index.md](analysis%20doc/00-index.md), [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md), [07-generation-documents.md](analysis%20doc/07-generation-documents.md), [03-authentification.md](analysis%20doc/03-authentification.md) (`SameSite` confirmé), [11-frontend.md](analysis%20doc/11-frontend.md) (packaging) et [README.md](README.md).

---

### [x] 16. Outbox transactionnel et `ConnectorTriggerRuntime` — option B

**Problème :** la décision n° 7 posait un outbox, tandis que `06`/`04` décrivaient un `ConnectorTriggerRuntime` entièrement synchrone (dossier → réserve → Flowable → confirme) sans outbox. Les deux séquences étaient incompatibles ; le pseudo-code du relais marquait en outre l'événement traité même après échec de publication.

**Décision retenue — option B (outbox après création/résolution du dossier) :**

1. **Phase producteur** (`ConnectorTriggerRuntime.onIncomingEvent`, synchrone, transaction unique) : authentification déjà faite par l'adaptateur web → `TriggerConnector.onEvent(...)` → pour chaque événement, créer le dossier **ou** résoudre `existingDossierReference` → écrire `outbox_event` (payload normalisé + `dossierId` + `connectorInstanceId`) → ACK webhook. **Jamais** de démarrage Flowable ici.

2. **Phase relais** (`@Scheduled`, transaction dédiée par événement) : lire les pending → réserver le `DossierWorkflowLink` (ou reprendre un lien `RESERVED` existant au rejeu) → démarrer Flowable → `confirmStarted` → `markProcessed` **uniquement en cas de succès, dans la même transaction**. Échec → incrément attempts, retry ; au-delà du plafond → dead-letter. Purge des événements `PROCESSED` au-delà d'une rétention configurable.

3. **Contenu de l'outbox :** `WorkflowTriggerEvent` déjà normalisé + `dossierId` (pas le payload brut) — la transformation n'est pas rejouable si elle échoue avant l'écriture (compromis accepté de l'option B).

4. **Idempotence au rejeu :** le relais ne recrée jamais le dossier ; si un lien `RESERVED`/`ACTIVE` existe déjà pour ce dossier + process, il le reprend. `WorkflowAlreadyRunningException` sur un autre workflow actif → dead-letter (le dossier existe déjà, l'ACK a été donné).

5. **`FOR UPDATE SKIP LOCKED` :** uniquement si le tenant est scalé sur plusieurs répliques (décision n° 13) ; pas une exigence du cas par défaut mono-instance.

**Écartées :** A (tout synchrone — rouvre n° 7), C (outbox du brut — ACK avant validation métier), D (hybride par source — double chemin).

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [05-workflow-engine.md](analysis%20doc/05-workflow-engine.md) (outbox + relais corrigés), [06-connecteurs.md](analysis%20doc/06-connecteurs.md) (`ConnectorTriggerRuntime` scindé) et [04-dossiers.md](analysis%20doc/04-dossiers.md) (création batch alignée).

---

### [x] 18. Résidus de refactoring et références orphelines

**Problème :** après les décisions n° 14–17, trois écarts ponctuels empêchaient d'utiliser les extraits comme spécification : `getWebhookAuthType()` inexistant sur `ConnectorInstanceData` ; `DocumentGenerationService` / `DocumentStorageService` absents de l'hexagone ; `updatedBy` affirmé par `12` mais absent de `Dossier` (et mauvaise attribution à `DossierElement`).

**Décision retenue — c1 + e1 + f1 :**

1. **c1 — `webhookAuthType` depuis `config`.** Lecture `WebhookAuthType.valueOf((String) instance.config().get("webhookAuthType"))` dans `WebhookController` — cohérent avec le `ConfigField` SELECT déjà documenté. Pas de champ de premier niveau sur le record.

2. **e1 — Aligner `08` sur l'hexagone.** `DocumentGenerationTask` injecte `GenerateDocumentUseCase` et `DocumentStoragePort` (définis dans `01` / `07`), plus de « Services » orphelins.

3. **f1 — `updatedBy` sur `Dossier`.** Champ ajouté au record, à l'entité JPA et aux mappers ; `withStatus` le propage. Pour `DossierElement`, `12` est corrigé : audit = `addedBy` / `addedAt` (pas `createdBy`/`updatedBy` — un élément est ajouté, pas mis à jour).

(Les résidus a/b/d/g étaient déjà corrigés par les décisions n° 14 et 16.)

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [06-connecteurs.md](analysis%20doc/06-connecteurs.md), [08-templates-documents.md](analysis%20doc/08-templates-documents.md), [04-dossiers.md](analysis%20doc/04-dossiers.md), [12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md).

---

### [x] 20. Contrôle d'accès au niveau type de dossier — `AccessContext` + `dossier_type_acl` + policy

**Problème :** le contrôle de rôle ne protège pas contre l'accès aux dossiers d'un type hors périmètre de l'utilisateur. L'esquisse `DossierAccessPort` ne distinguait pas appelant humain et système. Une première formulation de B2 posait une ACL **par instance** de dossier — amendée au profit d'une ACL **par `DossierType`**.

**Décision retenue — A2 + B2 (amendée) + C1 :**

1. **A2 — `AccessContext`.** Inchangé : `User` (ACL) · `System` (contournement) · `Service` (refus dossier). `DossierAwareTask` / `ConnectorTriggerRuntime` passent `AccessContext.system(...)`.

2. **B2 — table `dossier_type_acl`.** Source de vérité pour `DOSSIER_MANAGER` / `DOSSIER_VIEWER` : `(tenant_id, dossier_type_code, subject_user_id, permission)` avec `permission ∈ {VIEW, EDIT}` (`EDIT` ⇒ `VIEW`). Un grant sur le type s'applique à **toutes** les instances. Administration via `GrantDossierTypeAccessUseCase` / `RevokeDossierTypeAccessUseCase` (admins tenant). Création de dossier (humain) exige `EDIT` sur le type ; liste/recherche filtrée aux types `VIEW`. Pas de grant par instance, ni à la création, ni à l'assignation Flowable. `TENANT_ADMIN` / `WORKFLOW_ADMIN` contournent. Table à intégrer au point 25. Couvre en pratique la restriction « rôle × sous-ensemble de `DossierType` » du point 19.

3. **C1 — `DossierAccessPolicy`.** Service domaine ; `DossierTypeAclRepository` ; évaluation sur `dossierTypeCode` (directement ou via le dossier chargé). Refus → `AccessDeniedException` → 403 (point 24).

**Écartées / abandonnées :** ACL par instance de dossier (première B2) ; A1, A3, B1, B3, C2.

**Documenté dans :** [REVUE-PRE-IMPLEMENTATION.md](REVUE-PRE-IMPLEMENTATION.md), section 3, et répercuté dans [12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md), [04-dossiers.md](analysis%20doc/04-dossiers.md) et [06-connecteurs.md](analysis%20doc/06-connecteurs.md).

---

### [x] 34. ACL type de dossier sur le panneau dossier de la visualisation workflow

**Problème :** `GET /api/workflow-links/{linkId}/dossier` (feature `visualisation-workflow`,
décision DD-DOSSIER-LOAD) chargeait le dossier après authz « participant workflow ou
TENANT_ADMIN/WORKFLOW_ADMIN » **sans** repasser par `DossierAccessPolicy` (décision n° 20) —
motivé à l'origine par l'évitement d'une double vérification d'ACL (A10). Un simple participant ou
observer d'un workflow, sans entrée `dossier_type_acl` de niveau VIEW, pouvait donc lire
l'intégralité des champs métier du dossier — un modèle d'accès incohérent entre les deux portes
d'entrée pour lire le même dossier (`GetDossierUseCase` vs panneau viz), relevé par l'audit
sécurité de cette session.

**Décision retenue :** revenir sur DD-DOSSIER-LOAD/A10 — `WorkflowLinkVisualizationUseCaseImpl.getDossier`
appelle désormais `DossierAccessPolicy.assertCanView(dossier, actor)` en plus du contrôle
participant. `TENANT_ADMIN`/`WORKFLOW_ADMIN` continuent de contourner (comme partout ailleurs) ;
un participant/observer sans ACL VIEW garde l'accès au diagramme/historique/tâches (aucune donnée
métier du dossier) mais reçoit 403 sur le panneau dossier.

**Documenté dans :** [12-autorisation-conformite.md](analysis%20doc/12-autorisation-conformite.md),
[10-visualisation-workflow.md](analysis%20doc/10-visualisation-workflow.md).

