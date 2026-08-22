---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-08-22
updated: 2026-08-22
feature: deploiement-bpmn
design_ref: docs/ai/design/2026-08-22-feature-deploiement-bpmn.md
testing_ref: docs/ai/testing/2026-08-22-feature-deploiement-bpmn.md
---

# Project Planning & Task Breakdown — `deploiement-bpmn`

## Milestones

- [ ] M1: Domaine backend (use cases, port out, exceptions, value objects)
- [ ] M2: Adaptateur Flowable + endpoint web
- [ ] M3: Frontend — `BpmnCanvas.vue` (extraction) + refactor `BpmnViewer.vue`
- [ ] M4: Frontend — `BpmnUploadPreview.vue` + intégration dans `WorkflowConfigsAdminView.vue`
- [ ] M5: Documentation (`README.md`, `05-workflow-engine.md`, `GUIDE-UTILISATEUR.md`) + TODO §35
- [ ] M6: Vérification globale (backend + frontend + E2E)

## Task Breakdown

### M1: Domaine backend

- [ ] T1.1 — `ProcessDeploymentInfo`, `InvalidBpmnException`, `UnknownWorkflowKeyException` (domain model)
  - Dépend de : rien
  - Validation : compile, tests unitaires triviaux si logique non-trivale (sinon simples records/exceptions)
- [ ] T1.2 — `WorkflowDeploymentPort` (port out) : `findDeploymentInfo(tenantId, key)`, `deploy(tenantId, filename, bytes)`
  - Dépend de : T1.1
  - Validation : compile
- [ ] T1.3 — `DeployBpmnProcessUseCase` (port in) + `DeployBpmnProcessUseCaseImpl` : parse via `BpmnXMLConverter`, vérifie `WorkflowTypeConfigRepository.findByWorkflowKey`, délègue au port out
  - Dépend de : T1.1, T1.2
  - Validation : `DeployBpmnProcessUseCaseImplTest` (4 scénarios testing doc) exécuté et vert
- [ ] T1.4 — `GetProcessDeploymentInfoUseCase` (port in) + Impl
  - Dépend de : T1.1, T1.2
  - Validation : `GetProcessDeploymentInfoUseCaseImplTest` (2 scénarios) exécuté et vert

### M2: Adaptateur Flowable + endpoint web

- [ ] T2.1 — `FlowableWorkflowDeploymentAdapter implements WorkflowDeploymentPort` (`RepositoryService`/`RuntimeService`)
  - Dépend de : T1.2
  - Validation : `FlowableWorkflowDeploymentAdapterTest` (3 scénarios, déploiement réel H2/Flowable embarqué) exécuté et vert
- [ ] T2.2 — `BpmnUploadProperties` (config content-type, sans champ de taille — D-BPMN-UPLOAD-SIZE)
  - Dépend de : rien
  - Validation : tests unitaires (3 scénarios testing doc)
- [ ] T2.3 — `WorkflowBpmnDeploymentController` (`GET .../info`, `POST` multipart), `@PreAuthorize` même pattern que `WorkflowTypeConfigController`
  - Dépend de : T1.3, T1.4, T2.2
  - Validation : test d'intégration (7 scénarios testing doc, y compris `403`) exécuté et vert
- [ ] T2.4 — Gestion d'erreurs dans `WorkflowExceptionHandler` (existant) : `InvalidBpmnException` → `400`, `UnknownWorkflowKeyException` → code à trancher en implémentation
  - Dépend de : T1.1, T2.3
  - Validation : couvert par les tests d'intégration T2.3

### M3: Frontend — extraction `BpmnCanvas.vue`

- [ ] T3.1 — `BpmnCanvas.vue` (rendu bpmn-js bas-niveau, marqueurs optionnels)
  - Dépend de : rien
  - Validation : Vitest (2 scénarios testing doc)
- [ ] T3.2 — Refactor `BpmnViewer.vue` pour utiliser `BpmnCanvas.vue` en interne, contrat public inchangé
  - Dépend de : T3.1
  - Validation : tous les tests Vitest existants de `BpmnViewer.vue`/`DossierDetailView.vue` restent verts sans modification

### M4: Frontend — upload + intégration admin

- [ ] T4.1 — API wrappers frontend (`getDeploymentInfo(key)`, `deployBpmn(file)`) dans `frontend/src/api/workflow.ts` ou fichier dédié
  - Dépend de : T2.3 (contrat API stabilisé)
  - Validation : typecheck propre
- [ ] T4.2 — `BpmnUploadPreview.vue` (sélection fichier, `FileReader`, rendu via `BpmnCanvas.vue`, appel info, confirmation, déploiement)
  - Dépend de : T3.1, T4.1
  - Validation : Vitest (4 scénarios testing doc)
- [ ] T4.3 — Intégration dans `WorkflowConfigsAdminView.vue` (nouvelle section)
  - Dépend de : T4.2
  - Validation : Vitest (1 scénario) + E2E

### M5: Documentation

- [ ] T5.1 — Corriger `README.md` (section Proposition de valeur/Fonctionnalités) : retirer « designer visuel intégré », « sans redémarrage » comme automatiques, « cockpit Flowable » ; documenter le mécanisme réel (scan classpath + upload admin ciblé)
  - Dépend de : rien (peut être fait en parallèle du reste)
  - Validation : revue manuelle
- [ ] T5.2 — Corriger `05-workflow-engine.md` (même correction)
  - Dépend de : rien
  - Validation : revue manuelle
- [ ] T5.3 — Mettre à jour `GUIDE-UTILISATEUR.md` : ajouter le nouveau mécanisme d'upload à côté du dépôt manuel dans le repo (les deux coexistent — scan classpath au build/démarrage reste la référence CI, l'upload admin est le complément à chaud)
  - Dépend de : M4 terminé (pour décrire l'écran réel)
  - Validation : revue manuelle
- [ ] T5.4 — Clore TODO §35
  - Dépend de : tout ce qui précède
  - Validation : revue manuelle, cohérence avec l'état réel du code

### M6: Vérification globale

- [ ] T6.1 — `./mvnw verify` complet (backend)
  - Dépend de : M1, M2
  - Validation : 0 régression, seuils Jacoco maintenus
- [ ] T6.2 — `npm run test` + `typecheck` + `lint` (frontend)
  - Dépend de : M3, M4
  - Validation : 0 régression
- [ ] T6.3 — `npm run test:e2e` (Playwright, obligatoire — feature touche le frontend)
  - Dépend de : M4, T6.2
  - Validation : preuve fraîche (commande, code de sortie, compte de passes) consignée dans le testing doc

## Dependencies

- T4.1 (wrappers API frontend) dépend du contrat API backend stabilisé
  (T2.3) — séquencer M2 avant la partie frontend de M4 pour éviter de
  refaire les wrappers si le contrat change en cours de route.
- T5.3 (GUIDE-UTILISATEUR.md) dépend de l'écran réel construit (M4) pour
  décrire précisément le parcours, pas une intention.
- Aucune dépendance externe nouvelle (`flowable-bpmn-converter` déjà
  présent transitivement).

## Timeline & Estimates

Feature produit avec parcours utilisateur admin complet (backend + UI +
E2E) — pas d'estimation en jours demandée, séquencement par dépendance
(M1 → M2 → M3 → M4 → M6, M5 en parallèle dès que possible).

## Risks & Mitigation

- **Risque** : le refactor de `BpmnViewer.vue` (M3) casse le seul
  consommateur existant (`DossierDetailView.vue`). **Mitigation** :
  contrat public (`diagram`, `unavailable`) explicitement préservé par
  design (D-BPMN-PREVIEW-COMPONENT) ; validation = tests Vitest
  existants inchangés et verts, pas de nouveau test à écrire pour ce
  composant.
- **Risque** : `BpmnXMLConverter` (parsing pré-déploiement) a un
  comportement différent de la validation native de
  `RepositoryService.createDeployment().deploy()` — un fichier accepté
  par le parseur pourrait être rejeté par le moteur au déploiement
  réel, ou l'inverse. **Mitigation** : `FlowableWorkflowDeploymentAdapterTest`
  exerce le vrai déploiement Flowable, pas seulement le parsing
  préalable — écart détecté par les tests, pas supposé.
- **Risque** : code HTTP pour `UnknownWorkflowKeyException` pas
  explicitement tranché en design (400 vs 409 vs 422). **Mitigation** :
  décision différée à l'implémentation (T2.4), en cohérence avec les
  codes déjà utilisés par `WorkflowExceptionHandler` pour des erreurs de
  validation similaires — pas un blocage, un détail à trancher en
  cours de route sans redemander à l'utilisateur (pas un choix produit).
- **Risque** : E2E Playwright doit mocker un upload de fichier réel —
  pattern potentiellement nouveau dans `frontend/e2e/`. **Mitigation** :
  vérifier en implémentation s'il existe déjà un précédent d'upload de
  fichier testé en E2E (`DossierElementUploadController` a un
  équivalent frontend) avant d'inventer un nouveau mécanisme.

## Resources Needed

- Aucune ressource externe/humaine supplémentaire — travail solo IA sur
  la branche `claude/queue-next-feature-8r054i`.
- Aucune nouvelle dépendance (`flowable-bpmn-converter` déjà présent).
