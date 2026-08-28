---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: complete
created: 2026-08-27
updated: 2026-08-27
feature: serveur-mcp
phase1: 2026-08-27
phase3: 2026-08-27
phase4: 2026-08-27
phase5: 2026-08-27
phase7: 2026-08-27
phase9: 2026-08-27
---

# Implementation Guide — `serveur-mcp`

Phase 5 TDD démarrée (plan Phase 4 :
`docs/ai/planning/2026-08-27-feature-serveur-mcp.md`). Cible design :
`docs/ai/design/2026-08-27-feature-serveur-mcp.md`.

## M1 — Gardes domaine `API_SERVICE` (fait, 2026-08-27)

Fichiers touchés :

- `domain/dossier/DossierAccessPolicy.java` — `hasPermission` : ajout
  `user.hasRole("API_SERVICE")` au fork TA/WA (VIEW/EDIT tenant-wide).
- `domain/reporting/SlaReportAccess.java` — `allowedTypeCodes` : idem
  → `null` (unrestricted) pour `API_SERVICE`.
- `domain/dossier/TenantAdminGuard.java` — nouvelle méthode
  `requireTenantAdminOrApiService` ; `requireTenantAdmin` **inchangée**
  (purge / ACL / rétention / `TenantSlaSettings` continuent de la
  refuser à `API_SERVICE`, non-régression T1.4 sans changement de code).
- `domain/dossier/usecase/CreateDossierTypeUseCaseImpl.java` et
  `UpdateDossierTypeUseCaseImpl.java` — appellent désormais
  `requireTenantAdminOrApiService`.
- `domain/dossier/usecase/SearchDossiersUseCaseImpl.java` et
  `ListDossierTypesUseCaseImpl.java` — `API_SERVICE` ajouté au fork
  unrestricted (**DD-POLICY-TRIPLE**).
- Nouveau test `domain/reporting/SlaReportAccessTest.java` (pas de test
  dédié existant pour cette classe utilitaire statique).
- Tests étendus : `DossierAccessPolicyTest`, `TenantAdminGuardTest`,
  `CreateDossierTypeUseCaseImplTest`, `UpdateDossierTypeUseCaseImplTest`,
  `SearchDossiersUseCaseImplTest`, `ListDossierTypesUseCaseImplTest`.

Aucune déviation par rapport au design.

## M2 — Audit auteurs (fait, 2026-08-27)

Fichiers touchés :

- `db/changelog/changes/017-mcp-audit-authors.yaml` (+ include master) :
  `created_by`/`updated_by` varchar(128) NOT NULL sur `dossier_type` et
  `workflow_type_config`, backfill `'system'`, pattern `addColumn` +
  `sql` UPDATE + `addNotNullConstraint` (comme 015).
- `domain/dossier/model/DossierType.java` — record étendu à 13
  composants (`createdBy`, `updatedBy`). `inherit(...)` (8 args,
  ~20 sites de test) inchangé en signature, stampe `"system"` en
  interne. `withUpdate(...)` gagne un paramètre `updatedBy`, préserve
  `createdBy` existant.
- `domain/dossier/usecase/CreateDossierTypeUseCaseImpl.java` /
  `UpdateDossierTypeUseCaseImpl.java` — stampent `actor.userId()`.
  Aucun changement REST : `AccessContextFactory.user(...)` extrayait
  déjà `OidcUser.getSubject()` comme `userId`.
- `infrastructure/persistence/dossier/DossierTypeJpaEntity.java` —
  colonnes + mapping `createdBy`/`updatedBy`.
- 8 fichiers de test avec appel direct au constructeur 11-arg de
  `DossierType` (hors `inherit`) mis à jour avec `"system", "system"`
  littéraux (pas d'impact métier, ces tests ne portent pas sur l'audit).
- `domain/workflow/model/WorkflowTypeConfig.java` — record étendu à 10
  composants. **Constructeur canonique** requiert `createdBy`/
  `updatedBy` non-null ; un **second constructeur** (les 8 anciens
  paramètres) délègue avec `"system"/"system"` — décision prise pour
  éviter de toucher ~20 fichiers de test qui construisent ce record
  directement (pas de factory `inherit` préexistante ici, contrairement
  à `DossierType`).
- `infrastructure/persistence/workflow/WorkflowTypeConfigJpaEntity.java`
  — colonnes + mapping.
- `infrastructure/web/workflow/WorkflowApiDtos.java` —
  `WorkflowTypeConfigRequest.toDomain(...)` gagne `createdBy`/
  `updatedBy` en paramètres explicites.
- `infrastructure/web/workflow/WorkflowTypeConfigController.java` —
  `create` : `accessContextFactory.user(authentication).userId()` comme
  `createdBy`/`updatedBy` (remplace `requireOidcPrincipal`, sémantique
  équivalente + extraction du subject). `update` : préserve
  `existing.createdBy()`, stampe `updatedBy` avec l'acteur courant.

**Déviation mineure vs design** : `SaveWorkflowTypeConfigUseCase`
(`create`/`update`) n'a **pas** gagné de paramètre acteur — le
`WorkflowTypeConfig` passé en argument est déjà entièrement stampé par
l'appelant (contrôleur REST aujourd'hui, tool MCP `define_workflow`/
`deploy_workflow` en M6). Plus simple, testable au même niveau, et
conforme à l'esprit de la note design ("ajouter updatedBy... appelé
depuis le contrôleur REST et le tool MCP").

## M3 — Compatibilité schéma (fait, 2026-08-27)

Fichiers touchés :

- `domain/dossier/port/out/DossierRepository.java` — nouvelle méthode
  `findByDossierTypeCode(tenantId, dossierTypeCode)`.
- `infrastructure/persistence/dossier/SpringDataDossierRepository.java`
  — dérivée `findByTenantIdAndDossierTypeCode`.
- `infrastructure/persistence/dossier/JpaDossierRepositoryAdapter.java`
  — implémentation.
- `domain/dossier/port/in/CheckDossierTypeSchemaCompatibilityUseCase.java`
  (nouveau) — `Query(tenantId, dossierTypeCode, newFieldDefinitions,
  allowUnknownFields)` / `Result(incompatibleCount, sampleReferences)`.
- `domain/dossier/usecase/CheckDossierTypeSchemaCompatibilityUseCaseImpl.java`
  (nouveau) — charge tous les dossiers du type (pas de cap V1, conforme
  design), valide chacun avec `DossierFieldValidator.validate(newFields,
  dossier.fields(), allowUnknown)`, compte les échecs, échantillonne les
  5 premières références incompatibles.
- `ArchitectureRulesTest.DOSSIER_FIELDS_READ_ALLOWLIST` — ajout de
  `CheckDossierTypeSchemaCompatibilityUseCaseImpl` (seul nouveau membre,
  conforme design ; fait maintenant plutôt qu'en T8.1 car le code ne
  compile/passe pas la suite ArchUnit sinon).

Aucune déviation par rapport au design. `DefineDossierTypeTool` (T6.1)
appellera ce use case avant l'update pour bloquer un changement de
schéma incompatible.

## M4 — SDK Spring AI + transport (fait, 2026-08-27)

Fichiers touchés :

- `pom.xml` — `dependencyManagement` import `spring-ai-bom:2.0.0` ;
  dépendance `spring-ai-starter-mcp-server-webmvc` (pas de starter
  chat/model). **Preuve runtime (open item design #1)** : le BOM
  résout sans réseau restreint via le proxy agent ; la classe
  `org.springframework.ai.chat.client.ChatClient` n'existe même pas
  dans les jars résolus (`Class.forName` lève `ClassNotFoundException`)
  — plus fort que « pas de bean », **DD-SDK-FALLBACK non nécessaire**.
- `application.yml` — bloc `spring.ai.mcp.server` : `protocol:
  STREAMABLE`, `type: SYNC`, `capabilities.tool: true` (resource/
  prompt/completion `false`), `annotation-scanner.enabled: true`,
  `streamable-http.mcp-endpoint: /api/m2m/mcp`. Propriété
  `tool-callback-converter` du brouillon Phase 3 **n'existe pas** dans
  les métadonnées de config 2.0.0 réelles — omise (le module
  `ToolCallbackConverterAutoConfiguration` s'active par défaut).
  Aucun changement `SecurityConfig` : `/api/m2m/mcp` tombe déjà sous
  le matcher `/api/m2m/**` de la chaîne M2M Order 1 existante.
- `infrastructure/mcp/McpAccessContextFactory.java` (nouveau) — JWT
  `sub` → `client_id` → `"api-service"`, toujours rôle `{API_SERVICE}`.
- `infrastructure/mcp/McpServerContextTest.java` (nouveau, UT-CTX-1) —
  bean `io.modelcontextprotocol.server.McpSyncServer` présent + absence
  de `ChatClient` sur le classpath.
  `infrastructure/mcp/McpAccessContextFactoryTest.java` (UT-ACTOR-1).
  `infrastructure/mcp/McpEndpointApiTest.java` (IT-MCP-1 amorce) —
  MockMvc POST `/api/m2m/mcp` avec JWT M2M `API_SERVICE`, requête
  JSON-RPC `initialize`, statut < 400 obtenu **sans** élargir le
  matcher (**A-M2M-PATH non déclenché**).

**Ouvert restant (design #2)** : les noms Java exacts des annotations
2.0 sont confirmés par inspection des jars — `@McpTool` /
`@McpToolParam` (package `org.springframework.ai.mcp.annotation`),
scanner activé par `annotation-scanner.enabled=true`. Utilisés à partir
de M5/M6 pour les 9 tools.

## M5 — Tools lecture (fait, 2026-08-27)

Package `com.flowforge.infrastructure.mcp.tool` créé avec 7 tools
(`@Component` + méthode `@McpTool`, découverte par
`annotation-scanner.enabled=true`) :

- `GetDossierTool` (`get_dossier`) — xor `id`/`reference` ; acteur via
  `McpAccessContextFactory.currentActor()` (lit
  `SecurityContextHolder`, JWT déjà authentifié par la chaîne M2M avant
  que le tool ne s'exécute — WebMVC `type: SYNC`, même thread) ;
  agrège `GetDossierUseCase` (redaction déjà faite en interne via
  `DossierFieldRedactor`, car l'acteur MCP n'a jamais le rôle
  `TENANT_ADMIN`), pagine `ListDossierElements` /
  `GetDossierWorkflowHistory` (size 100 jusqu'à épuisement), appelle
  `ListDossierRelations` une fois. Nouveau DTO `DossierMcpView` (dans
  `infrastructure.mcp.tool`, allowlist ArchUnit `Dossier.fields()`) —
  le tool lui-même ne touche jamais les champs bruts.
- `SearchDossiersTool` (`search_dossiers`), `QuerySlaSummaryTool`
  (`query_sla_summary`) — relais direct vers `SearchDossiersUseCase` /
  `GetSlaSummaryUseCase`, validation déjà portée par les compact
  constructors domaine (`PageQuery`/`SearchDossiersQuery` rejettent
  size > 100).
- `ListDocumentTemplatesTool` (`list_document_templates`) —
  `includeContent=true` rejeté **dans le tool** avant tout appel
  use case (le use case honorerait `true` sans discuter, donc le
  garde-fou MCP doit être une couche au-dessus).
- `GetDossierTypeTool`, `ListDossierTypesTool`, `ListWorkflowTypesTool`
  — relais directs, pas de logique propre.

**Décision non prévue par le design (acteur MCP)** : le design ne
précisait pas comment un `@McpTool` récupère l'identité de l'appelant.
Choix : `McpAccessContextFactory.currentActor()` lit
`SecurityContextHolder.getContext().getAuthentication()`, attend un
`JwtAuthenticationToken` (posé par la chaîne Security M2M Order 1 avant
que la requête n'atteigne le transport MCP), sinon lève
`IllegalStateException`. Validé empiriquement par
`McpEndpointApiTest` (T4.3) : l'authentification est bien active au
moment où le transport traite la requête `initialize`. Sera revalidé
de bout en bout avec un vrai appel de tool en M7 (IT-MCP-2..9).

**Pas de `McpToolExceptionMapper` séparé** (composant envisagé au
design) : les exceptions domaine (`IllegalArgumentException`,
`AccessDeniedException`, `DossierNotFoundException`,
`SlaCohortTooLargeException`, …) sont laissées remonter telles quelles
— le SDK Spring AI MCP convertit une exception non interceptée
provenant d'un tool en résultat `isError=true` avec le message de
l'exception (comportement standard du SDK MCP côté serveur). À
confirmer explicitement par un test d'erreur en M7 ; si le
comportement observé diverge, un mapper dédié sera ajouté à ce
moment-là plutôt que anticipé sans preuve.

## M6 — Tools écriture (fait, 2026-08-27)

Fichiers touchés :

- `domain/dossier/model/DossierTypeSchemaIncompatibleException.java` —
  nouvelle exception domaine (code `SCHEMA_INCOMPATIBLE`, même style
  que `SlaCohortTooLargeException`), levée par le tool (pas par
  `CheckDossierTypeSchemaCompatibilityUseCase`, qui reste un simple
  compteur réutilisable pour d'autres appelants futurs).
- `infrastructure/mcp/tool/McpArgs.java` (nouveau) — petit utilitaire
  partagé `requireString`/`requireBoolean` sur `Map<String,Object>`,
  extrait de la première version de `DefineDossierTypeTool`.
- `infrastructure/mcp/tool/DefineDossierTypeTool.java` — `define_dossier_type`.
- `infrastructure/mcp/tool/DeployWorkflowTool.java` — `deploy_workflow`.
- `infrastructure/config/DossierConfig.java` — **bean manquant ajouté** :
  `CheckDossierTypeSchemaCompatibilityUseCase` (créé en M3 mais jamais
  câblé comme `@Bean` — l'omission ne s'est révélée qu'ici, au premier
  point où quelque chose l'injecte réellement via le conteneur Spring ;
  aucun test M3 ne chargeait le contexte complet).

**Découverte non anticipée par le design, importante pour la suite** :
un `@McpTool` prenant **seulement** `McpSchema.CallToolRequest` en
paramètre génère un `inputSchema` **vide** (`McpJsonSchemaGenerator`
exclut ce type de la génération de schéma — vérifié par désassemblage
du jar 2.0.0, confirmé empiriquement via `tools/list` réel). Un agent
appelant n'aurait aucune visibilité sur les arguments attendus. **Fix
retenu** : déclarer *en plus* des `@McpToolParam` typés portant les
mêmes noms que les clés JSON réellement lues dans
`request.arguments()` — le générateur les inclut dans le schéma, le
framework les injecte à l'appel, mais le corps de la méthode continue
de tout relire depuis la map brute (seul chemin qui permet de rejeter
les clés interdites de `define_dossier_type`, cf. M3/T6.1). Les
variables locales issues de la map sont préfixées `raw*` pour éviter
un conflit de nom Java avec les paramètres typés homonymes. Prouvé par
un vrai appel `tools/call` `dryRun` (`isError:false`) et un appel avec
`retentionDays` (`isError:true`, message d'exception, pas de stack
trace) — voir `McpEndpointApiTest`.

**Confirmation empirique (pas de `McpToolExceptionMapper` nécessaire)** :
le SDK convertit toute exception non interceptée levée par un tool en
`isError:true` avec le message de l'exception. Décision de M5
confirmée, pas de composant à ajouter.

## M7 — IT HTTP M2M (fait, 2026-08-27)

Nouveau fichier `infrastructure/mcp/McpToolCallIntegrationTest.java`
(7 tests, `@SpringBootTest` + `MockMvc`, réutilise le pattern `m2mJwt`
de `ConnectorExecuteApiTest`) :

- Auth : `list_dossier_types` OK avec JWT valide ; 403 si `tenant_id`
  ne correspond pas à l'instance ; 403 sans rôle `API_SERVICE`.
- `define_dossier_type` : persistance réelle vérifiée par lecture
  directe du repository (pas seulement via un autre appel MCP), stamp
  `createdBy` = `sub` du JWT.
- `deploy_workflow` : config absente au départ, présente après l'appel
  (`GetWorkflowTypeConfigUseCase.getByKey`) — aucun mock Flowable
  nécessaire, le contexte `@SpringBootTest` démarre déjà un moteur
  Flowable réel sur H2 (mode PostgreSQL), comme tous les autres tests
  BPMN du projet.
- `get_dossier` : dossier avec champ `sensitive=true` seedé directement
  via `DossierTypeRepository`/`DossierRepository`, valeur absente de la
  réponse `tools/call`.
- `search_dossiers` : page par défaut 20 visible dans la réponse.

**Portée volontairement réduite pour IT-MCP-8** (cap SLA 10k) : ajouter
une vraie IT à 10 001 dossiers persistés serait disproportionné pour
un comportement déjà couvert par un test domaine existant
(`GetSlaSummaryUseCaseImplTest.cohortTooLarge_rejected`, mock du port
de comptage, feature `reporting-sla`). À la place :
`QuerySlaSummaryToolTest.cohortTooLarge_propagatesAsToolError` prouve
que le tool ne capture pas `SlaCohortTooLargeException` — elle remonte
telle quelle vers le mapping `isError` générique du SDK (M6). La
non-régression REST `/api/reports/**` ne nécessite aucun nouveau test :
aucun fichier de ce contrôleur n'a été touché (`SlaReportController`
reste `hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN','DOSSIER_MANAGER')`,
sans `API_SERVICE`).

Aucune déviation par rapport au design. Toutes les IT sont passées du
premier coup grâce aux vérifications empiriques déjà faites en M4/M6
(session MCP, auth, format de réponse).

## M8 — ArchUnit + doc (fait, 2026-08-27)

- `ArchitectureRulesTest.domain_has_no_framework_dependency` — ajout de
  `io.modelcontextprotocol..` à la liste des packages interdits dans
  `..domain..`. `org.springframework.ai..` était déjà couvert par le
  motif générique `org.springframework..` présent depuis l'origine de
  cette règle — pas de changement nécessaire pour lui.
  `only_allowlisted_classes_read_raw_dossier_fields` (existante) couvre
  déjà tous les tools MCP par construction (aucun tool en dehors de
  `DossierMcpView` n'est dans l'allowlist ; `CheckDossierTypeSchemaCompatibilityUseCaseImpl`
  y est depuis M3).
- `README.md` — nouvelle sous-section « Serveur MCP (outillage agent IA
  interne) » dans « Fonctionnalités principales ».
- `GETTING-STARTED.md` — nouvelle section §11 « Serveur MCP (outillage
  agent IA) » : endpoint, chaîne de sécurité M2M réutilisée, exemple
  `curl` d'un appel `initialize` avec JWT `Authorization: Bearer`.
  Aucun fichier `frontend/**` touché (confirmé par `git status`).

## M9 — Vérification globale (fait, 2026-08-27)

- `./mvnw verify -Dmaven.compiler.release=21` : **succès**, Jacoco
  BUNDLE (LINE ≥ 85 %, BRANCH ≥ 75 %) tenus sans dérogation malgré le
  volume de code neuf (packages `infrastructure.mcp`,
  `infrastructure.mcp.tool`, ~15 fichiers de test dédiés).
- `cd frontend && npm run test` : **213/213** verts (Vitest).
- `npx playwright test` : **55/55** verts. Nécessite
  `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium` dans cet
  environnement d'exécution sandboxé (le binaire `chrome-headless-shell`
  attendu par la version de Playwright installée n'est pas celui
  pré-installé côté conteneur) — particularité de l'environnement, pas
  du code livré ; `playwright.config.ts` supporte déjà cette variable
  nativement, aucun changement de configuration nécessaire.
- Aucun fichier `frontend/**` modifié par cette feature (D-SPA) : 0
  nouveau spec MCP, 0 mock `frontend/e2e/**` touché — confirmé par
  `git status` sur toute la durée de la Phase 5.

**Statut final Phase 5** : les 32 tâches des 9 milestones sont
terminées, testées (TDD rouge → vert à chaque étape) et poussées sur
`claude/pr-81-phase-5-bgd04q`. Le serveur MCP `/api/m2m/mcp` expose 9
tools fonctionnels, authentifiés par le JWT M2M `API_SERVICE`
existant, sans starter chat/model, sans nouvelle chaîne de sécurité,
sans impact sur le SPA.

## Phase 7 — Check Implementation (revue d'alignement, 2026-08-27)

Relecture fichier par fichier de tout le diff (79 fichiers vs `main`,
[PR #82](https://github.com/superjojo79/FlowForge/pull/82)) contre le
design et les requirements Phase 2/3, après redémarrage du conteneur
de session : `./mvnw compile` + `./mvnw test` complet rejoués à froid,
verts (aucune régression liée au redémarrage).

**Couverture requirements** (table « Requirements coverage » du
design) : les 22 lignes sont couvertes par le code livré, sans écart
au-delà de ceux déjà consignés milestone par milestone ci-dessus
(fallback SDK non déclenché, A-M2M-PATH non déclenché, tool-callback-converter
absent des métadonnées 2.0.0 réelles, `deploy_workflow` fait plus que
le flux REST existant par intention du design).

**Points vérifiés spécifiquement lors de cette passe** :

- **Ordre `GetDossierTool.drain()`** (pagination interne size 100) :
  condition d'arrêt `page + 1 >= totalPages` revérifiée sur les cas
  limites (0 élément, exactement 100, 101) — pas d'off-by-one, pas de
  boucle infinie.
- **`DeployWorkflowTool` — ordre parse → upsert → deploy** : la
  validation XML (extraction de clé, levée d'`InvalidBpmnException` si
  invalide) se fait avant toute écriture ; l'upsert `WorkflowTypeConfig`
  se fait avant l'appel `DeployBpmnProcessUseCase.deploy`, conforme à
  DD-DEPLOY-ORDER et A-NO-COMPENSATE (pas de rollback si le deploy
  échoue après l'upsert — risque accepté et documenté au design).
- **Autorisation de `SaveWorkflowTypeConfigUseCase`** (appelée par
  `DeployWorkflowTool`) : cette use case ne porte **aucune** garde
  interne (contrairement à `Create/UpdateDossierTypeUseCaseImpl`, qui
  appellent `requireTenantAdminOrApiService`) — côté REST elle est
  protégée uniquement par `@PreAuthorize` au contrôleur. Vérifié que ce
  n'est **pas** une régression de sécurité : tout appelant MCP a déjà
  dû franchir la chaîne Security M2M Order 1
  (`hasAuthority("API_SERVICE")` sur `/api/m2m/**`) avant qu'un tool ne
  s'exécute — il n'existe aucun chemin pour atteindre
  `DeployWorkflowTool` sans ce rôle. Le point de contrôle unique est le
  filtre de sécurité, pas une garde par use case ; comportement
  identique à `M2mConnectorExecuteController`. Noté ici pour qu'un
  futur lecteur ne le prenne pas pour un oubli.
- **`McpAccessContextFactory.currentActor()`** : lève
  `IllegalStateException` (fail-closed) si l'authentification courante
  n'est pas un `JwtAuthenticationToken` — cohérent avec D-ACTOR (jamais
  de repli silencieux vers un acteur `system`).
- **Diffs domaine minimaux** (`DossierAccessPolicy`, `SlaReportAccess`,
  `SearchDossiersUseCaseImpl`, `ListDossierTypesUseCaseImpl`,
  `TenantAdminGuard`, `DossierType`, `WorkflowTypeConfig`) relus ligne à
  ligne : chaque changement est chirurgical (un `||
  user.hasRole("API_SERVICE")` par site, une méthode ajoutée, deux
  composants de record ajoutés avec un constructeur de compatibilité
  8/11-arg) — aucun effet de bord sur le comportement TA/WA/DOSSIER_MANAGER
  existant, confirmé par les tests de non-régression déjà en place
  (T1.4, suites `*UseCaseImplTest` étendues plutôt que remplacées).

**Déviations mineures relevées, sans impact fonctionnel** :

- Ordre des imports dans `DossierConfig.java` (nouvel import inséré au
  milieu du bloc existant plutôt qu'en position alphabétique stricte) —
  cosmétique, aucun outil de style (checkstyle/spotless) n'est
  configuré dans ce dépôt pour l'imposer.

**Verdict** : implémentation alignée avec le design et les
requirements. Aucune déviation majeure, aucun défaut de correction ou
de sécurité identifié. Prêt pour Phase 8 (dev-testing — déjà largement
couverte de fait par le TDD de Phase 5, cf. `docs/ai/testing/...md`)
et Phase 9 (dev-review).

## Development Setup

Workspace : branche `cursor/serveur-mcp-e12d`, no-worktree.
Stack : Java 25 / Spring Boot 4.1. Commandes :

```bash
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test && npm run test:e2e
```

## Code Structure

- `com.flowforge.infrastructure.mcp` — tools + factory JWT.
- Domaine : gardes **D-POLICY-AS**, `requireTenantAdminOrApiService`,
  `createdBy`/`updatedBy`, `CheckDossierTypeSchemaCompatibilityUseCase`.
- Liquibase `017-mcp-audit-authors.yaml`.
- `pom.xml` : BOM Spring AI 2.0.0 + starter MCP WebMVC.

## Implementation Notes

TDD (`tdd` + `verify`). Ne pas lire `Dossier.fields()` depuis un tool
MCP. Ne pas élargir `requireTenantAdmin`. Ne pas ajouter de starter
chat.

## Phase 9 — Revue finale pre-push (2026-08-27)

Revue holistique du diff complet contre `main` (83 fichiers, +5286/-133,
[PR #82](https://github.com/superjojo79/FlowForge/pull/82)), en
complément de l'alignement design déjà vérifié en Phase 7 et de la
fermeture des trous de couverture en Phase 8.

**Points vérifiés spécifiquement lors de cette passe** :

- **Dépendances** (`pom.xml`) : ajout minimal — un seul BOM
  (`spring-ai-bom:2.0.0`, import scope) et un seul starter
  (`spring-ai-starter-mcp-server-webmvc`), conforme à D-SDK=A (pas de
  starter chat/model). Pas de conflit de version détecté (build vert).
- **Sécurité rétrograde de la migration 017** : `addColumn` (nullable)
  → backfill `UPDATE ... WHERE ... IS NULL` → `addNotNullConstraint`,
  avec bloc `rollback` (`dropColumn`) sur les deux changesets. Pattern
  identique à la migration 015 déjà en prod. Les deux `JpaEntity`
  (`DossierTypeJpaEntity`, `WorkflowTypeConfigJpaEntity`) déclarent
  `created_by`/`updated_by` `nullable = false` et les alimentent
  systématiquement depuis le domaine (`fromDomain`) — aucun chemin
  d'insertion ne peut laisser les colonnes nulles après le déploiement.
- **Compatibilité ascendante des records** : `DossierType` (13
  composants) et `WorkflowTypeConfig` (10, avec constructeur de
  compatibilité 8-arg par défaut `"system"`) — tous les appelants
  in-repo recompilent et passent (`./mvnw verify` vert), aucun
  appelant externe/public concerné (records domaine internes).
- **`git merge-base` vs `origin/main`** : aucune divergence, `main` n'a
  pas bougé depuis la création de la branche — pas de conflit de merge
  à résoudre.
- **Revue GitHub** : 0 review thread ouvert sur la PR #82.
- **CI** : `build` et `frontend` échouent sur le head commit, mais
  confirmé **non lié à cette PR** — échec instantané (~2 s, aucun
  runner jamais assigné) reproduit à l'identique sur un re-run manuel,
  et identique sur tous les runs CI récents du dépôt y compris des
  push directs sur `main` sans rapport avec cette feature (ex. le
  merge de la PR #80). Problème d'infrastructure runner GitHub Actions
  préexistant et hors du diff ; commentaire de statut posté sur la PR
  avec les preuves locales (`./mvnw verify`, Vitest 213/213, Playwright
  55/55).
- **Frontend** : aucun fichier `frontend/**` touché par cette feature
  (D-SPA=A) — pas d'obligation de preuve Playwright supplémentaire
  pour cette phase au-delà de la régression déjà consignée en M9.

**Checklist finale** :

- [x] Correspondance design : 22/22 lignes de couverture requirements,
  aucun écart non consigné (Phase 7).
- [x] Pas de trou de logique identifié (Phase 7 + Phase 9).
- [x] Sécurité traitée : chaîne JWT M2M réutilisée sans modification,
  clés interdites retention/SLA bloquées avant toute écriture,
  `McpAccessContextFactory.currentActor()` fail-closed, aucune fuite de
  champ sensible (`get_dossier` masque `sensitive=true`, testé IT).
- [x] Points d'intégration vérifiés : Liquibase 017 (colonnes NOT NULL
  + backfill + rollback), ArchUnit (`io.modelcontextprotocol` interdit
  en domaine), 9 tools enregistrés (`tools/list`).
- [x] Tests couvrant les changements : `infrastructure.mcp.tool` à
  100 % instructions / 97 % branches (Phase 8), 7 IT HTTP M2M réelles,
  Jacoco BUNDLE (LINE ≥ 85 %, BRANCH ≥ 75 %) tenu.
- [x] Docs à jour : requirements/design/planning/implementation/testing
  tous datés 2026-08-27, phases 1-9 tracées.

**Verdict** : feature prête à merger sur le fond (code, tests, docs).
Seul point non résolu est le CI GitHub Actions cassé au niveau
infrastructure du dépôt (hors scope de cette PR) — à surveiller côté
mainteneur du dépôt, pas un blocage introduit par `serveur-mcp`.
