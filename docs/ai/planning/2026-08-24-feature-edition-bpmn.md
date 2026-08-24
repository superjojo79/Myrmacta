---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: done
created: 2026-08-24
updated: 2026-08-24
feature: edition-bpmn
design_ref: docs/ai/design/2026-08-24-feature-edition-bpmn.md
testing_ref: docs/ai/testing/2026-08-24-feature-edition-bpmn.md
---

# Project Planning & Task Breakdown — `edition-bpmn`

> Plan exécuté (Phase 5, 2026-08-24). M1-M4 (backend) et M5-M7 (frontend,
> hors E2E) implémentés en parallèle par deux agents dans des worktrees
> git isolés (contrat API déjà figé par le design, aucune dépendance
> d'exécution croisée), fusionnés séquentiellement (backend en premier via
> `git merge`, frontend via `git cherry-pick` — sa branche partait d'une
> base `origin/main` divergente incluant une feature non liée, seuls les 3
> commits M5/M6/M7 réellement scopés `frontend/**` ont été repris). M8
> exécuté par l'orchestrateur sur l'arbre fusionné.

## Milestones

- [x] M1: Domaine backend brouillon (modèle, port out, use cases)
- [x] M2: Persistence brouillon (entité JPA, adaptateur, migration Liquibase)
- [x] M3: Lecture du XML déployé (`WorkflowDeploymentPort.findDeployedXml` + endpoint)
- [x] M4: Endpoint brouillon + suppression post-déploiement
- [x] M5: Frontend — canvas éditeur (`bpmn-js` Modeler + properties panel + provider Flowable)
- [x] M6: Frontend — composables partagés (`useBpmnDraft`, `useBpmnDeployment`)
- [x] M7: Frontend — écran dédié + intégration admin
- [x] M8: Vérification globale (backend + frontend + E2E)

## Task Breakdown

### M1: Domaine backend brouillon

- [x] T1.1 — `BpmnDraft` (domain model, record) — `domain/workflow/model/`
  - Validation : compile ✓
- [x] T1.2 — `BpmnDraftRepository` (port out, pur) — `domain/workflow/port/out/`
  - Validation : `ArchitectureRulesTest.domain_has_no_framework_dependency` vert ✓
- [x] T1.3 — `SaveBpmnDraftUseCase`/`SaveBpmnDraftUseCaseImpl` — pas de vérification `WorkflowTypeConfigRepository` (D-EDIT-DRAFT-VALIDATION)
  - Validation : `SaveBpmnDraftUseCaseImplTest` (3 scénarios) exécuté et vert ✓
- [x] T1.4 — `GetBpmnDraftUseCase`/`GetBpmnDraftUseCaseImpl`
  - Validation : `GetBpmnDraftUseCaseImplTest` (2 scénarios) exécuté et vert ✓

### M2: Persistence brouillon

- [x] T2.1 — Migration Liquibase `013-bpmn-draft-tables.yaml` (table `bpmn_draft`, clé composite, `rollback`)
  - Validation : `./mvnw verify` applique la migration sans erreur ✓
- [x] T2.2 — `BpmnDraftJpaEntity` (`@IdClass`) + `SpringDataBpmnDraftRepository`
  - Validation : compile ✓
- [x] T2.3 — `JpaBpmnDraftRepositoryAdapter implements BpmnDraftRepository`
  - Validation : `JpaBpmnDraftRepositoryAdapterTest` (5 scénarios — 1 de plus que prévu : isolation tenant explicitement testée) exécuté et vert ✓. `BpmnDraftJpaEntityTest` (7 scénarios, non prévu au testing doc initial — mapping domaine↔entité testé isolément, cf. `deploiement-bpmn` où `FlowableBpmnParserAdapterTest` avait eu le même statut).

### M3: Lecture du XML déployé

- [x] T3.1 — `WorkflowDeploymentPort.findDeployedXml(tenantId, processDefinitionKey)`
  - Validation : compile ✓
- [x] T3.2 — Implémentation `FlowableWorkflowDeploymentAdapter` via `RepositoryService.getProcessModel(...)`
  - Validation : `FlowableWorkflowDeploymentAdapterTest` (+2 scénarios) exécuté et vert, scénarios existants non régressés ✓
- [x] T3.3 — `GET /api/workflow-bpmn-deployments/{processDefinitionKey}/xml`
  - Validation : `WorkflowBpmnDeploymentApiTest` (+4 scénarios, 1 de plus que prévu) exécuté et vert, scénarios existants non régressés ✓

### M4: Endpoint brouillon + suppression post-déploiement

- [x] T4.1 — `BpmnDraftController` (`GET`/`PUT /api/workflow-bpmn-drafts/{processDefinitionKey}`)
  - Validation : `BpmnDraftApiTest` (6 scénarios) exécuté et vert ; golden OpenAPI régénéré ✓
  - **Écart mineur documenté (backend agent)** : `deployedXml` sur `WorkflowBpmnDeploymentController` appelle `WorkflowDeploymentPort` directement, sans use case intermédiaire dédié — fidèle au diagramme mermaid du design (`DC → WD → FA`) et au précédent déjà présent (`WorkflowTaskController` → `WorkflowEnginePort`). Pas de classe use case superflue créée ; aucun impact sur l'architecture hexagonale (le domaine reste consulté via un port, pas une dépendance directe à l'infrastructure).
- [x] T4.2 — `DeployBpmnProcessUseCaseImpl` modifié (suppression brouillon post-succès, D-DESIGN-DRAFT-LIFECYCLE)
  - Validation : `DeployBpmnProcessUseCaseImplTest` (+2 scénarios) exécuté et vert, 3 scénarios existants non régressés ✓
- [x] T4.3 — Bean Spring (`WorkflowConfig`) pour les nouveaux use cases
  - Validation : contexte Spring démarre (`./mvnw verify`) ✓

### M5: Frontend — canvas éditeur

- [x] T5.0 — `bpmn-js-properties-panel` ajouté à `frontend/package.json`
  - **Écart documenté** : `npm view` résout par défaut la dernière version majeure (`6.0.1`), explicitement dépréciée (`npm warn deprecated ... use 5.x instead`). Fixé à `^5.65.0` (peer range `bpmn-js >= 11.5`, compatible avec `^18.24.0` déjà présent). `camunda-bpmn-js-behaviors@1.18.0` auto-installé comme peer dependency (présent au lockfile, jamais importé par le code — seuls `BpmnPropertiesPanelModule`/`BpmnPropertiesProviderModule` standards sont câblés).
  - Validation : licence `bpmn-js-properties-panel@5.65.0` lue (`node_modules/.../LICENSE`) = MIT (Camunda Services GmbH), peer `@bpmn-io/properties-panel@3.51.2` également MIT ✓
- [x] T5.1 — `flowablePropertiesProvider.ts`
  - Validation : Vitest (5 scénarios — 1 de plus que prévu) exécuté et vert ✓. **Risque du design levé empiriquement, pas un problème** : un attribut `flowable:*` non couvert par le provider est bien préservé par le moddle `bpmn-js` (lecture générique `$attrs`/`get()`/`set()`, re-sérialisé verbatim) — pas besoin d'extension moddle dédiée.
- [x] T5.2 — `BpmnEditorCanvas.vue`
  - Validation : Vitest (3 scénarios) exécuté et vert ✓
  - **Bug réel trouvé et corrigé (Phase 8/M8, pas en Phase 5)** : `modeler.get('commandStack').on(...)` — `CommandStack` de diagram-js n'a pas de méthode `.on()` propre (les événements passent par l'`eventBus`) ; l'erreur (`"p.get(...).on is not a function"`) n'apparaissait qu'au premier rendu réel dans un navigateur (Playwright), les tests Vitest mockant le `commandStack` avec un `.on()` fictif ne pouvaient pas la détecter. Corrigé en `modeler.get('eventBus').on('commandStack.changed', ...)`, mocks Vitest alignés sur la vraie API.

### M6: Frontend — composables partagés

- [x] T6.1 — Wrappers API (`getBpmnDraft`, `saveBpmnDraft`, `getDeployedBpmnXml`)
  - Validation : typecheck propre ✓, 5 tests ajoutés à `workflowBpmnDeployment.spec.ts`
- [x] T6.2 — `useBpmnDraft.ts`
  - Validation : Vitest (5 scénarios) exécuté et vert ✓
- [x] T6.3 — `useBpmnDeployment.ts` (extrait de `BpmnUploadPreview.vue`)
  - Validation : Vitest (4 scénarios — 1 de plus que prévu) exécuté et vert ✓
- [x] T6.4 — Refactor `BpmnUploadPreview.vue`
  - Validation : les 5 tests Vitest existants restent verts **sans modification** ✓ (contrat public préservé, cf. risque anticipé)

### M7: Frontend — écran dédié + intégration admin

- [x] T7.1 — `BpmnEditorView.vue`
  - Validation : Vitest (6 scénarios) exécuté et vert ✓
  - **Bug réel trouvé et corrigé en TDD (frontend agent, Phase 5)** : montage précoce de `BpmnEditorCanvas` pendant que `useBpmnDraft.load()` était encore en vol (Vue monte les enfants avant que le `onMounted` du parent ne s'exécute), créant une course entre un import du template vide et le vrai import (brouillon/déployé). Corrigé par un flag `ready` qui ne bascule qu'une fois le chargement initial réglé — un seul `importXML` déterministe, pas de flicker.
- [x] T7.2 — Route `/admin/bpmn-editor`
  - Validation : Vitest routeur (1 scénario) ✓
- [x] T7.3 — Lien « Éditer » dans `WorkflowConfigsAdminView.vue`
  - Validation : Vitest (1 scénario) + E2E (`E2E-EDIT-BPMN-2/3/4`) ✓

### M8: Vérification globale (exécuté par l'orchestrateur, sur l'arbre fusionné)

- [x] T8.1 — `./mvnw verify` complet
  - Validation : **1106 tests, 0 échec**, LINE 93.86% / BRANCH 77.14% (≥ seuils), `ArchitectureRulesTest` 4/4 vert. Preuve fraîche 2026-08-24, cette session, après fusion backend+frontend.
- [x] T8.2 — `npm run test` + `typecheck` + `lint`
  - Validation : **193/193 tests**, typecheck et lint propres. Preuve fraîche 2026-08-24.
- [x] T8.3 — `npm run test:e2e` (Playwright)
  - Validation : **47/47 scénarios** (43 existants, 0 régression + 4 nouveaux `E2E-EDIT-BPMN-1..4`). Preuve fraîche 2026-08-24 : `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium npx playwright test` → `47 passed (23.4s)`.
  - **Écarts découverts et corrigés en écrivant ces 4 scénarios (pas prévus en Phase 4)** :
    1. Navigation : `page.goto('/admin/bpmn-editor...')` en direct échoue silencieusement (le build en chemins relatifs, décision n°27, résout `./assets/...` contre l'URL profonde et 404 le bundle JS — limitation préexistante du dépôt, aucun autre test E2E ne fait de `goto` direct sur une route imbriquée). Corrigé en naviguant par clic (`Workflows` → `Éditer`), comme tout le reste de la suite.
    2. Race condition de test : cliquer "Enregistrer"/"Déployer" avant que le `Modeler` (import dynamique + `importXML` asynchrones) soit prêt fait échouer silencieusement l'extraction de clé. Corrigé par une attente explicite sur `.djs-element` avant toute interaction.
    3. Le bug réel `eventBus`/`commandStack` (cf. T5.2) — trouvé en écrivant ces tests, corrigé dans le code de production, pas contourné dans les tests.
    4. Fixtures BPMN sans DI (`bpmndi:BPMNDiagram`) rejetées par le `Modeler` réel (`"no diagram to display"`) alors qu'elles « marchaient » avec le `NavigatedViewer` en lecture seule de `BpmnUploadPreview.vue` (qui n'a jamais vérifié que le rendu réussissait). Fixtures dédiées avec DI complète ajoutées pour les tests de l'éditeur, sans toucher aux fixtures existantes partagées par `E2E-BPMN-1/2/3`.
- [x] T8.4 — Mise à jour documentation
  - Cette mise à jour (planning, testing, implementation, TODO §39, FILE-ATTENTE.md, `analysis doc/14`).

## Dependencies

Respectées telles que planifiées : M1→M2 avant M4 ; M3/M4 avant la partie
frontend de M6 (T6.1) — le contrat API a servi de spécification à l'agent
frontend sans qu'il ait besoin du code backend réel sous les yeux
(travail réellement parallèle, worktrees isolés) ; T6.3 fait en parallèle
de M1-M4 comme prévu ; T7.1 après M5+M6.

## Timeline & Estimates

Exécuté en 2 agents parallèles (~30 min chacun, worktrees isolés) + ~45 min
d'orchestration M8 (fusion, débogage E2E, correction du bug eventBus).

## Risks & Mitigation — issue réelle

- `bpmn-js-properties-panel`/`bpmn-js` : compatible, cf. T5.0.
- Moddle et attribut `flowable:*` non couvert : préservé, risque levé (T5.1).
- Refactor `BpmnUploadPreview.vue` : aucune régression (T6.4).
- `findDeployedXml` : contrat clé jamais déployée → vide, respecté (T3.2).
- Suppression brouillon post-déploiement : aucune régression sur les
  scénarios existants de `DeployBpmnProcessUseCaseImplTest` (T4.2).
- **Risque non anticipé, matérialisé** : le bug `eventBus`/`commandStack`
  (cf. T5.2/T8.3) n'était couvert par aucun risque identifié en Phase 4 —
  les tests Vitest mockant intégralement `bpmn-js` ne pouvaient pas le
  détecter par construction. C'est exactement la classe de risque que
  l'E2E (T8.3) est censée couvrir, et qui l'a effectivement fait ici.

## Resources Needed

`bpmn-js-properties-panel` (MIT, `^5.65.0`) — seule nouvelle dépendance,
licence confirmée avant usage (T5.0).
