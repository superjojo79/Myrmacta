---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: done
updated: 2026-09-11
---

# Testing Strategy — `introduction-dmn`

> Scénarios dérivés des success criteria de
> [requirements](../requirements/2026-09-11-feature-introduction-dmn.md) et des composants de
> [design](../design/2026-09-11-feature-introduction-dmn.md). Chaque scénario est référencé par au
> moins une tâche du [planning](../planning/2026-09-11-feature-introduction-dmn.md).
>
> **Correction (Phase 5/M1, puis reconfirmée en Phase 8) :** la version initiale de ce document
> parlait de « grammaire FEEL » partout ci-dessous (intervalles, listes de valeurs, fonctions).
> Flowable **n'implémente pas FEEL** pour ses tables de décision : le moteur réel est **JUEL**
> (même moteur que les gateways BPMN), vérifié par décompilation du bytecode
> `flowable-dmn-engine` réel, pas supposé — voir
> [05-workflow-engine.md](../../../analysis%20doc/05-workflow-engine.md) §« Tables de décision —
> DMN ». Les scénarios ci-dessous sont corrigés en conséquence ; les mentions historiques de
> « FEEL » dans les titres de section sont conservées à l'identique pour ne pas réécrire
> l'historique, mais chaque case cochée référence le comportement JUEL réel.

## Test Coverage Goals

- Même seuil Jacoco que le reste du dépôt (`BUNDLE` LINE 85% / BRANCH 75%, TODO §28) — **tenu**,
  `./mvnw verify` vert avec les seuils actifs (aucune dérogation demandée).
- **Toute affirmation de sécurité doit être prouvée par exécution réelle contre le vrai moteur
  Flowable DMN, pas seulement par lecture statique du parseur** — exigence héritée directement de
  H1-bis. **Tenu** : `DmnJuelSecuritySmokeIT` déploie une vraie table sur le vrai moteur et lit
  l'exception réelle levée par UEL en résolvant un identifiant nu contre un bean Spring vivant.
- Non-régression stricte sur les 7 process BPMN déjà livrés et sur les scénarios Playwright
  existants — **tenu**, `FlowableBpmnParserAdapterShippedProcessesTest` (non-régression) et 71/71
  Playwright (0 régression, y compris hors périmètre DMN).

## Unit Tests

### `FlowableDmnParserAdapter` (allowlist JUEL, nouveau)

- [x] T1.1 : une table de décision dont toutes les cellules respectent la grammaire allowlist
  (littéraux, comparaisons, `-`) est acceptée — `FlowableDmnParserAdapterTest` (`extractDecisionKey_validTable_returnsDecisionId`,
  `extractDecisionKey_bareValueImplicitEquality_isAccepted`,
  `extractDecisionKey_numberAndComparisonOperators_areAccepted`,
  `extractDecisionKey_emptyOrDashCell_isAccepted`). **Pas d'intervalles/listes** : non supportés
  par l'implémentation JUEL réelle de Flowable (confirmé M1), retirés du périmètre testé.
- [x] T1.2 : une cellule hors grammaire (EL brut `#{}`/`${}`, navigation de propriété `.xxx`,
  invocation de fonction, identifiant nu) est rejetée avec un message explicite —
  `extractDecisionKey_bareUnquotedIdentifier_isRejected_H1BisEquivalent`,
  `extractDecisionKey_operatorFollowedByBareIdentifier_isRejected`,
  `extractDecisionKey_rawElPassthroughSyntax_isRejected`,
  `extractDecisionKey_propertyNavigationPrefix_isRejected`,
  `extractDecisionKey_functionInvocationCell_isRejected`,
  `extractDecisionKey_unsafeOutputEntry_isRejected`.
- [x] T1.3 : `decision` sans `id` ou XML DMN mal formé → rejet (miroir
  `extractProcessDefinitionKey` sur XML invalide côté BPMN) —
  `extractDecisionKey_malformedXml_throwsInvalidDmnException`,
  `extractDecisionKey_wellFormedButNotDmn_throwsInvalidDmnException`,
  `extractDecisionKey_decisionWithNoIdAttribute_isRejected` (**ajouté en Phase 8**, gap de
  couverture réel trouvé — jamais exercé avant).
- [x] T1.4 : hit policy hors `UNIQUE`/`FIRST`/`COLLECT` → rejetée (D-DMN-HITPOLICY) —
  `extractDecisionKey_disallowedHitPolicy_isRejected`, `extractDecisionKey_collectHitPolicy_isAllowed`,
  `extractDecisionKey_firstHitPolicy_isAllowed`.
- [x] Additional coverage (Phase 8) : `extractDecisionKey_decisionTableWithNoRules_isRejected`
  (table à 0 règle), `extractDecisionKey_decisionWithLiteralExpressionInsteadOfTable_isRejected`
  (`<decision>` portant un `<literalExpression>` plutôt qu'une `<decisionTable>`),
  `extractDecisionKey_multipleDecisions_isRejected` (plusieurs `<decision>`). **Un cas
  investigué et sciemment non couvert** : `assertSafeCellText`'s `rawText == null`
  court-circuit (ligne 97-98) — vérifié inatteignable via le chemin de parsing public (le schéma
  DMN 1.1 exige un `<text>` sur chaque `inputEntry`/`outputEntry`, un `<inputEntry/>` sans `<text>`
  échoue la validation de schéma avant d'atteindre ce code) ; documenté en commentaire dans le
  fichier de test plutôt que forcé par un test synthétique contournant l'API publique.

### `FlowableBpmnParserAdapter.assertSafeServiceTask` (exception `type="dmn"`, modifié)

- [x] T2.1 : `serviceTask flowable:type="dmn"` avec un seul `flowable:field
  name="decisionTableReferenceKey"` en `flowable:string` littéral → accepté —
  `extractProcessDefinitionKey_dmnServiceTaskWithValidField_isAccepted`.
- [x] T2.2 : `flowable:expression` au lieu de `flowable:string` → **rejeté** —
  `extractProcessDefinitionKey_dmnServiceTaskWithExpressionField_rejected`.
- [x] T2.3 : champ supplémentaire ou attribut d'implémentation additionnel → rejeté —
  `extractProcessDefinitionKey_dmnServiceTaskWithTwoFields_rejected`,
  `extractProcessDefinitionKey_dmnServiceTaskWithWrongFieldName_rejected`,
  `extractProcessDefinitionKey_dmnServiceTaskWithNoField_rejected`.
- [x] T2.4 : tout autre `type` intégré (`mail`, `camel`, `http`, `shell`, `mule`) reste rejeté —
  non-régression couverte par les cas existants de `FlowableBpmnParserAdapterTest` (inchangés).
- [x] T2.5 (miroir `FlowableBpmnAllowlistGapTest`) : `decisionTableReferenceKey` contenant `${`/`#{`
  → rejeté — `extractProcessDefinitionKey_dmnServiceTaskWithElExpressionInLiteralValue_rejected`.
- [x] Additional coverage (Phase 7) : `extractReferencedDecisionKeys_dmnServiceTaskNestedInSubProcess_returnsTheKey`
  — la récursion `SubProcess` de `extractReferencedDecisionKeys` n'avait jamais été exercée par une
  fixture imbriquée.

### Domaine (`DeployDecisionTableUseCaseImpl`, nouveau)

- [x] T3.1 : déploiement d'une table valide → `DecisionTableDeploymentResult` correct, brouillon
  associé supprimé — `DeployDecisionTableUseCaseImplTest`.
- [x] T3.2 : rien à déployer si le parsing échoue (aucun appel au port de déploiement) —
  `DeployDecisionTableUseCaseImplTest`.

### `DeployBpmnProcessUseCaseImpl` (modifié — vérification clé DMN référencée)

- [x] T4.1 : un `serviceTask type="dmn"` référence une clé de décision **non déployée** → rejet
  strict au déploiement du process (`UnknownDecisionKeyException` → 404) —
  `DeployBpmnProcessUseCaseImplTest`.
- [x] T4.2 : référence une clé **déployée** → déploiement du process accepté —
  `DeployBpmnProcessUseCaseImplTest`.

## Integration Tests

- [x] T5.1 (miroir `FlowableBpmnAllowlistGapTest`) : preuve par **exécution réelle** contre le vrai
  moteur DMN qu'un identifiant nu resolvant contre un bean Spring vivant est bien détecté/rejeté —
  `DmnJuelSecuritySmokeIT` (2 scénarios : bean réel résolu vs identifiant inconnu, messages
  d'exception réels du moteur comparés).
- [x] T5.2 (miroir `FlowableBpmnParserAdapterShippedProcessesTest`) : les process BPMN déjà livrés
  continuent de se déployer sans changement de comportement — non-régression confirmée, `./mvnw
  verify` vert, aucun changement de comportement sur les fixtures existantes. Miroir DMN ajouté en
  M11 : `FlowableDmnParserAdapterShippedDecisionsTest` (le `.dmn` livré passe l'allowlist).
- [x] T5.3 : endpoints REST admin — RBAC (`TENANT_ADMIN`/`WORKFLOW_ADMIN` uniquement, 403 sinon),
  cycle brouillon → déploiement → suppression du brouillon — `DmnDraftApiTest` (8 scénarios),
  `DmnDecisionDeploymentApiTest` (11 scénarios, **ajoutés en Phase 7** — jamais testés avant).
- [x] T5.4 : tools MCP — `dryRun` ne déploie rien, RBAC cohérent avec `deploy_workflow` (garde
  domaine, pas de raccourci `API_SERVICE`) — `DeployDecisionTableToolTest`,
  `ListDecisionTablesToolTest`, `GetDecisionTableToolTest` (**ajoutés en Phase 7**).
- [x] T5.5 : scénario de bout en bout du cas illustratif retenu (D-DMN-SCOPE) — table « grille
  d'éligibilité » déployée, invoquée par un process BPMN réel via `serviceTask type="dmn"`,
  résultat lisible en variable(s) de process — `DmnServiceTaskEndToEndSmokeIT` (déploiement
  programmatique, preuve du mécanisme) **et** `DmnGrilleEligibiliteSmokeIT` (M11, fixture
  réellement livrée, auto-déployée au démarrage par `TenantDmnDeployment`, 3 branches de la
  grille).
- [x] T5.6 : une valeur potentiellement sensible produite par la table de décision transite par le
  masquage `SendNotificationUseCaseImpl` avant toute écriture outbox — **vérifié, pas un gap
  DMN-spécifique** : `SendNotificationUseCaseImpl.redactSensitiveFields` ne masque que le contenu
  d'un `DossierFieldsSnapshot` explicitement transmis par l'événement de notification, quel que
  soit le producteur de la variable (DMN, `user-question`, `dossierFields` bruts). Aucun producteur
  du dépôt ne câble aujourd'hui ce paramètre — comportement déjà documenté comme limitation connue,
  repo-wide, dans `GUIDE-CONCEPTION-BPMN.md` §8 (« aucun chemin BPMN n'applique le masquage avant
  l'envoi »), pas introduit ni aggravé par cette feature. Une variable produite par une table DMN
  suit exactement le même comportement (non masqué) qu'une variable produite par n'importe quel
  autre `serviceTask` — cohérence vérifiée, pas une régression de sécurité spécifique à DMN.

## End-to-End Tests (Playwright)

- [x] T6.1 : création d'une table de décision depuis `/admin/dmn-editor` (éditeur `dmn-js` vierge),
  sauvegarde en brouillon → `E2E-EDIT-DMN-1`.
- [x] T6.2 : édition d'une table déjà déployée (3ᵉ point d'entrée) → XML chargé correctement dans
  l'éditeur. **Gap réel trouvé et corrigé en Phase 7** : ce point d'entrée était codé
  (`route.query.decisionKey`) mais jamais atteignable depuis l'UI (aucun écran ne liste les tables
  déployées, contrairement au BPMN). Corrigé par un champ **Charger une table existante** dans la
  barre d'outils de l'éditeur — couvert par `DmnEditorView.spec.ts`
  (`the "Charger" button loads an existing table by key typed into the field`) côté Vitest ; pas
  de nouveau scénario Playwright dédié (le mécanisme de chargement lui-même — `useDmnDraft.load()`
  — est déjà exercé en E2E via `E2E-EDIT-DMN-1`/`E2E-EDIT-DMN-2`, la seule pièce nouvelle est le
  déclencheur UI, entièrement couvert en unitaire).
- [x] T6.3 : déploiement depuis l'éditeur (bouton « Déployer ») → confirmation, table déployée —
  `E2E-EDIT-DMN-2`.
- [x] T6.4 : dans l'éditeur BPMN existant, poser `flowable:type="dmn"` +
  `decisionTableReferenceKey` sur un `serviceTask` via le panel de propriétés → round-trip XML
  correct — `E2E-EDIT-BPMN-5`. **2 bugs réels trouvés et corrigés** à cette étape (sérialisation
  `moddle-xml`, voir doc implémentation).
- [x] T6.5 : confirmation navigateur (`beforeunload`) si modifications non enregistrées — miroir
  BPMN, comportement partagé (`useDmnDraft.ts`), couvert unitairement
  (`useDmnDraft.spec.ts`, Phase 7) plutôt qu'en E2E dédié (même mécanisme déjà prouvé en E2E côté
  BPMN, `E2E-EDIT-BPMN-*`).

## Test Data

- Fixture `.dmn` de la grille d'éligibilité livrée en production :
  `src/main/resources/decisions/grille-eligibilite-client.dmn` (hit policy `FIRST`, 2 critères
  combinés) — **pas** `src/test/resources/dmn/` comme envisagé en Phase 4 : décidé livrée comme
  exemple produit (M11), auto-déployée au démarrage par `TenantDmnDeployment`, cohérent avec le
  choix déjà fait pour les process `-smoke` BPMN.
- Process BPMN de démonstration : `src/main/resources/processes/dmn-grille-eligibilite-smoke.bpmn20.xml`,
  même mécanisme de livraison.
- Payloads JUEL malveillants (pas FEEL, corrigé) : identifiants nus correspondant à des noms de
  beans Spring réels (`connectorServiceTask`), adaptés de l'attaque H1-bis déjà découverte côté
  BPMN — `FlowableDmnParserAdapterTest`/`DmnJuelSecuritySmokeIT`.

## Test Reporting & Coverage

**Backend** (`./mvnw verify`, JDK 25 — voir Design Deviation JDK25 dans le doc implémentation) :
**1875 tests, 0 échec**, seuils Jacoco `BUNDLE` LINE 85%/BRANCH 75% tenus. Couverture par classe de
la feature vérifiée explicitement (pas seulement l'agrégat `BUNDLE`, Phase 8) :

| Classe | Instr. manquées | Branches manquées | Lignes manquées | Commentaire |
|---|---|---|---|---|
| `FlowableDmnParserAdapter` | 9 (était 28) | 9/50 (était 12/50) | 1/57 (était 4/57) | 3 gaps réels comblés en Phase 8 (id absent, expression au lieu de table, 0 règle) ; la ligne restante est un `return` défensif vérifié inatteignable via le chemin de parsing public (schéma DMN exige `<text>`) |
| `FlowableDmnDeploymentAdapter` | 6 | 0/10 | 2/36 | Parité exacte avec `FlowableWorkflowDeploymentAdapter` (précédent BPMN) — gap accepté déjà présent côté BPMN |
| `DmnDecisionDeploymentController` | 8 | 1/6 | 2/27 | Quasi-parité avec `WorkflowBpmnDeploymentController` (8/1/2 vs 8/1/2, écart d'1 ligne) — gap accepté déjà présent côté BPMN |
| `TenantDmnDeployment` | 1 | 1/4 | 1/15 | Parité exacte avec `TenantProcessDeployment` — branche "classpath vide" non testable sans mock du resolver, accepté côté BPMN comme côté DMN |
| Reste (drafts, use cases, MCP tools, persistence) | 0 | 0 | 0 | 100% sur les 4 métriques (nouveaux tests Phase 7) |

**Frontend** (`npm run test:coverage`) : sommaire global 85.83% statements / 79.12% branches /
84.93% functions / 87.63% lines (seuils 80/75/70/60 tenus). Fichiers DMN :

| Fichier | Statements | Branches | Functions | Lines | Commentaire |
|---|---|---|---|---|---|
| `useDmnDraft.ts` | 95.65 | 83.33 | 83.33 | 95.65 | Parité exacte avec `useBpmnDraft.ts` — gap accepté déjà présent côté BPMN |
| `useDmnDeployment.ts` | 100 | 100 (était 50) | 100 | 100 | Gap réel comblé en Phase 8 (branche de repli `?? 'Déploiement impossible'` jamais atteinte — nécessitait un corps `ProblemDetail` sans champ `detail`, pas juste une erreur réseau générique) |
| `DmnEditorCanvas.vue` | 100 | 100 | 100 | 100 | — |
| `DmnEditorView.vue` | 81.15 | 76.36 | 90.9 | 88.52 | Légèrement au-dessus de `BpmnEditorView.vue` (80.64/75.51/88.88/87.27) — au moins à parité |

`cd frontend && npm run lint` / `npm run build` — propres. `npx vitest run` — **351/351** (était
350, +1 test Phase 8). `npx playwright test --project=chromium` — **71/71, 0 échec** (preuve
fraîche Phase 8, 33.0s ; run précédent en Phase 7 avait 2 échecs transitoires de contention
ressources, reconfirmés verts en isolation et non reproduits ici).

## Manual Testing

Revue manuelle du panel de propriétés complété dans l'éditeur BPMN effectuée via l'E2E réel
(`E2E-EDIT-BPMN-5`), pas seulement une revue visuelle statique — cohérent avec la doctrine du
dépôt (preuve d'exécution avant affirmation).

## Performance Testing

Hors périmètre — même volumétrie et modèle de déploiement à froid que BPMN, confirmé sans
changement de performance en production (voir doc implémentation §Performance Considerations).

## Bug Tracking

Bugs réels trouvés pendant ce cycle, tous suivant le protocole H1/H1-bis (preuve empirique avant
correctif) : voir `docs/ai/implementation/2026-09-11-feature-introduction-dmn.md` §Design
Deviations pour le détail complet (bug JDK25/Flowable, 2 bugs `moddle-xml`, gap MCP tool count,
gaps de couverture Phase 7/8, gap d'accessibilité UI). Aucun bug de sécurité non corrigé à ce
stade.
