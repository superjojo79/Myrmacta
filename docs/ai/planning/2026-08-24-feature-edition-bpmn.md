---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: planned
created: 2026-08-24
updated: 2026-08-24
feature: edition-bpmn
design_ref: docs/ai/design/2026-08-24-feature-edition-bpmn.md
testing_ref: docs/ai/testing/2026-08-24-feature-edition-bpmn.md
---

# Project Planning & Task Breakdown — `edition-bpmn`

> Plan initial (Phase 4). Aucune tâche démarrée — implémentation pas
> lancée (décision explicite de l'utilisateur de dérouler le cycle
> `dev-lifecycle` phase par phase). Toutes les cases `[ ]` seront cochées
> en Phase 5/6/7 avec preuve d'exécution fraîche.

## Milestones

- [ ] M1: Domaine backend brouillon (modèle, port out, use cases)
- [ ] M2: Persistence brouillon (entité JPA, adaptateur, migration Liquibase)
- [ ] M3: Lecture du XML déployé (`WorkflowDeploymentPort.findDeployedXml` + endpoint)
- [ ] M4: Endpoint brouillon + suppression post-déploiement
- [ ] M5: Frontend — canvas éditeur (`bpmn-js` Modeler + properties panel + provider Flowable)
- [ ] M6: Frontend — composables partagés (`useBpmnDraft`, `useBpmnDeployment`)
- [ ] M7: Frontend — écran dédié + intégration admin
- [ ] M8: Vérification globale (backend + frontend + E2E)

## Task Breakdown

### M1: Domaine backend brouillon

- [ ] T1.1 — `BpmnDraft` (domain model, record) — `domain/workflow/model/`
  - Dépend de : rien
  - Validation : compile
- [ ] T1.2 — `BpmnDraftRepository` (port out, pur) : `findByTenantIdAndProcessDefinitionKey`, `save`, `deleteByTenantIdAndProcessDefinitionKey` — `domain/workflow/port/out/`
  - Dépend de : T1.1
  - Validation : compile, `ArchitectureRulesTest.domain_has_no_framework_dependency` vert
- [ ] T1.3 — `SaveBpmnDraftUseCase` (port in) + `SaveBpmnDraftUseCaseImpl` : valide via `BpmnParserPort` (bien-formé + cohérence clé chemin/XML), délègue au port out — **pas** de vérification `WorkflowTypeConfigRepository` (D-EDIT-DRAFT-VALIDATION)
  - Dépend de : T1.1, T1.2
  - Validation : `SaveBpmnDraftUseCaseImplTest` (3 scénarios testing doc) exécuté et vert
- [ ] T1.4 — `GetBpmnDraftUseCase` (port in) + `GetBpmnDraftUseCaseImpl`
  - Dépend de : T1.1, T1.2
  - Validation : `GetBpmnDraftUseCaseImplTest` (2 scénarios testing doc) exécuté et vert

### M2: Persistence brouillon

- [ ] T2.1 — Migration Liquibase `013-bpmn-draft-tables.yaml` (table `bpmn_draft`, clé composite `tenant_id`+`process_definition_key`, colonnes `xml`/`updated_at`/`updated_by`, bloc `rollback`)
  - Dépend de : rien
  - Validation : `./mvnw verify` applique la migration sans erreur (H2 MODE=PostgreSQL)
- [ ] T2.2 — `BpmnDraftJpaEntity` (`@IdClass`, même style que `WorkflowIdempotencyJpaEntity`) + `SpringDataBpmnDraftRepository` — `infrastructure/persistence/workflow/`
  - Dépend de : T2.1
  - Validation : compile
- [ ] T2.3 — `JpaBpmnDraftRepositoryAdapter implements BpmnDraftRepository`
  - Dépend de : T1.2, T2.2
  - Validation : `JpaBpmnDraftRepositoryAdapterTest` (4 scénarios testing doc, JPA réel H2) exécuté et vert

### M3: Lecture du XML déployé

- [ ] T3.1 — `WorkflowDeploymentPort.findDeployedXml(tenantId, processDefinitionKey)` (nouvelle méthode sur le port existant)
  - Dépend de : rien
  - Validation : compile
- [ ] T3.2 — Implémentation dans `FlowableWorkflowDeploymentAdapter` via `RepositoryService.getProcessModel(...)` sur la dernière version
  - Dépend de : T3.1
  - Validation : `FlowableWorkflowDeploymentAdapterTest` (2 nouveaux scénarios testing doc, H2/Flowable embarqué) exécuté et vert, 4 scénarios existants non régressés
- [ ] T3.3 — `GET /api/workflow-bpmn-deployments/{processDefinitionKey}/xml` sur `WorkflowBpmnDeploymentController` (existant, modifié)
  - Dépend de : T3.2
  - Validation : `WorkflowBpmnDeploymentApiTest` (3 nouveaux scénarios testing doc) exécuté et vert, 8 scénarios existants non régressés

### M4: Endpoint brouillon + suppression post-déploiement

- [ ] T4.1 — `BpmnDraftController` (`GET`/`PUT /api/workflow-bpmn-drafts/{processDefinitionKey}`), `@PreAuthorize` même pattern que `WorkflowBpmnDeploymentController`
  - Dépend de : T1.3, T1.4, T2.3
  - Validation : `BpmnDraftApiTest` (6 scénarios testing doc) exécuté et vert ; golden OpenAPI régénéré
- [ ] T4.2 — `DeployBpmnProcessUseCaseImpl` modifié : injecte `BpmnDraftRepository`, supprime le brouillon de la clé après un déploiement réussi (D-DESIGN-DRAFT-LIFECYCLE)
  - Dépend de : T1.2, T2.3
  - Validation : `DeployBpmnProcessUseCaseImplTest` (2 nouveaux scénarios testing doc) exécuté et vert, scénarios existants non régressés
- [ ] T4.3 — Bean Spring (`WorkflowConfig`) pour les nouveaux use cases, injection `tenantProperties.id()` (même pattern que `DeployBpmnProcessUseCaseImpl`)
  - Dépend de : T1.3, T1.4, T4.2
  - Validation : contexte Spring démarre (`./mvnw verify`)

### M5: Frontend — canvas éditeur

- [ ] T5.0 — Ajout `bpmn-js-properties-panel` à `frontend/package.json` (licence MIT vérifiée avant `npm install`, D-EDIT dépendance)
  - Dépend de : rien
  - Validation : `npm install` propre, `LICENSE` du package lu et confirmée MIT
- [ ] T5.1 — `flowablePropertiesProvider.ts` (provider custom : `async`/`delegateExpression`/`expression` sur `serviceTask`, `scope` sur `signal`) — `frontend/src/components/workflow/bpmn-properties/`
  - Dépend de : T5.0
  - Validation : Vitest (4 scénarios testing doc, y compris préservation d'un attribut non couvert)
- [ ] T5.2 — `BpmnEditorCanvas.vue` (`bpmn-js/lib/Modeler` + panel standard + provider custom, événement « modifications non enregistrées » via `commandStack.changed`, `saveXML()` exposé) — `frontend/src/components/workflow/`
  - Dépend de : T5.0, T5.1
  - Validation : Vitest (3 scénarios testing doc)

### M6: Frontend — composables partagés

- [ ] T6.1 — API wrappers frontend (`getBpmnDraft`, `saveBpmnDraft`, `getDeployedBpmnXml`) dans `frontend/src/api/workflows.ts`
  - Dépend de : T3.3, T4.1 (contrat API stabilisé)
  - Validation : typecheck propre
- [ ] T6.2 — `useBpmnDraft.ts` (composable : `load`/`save`, état « non enregistré », écouteur `beforeunload`) — `frontend/src/composables/`
  - Dépend de : T6.1
  - Validation : Vitest (5 scénarios testing doc)
- [ ] T6.3 — `useBpmnDeployment.ts` (composable, extrait de la logique déjà écrite dans `BpmnUploadPreview.vue` — appel info, confirmation, `POST`) — `frontend/src/composables/`
  - Dépend de : rien (logique existante à extraire, pas de nouveau contrat API)
  - Validation : Vitest (3 scénarios testing doc)
- [ ] T6.4 — Refactor `BpmnUploadPreview.vue` pour consommer `useBpmnDeployment.ts` (contrat public inchangé)
  - Dépend de : T6.3
  - Validation : tous les tests Vitest existants (5, `deploiement-bpmn`) restent verts sans modification

### M7: Frontend — écran dédié + intégration admin

- [ ] T7.1 — `BpmnEditorView.vue` (route `/admin/bpmn-editor`, query param `processDefinitionKey` optionnel, orchestration `BpmnEditorCanvas.vue` + `useBpmnDraft` + `useBpmnDeployment`, sélecteur de fichier local pour l'import) — `frontend/src/views/admin/`
  - Dépend de : T5.2, T6.2, T6.3
  - Validation : Vitest (6 scénarios testing doc)
- [ ] T7.2 — Route `/admin/bpmn-editor` dans `frontend/src/app/routes.ts` (`meta: { roles: ['TENANT_ADMIN', 'WORKFLOW_ADMIN'] }`)
  - Dépend de : T7.1
  - Validation : garde de rôle vérifiée (Vitest routeur ou E2E)
- [ ] T7.3 — Lien « Éditer » par config dans `WorkflowConfigsAdminView.vue`
  - Dépend de : T7.2
  - Validation : Vitest (1 scénario testing doc) + E2E

### M8: Vérification globale

- [ ] T8.1 — `./mvnw verify` complet (backend)
  - Dépend de : M1, M2, M3, M4
  - Validation : 0 régression, seuils Jacoco maintenus (LINE ≥ 85%, BRANCH ≥ 75%)
- [ ] T8.2 — `npm run test` + `typecheck` + `lint` (frontend)
  - Dépend de : M5, M6, M7
  - Validation : 0 régression
- [ ] T8.3 — `npm run test:e2e` (Playwright, obligatoire — feature touche le frontend)
  - Dépend de : M7, T8.2
  - Validation : preuve fraîche (commande, code de sortie, compte de passes) consignée dans le testing doc, 4 nouveaux scénarios `E2E-EDIT-BPMN-1..4` verts
- [ ] T8.4 — Mise à jour documentation (`analysis doc/14-edition-bpmn.md`, `TODO.md` §39, `FILE-ATTENTE.md`) reflétant l'implémentation livrée
  - Dépend de : T8.1, T8.2, T8.3
  - Validation : revue manuelle, cohérence avec l'état réel du code

## Dependencies

- T4.1 (endpoint brouillon) dépend de T1.3/T1.4 (use cases) et T2.3
  (adaptateur JPA) — séquencer M1→M2 avant M4.
- T6.1 (wrappers API frontend) dépend du contrat API backend stabilisé
  (T3.3, T4.1) — séquencer M3/M4 avant la partie frontend de M6, comme
  pour `deploiement-bpmn` (T4.1 y dépendait de T2.3).
- T6.3 (extraction `useBpmnDeployment.ts`) n'a **pas** de dépendance
  backend — peut être fait en parallèle de M1-M4, contrairement à T6.1/T6.2.
- T7.1 dépend de M5 **et** M6 complets (assemble canvas + composables).
- Aucune dépendance externe nouvelle côté backend (`flowable-bpmn-converter`,
  `RepositoryService` déjà présents). Côté frontend, seule nouvelle
  dépendance : `bpmn-js-properties-panel` (T5.0).

## Timeline & Estimates

Feature avec parcours utilisateur admin complet (backend + UI + E2E) —
pas d'estimation en jours demandée, séquencement par dépendance :
M1 → M2 → M3/M4 (parallélisables) → M5/M6 (T6.3 peut démarrer tôt) → M7 → M8.

## Risks & Mitigation

- **Risque** : `bpmn-js-properties-panel` peut avoir des contraintes de
  version/peer-dependency avec `bpmn-js` `^18.24.0` déjà fixé.
  **Mitigation** : vérifier la matrice de compatibilité officielle
  bpmn.io avant `npm install` (T5.0), avant d'écrire du code contre
  l'API du panel.
- **Risque** : comportement du moddle `bpmn-js` sur un attribut
  `flowable:*` non couvert par `flowablePropertiesProvider.ts` — supposé
  préservé (moddle générique), pas vérifié en Phase 3 (point ouvert
  design). **Mitigation** : scénario dédié en T5.1 (testing doc) avant
  d'aller plus loin — si le moddle perd effectivement l'attribut, retour
  en design pour élargir `BpmnDraftValidation`/le moddle custom, pas une
  simple correction de bug.
- **Risque** : refactor de `BpmnUploadPreview.vue` (T6.4) casse le
  comportement existant. **Mitigation** : contrat public préservé par
  design (D-EDIT-DEPLOY-EXTRACT), validation = les 5 tests Vitest
  existants restent verts sans modification, pas de nouveau test requis
  pour ce composant au-delà de la non-régression.
- **Risque** : `findDeployedXml` (T3.2) — `RepositoryService.getProcessModel`
  peut lever une exception ou renvoyer `null` selon l'état du déploiement
  (ressource BPMN absente d'un déploiement partiel). **Mitigation** :
  scénario dédié « clé jamais déployée → résultat vide » en T3.2 pour
  fixer le contrat avant l'endpoint (T3.3).
- **Risque** : suppression du brouillon post-déploiement (T4.2) introduit
  un effet de bord dans `DeployBpmnProcessUseCaseImpl`, use case déjà
  existant et testé (§35). **Mitigation** : les 3 scénarios existants de
  `DeployBpmnProcessUseCaseImplTest` doivent rester verts sans
  modification ; les 2 nouveaux scénarios sont additifs, pas des
  remplacements.

## Resources Needed

- Aucune ressource externe/humaine supplémentaire.
- Nouvelle dépendance frontend unique : `bpmn-js-properties-panel` (MIT,
  licence à confirmer explicitement en T5.0 avant `npm install`).
