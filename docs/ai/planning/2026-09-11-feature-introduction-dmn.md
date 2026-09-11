---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown — `introduction-dmn`

> 12 milestones séquencés par dépendance (plus large que les 8 de `edition-bpmn` — le périmètre
> retenu en Phase 1 cumule activation moteur + durcissement sécurité + éditeur intégré + MCP dans
> un seul cycle, assumé explicitement). Chaque tâche référence les scénarios de
> [testing](../testing/2026-09-11-feature-introduction-dmn.md) (`T*`) qu'elle couvre — tout
> scénario T* est couvert par au moins une tâche (vérifié ci-dessous).

## Milestones

- [ ] **M1** — Grammaire FEEL de l'allowlist (spike bloquant, prérequis de M2)
- [ ] **M2** — Activation moteur + parsing/validation DMN (domaine + `FlowableDmnParserAdapter`)
- [ ] **M3** — Déploiement DMN (`DmnDeploymentPort` + `DeployDecisionTableUseCase`)
- [ ] **M4** — Exception BPMN `serviceTask type="dmn"` + vérification clé au déploiement process
- [ ] **M5** — Persistence brouillon (backend)
- [ ] **M6** — Endpoints admin REST déploiement
- [ ] **M7** — Éditeur frontend (`dmn-js` Modeler)
- [ ] **M8** — Complément éditeur BPMN existant (référencer une table depuis un `serviceTask`)
- [ ] **M9** — Écran admin `/admin/dmn-editor` + navigation
- [ ] **M10** — Tools MCP
- [ ] **M11** — Scénario illustratif livré (fixtures grille d'éligibilité)
- [ ] **M12** — Documentation + vérification globale

## Task Breakdown

### M1 — Grammaire FEEL de l'allowlist (bloquant, avant tout code de parsing)

- [ ] **1.1** Investiguer le moteur FEEL utilisé par Flowable DMN (bibliothèque, mode restreint
  disponible pour désactiver invocation de fonction externe / navigation de contexte arbitraire ?)
  — outcome : note technique tranchant « restriction au niveau texte XML (miroir
  `assertSafeConditionExpression`) » vs « configuration du moteur FEEL lui-même », si l'option
  existe. Dépendances : aucune. Validation : lecture du code/doc Flowable DMN réel, pas supposé.
- [ ] **1.2** Définir la grammaire allowlist exacte à partir du scénario grille d'éligibilité
  (littéraux, comparaisons, intervalles `[a..b]`, listes `"a","b"`, `-`, `and`/`or`/`not()` —
  périmètre minimal nécessaire au scénario retenu, pas une grammaire FEEL générique). Dépendances :
  1.1. Validation : document de conception court (peut vivre dans le Javadoc de
  `FlowableDmnParserAdapter`, comme `assertSafeConditionExpression`), confirmé par relecture avant
  d'écrire le moindre test.

### M2 — Activation moteur + parsing/validation DMN

- [ ] **2.1** Retirer `DmnEngineAutoConfiguration`/`DmnEngineServicesAutoConfiguration` de
  `application.yml`. Dépendances : aucune (peut démarrer en parallèle de M1). Validation :
  `./mvnw verify` démarre toujours, aucune régression.
- [ ] **2.2** Domaine : `DecisionTableDeploymentResult`, `DecisionTableDeploymentInfo`, `DmnDraft`
  (records), `InvalidDmnException` (miroir `InvalidBpmnException`). Dépendances : aucune.
- [ ] **2.3** `DmnParserPort` (port.out) + `FlowableDmnParserAdapter` implémentant la grammaire de
  1.2. Dépendances : 1.2, 2.2. Validation : T1.1, T1.2, T1.3, T1.4 (TDD — test qui échoue d'abord).
- [ ] **2.4** Test d'intégration réel contre le vrai moteur DMN (miroir
  `FlowableBpmnAllowlistGapTest`) — preuve empirique qu'une expression construite pour contourner
  la grammaire échoue réellement à l'exécution, pas seulement à la lecture statique. Dépendances :
  2.3, moteur activé (2.1). Validation : T5.1.

### M3 — Déploiement DMN

- [ ] **3.1** `DmnDeploymentPort` (port.out) + `FlowableDmnDeploymentAdapter`
  (`DmnRepositoryService` — API exacte à vérifier contre Flowable DMN réel, design §Points
  restants). Dépendances : 2.1, 2.2.
- [ ] **3.2** `DeployDecisionTableUseCase`/`Impl` (parse → déploie → supprime brouillon associé).
  Dépendances : 2.3, 3.1, M5 (repository brouillon — ou stub temporaire si M5 n'est pas encore
  livré, à trancher en implémentation). Validation : T3.1, T3.2.

### M4 — Exception BPMN `serviceTask type="dmn"` + vérification clé

- [ ] **4.1** Modifier `FlowableBpmnParserAdapter.assertSafeServiceTask` — exception stricte
  `type="dmn"` (littéral, un seul `flowable:field name="decisionTableReferenceKey"` en
  `flowable:string`). Dépendances : aucune techniquement, mais à ne merger qu'avec 4.2 (pas de
  fenêtre où le parsing accepte la forme sans que la clé soit vérifiée à l'usage). Validation :
  T2.1, T2.2, T2.3, T2.4, T2.5.
- [ ] **4.2** Modifier `DeployBpmnProcessUseCaseImpl` pour vérifier que tout
  `decisionTableReferenceKey` référencé par le process correspond à une table **déployée**
  (miroir `UnknownWorkflowKeyException`). Dépendances : 4.1, 3.1 (savoir interroger les tables
  déployées). Validation : T4.1, T4.2.
- [ ] **4.3** Non-régression : les 7 process BPMN déjà livrés se déploient sans changement de
  comportement après 4.1/4.2 (miroir `FlowableBpmnParserAdapterShippedProcessesTest`). Dépendances
  : 4.1, 4.2. Validation : T5.2.
- [ ] **4.4** Scénario de bout en bout réel : process BPMN avec `serviceTask type="dmn"` → décision
  DMN évaluée → variable(s) de process lisible(s), et masquage `SendNotificationUseCaseImpl`
  vérifié sur une valeur sensible produite. Dépendances : 2.4, 3.2, 4.2, 11.1 (fixture). Validation
  : T5.5, T5.6.

### M5 — Persistence brouillon (backend)

- [ ] **5.1** Migration Liquibase `dmn_draft` (contexte tenant, numéro de fichier réservé dans la
  séquence existante — design « Points restants »). Dépendances : aucune.
- [ ] **5.2** `DmnDraftRepository` (port.out) + `DmnDraftJpaEntity` + adaptateur JPA (composite
  `tenant_id`/`decision_key`, miroir `BpmnDraftJpaEntity`). Dépendances : 5.1, 2.2.
- [ ] **5.3** `DmnDraftController` (`GET`/`PUT /api/dmn-decision-drafts/{decisionKey}`).
  Dépendances : 5.2. Validation : T5.3 (partiel — cycle brouillon).

### M6 — Endpoints admin REST déploiement

- [ ] **6.1** `DmnDecisionDeploymentController` (`GET info`, `GET .../xml`, `POST` déploiement,
  `TENANT_ADMIN`/`WORKFLOW_ADMIN`, miroir `WorkflowBpmnDeploymentController`). Dépendances : 3.2,
  5.3 (suppression brouillon post-déploiement). Validation : T5.3 (RBAC + cycle complet).

### M7 — Éditeur frontend (`dmn-js` Modeler) — parallélisable à M5/M6 une fois M2-M4 stabilisés

- [ ] **7.1** Ajouter `dmn-js` à `frontend/package.json` (licence déjà vérifiée Phase 1/3 — MIT +
  clause filigrane, identique `bpmn-js`). Dépendances : aucune.
- [ ] **7.2** `DmnEditorCanvas.vue` (`dmn-js` Modeler, sans provider custom — design D2).
  Dépendances : 7.1.
- [ ] **7.3** Composables `useDmnDraft.ts`/`useDmnDeployment.ts` (miroir
  `useBpmnDraft`/`useBpmnDeployment`). Dépendances : 5.3, 6.1 (contrat API), 7.2.
- [ ] **7.4** Scénarios Playwright E2E réels (pas mockés) : création, édition d'une table
  existante, déploiement, confirmation navigateur sur modifications non enregistrées. Dépendances :
  7.3, M9 (écran monté). Validation : T6.1, T6.2, T6.3, T6.5.

### M8 — Complément éditeur BPMN existant (référencer une table DMN)

- [ ] **8.1** Étendre `flowablePropertiesProvider.ts` — nouveau champ pour poser
  `flowable:type="dmn"` + `decisionTableReferenceKey` sur un `serviceTask`, alimenté par la liste
  des tables déployées (nouvel appel API, réutilise `GET /api/dmn-decision-deployments/info` en
  liste ou un endpoint de liste dédié — à trancher en implémentation si le design ne suffit pas).
  Dépendances : 6.1 (liste des tables déployées disponible côté API), 4.1 (shape acceptée côté
  parsing).
- [ ] **8.2** Scénario Playwright E2E réel : round-trip XML sur un diagramme neuf avec ce nouvel
  attribut `flowable:*` (non-régression directe du bug latent TODO §39 — premier attribut
  `flowable:*` perdu sur un diagramme vierge). Dépendances : 8.1. Validation : T6.4.

### M9 — Écran admin `/admin/dmn-editor` + navigation

- [ ] **9.1** `DmnEditorView.vue` (route `/admin/dmn-editor`, `decisionKey` en query param
  optionnel, miroir `BpmnEditorView.vue`) + lien depuis un écran admin existant (à déterminer —
  `/admin/workflow-types` comme pour BPMN, ou nouvelle entrée dédiée). Dépendances : 7.2, 7.3.

### M10 — Tools MCP

- [ ] **10.1** `DeployDecisionTableTool` (`deploy_decision_table`, `dryRun` supporté, miroir
  `DeployWorkflowTool`). Dépendances : 3.2, 4.2 (cohérence de validation avec le chemin REST/BPMN).
- [ ] **10.2** `ListDecisionTablesTool` (`list_decision_tables`). Dépendances : 3.1.
- [ ] **10.3** `GetDecisionTableTool` (`get_decision_table`). Dépendances : 3.1.
- [ ] **10.4** RBAC identique à `deploy_workflow` (garde domaine, pas de raccourci `API_SERVICE`) +
  vérification `dryRun` ne déploie/compte rien. Dépendances : 10.1-10.3. Validation : T5.4.

### M11 — Scénario illustratif livré

- [ ] **11.1** Fixture `.dmn` de la grille d'éligibilité (critères combinés, hit policy
  `FIRST`/`COLLECT` selon la règle) + process BPMN de démonstration l'invoquant via `serviceTask
  type="dmn"`. Dépendances : 1.2 (grammaire, pour être sûr que la fixture reste dans l'allowlist),
  4.1. Support de T5.5/Test Data.

### M12 — Documentation + vérification globale

- [ ] **12.1** `README.md`, `analysis doc/05-workflow-engine.md`, `analysis doc/00-index.md` —
  retrait des mentions « DMN hors V1 » / « auto-config exclue ». Dépendances : feature livrée.
- [ ] **12.2** `FILE-ATTENTE.md` — clôture TODO §61 (retrait définitif, pas seulement « retiré de Ne
  pas lancer »). `GUIDE-CONCEPTION-BPMN.md` — nouvelle section « quand utiliser une table DMN vs un
  gateway », plus documentation du nouveau champ `flowable:type="dmn"` dans le catalogue des
  `serviceTask`.
- [ ] **12.3** `GUIDE-UTILISATEUR.md` — nouvel écran admin `/admin/dmn-editor` documenté (convention
  `flowforge-conventions` § Workflow de feature point 6 — écran admin livré = doc à jour avant
  clôture).
- [ ] **12.4** `./mvnw verify -Dmaven.compiler.release=21` (0 échec, Jacoco tenu) + `cd frontend &&
  npm run test:all` (Vitest + Playwright, 0 régression). Validation : Test Reporting & Coverage.
- [ ] **12.5** `CHANGELOG.md` — entrée `[Non publié]` synthétique avant ouverture/mise à jour de la
  PR (convention § Workflow de feature point 7).

## Couverture testing → planning (vérification exhaustive)

| Scénario testing | Tâche(s) |
|---|---|
| T1.1–T1.4 | 2.3 |
| T2.1–T2.5 | 4.1 |
| T3.1–T3.2 | 3.2 |
| T4.1–T4.2 | 4.2 |
| T5.1 | 2.4 |
| T5.2 | 4.3 |
| T5.3 | 5.3, 6.1 |
| T5.4 | 10.4 |
| T5.5 | 4.4, 11.1 |
| T5.6 | 4.4 |
| T6.1, T6.2, T6.3, T6.5 | 7.4 |
| T6.4 | 8.2 |

Aucun scénario orphelin.

## Dependencies

Ordre de blocage principal : **M1 → M2 → (M3, M4 en parallèle une fois M2 stable) → M5/M6 → M7/M8
en parallèle → M9 → M10/M11 → M12**. M5/M6 (backend REST) et M7 (frontend éditeur) peuvent se
développer en parallèle une fois le contrat API stabilisé par le design — même pattern que
`edition-bpmn` (M1-M4 backend / M5-M7 frontend en parallèle, M8 vérification globale sur l'arbre
fusionné). M8 (complément éditeur BPMN) dépend de M6 (liste des tables) et de M4.1 (shape acceptée)
— peut démarrer dès ces deux prérequis, sans attendre M7/M9.

Dépendance externe : aucune nouvelle dépendance Maven (moteur DMN déjà sur le classpath via
`flowable-spring-boot-starter`) ; une nouvelle dépendance npm (`dmn-js`, licence déjà vérifiée).

## Timeline & Estimates

Pas d'estimation en jours/points — cohérent avec l'absence de suivi de charge ailleurs dans ce
dépôt (le rythme réel est piloté par cycle `dev-lifecycle`, pas par sprint). Le périmètre (12
milestones vs 8 pour `edition-bpmn`) reflète le choix explicite de l'utilisateur de cumuler
activation + sécurité + éditeur + MCP en un seul cycle plutôt que de l'étaler.

## Risks & Mitigation

- **Risque dominant : M1/M2 (grammaire FEEL) sous-estimé.** Si l'investigation 1.1 révèle que le
  moteur FEEL de Flowable DMN ne permet aucune restriction fine (tout ou rien), la seule option
  devient une validation texte exhaustive comme pour UEL — plus fragile, plus de cas de
  contournement possibles (leçon H1→H1-bis : une 1ère passe insuffisante n'est découverte qu'à
  l'exécution). Mitigation : ne pas fermer M2 sans T5.1 vert, et considérer une revue adversariale
  dédiée avant merge si le doute persiste après implémentation (comme suggéré, non retenu, en
  Phase 1 D-DMN-SECURITY-OWNER — à reconsidérer si M1 révèle un risque plus large que prévu).
- **Risque M3 : API `DmnRepositoryService` non symétrique à `RepositoryService` BPMN** (design
  « Points restants » #2) — peut faire dévier la forme de `DecisionTableDeploymentInfo`. Mitigation
  : vérifier tôt (début M3), avant d'écrire le contrat REST/MCP qui en dépend (M6, M10).
- **Risque M8 : source de la liste des tables déployées côté éditeur BPMN** non entièrement
  précisée dans le design — dépend d'un endpoint qui n'existe pas encore explicitement pour un
  usage « liste » (seul `info` par clé unique est prévu). Mitigation : trancher en M6 si un
  endpoint de liste est nécessaire, plutôt que découvert en implémentant M8.
- **Risque de dérive de périmètre** : le cycle cumule 4 volets (moteur/sécurité/éditeur/MCP) — tout
  ajout non prévu ici doit repasser par une décision explicite (`AskUserQuestion`), pas être ajouté
  silencieusement en implémentation, cohérent avec la discipline déjà suivie en Phase 1/3.

## Resources Needed

Aucune ressource externe nouvelle — même stack, même équipe (agent unique, no-worktree, branche de
session). `dmn-js` est la seule nouvelle dépendance (frontend, déjà vérifiée).
