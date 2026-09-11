---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy — `introduction-dmn`

> Scénarios dérivés des success criteria de
> [requirements](../requirements/2026-09-11-feature-introduction-dmn.md) et des composants de
> [design](../design/2026-09-11-feature-introduction-dmn.md). Chaque scénario est référencé par au
> moins une tâche du [planning](../planning/2026-09-11-feature-introduction-dmn.md).

## Test Coverage Goals

- Même seuil Jacoco que le reste du dépôt (`BUNDLE` LINE 85% / BRANCH 75%, TODO §28) — pas de
  dérogation pour ce cycle.
- **Toute affirmation de sécurité doit être prouvée par exécution réelle contre le vrai moteur
  Flowable DMN, pas seulement par lecture statique du parseur** — exigence héritée directement de
  H1-bis (une première restriction « littéral simple » côté BPMN s'était révélée contournable à
  l'exécution malgré une lecture de code qui semblait correcte).
- Non-régression stricte sur les 7 process BPMN déjà livrés et sur les 68 scénarios Playwright
  existants.

## Unit Tests

### `FlowableDmnParserAdapter` (allowlist FEEL, nouveau)

- [ ] T1.1 : une table de décision dont toutes les cellules respectent la grammaire allowlist
  (litéraux, comparaisons, intervalles, listes de valeurs, `-`) est acceptée.
- [ ] T1.2 : une cellule FEEL hors grammaire (invocation de fonction, navigation de contexte,
  `for`/`some`/`every`, tout ce qui ressemble à un appel) est rejetée avec un message explicite.
- [ ] T1.3 : `decision` sans `id` ou XML DMN mal formé → rejet (miroir
  `extractProcessDefinitionKey` sur XML invalide côté BPMN).
- [ ] T1.4 : hit policy hors `UNIQUE`/`FIRST`/`COLLECT` → rejetée (D-DMN-HITPOLICY, périmètre
  Phase 1).
- [ ] Additional coverage : table avec 0 règle, table avec règles dupliquées (comportement délégué
  au moteur ou rejeté explicitement — à trancher en implémentation si le design ne le couvre pas).

### `FlowableBpmnParserAdapter.assertSafeServiceTask` (exception `type="dmn"`, modifié)

- [ ] T2.1 : `serviceTask flowable:type="dmn"` avec un seul `flowable:field
  name="decisionTableReferenceKey"` en `flowable:string` littéral → accepté.
- [ ] T2.2 : même élément avec `flowable:expression` au lieu de `flowable:string` sur
  `decisionTableReferenceKey` → **rejeté** (miroir direct de la leçon `assertLiteralAssigneeValue`
  — jamais d'expression sur un champ d'identité).
- [ ] T2.3 : `serviceTask flowable:type="dmn"` avec un champ supplémentaire, ou un attribut
  d'implémentation additionnel (`delegateExpression` en plus de `type`) → rejeté (shape stricte,
  pas de superset toléré).
- [ ] T2.4 : tout autre `type` intégré (`mail`, `camel`, `http`, `shell`, `mule`) reste rejeté —
  non-régression explicite de la règle existante, pas seulement supposée inchangée.
- [ ] T2.5 (miroir `FlowableBpmnAllowlistGapTest`) : un `decisionTableReferenceKey` syntaxiquement
  valide mais construit pour ressembler à autre chose qu'une clé (ex. contenant `${`, `#{`,
  caractères de contrôle) → rejeté à la validation littérale, avant même la résolution de clé.

### Domaine (`DeployDecisionTableUseCaseImpl`, nouveau)

- [ ] T3.1 : déploiement d'une table valide → `DecisionTableDeploymentResult` correct, brouillon
  associé supprimé (miroir `D-DESIGN-DRAFT-LIFECYCLE`).
- [ ] T3.2 : rien à déployer si le parsing échoue (aucun appel au port de déploiement) —
  vérification d'ordre, comme `DeployBpmnProcessUseCaseImpl`.

### `DeployBpmnProcessUseCaseImpl` (modifié — vérification clé DMN référencée)

- [ ] T4.1 : un `serviceTask type="dmn"` référence une clé de décision **non déployée** → rejet
  strict au déploiement du process (miroir `UnknownWorkflowKeyException` → 404).
- [ ] T4.2 : référence une clé **déployée** → déploiement du process accepté.

## Integration Tests

- [ ] T5.1 (miroir `FlowableBpmnAllowlistGapTest`) : preuve par **exécution réelle** contre le vrai
  moteur DMN qu'une expression FEEL construite pour ressembler à une référence légitime ne peut pas
  échapper à l'évaluation prévue (ex. atteindre une méthode/bean hors du contexte de la table) —
  pas seulement une lecture statique de l'AST DMN.
- [ ] T5.2 (miroir `FlowableBpmnParserAdapterShippedProcessesTest`) : les 7 process BPMN déjà
  livrés continuent de se déployer sans changement de comportement après l'ajout de l'exception
  `type="dmn"`.
- [ ] T5.3 : endpoints REST admin (`DmnDraftController`, `DmnDecisionDeploymentController`) —
  RBAC (`TENANT_ADMIN`/`WORKFLOW_ADMIN` uniquement, 403 sinon), cycle brouillon → déploiement →
  suppression du brouillon.
- [ ] T5.4 : tools MCP — `dryRun` ne déploie rien et ne compte pas (cohérence `deploy_workflow`),
  RBAC identique (garde domaine, pas de raccourci `API_SERVICE`).
- [ ] T5.5 : scénario de bout en bout du cas illustratif retenu (D-DMN-SCOPE) — table « grille
  d'éligibilité » déployée, invoquée par un process BPMN réel via `serviceTask type="dmn"`,
  résultat lisible en variable(s) de process après exécution (miroir
  `UserQuestionFormSmokeIT`/`FormKeySmokeIT`).
- [ ] T5.6 : une valeur potentiellement sensible produite par la table de décision transite par le
  masquage `SendNotificationUseCaseImpl` avant toute écriture outbox (contrainte requirements —
  vérifier que le mécanisme s'applique bien aux variables issues d'un `serviceTask`, pas seulement
  `dossierFields`/`user-question`).

## End-to-End Tests (Playwright)

- [ ] T6.1 : création d'une table de décision depuis `/admin/dmn-editor` (éditeur `dmn-js` vierge),
  sauvegarde en brouillon, rechargement de page → brouillon restauré.
- [ ] T6.2 : édition d'une table déjà déployée (3ᵉ point d'entrée, miroir `edition-bpmn`
  D-EDIT-ENTRYPOINT) → XML chargé correctement dans l'éditeur.
- [ ] T6.3 : déploiement depuis l'éditeur (bouton « Déployer ») → confirmation, table listée comme
  déployée.
- [ ] T6.4 : dans l'éditeur BPMN existant (`/admin/bpmn-editor`), poser `flowable:type="dmn"` +
  `decisionTableReferenceKey` sur un `serviceTask` via le panel de propriétés complété (design
  « Component Breakdown ») → round-trip XML correct (non-régression du bug latent trouvé sur
  `edition-bpmn`, TODO §39 : premier attribut `flowable:*` sur un diagramme vierge).
- [ ] T6.5 : confirmation navigateur (`beforeunload`) si modifications non enregistrées — miroir
  `D-EDIT-UNSAVED` reconduit (design D5).

## Test Data

- Fixture `.dmn` de la grille d'éligibilité (D-DMN-SCOPE) — critères combinés (type de demandeur,
  montant, statut), hit policy `FIRST`/`COLLECT` selon la règle, à déposer en
  `src/test/resources/dmn/` (miroir `src/test/resources/processes/`).
- Process BPMN de démonstration invoquant cette table (`src/main/resources/processes/` ou
  `src/test/resources/processes/` selon si livré comme exemple produit ou fixture de test — à
  trancher en implémentation).
- Payloads FEEL malveillants pour T1.2/T2.2/T2.5 — à construire à partir des tentatives de
  contournement déjà découvertes côté BPMN (H1-bis) adaptées à la syntaxe FEEL.

## Test Reporting & Coverage

- `./mvnw verify -Dmaven.compiler.release=21` — 0 échec, Jacoco LINE 85%/BRANCH 75% tenu sur les
  classes de la feature (idéalement 100% comme les cycles récents, sans behavior non couvert).
- `cd frontend && npm run test:all` (Vitest + Playwright) — 0 régression sur les 289+/68+ tests
  existants, nouveaux scénarios T6.1-T6.5 verts.

## Manual Testing

- Revue manuelle du panel de propriétés complété dans l'éditeur BPMN (liste des tables DMN
  déployées à jour, pas de saisie libre permise pour `decisionTableReferenceKey`).

## Performance Testing

Hors périmètre — même volumétrie et modèle de déploiement à froid que BPMN (design « Non-Functional
Requirements »).

## Bug Tracking

Toute faille trouvée en implémentation ou en revue (notamment sur l'allowlist FEEL, risque
dominant) suit le même protocole que H1/H1-bis : preuve empirique avant correctif, pas de correctif
sans reproduction réelle.
