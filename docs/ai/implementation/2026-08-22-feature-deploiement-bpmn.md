---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: done
created: 2026-08-22
updated: 2026-08-22
feature: deploiement-bpmn
design_ref: docs/ai/design/2026-08-22-feature-deploiement-bpmn.md
planning_ref: docs/ai/planning/2026-08-22-feature-deploiement-bpmn.md
---

# Implementation Guide — `deploiement-bpmn`

20 tâches planifiées (T1.1–T6.3) exécutées en TDD sur 6 milestones
(M1 domaine backend, M2 adaptateur Flowable + endpoint, M3 extraction
`BpmnCanvas.vue`, M4 upload + intégration admin, M5 documentation, M6
vérification globale), plus Phase 6 (planning reconcilié) et Phase 7
(check implementation — revue fichier par fichier, 1 vraie
`NullPointerException` potentielle trouvée et corrigée). `./mvnw
verify` final : **1075 tests, 0 échec**, seuils Jacoco tenus ; frontend
**163 tests Vitest, 0 échec**, typecheck/lint propres ; **43/43
Playwright** (3 nouveaux, 0 régression). Statut : implémentation
**terminée et revue** ; prochaine étape `dev-review` (Phase 9, close
dans ce document).

## Development Setup

Aucun changement d'environnement — même stack que le reste du projet
(JDK 25, `flowable-bpmn-converter` déjà dépendance transitive de
`flowable-spring-boot-starter`, aucune nouvelle dépendance ajoutée).

## Code Structure

- `domain/workflow/model/` — `ProcessDeploymentInfo`,
  `BpmnDeploymentResult`, `InvalidBpmnException`,
  `UnknownWorkflowKeyException`.
- `domain/workflow/port/{in,out}/` — `DeployBpmnProcessUseCase`,
  `GetProcessDeploymentInfoUseCase` (in) ; `WorkflowDeploymentPort`,
  `BpmnParserPort` (out).
- `domain/workflow/usecase/` — `DeployBpmnProcessUseCaseImpl`,
  `GetProcessDeploymentInfoUseCaseImpl`.
- `infrastructure/flowable/` — `FlowableWorkflowDeploymentAdapter`
  (implémente `WorkflowDeploymentPort`), `FlowableBpmnParserAdapter`
  (implémente `BpmnParserPort`).
- `infrastructure/config/BpmnUploadProperties.java` — allowlist
  content-type, sans champ de taille.
- `infrastructure/web/workflow/` — `WorkflowBpmnDeploymentController`,
  `WorkflowBpmnDeploymentApiDtos` (`ProcessDeploymentInfoResponse`,
  `BpmnDeploymentResultResponse`), `WorkflowExceptionHandler` (modifié :
  2 nouveaux mappings).
- `frontend/src/components/workflow/` — `BpmnCanvas.vue` (nouveau, bas
  niveau), `BpmnViewer.vue` (refactor interne), `BpmnUploadPreview.vue`
  (nouveau).
- `frontend/src/api/workflows.ts` — `getBpmnDeploymentInfo`,
  `deployBpmn` (modifié, ajout).
- `frontend/src/views/admin/WorkflowConfigsAdminView.vue` — section
  BPMN ajoutée en bas de page.

## Implementation Notes

### Core Features

- **Parsing/validation BPMN** — extrait derrière un port
  (`BpmnParserPort`/`FlowableBpmnParserAdapter`), voir Design
  Deviations : le domaine ne doit pas dépendre de `org.flowable..`.
- **Déploiement Flowable réel** (`FlowableWorkflowDeploymentAdapter`) —
  `RepositoryService.createDeployment().addBytes(...).deploy()` ;
  normalise le nom de ressource pour garantir un suffixe reconnu par
  Flowable (`.bpmn20.xml`/`.bpmn`/`.xml`) même si le nom de fichier
  original ne l'a pas ou est absent (voir Design Deviations).
- **Rejet strict clé inconnue** (D-BPMN-KEY-VALIDATION) —
  `UnknownWorkflowKeyException` → `404`, groupé avec
  `WorkflowTypeConfigNotFoundException` dans `WorkflowExceptionHandler`
  (même condition métier, symétrie avec `AttachWorkflowUseCase`).
- **Prévisualisation 100% locale** (`BpmnUploadPreview.vue`) — lecture
  via `File.text()`, extraction du `processDefinitionKey` par regex
  côté client (non-barrière de sécurité, juste pour l'appel
  informatif `GET .../info`), rendu via `BpmnCanvas.vue`, aucun appel
  de déploiement avant confirmation explicite.
- **`BpmnCanvas.vue`** — extrait de `BpmnViewer.vue`, contrat public de
  ce dernier inchangé (`diagram`, `unavailable`), seul consommateur
  (`DossierDetailView.vue`) non impacté.

### Patterns & Best Practices

- **Précédents de code réutilisés systématiquement** :
  `@PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")` au
  niveau classe (pattern `ConnectorAdminController`, pas
  `WorkflowTypeConfigController` qui garde seulement create/update —
  ce choix explicite est documenté en design, les deux endpoints BPMN
  sont gardés) ; patron multipart (`DossierElementUploadController`) ;
  style d'adaptateur Flowable (`FlowableWorkflowVisualizationAdapter`).
- **Aucun `AccessContextFactory.requireOidcPrincipal`** dans
  `WorkflowBpmnDeploymentController` — vérifié en Phase 7 contre
  `ConnectorAdminController` (endpoints admin purs, sans besoin
  d'identité d'acteur pour une ACL) : ce n'est pas un gap, c'est le bon
  précédent à suivre (`WorkflowTypeConfigController` l'utilise mais
  sert un cas différent — accès en lecture ouvert à tout utilisateur
  authentifié sur `list`/`get`, garde sur `create`/`update` seulement).
- **Validation en profondeur, jamais côté client seul** — la
  prévisualisation frontend n'est qu'un confort ; tout (BPMN 2.0
  valide, clé connue) est revalidé côté serveur avant tout effet de
  bord, cohérent avec `DossierElementUploadController`.

## Design Deviations

1. **Écart d'architecture trouvé et corrigé (T1.3, TDD)** :
   `BpmnXMLConverter` (parsing BPMN) ne pouvait pas rester inline dans
   `DeployBpmnProcessUseCaseImpl` — `ArchitectureRulesTest.domain_has_no_framework_dependency`
   l'a détecté immédiatement (le domaine ne doit pas dépendre de
   `org.flowable..`). Extrait en `BpmnParserPort` (port out, nouveau,
   non prévu au design initial) + `FlowableBpmnParserAdapter`
   (infrastructure). Couvert par `FlowableBpmnParserAdapterTest` (3
   scénarios).
2. **Écart de couverture BRANCH trouvé et comblé (T6.1)** — `./mvnw
   verify` complet a montré BRANCH réel à 74.92%, sous le seuil de
   75% : le chemin de rejet content-type de
   `WorkflowBpmnDeploymentController.deploy` n'était exercé par aucun
   test existant (tous utilisaient un content-type autorisé). Un
   scénario ciblé (`deploy_disallowedContentType_returns400`) a suffi
   à repasser au-dessus du seuil.
3. **Bug réel trouvé et corrigé en Phase 7 (Check Implementation)** :
   `FlowableWorkflowDeploymentAdapter.bpmnResourceName(String filename)`
   appelait `filename.endsWith(...)` sans garde contre `null`. Un
   `MultipartFile.getOriginalFilename()` peut légitimement renvoyer
   `null` en production (partie multipart sans paramètre `filename`
   dans son en-tête `Content-Disposition` — le contrat de l'interface
   le documente explicitement), ce qui aurait provoqué une
   `NullPointerException` non gérée (500) au lieu d'un comportement
   contrôlé. `MockMultipartFile` ne peut pas reproduire ce cas exact
   (son constructeur remplace un `null` par `""`), donc aucun test
   d'intégration ne l'avait exercé. Corrigé par une garde `null` dans
   `bpmnResourceName` (traite `null` comme `""`, déjà le comportement
   sûr existant pour une chaîne vide) ; couvert par un test direct sur
   l'adaptateur (`deploy_nullFilename_doesNotThrowAndDeploysWithDefaultResourceName`,
   `FlowableWorkflowDeploymentAdapterTest`) plutôt qu'au niveau API
   (impossible à reproduire fidèlement via `MockMultipartFile`) ; un
   test API complémentaire (`deploy_blankOriginalFilename_stillDeploysInsteadOfCrashing`)
   couvre le cas `""` réellement simulable par `MockMultipartFile`.
4. **Robustesse mineure trouvée et corrigée en Phase 7** :
   `BpmnUploadPreview.vue`'s regex d'extraction du `processDefinitionKey`
   ne matchait que les attributs `id="..."` entre guillemets doubles ;
   XML autorise aussi les guillemets simples (`id='...'`). Corrigé
   (`\s*=\s*["']([^"']+)["']`) ; couvert par un nouveau test Vitest.
   Impact limité (prévisualisation seulement, pas une barrière de
   sécurité — le backend revalide toujours via `BpmnXMLConverter`),
   corrigé par souci de robustesse plutôt que laissé en dette.
5. **Documentation incohérente trouvée et corrigée** — le design doc
   affirmait « 7 décisions techniques actées » en Phase 3 mais n'en
   documentait que 5 (les 5 lignes du tableau Design Decisions).
   Vérifié dans l'historique de session : 5 appels `AskUserQuestion`
   réels en Phase 3 (le « 7 » comptait par erreur les 2 questions déjà
   closes en Phase 1/2). Corrigé dans le design doc.

## Coverage

Aucun changement aux seuils Jacoco existants (`fiabilisation-tests-plateforme` :
LINE 85%, BRANCH 75%). État final (`./mvnw verify`, 1075 tests) : tous
les seuils tenus (voir Design Deviation 2 pour l'écart trouvé et
comblé en cours de route).

## Files Changed

**Production (backend) :**
- `src/main/java/com/flowforge/domain/workflow/model/{ProcessDeploymentInfo,BpmnDeploymentResult,InvalidBpmnException,UnknownWorkflowKeyException}.java`
- `src/main/java/com/flowforge/domain/workflow/port/in/{DeployBpmnProcessUseCase,GetProcessDeploymentInfoUseCase}.java`
- `src/main/java/com/flowforge/domain/workflow/port/out/{WorkflowDeploymentPort,BpmnParserPort}.java`
- `src/main/java/com/flowforge/domain/workflow/usecase/{DeployBpmnProcessUseCaseImpl,GetProcessDeploymentInfoUseCaseImpl}.java`
- `src/main/java/com/flowforge/infrastructure/flowable/{FlowableWorkflowDeploymentAdapter,FlowableBpmnParserAdapter}.java`
- `src/main/java/com/flowforge/infrastructure/config/BpmnUploadProperties.java`
- `src/main/java/com/flowforge/infrastructure/config/WorkflowConfig.java` (modifié — 4 nouveaux beans)
- `src/main/java/com/flowforge/infrastructure/web/workflow/{WorkflowBpmnDeploymentController,WorkflowBpmnDeploymentApiDtos}.java`
- `src/main/java/com/flowforge/infrastructure/web/workflow/WorkflowExceptionHandler.java` (modifié)
- `src/main/resources/application.yml` (modifié — `app.workflow.bpmn-upload`)
- `openapi/openapi.yaml` (golden régénéré)

**Production (frontend) :**
- `frontend/src/components/workflow/BpmnCanvas.vue` (nouveau)
- `frontend/src/components/workflow/BpmnViewer.vue` (modifié — refactor interne)
- `frontend/src/components/workflow/BpmnUploadPreview.vue` (nouveau)
- `frontend/src/api/types.ts`, `frontend/src/api/workflows.ts` (modifiés)
- `frontend/src/views/admin/WorkflowConfigsAdminView.vue` (modifié)

**Tests (nouveaux, sauf mention) :**
- `src/test/java/com/flowforge/domain/workflow/usecase/{DeployBpmnProcessUseCaseImplTest,GetProcessDeploymentInfoUseCaseImplTest}.java`
- `src/test/java/com/flowforge/infrastructure/flowable/{FlowableWorkflowDeploymentAdapterTest,FlowableBpmnParserAdapterTest}.java`
- `src/test/java/com/flowforge/infrastructure/config/BpmnUploadPropertiesTest.java`
- `src/test/java/com/flowforge/infrastructure/web/workflow/WorkflowBpmnDeploymentApiTest.java`
- `frontend/src/components/workflow/{BpmnCanvas.spec.ts,BpmnUploadPreview.spec.ts}`
- `frontend/src/api/workflowBpmnDeployment.spec.ts`
- `frontend/src/api/apiCoverage.spec.ts` (modifié)
- `frontend/src/views/admin/adminViews.spec.ts` (modifié)
- `frontend/e2e/pilotage.spec.ts` (modifié — 3 nouveaux scénarios `E2E-BPMN-*`)

**Documentation :**
- `README.md`, `docs/specs/05-workflow-engine.md` (mentions non livrées retirées)
- `GUIDE-UTILISATEUR.md` (§3 réécrit — 2 mécanismes de déploiement documentés)
- `TODO.md` (§35 clos), `FILE-ATTENTE.md` (reconcilié)

## Integration Points

Aucune API externe modifiée. Nouveaux endpoints `/api/workflow-bpmn-deployments`
et `/api/workflow-bpmn-deployments/info`, isolés (pas d'appelant existant
à migrer). `BpmnViewer.vue` : contrat public préservé, aucun appelant à
modifier.

## Error Handling

Voir Design Deviations 1 et 3. `WorkflowExceptionHandler` : `InvalidBpmnException` → `400`,
`UnknownWorkflowKeyException` → `404` (groupé avec `WorkflowTypeConfigNotFoundException`).
Toute validation (content-type, BPMN 2.0, clé connue) revalidée côté
serveur, jamais confiance au client (cohérent avec
`DossierElementUploadController`).

## Performance Considerations

Aucun changement de performance en production. Déploiement BPMN = opération
admin ponctuelle, pas un chemin chaud. Prévisualisation 100% locale
côté client (aucun coût serveur avant confirmation).

## Security Notes

- **Autorisation** : `@PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")`
  au niveau classe sur `WorkflowBpmnDeploymentController` — les deux
  endpoints (`GET .../info`, `POST`) gardés, décision explicite du
  design (au-delà du garde minimal de `WorkflowTypeConfigController`
  sur `list`/`get`).
- **Validation d'entrée** : content-type allowlist, parsing/validation
  BPMN 2.0 côté serveur avant tout effet de bord, clé de processus
  vérifiée contre `WorkflowTypeConfigRepository` avant déploiement —
  aucune confiance dans la prévisualisation frontend.
- **Robustesse** : voir Design Deviation 3 (NPE potentielle sur
  `originalFilename` null trouvée et corrigée).
- **Pas de nouvelle surface d'attaque** : aucune nouvelle dépendance,
  `flowable-bpmn-converter` déjà présent transitivement et déjà utilisé
  ailleurs dans le moteur.

## Phase 9 — revue finale (2026-08-22)

Revue holistique (au-delà du fichier-par-fichier de Phase 7) :

- **Alignement design** : les 6 success criteria du requirements doc et
  les 5 décisions techniques du design doc (Phase 3, corrigé de « 7 »
  à « 5 » en Phase 7 après vérification dans l'historique de session —
  voir Design Deviation 5) sont tous couverts (tableau de traçabilité
  design doc). Aucun écart.
- **Cohérence avec modules similaires** : pattern `@PreAuthorize` suit
  `ConnectorAdminController` (vérifié explicitement en Phase 7, pas
  seulement supposé) ; patron multipart suit
  `DossierElementUploadController` ; style d'adaptateur Flowable suit
  `FlowableWorkflowVisualizationAdapter` — aucune divergence non
  justifiée.
- **Duplication** : aucune — `BpmnParserPort`/`FlowableBpmnParserAdapter`
  est une extraction motivée par une règle ArchUnit réelle, pas une
  duplication de logique existante.
- **Rupture de contrat** : aucune — tous les composants sont nouveaux
  ou (pour `BpmnViewer.vue`) refactorés avec contrat public strictement
  préservé et vérifié par les tests existants inchangés.
- **Sécurité rollback** : aucune migration de base de données (le
  versionnement est entièrement porté par les tables natives Flowable,
  déjà en place) ; `pom.xml` inchangé (aucune nouvelle dépendance) ;
  revert trivial si nécessaire.
- **2 vraies déviations trouvées et corrigées en Phase 7** (au-delà des
  2 déjà trouvées et corrigées en cours d'implémentation, Design
  Deviations 1-2) : NPE potentielle sur filename `null` (Design
  Deviation 3), extraction regex trop stricte côté prévisualisation
  frontend (Design Deviation 4). Aucune autre trouvée après relecture
  fichier par fichier des 44 fichiers modifiés/créés.

**Checklist finale :** design ✅ · pas de gap logique ✅ (2 trouvés et
corrigés en Phase 7) · sécurité ✅ · points d'intégration vérifiés ✅ ·
tests couvrent les changements ✅ (1075 backend + 163 frontend + 43
Playwright, tous verts) · docs à jour ✅ (requirements, design,
planning, implementation, testing, TODO.md, FILE-ATTENTE.md).

**Aucun blocage.** Feature prête à être poussée / PR à ouvrir sur
demande explicite de l'utilisateur (convention établie cette session).
