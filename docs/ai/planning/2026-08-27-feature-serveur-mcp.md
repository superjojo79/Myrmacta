---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: planned
created: 2026-08-27
updated: 2026-08-27
feature: serveur-mcp
phase4: 2026-08-27
requirements_ref: docs/ai/requirements/2026-08-27-feature-serveur-mcp.md
design_ref: docs/ai/design/2026-08-27-feature-serveur-mcp.md
testing_ref: docs/ai/testing/2026-08-27-feature-serveur-mcp.md
---

# Project Planning & Task Breakdown — `serveur-mcp`

Phase 4 **planned** (2026-08-27). Requirements Phase 2 + design Phase 3
**reviewed**. Toutes les tâches **not started**. TDD obligatoire
(red → green → refactor). Pas d'implémentation dans cette phase.

Workspace : `cursor/serveur-mcp-e12d`, no-worktree. Task tracing
indisponible (`unknown command 'task'`). Lint feature OK ; branche
`feature-serveur-mcp` = MISS attendu.

## Milestones

**What are the major checkpoints?**

- [x] **M1** Gardes domaine `API_SERVICE` (policy, search, list types, SLA, write type)
- [x] **M2** Auteurs Liquibase 017 + `createdBy`/`updatedBy` (types + REST stamp)
- [x] **M3** Compatibilité schéma (`CheckDossierTypeSchemaCompatibilityUseCase`)
- [x] **M4** SDK Spring AI + endpoint `/api/m2m/mcp` + factory JWT (sans ChatClient)
- [x] **M5** Tools lecture (7 : get_dossier, search_dossiers, query_sla_summary, list_document_templates, get_dossier_type, list_dossier_types, list_workflow_types)
- [ ] **M2** Auteurs Liquibase 017 + `createdBy`/`updatedBy` (types + REST stamp)
- [ ] **M3** Compatibilité schéma (`CheckDossierTypeSchemaCompatibilityUseCase`)
- [ ] **M4** SDK Spring AI + endpoint `/api/m2m/mcp` + factory JWT (sans ChatClient)
- [ ] **M5** Tools lecture (7)
- [ ] **M6** Tools écriture (`define_dossier_type`, `deploy_workflow`) + `dryRun`
- [ ] **M7** IT MockMvc JWT / persist / SLA / search
- [ ] **M8** ArchUnit + README / getting-started
- [ ] **M9** `./mvnw verify` + régression FE (0 spec MCP)

Séquence : M1 → M2 → M3 → M4 → M5/M6 (M5 peut démarrer dès M4 ; M6
dépend M2+M3+M4) → M7 → M8 → M9.

## Task Breakdown

**What specific work needs to be done?**

Chaque tâche : failing test d'abord. Evidence = sortie de test ciblé
puis, en fin de milestone, `./mvnw test -Dtest=…` (ou classe) vert.

### M1 — Gardes domaine

- [x] **T1.1** `DossierAccessPolicy` : `API_SERVICE` VIEW tenant-wide
      (comme WA) ; `Service` toujours refusé.
      Outcome : `hasRole("API_SERVICE")` → true comme TA/WA.
      Dépend : —
      Tests : UT-POL-1, UT-POL-2 (`DossierAccessPolicyTest.apiService_bypassesWithoutAcl`)
- [x] **T1.2** `SlaReportAccess` : `API_SERVICE` unrestricted (`null`) ;
      `Service` / `DOSSIER_VIEWER` inchangés (D-AUTHZ C).
      Tests : UT-POL-3, UT-POL-4 (nouveau `SlaReportAccessTest`)
      Dépend : —
- [x] **T1.3** `TenantAdminGuard.requireTenantAdminOrApiService` ;
      Create/Update type l'utilisent. `requireTenantAdmin` **inchangé**.
      TA OK, `API_SERVICE` OK, WA seul refusé.
      Tests : UT-GUARD-1, UT-GUARD-2, UT-GUARD-3
      Dépend : —
- [x] **T1.4** Non-régression : purge / ACL / rétention tenant /
      `TenantSlaSettings` restent `requireTenantAdmin` — `API_SERVICE`
      refusé.
      Tests : UT-GUARD-4 (`TenantAdminGuardTest.apiServiceAlone_isDeniedByRequireTenantAdmin`)
      Dépend : T1.3
- [x] **T1.5** `SearchDossiersUseCaseImpl` + `ListDossierTypesUseCaseImpl` :
      `API_SERVICE` unrestricted comme WA (**DD-POLICY-TRIPLE**).
      Tests : étendre les UT search/list existants (cas `API_SERVICE`).
      Dépend : T1.1

### M2 — Audit auteurs

- [x] **T2.1** Liquibase `017-mcp-audit-authors.yaml` : `created_by` /
      `updated_by` varchar(128) NOT NULL + backfill `'system'` sur
      `dossier_type` et `workflow_type_config`. Include master.
      Tests : IT-LIQ (`RetentionPersistenceIT` démarre le contexte avec
      le changelog 017 appliqué)
      Dépend : —
- [x] **T2.2** `DossierType` + JPA + Create/Update : stamp `createdBy` /
      `updatedBy`. Fixtures tests existants : `"system"` (via
      `DossierType.inherit`, inchangé) ou acteur explicite pour les
      appels directs au constructeur complet.
      REST `DossierTypeController` passe déjà `OidcUser.subject` via
      `AccessContextFactory.user(...).userId()` — aucun changement
      contrôleur nécessaire.
      Tests : UT Create/Update étendus (stamp createdBy/updatedBy,
      update conserve createdBy) + UT-AUDIT-1 (partie type)
      Dépend : T2.1, T1.3
- [x] **T2.3** `WorkflowTypeConfig` + JPA + REST contrôleur stamp OIDC.
      Constructeur canonique à 10 champs (+ createdBy/updatedBy) ;
      constructeur de compatibilité à 8 champs conservé (défaut
      `"system"`/`"system"`) pour ne pas casser les ~20 sites de test
      existants. `SaveWorkflowTypeConfigUseCase` inchangée (create/update
      reçoivent un `WorkflowTypeConfig` déjà stampé par l'appelant —
      contrôleur REST aujourd'hui, tool MCP en M6).
      Tests : `WorkflowTypeConfigTest` (constructeur complet, défaut
      système, rejet null) + `WorkflowPersistenceTest` (round-trip
      persistance déjà couvert par égalité totale du record)
      Dépend : T2.1

### M3 — Compatibilité schéma

- [x] **T3.1** `DossierRepository.findByDossierTypeCode(tenantId, code)`
      + adaptateur JPA (`findByTenantIdAndDossierTypeCode`).
      Tests : `DossierPersistenceTest.findByDossierTypeCode_isScopedToTenantAndType`
      Dépend : —
- [x] **T3.2** `CheckDossierTypeSchemaCompatibilityUseCase` :
      compte les dossiers qui échouent `DossierFieldValidator` ;
      allowlist ArchUnit `Dossier.fields()` **+=** ce use case seulement
      (`CheckDossierTypeSchemaCompatibilityUseCaseImpl`).
      Tests : UT-DEF-6 (domaine) — count 0 vs > 0, plus cap échantillon à 5
      Dépend : T3.1

### M4 — SDK + transport

- [x] **T4.1** `pom.xml` : BOM `spring-ai-bom:2.0.0` +
      `spring-ai-starter-mcp-server-webmvc`. YAML STREAMABLE,
      `mcp-endpoint=/api/m2m/mcp`, capabilities tool only, pas de
      starter chat. IT contexte : **aucun** bean `ChatClient`.
      **DD-SDK-FALLBACK non déclenché** : le BOM résout proprement, la
      classe `ChatClient` n'existe même pas sur le classpath (aucun
      starter chat/model tiré transitivement).
      Tests : UT-CTX-1 (`McpServerContextTest`)
      Dépend : —
- [x] **T4.2** `McpAccessContextFactory` : JWT `sub` sinon `client_id`
      sinon `"api-service"` ; rôles `{API_SERVICE}`. Jamais `system` /
      `Service`.
      Tests : UT-ACTOR-1 (`McpAccessContextFactoryTest`)
      Dépend : T4.1
- [x] **T4.3** Preuve que POST `/api/m2m/mcp` est l'endpoint MCP
      (initialize). **A-M2M-PATH non déclenché** : le starter respecte
      `streamable-http.mcp-endpoint` tel que configuré, réponse < 400
      dès le premier essai, sous la chaîne M2M existante (même JWT).
      Tests : IT-MCP-1 (amorce, `McpEndpointApiTest`)
      Dépend : T4.1, T4.2

### M5 — Tools lecture

- [x] **T5.1** `GetDossierTool` : xor id/reference ; agrège 4 UC
      (`GetDossier`, `ListDossierElements`, `GetDossierWorkflowHistory`,
      `ListDossierRelations`) ; pagination interne size 100 jusqu'à
      épuisement ; sérialisation via `DossierMcpView` (nouveau DTO infra,
      allowlist ArchUnit `fields()`) — le tool lui-même n'appelle jamais
      `Dossier.fields()`.
      Tests : UT-GET-1, UT-GET-2, UT-GET-3 (`GetDossierToolTest`)
      Dépend : T4.2, T1.1
- [x] **T5.2** `SearchDossiersTool` : relais `SearchDossiersQuery` ;
      size 101 → erreur (validation déjà portée par le compact
      constructor `SearchDossiersQuery`/`PageQuery`, pas de code
      dupliqué dans le tool).
      Tests : UT-SEARCH-1 (`SearchDossiersToolTest`)
      Dépend : T4.2, T1.5
- [x] **T5.3** `QuerySlaSummaryTool` → `GetSlaSummaryUseCase.Query`.
      Tests : UT-SLA-1 (`QuerySlaSummaryToolTest`)
      Dépend : T4.2, T1.2
- [x] **T5.4** `ListDocumentTemplatesTool` : `includeContent` false par
      défaut ; `true` rejeté **avant** d'appeler le use case (le use
      case honorerait `true`, donc le garde-fou doit être dans le tool).
      Tests : UT-TPL-1 (`ListDocumentTemplatesToolTest`)
      Dépend : T4.2
- [x] **T5.5** `GetDossierTypeTool`, `ListDossierTypesTool`,
      `ListWorkflowTypesTool` (pages **A-LIST-PARITY**).
      Tests : `GetDossierTypeTool`/`ListDossierTypesTool` couverts par
      compilation + ArchUnit + réutilisation directe des use cases déjà
      testés (T1.1/T1.5) ; pas de logique propre à tester en plus du
      relais PageQuery (défaut 20 / max 100, déjà couvert par
      `PageQuery`).
      Dépend : T4.2, T1.5

### M6 — Tools écriture

- [ ] **T6.1** `DefineDossierTypeTool` : create vs update ; schéma
      complet ; clés rétention/SLA → erreur ; update conserve
      rétention/SLA ; check compat avant save ; `dryRun` sans save /
      sans stamp.
      Tests : UT-DEF-1..6, UT-AUDIT-1 (MCP)
      Dépend : T2.2, T3.2, T4.2, T1.3
- [ ] **T6.2** `DeployWorkflowTool` : UTF-8 ; taille ≤ multipart max ;
      parse + clé match **puis** upsert **puis** deploy ; `dryRun` ;
      `allowedDossierStatuses` vide → invariant.
      Tests : UT-DEP-1..5
      Dépend : T2.3, T4.2
- [ ] **T6.3** Enregistrer exactement **9** tools (pas de
      ToolCallback parasite).
      Tests : IT-MCP-1 (complet)
      Dépend : T5.*, T6.1, T6.2, T4.3

### M7 — IT HTTP M2M

Réutiliser `m2mJwt` de `ConnectorExecuteApiTest`.

- [ ] **T7.1** JWT HMAC + `tenant_id` match → `list_dossier_types` OK.
      Tests : IT-MCP-2
      Dépend : T6.3, T5.5
- [ ] **T7.2** Mauvais `tenant_id` → 403 ; sans `API_SERVICE` → 403.
      Tests : IT-MCP-3, IT-MCP-4
      Dépend : T4.3
- [ ] **T7.3** `define_dossier_type` persisté ; lecture use case / GET
      type le voit ; create sans `slaTargetHours`.
      Tests : IT-MCP-5
      Dépend : T6.1
- [ ] **T7.4** `deploy_workflow` crée la config si absente + deploy
      (port Flowable mocké / H2 comme `WorkflowBpmnDeploymentApiTest`).
      Tests : IT-MCP-6
      Dépend : T6.2
- [ ] **T7.5** `get_dossier` : champ `sensitive` absent en clair.
      Tests : IT-MCP-7
      Dépend : T5.1
- [ ] **T7.6** `query_sla_summary` cap 10k → `SLA_COHORT_TOO_LARGE` ;
      REST `/api/reports/**` `@PreAuthorize` **sans** `API_SERVICE`
      (non-régression).
      Tests : IT-MCP-8
      Dépend : T5.3
- [ ] **T7.7** `search_dossiers` page défaut 20.
      Tests : IT-MCP-9
      Dépend : T5.2

### M8 — ArchUnit + doc

- [ ] **T8.1** ArchUnit : `domain` n'importe pas
      `org.springframework.ai` / `io.modelcontextprotocol` ; tools MCP
      hors allowlist `fields()` ; use case compat **dans** l'allowlist.
      Tests : IT-ARCH
      Dépend : T3.2, T5.1, T4.1
- [ ] **T8.2** README + getting-started : URL `/api/m2m/mcp`, rôle
      `API_SERVICE`, exemple `Authorization: Bearer`. **Zéro** fichier
      `frontend/**` (sauf si régression forcée — interdit par D-SPA).
      Tests : relecture ; E2E-REG mocks non touchés
      Dépend : T4.3

### M9 — Vérification globale

- [ ] **T9.1** `./mvnw verify -Dmaven.compiler.release=21` (Jacoco
      BUNDLE tenus).
      Dépend : M1–M8
- [ ] **T9.2** `cd frontend && npm run test` **et** `npm run test:e2e`
      (régression ; 0 spec MCP ; 0 mock `frontend/e2e/**` modifié).
      Tests : E2E-REG-1, E2E-REG-2
      Dépend : T8.2
- [ ] **T9.3** Docs planning/implementation à jour (Phase 6 à chaque
      tâche Phase 5).

## Dependencies

**What needs to happen in what order?**

```
T1.1 → T1.5 → T5.2, T5.5
T1.2 → T5.3 → T7.6
T1.3 → T1.4, T2.2, T6.1
T2.1 → T2.2, T2.3
T2.3 → T6.2 → T7.4
T3.1 → T3.2 → T6.1 → T7.3
T4.1 → T4.2 → T4.3 → T5.*, T6.*
T5.* + T6.* → T6.3 → T7.1
T4.3 → T7.2
T5.1 → T7.5
T5.2 → T7.7
T3.2 + T5.1 + T4.1 → T8.1
T4.3 → T8.2 → T9.2
M1–M8 → T9.1
```

Pas de dépendance FE. `reporting-sla` déjà livré (PR #80).

## Timeline & Estimates

**When will things be done?**

Pas de calendrier. Ordre technique ci-dessus.
Invasivité **modérée** : nouveau package infra + touches domaine
(gardes, 2 records auteur, 1 use case, 1 méthode repo) + 1 changelog.
Risque principal = câblage Spring AI 2.0 (T4.1 / T4.3), pas le métier.

## Risks & Mitigation

**What could go wrong?**

| Risque | Mitigation |
|---|---|
| Starter MCP ignore `mcp-endpoint` | T4.3 / **A-M2M-PATH** même JWT |
| BOM tire un `ChatClient` | UT-CTX-1 ; **DD-SDK-FALLBACK** |
| `@McpTool` vs `ToolCallback` 2.0.0 | Vérifier le starter en T4.1 ; 9 beans explicites (T6.3) |
| Create/Update `DossierType` compile break (auteur) | T2.2 : corriger **tous** les tests existants du type |
| Scan schéma charge trop de dossiers | Pas de cap V1 (design) ; test avec 2–3 dossiers |
| Jacoco nouvelles classes MCP | Couvrir dryRun / xor / extra JSON / ordre deploy |
| Playwright flaky | Ne pas toucher `frontend/e2e/**` |
| Double parse XML (tool + Deploy UC) | Accepté ; tests d'ordre UT-DEP-1/2 |

## Resources Needed

**What do we need to succeed?**

- JDK snapshot + `./mvnw` ; secret test `app.security.m2m.jwt-secret`.
- Helper JWT : `ConnectorExecuteApiTest.m2mJwt`.
- Fixtures BPMN `deploiement-bpmn` / `WorkflowBpmnDeploymentApiTest`.
- Maven Central : `spring-ai-starter-mcp-server-webmvc` 2.0.0.
- Node uniquement pour T9.2 (régression).
- Task tracing indisponible.

## Traceability testing → tasks

| Scénario | Tâche |
|---|---|
| UT-POL-1, UT-POL-2 | T1.1 |
| UT-POL-3, UT-POL-4 | T1.2 |
| UT-GUARD-1..3 | T1.3 |
| UT-GUARD-4 | T1.4 |
| Search/list `API_SERVICE` | T1.5 |
| IT-LIQ | T2.1 |
| UT-AUDIT-1 (REST type / MCP) | T2.2, T6.1 |
| Save workflow auteur | T2.3 |
| UT-DEF-6 (domaine) | T3.2 |
| UT-CTX-1 | T4.1 |
| UT-ACTOR-1 | T4.2 |
| IT-MCP-1 amorce | T4.3 |
| UT-GET-1..3 | T5.1 |
| UT-SEARCH-1 | T5.2 |
| UT-SLA-1 | T5.3 |
| UT-TPL-1 | T5.4 |
| list/get types | T5.5 |
| UT-DEF-1..6 | T6.1 |
| UT-DEP-1..5 | T6.2 |
| IT-MCP-1 complet | T6.3 |
| IT-MCP-2 | T7.1 |
| IT-MCP-3, IT-MCP-4 | T7.2 |
| IT-MCP-5 | T7.3 |
| IT-MCP-6 | T7.4 |
| IT-MCP-7 | T7.5 |
| IT-MCP-8 | T7.6 |
| IT-MCP-9 | T7.7 |
| IT-ARCH | T8.1 |
| README | T8.2 |
| E2E-REG-1, E2E-REG-2 | T9.2 |
| `./mvnw verify` | T9.1 |

Aucun scénario testing orphelin.

## Progress

Phase 4 **planned**. 9 milestones, 32 tâches. Phase 5 démarrée
(2026-08-27) : **M1–M5 terminés** (T1.1–T1.5, T2.1–T2.3, T3.1–T3.2,
T4.1–T4.3, T5.1–T5.5, TDD vert, `./mvnw test` complet vert). Reste
M6–M9 not started.

Prochain : **T6.1** (`DefineDossierTypeTool`).
