---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: done
created: 2026-09-11
updated: 2026-09-11
feature: introduction-dmn
design_ref: docs/ai/design/2026-09-11-feature-introduction-dmn.md
planning_ref: docs/ai/planning/2026-09-11-feature-introduction-dmn.md
---

# Implementation Guide — `introduction-dmn`

12 milestones (M1-M12) exécutés en TDD sur la branche de session
`claude/project-status-next-features-w043uj` (no-worktree, convention de ce dépôt). M1-M6/M10
(backend) puis M7-M9 (frontend) livrés séquentiellement ; M11 (fixture) et M12 (documentation +
vérification globale) ont révélé et corrigé des bugs réels (voir Design Deviations). Phase 7
(revue fichier par fichier, dédiée) a trouvé et comblé un **gap de couverture de test important**
(toute la persistence/API/MCP de la partie DMN du dépôt, jamais testée avant cette revue) et un
**gap d'accessibilité UI réel** (3ᵉ point d'entrée de l'éditeur promis par le design, jamais
atteignable depuis l'interface). Phase 8 (mesure de couverture par classe, ci-dessous) a comblé 3
gaps réels supplémentaires dans le fichier sécurité-critique `FlowableDmnParserAdapter` et 1 côté
frontend.

`./mvnw verify` final : **1875 tests, 0 échec** (backend, y compris les 3 tests ajoutés en Phase
8). Frontend **351 tests Vitest, 0 échec** (+1 Phase 8), `test:coverage` 85.83%/79.12%/84.93%/
87.63% (seuils 80/75/70/60 tenus), typecheck/lint propres. **71/71 Playwright** (0 régression,
preuve fraîche Phase 8, dont 3 scénarios `E2E-EDIT-DMN-*`/`E2E-EDIT-BPMN-5`). Phase 9 (revue
holistique, ci-dessous) n'a trouvé aucun bug et confirmé l'ensemble des points de contrat/
sécurité/rollback. Statut : implémentation **terminée**, cycle `dev-lifecycle` complet
(Phases 1-9). Prête à être poussée / PR à créer sur demande explicite de l'utilisateur.

## Development Setup

Backend : aucune nouvelle dépendance Maven (moteur DMN déjà sur le classpath via
`flowable-spring-boot-starter`, auto-config réactivée dans `application.yml`). Frontend : une
nouvelle dépendance npm, `dmn-js@17.10.2` (toolkit sœur de `bpmn-js`, même licence MIT + filigrane
déjà acceptée). Environnement de build : ce conteneur ne fournissait que JDK 21 par défaut — JDK 25
(`openjdk-25-jdk-headless`, requis par `pom.xml`) installé via `apt` avant toute compilation.

## Code Structure

**Backend :**
- `domain/workflow/model/` — `InvalidDmnException`, `DecisionTableDeploymentResult`,
  `DecisionTableDeploymentInfo`, `DmnDraft`, `UnknownDecisionKeyException` (nouveaux, miroirs
  exacts des équivalents BPMN).
- `domain/workflow/port/{in,out}/` — `DeployDecisionTableUseCase`,
  `GetDecisionTableDeploymentInfoUseCase`, `SaveDmnDraftUseCase`, `GetDmnDraftUseCase`,
  `ListDecisionTablesUseCase` (in, nouveaux) ; `DmnParserPort`, `DmnDeploymentPort`,
  `DmnDraftRepository` (out, nouveaux) ; `BpmnParserPort` (out, modifié —
  `+extractReferencedDecisionKeys`).
- `domain/workflow/usecase/` — 5 nouvelles implémentations miroir ; `DeployBpmnProcessUseCaseImpl`
  modifié (vérifie que chaque décision référencée par un `serviceTask type="dmn"` est bien
  déployée avant d'accepter le process, `UnknownDecisionKeyException` sinon).
- `infrastructure/flowable/FlowableDmnParserAdapter.java` (nouveau) — le fichier sécurité-critique
  de la feature : allowlist JUEL stricte sur chaque cellule d'entrée/sortie.
- `infrastructure/flowable/FlowableDmnDeploymentAdapter.java` (nouveau) — implémente
  `DmnDeploymentPort` via `DmnRepositoryService`.
- `infrastructure/flowable/TenantDmnDeployment.java` (nouveau, M11) — miroir de
  `TenantProcessDeployment` : auto-déploie `classpath*:/decisions/**/*.dmn` au démarrage.
- `infrastructure/flowable/FlowableBpmnParserAdapter.java` (modifié) — exception native
  `type="dmn"` dans `assertSafeServiceTask`/`assertSafeElement`, `extractReferencedDecisionKeys`
  (+ récursion `SubProcess`).
- `infrastructure/persistence/workflow/` — `DmnDraftJpaEntity` (`@IdClass`),
  `SpringDataDmnDraftRepository`, `JpaDmnDraftRepositoryAdapter` (nouveaux, miroir BPMN).
- `infrastructure/web/workflow/` — `DmnDraftController`, `DmnDecisionDeploymentController`,
  `DmnDecisionDeploymentApiDtos` (nouveaux) ; `WorkflowExceptionHandler` modifié
  (`InvalidDmnException`→400, `UnknownDecisionKeyException`→404).
- `infrastructure/mcp/tool/` — `DeployDecisionTableTool`, `ListDecisionTablesTool`,
  `GetDecisionTableTool` (nouveaux).
- `infrastructure/config/WorkflowConfig.java` (modifié) — beans des nouveaux use cases +
  `EngineConfigurationConfigurer<SpringDmnEngineConfiguration>` (Design Deviation JDK25, Phase 7).
- `db/changelog/changes/021-dmn-draft-tables.yaml` (nouveau) + `db.changelog-master.yaml`
  (modifié).
- `src/main/resources/decisions/grille-eligibilite-client.dmn` +
  `src/main/resources/processes/dmn-grille-eligibilite-smoke.bpmn20.xml` (M11, nouveaux) —
  scénario illustratif livré, auto-déployés au démarrage.
- `openapi/openapi.yaml` (golden régénéré).

**Frontend :**
- `components/workflow/DmnEditorCanvas.vue` (nouveau) — `dmn-js/lib/Modeler`, sans provider de
  propriétés custom (D2).
- `components/workflow/bpmn-properties/flowablePropertiesProvider.ts` (modifié) — champ
  `decisionTableReferenceKey` sur `serviceTask type="dmn"` (M8).
- `composables/useDmnDraft.ts`, `composables/useDmnDeployment.ts` (nouveaux, miroir exact des
  composables BPMN).
- `views/admin/DmnEditorView.vue` (nouveau) — écran `/admin/dmn-editor` ; toolbar complétée en
  Phase 7 (champ **Charger une table existante**, voir Design Deviations).
- `app/routes.ts` (modifié — route `/admin/dmn-editor`), `app/AppShell.vue` (modifié — lien nav
  « Tables de décision »).
- `api/workflows.ts`, `api/types.ts` (modifiés — types + fonctions DMN).
- `types/dmn-js-shims.d.ts` (nouveau — `dmn-js` ne fournit aucun type).

## Implementation Notes

### Core Features

- **Mécanisme d'invocation = `serviceTask flowable:type="dmn"`, pas `businessRuleTask`** (D1,
  correction majeure de Phase 3) — exception native étroite dans `FlowableBpmnParserAdapter`
  (`type` littéral `"dmn"`, exactement un `flowable:field` `decisionTableReferenceKey` en
  `flowable:string`, jamais `flowable:expression`).
- **Flowable DMN = JUEL, pas FEEL** (correction majeure de Phase 5/M1, vérifiée par décompilation
  du moteur réel, pas supposée) — `FlowableDmnParserAdapter` adapte
  `assertSafeConditionExpression` à la grammaire JUEL réelle (`[opérateur] valeur`, pas une
  grammaire FEEL à inventer). Preuve d'exécution réelle sur le vrai moteur, pas seulement lecture
  statique : `DmnJuelSecuritySmokeIT` (bean Spring résolu par un identifiant nu, même classe que
  H1-bis).
- **`dmn-js` Modeler sans provider custom** (D2) — le `.dmn` est du DMN 1.3 standard ; l'extension
  `flowable:*` est portée côté éditeur **BPMN** existant (le `serviceTask`), pas côté DMN.
- **Brouillon persisté, clé composite** (miroir `BpmnDraft`) — `DmnDraftJpaEntity` avec `@IdClass`
  sur `(tenant_id, decision_key)`.
- **Suppression du brouillon après déploiement réussi** (miroir D-DESIGN-DRAFT-LIFECYCLE) —
  `DeployDecisionTableUseCaseImpl.deploy()`.
- **Scénario illustratif livré** (M11) — `grille-eligibilite-client.dmn` (hit policy `FIRST`,
  2 critères combinés) + process de démonstration, auto-déployés comme les process `-smoke`
  existants (`TenantDmnDeployment`, miroir `TenantProcessDeployment`) ; preuve de bout en bout par
  `DmnGrilleEligibiliteSmokeIT` (3 branches, sans déploiement manuel dans le test).

### Patterns & Best Practices

- **Précédents de code réutilisés systématiquement** : `@IdClass` composite
  (`BpmnDraftJpaEntity`/`WorkflowIdempotencyJpaEntity`) ; style d'adaptateur Flowable
  (`FlowableWorkflowDeploymentAdapter` étendu conceptuellement, pas dupliqué à l'aveugle) ; scan
  classpath au démarrage (`TenantProcessDeployment` → `TenantDmnDeployment`) ; garde null/blank en
  tête de `save()` (`SaveDmnDraftUseCaseImpl`, apprise de la Design Deviation 6 de `edition-bpmn`
  — **déjà présente dès l'écriture initiale ici**, pas retrouvée en Phase 7).
- **Sécurité par allowlist, jamais par liste noire** — `FlowableDmnParserAdapter` accepte un
  ensemble fermé de formes (littéral, littéral+opérateur, `-`), rejette tout le reste
  explicitement, même logique que `assertSafeConditionExpression`.

## Design Deviations

1. **Bug réel trouvé en Phase 7 M8 (frontend, pas M8 initial)** — deux bugs de sérialisation
   `moddle-xml` distincts, trouvés via un round-trip E2E réel (`E2E-EDIT-BPMN-5`), non par lecture
   statique : (a) le conteneur `bpmn:ExtensionElements` doit être construit via `bpmnFactory.create`
   (type enregistré, schéma déclare `values` comme enfants), jamais `moddle.createAny`, sous peine
   de sérialiser `values="[object Object]"` ; (b) un élément générique `createAny` doit porter ses
   enfants sur la propriété `$children` spécifiquement (convention reconnue par
   `ElementSerializer.collectGenericContainments`), toute autre propriété étant sérialisée comme
   attribut plat stringifié. Documenté en détail dans `flowablePropertiesProvider.ts`.
2. **Bug réel trouvé en M12 (vérification finale, pas Phase 7)** — `./mvnw verify` complet a révélé
   que le déploiement de **toute** table DMN faisait planter Flowable 8.0.0 sur le JDK 25 de ce
   dépôt (`DmnXMLException: StAXSource(XMLStreamReader) with XMLStreamReader not in
   XMLStreamConstants.START_DOCUMENT or XMLStreamConstants.START_ELEMENT state`). Root-cause
   identifiée par décompilation (`javap -p -c`) de `flowable-dmn-engine`/
   `flowable-dmn-xml-converter` 8.0.0, pas supposée : `DmnParse.execute()` transmet
   `DmnEngineConfiguration.isEnableSafeDmnXml()` (vrai par défaut, seul point d'appel de cette
   méthode) comme 3ᵉ argument de `DmnXMLConverter#convertToDmnModel`, qui choisit alors de
   réutiliser un même `XMLStreamReader` entre la détection du namespace et la validation de
   schéma — cassé sur ce JDK. **Pas une régression de sécurité XXE** : les réglages anti-XXE de
   `convertToDmnModel` (DTD/entités externes désactivées) sont appliqués **inconditionnellement**,
   indépendamment de ce flag, vérifié dans le même désassemblage. `WorkflowConfig` déclare un
   `EngineConfigurationConfigurer<SpringDmnEngineConfiguration>` mettant `enableSafeDmnXml=false`,
   documenté en détail sur place. Pas de version Flowable 8.0.x plus récente disponible sur Maven
   Central pour corriger en amont.
3. **Écart mineur trouvé en M12** — `McpEndpointApiTest.toolsList_registersExactlyTheExpectedTools`
   attendait encore 20 tools MCP (liste figée avant M10) au lieu des 23 réels après l'ajout de
   `deploy_decision_table`/`list_decision_tables`/`get_decision_table`. Corrigé en ajoutant les 3
   noms à `EXPECTED_TOOLS` — pas un bug de production, un test de non-régression jamais mis à jour
   au fil de M10.
4. **Gap de couverture de test important trouvé en Phase 7 (revue fichier par fichier dédiée)** —
   toute la chaîne persistence/API REST/MCP tools/adaptateur de déploiement côté DMN n'avait
   **aucun test dédié** avant cette revue (`grep`-vérifié sur le nom de chaque classe dans
   `src/test`, pas supposé) : `SaveDmnDraftUseCaseImpl`, `GetDmnDraftUseCaseImpl`,
   `DmnDraftJpaEntity`, `JpaDmnDraftRepositoryAdapter`, `DmnDraftController`,
   `GetDecisionTableDeploymentInfoUseCaseImpl`, `ListDecisionTablesUseCaseImpl`,
   `DmnDecisionDeploymentController`, `FlowableDmnDeploymentAdapter.findAllLatestDeployments`
   (jamais exercé sur le vrai moteur), et les 3 tools MCP DMN. Chaque équivalent BPMN avait son
   test dédié (`BpmnDraftApiTest`, `WorkflowBpmnDeploymentApiTest`,
   `GetProcessDeploymentInfoUseCaseImplTest`, `DeployWorkflowToolTest`…) — l'écart n'avait pas de
   raison fonctionnelle, seulement un oubli lors des commits M5/M6/M10 initiaux. Comblé par 12
   nouveaux fichiers de test (59 tests), miroir méthode par méthode des précédents BPMN, tous
   verts. Aucun bug de production trouvé par cette nouvelle couverture (le code était déjà
   correct — y compris la garde null/filename déjà présente dans
   `FlowableDmnDeploymentAdapter.dmnResourceName`), seule l'absence de preuve d'exécution était le
   problème.
5. **Gap de couverture frontend trouvé en Phase 7** — même constat côté Vitest : `useDmnDraft.ts`,
   `useDmnDeployment.ts`, `DmnEditorCanvas.vue`, `DmnEditorView.vue` n'avaient aucun test unitaire
   dédié (seulement de l'E2E), contrairement à leurs 4 équivalents BPMN
   (`useBpmnDraft.spec.ts`/`useBpmnDeployment.spec.ts`/`BpmnEditorCanvas.spec.ts`/
   `BpmnEditorView.spec.ts`). Comblé par 4 nouveaux fichiers (27 tests), miroir des précédents.
6. **Gap d'accessibilité UI réel trouvé en Phase 7, corrigé** — le « 3ᵉ point d'entrée » de
   l'éditeur DMN (« édition d'une table déjà déployée », promis par D-DMN-DEPLOY/design) était
   codé (`route.query.decisionKey`) mais **jamais atteignable depuis l'UI** : contrairement au
   BPMN (lien « Éditer » sur `WorkflowConfigsAdminView`, `?processDefinitionKey=...`), aucun écran
   de la SPA ne liste les tables de décision déployées ni ne lie vers
   `/admin/dmn-editor?decisionKey=...` — un administrateur ne pouvait y accéder qu'en tapant l'URL
   à la main. Une liste dédiée aurait été une nouvelle fonctionnalité (hors scope M9, déjà livré) ;
   corrigé à la place par un champ **Charger une table existante** dans la barre d'outils de
   l'éditeur lui-même (clé tapée à la main, réutilise `useDmnDraft.load()` déjà écrit) — ferme
   l'accès manquant sans construire un nouvel écran. `GUIDE-UTILISATEUR.md` mis à jour en
   conséquence.
7. **Gap de couverture backend trouvé en Phase 7, comblé** — `extractReferencedDecisionKeys`
   (`FlowableBpmnParserAdapter`) recourt dans `SubProcess.getFlowElements()` mais n'avait jamais
   été exercé avec une fixture imbriquant un `serviceTask type="dmn"` dans un `subProcess`. Le
   code était déjà correct (la récursion fonctionne) ; 1 test ajouté
   (`extractReferencedDecisionKeys_dmnServiceTaskNestedInSubProcess_returnsTheKey`) confirme le
   comportement plutôt que de le laisser non prouvé.

**Point ouvert non corrigé, signalé plutôt que tranché silencieusement** (sévérité faible, cas
limite hors success criteria actés) : `DmnDraft`/`SaveDmnDraftUseCaseImpl` ne valident pas qu'une
table DMN sauvegardée en brouillon respecte déjà l'allowlist JUEL avant l'enregistrement — en
réalité si, `SaveDmnDraftUseCaseImpl.save()` appelle `dmnParserPort.extractDecisionKey()` qui
valide intégralement (parsing + hit policy + allowlist) avant tout `draftRepository.save()`, donc
ce n'est **pas** un gap réel — vérifié en relisant le fichier plutôt que supposé, mentionné ici
pour mémoire de la question posée pendant la revue.

## Coverage

Seuils Jacoco existants tenus (LINE 85%, BRANCH 75%), aucune dérogation. Mesure par classe (pas
seulement l'agrégat `BUNDLE`) faite en **Phase 8** — voir
[docs/ai/testing/2026-09-11-feature-introduction-dmn.md](../testing/2026-09-11-feature-introduction-dmn.md)
§Test Reporting & Coverage pour le détail complet par fichier. Résumé : 3 gaps de couverture réels
trouvés et comblés dans `FlowableDmnParserAdapter` (le fichier sécurité-critique de la feature —
`decision` sans `id`, `decision` portant un `literalExpression` au lieu d'une `decisionTable`,
`decisionTable` à 0 règle) et 1 côté frontend (`useDmnDeployment.ts`, branche de repli du message
d'erreur). Le reste des écarts résiduels (`FlowableDmnDeploymentAdapter`,
`DmnDecisionDeploymentController`, `TenantDmnDeployment`, `useDmnDraft.ts`) est en **parité
exacte** avec son équivalent BPMN — un gap déjà accepté côté BPMN, pas quelque chose d'introduit
par cette feature. `./mvnw verify` final (Phase 8) : **1875 tests, 0 échec**. Frontend : **351
tests Vitest** (+1 sur les 350 de fin de Phase 7), `npm run test:coverage` : 85.83% statements /
79.12% branches / 84.93% functions / 87.63% lines (seuils 80/75/70/60 tenus). Playwright :
**71/71, 0 échec** (preuve fraîche Phase 8).

## Files Changed

**Production (backend) :** voir Code Structure ci-dessus (18 fichiers nouveaux/modifiés hors
tests/docs).

**Production (frontend) :** voir Code Structure ci-dessus (10 fichiers nouveaux/modifiés hors
tests/docs).

**Tests (backend, nouveaux en Phase 5/M1-M11 puis Phase 7) :**
- `FlowableDmnParserAdapterTest` (16), `DmnJuelSecuritySmokeIT` (2),
  `DmnServiceTaskEndToEndSmokeIT` (1), `FlowableDmnParserAdapterShippedDecisionsTest` (1),
  `DmnGrilleEligibiliteSmokeIT` (3), `DeployDecisionTableUseCaseImplTest` (4),
  `FlowableBpmnParserAdapterTest` (modifié, +11 cas DMN dont 1 en Phase 7),
  `DeployBpmnProcessUseCaseImplTest` (modifié, +2 cas décision inconnue).
- **Phase 7 (nouveaux, 59 tests)** : `SaveDmnDraftUseCaseImplTest`, `GetDmnDraftUseCaseImplTest`,
  `DmnDraftJpaEntityTest`, `JpaDmnDraftRepositoryAdapterTest`, `DmnDraftApiTest`,
  `GetDecisionTableDeploymentInfoUseCaseImplTest`, `ListDecisionTablesUseCaseImplTest`,
  `DmnDecisionDeploymentApiTest`, `DeployDecisionTableToolTest`, `ListDecisionTablesToolTest`,
  `GetDecisionTableToolTest`, `FlowableDmnDeploymentAdapterTest`.

**Tests (frontend, nouveaux) :**
- `pilotage.spec.ts` (modifié — `E2E-EDIT-BPMN-5`, `E2E-EDIT-DMN-1`, `E2E-EDIT-DMN-2`).
- **Phase 7 (nouveaux, 27 tests)** : `useDmnDraft.spec.ts`, `useDmnDeployment.spec.ts`,
  `DmnEditorCanvas.spec.ts`, `DmnEditorView.spec.ts`.
- `routes.spec.ts` (modifié — garde de rôle `/admin/dmn-editor`).

**Documentation :**
- `docs/ai/{requirements,design,planning,testing,implementation}/2026-09-11-feature-introduction-dmn.md`.
- `docs/specs/{00-index,05-workflow-engine,25-introduction-dmn}.md`.
- `README.md`, `GUIDE-CONCEPTION-BPMN.md`, `GUIDE-UTILISATEUR.md`, `CHANGELOG.md`.
- `TODO.md` (§61, clôturé), `FILE-ATTENTE.md`.

## Integration Points

Nouveaux endpoints REST isolés : `/api/dmn-decision-drafts/{key}` (GET/PUT),
`/api/dmn-decision-deployments` (POST), `.../info` (GET), `.../{key}/xml` (GET). `BpmnParserPort`
étendu de façon additive (`extractReferencedDecisionKeys`) — aucune signature existante changée.
Nouveaux tools MCP additifs, aucun tool existant modifié (hormis la liste attendue par
`McpEndpointApiTest`, Design Deviation 3).

## Error Handling

`SaveDmnDraftUseCaseImpl`/`DeployDecisionTableUseCaseImpl` : XML `null`/blanc/malformé →
`InvalidDmnException` → 400 ; clé chemin/XML incohérente → 400. Contrôleurs : 204 pour une
absence de brouillon/déploiement (état normal, pas une erreur). `UnknownDecisionKeyException` →
404, cohérent avec `UnknownWorkflowKeyException`. Toute validation reproduite côté serveur, jamais
confiance dans l'éditeur frontend.

## Performance Considerations

Aucun changement de performance en production. Brouillon DMN = `text` unique par clé, même
volumétrie que le brouillon BPMN. Déploiement DMN au démarrage (`TenantDmnDeployment`) : un seul
fichier livré (`grille-eligibilite-client.dmn`), coût de démarrage négligeable, même mécanisme
déjà en place pour 7+ process BPMN.

## Security Notes

- **JUEL, pas FEEL** — voir Core Features et `05-workflow-engine.md` §« Tables de décision — DMN »
  pour le détail complet de l'allowlist et sa preuve d'exécution réelle.
- **Autorisation** : `TENANT_ADMIN`/`WORKFLOW_ADMIN` sur tous les nouveaux endpoints REST, cohérent
  avec BPMN. MCP : pas de garde domaine dédiée dans les use cases DMN (cohérent avec le précédent
  `DeployBpmnProcessUseCaseImpl` — la frontière de confiance MCP est la chaîne JWT M2M, pas un
  raccourci `API_SERVICE`).
- **XXE** : réglages anti-XXE de Flowable (DTD/entités externes désactivées) appliqués
  inconditionnellement, vérifié par décompilation — le contournement JDK25 (Design Deviation 2)
  ne les affecte pas.
- **Pas de nouvelle surface d'attaque frontend** : seule nouvelle dépendance npm (`dmn-js`),
  licence MIT déjà acceptée pour `bpmn-js`.

## Phase 7 — check implementation (2026-09-11)

Revue fichier par fichier dédiée, sur demande explicite de l'utilisateur (« lance la phase 7 »),
au-delà de la revue faite en écrivant chaque milestone : relecture systématique des 80 fichiers du
diff (`git diff --stat` contre la base avant `introduction-dmn`) et de leurs équivalents BPMN pour
vérifier la parité (chaque asymétrie non justifiée = suspect).

- **Alignement design** : D1 (exception native `type="dmn"`), D2 (`dmn-js` sans provider custom),
  D3 (tools MCP), D4 (clé de brouillon composite), D5 (non-goals reconduits) tous vérifiés contre
  le code réel, pas supposés. Aucun gap d'alignement design trouvé.
- **Cohérence avec modules similaires** : chaque classe DMN comparée ligne à ligne à son
  équivalent BPMN (voir tableau Code Structure) — la quasi-totalité suit le miroir exact ;
  l'unique asymétrie fonctionnelle légitime est l'absence de garde « clé déjà connue » côté DMN
  (pas d'équivalent `WorkflowTypeConfigRepository`), confirmée dans le design comme un choix
  assumé, pas un oubli.
- **Gap de couverture de test majeur trouvé et comblé** (Design Deviations 4/5) : 59 tests backend
  + 27 tests frontend nouveaux, aucun bug de production révélé — la couverture manquante était le
  problème, pas le code sous-jacent.
- **Gap d'accessibilité UI réel trouvé et corrigé** (Design Deviation 6) : 3ᵉ point d'entrée de
  l'éditeur DMN promis par le design mais jamais atteignable depuis l'UI.
- **Gap de couverture backend mineur trouvé et comblé** (Design Deviation 7) : récursion
  `SubProcess` de `extractReferencedDecisionKeys` jamais exercée par une fixture imbriquée.
- **Duplication** : aucune — chaque composant DMN réutilise délibérément le pattern BPMN
  correspondant sans réimplémenter une logique déjà écrite.
- **Rupture de contrat** : aucune — tous les ajouts sont additifs (nouveaux endpoints, nouveau
  tool MCP, nouvelle méthode de port).
- **Sécurité** : allowlist JUEL revue une nouvelle fois contre le bytecode décompilé du moteur
  (pas re-décompilé, réutilisation de l'analyse déjà faite en Phase 5/M1, confirmée toujours
  valide) ; contournement JDK25 revérifié comme sans impact XXE (Design Deviation 2).

**Validation finale (preuve d'exécution fraîche, cette session) :** `./mvnw verify` complet
(`clean verify`, sans `-Dskip.frontend`) : backend 1875 tests / 0 échec, build frontend intégré
réussi. `npm run lint` propre. `npm run build` (`vue-tsc -b && vite build`) propre. `npx vitest run`
350/350. `npx playwright test --project=chromium` 71/71 (2 échecs transitoires
`net::ERR_HTTP_RESPONSE_CODE_FAILURE`, liés à la contention de ressources pendant un build Maven
concurrent, non reproductibles en isolation — reconfirmés verts séparément).

**Checklist :** design ✅ · pas de gap logique ✅ (0 bug de production trouvé cette phase — 2 gaps
de couverture et 1 gap d'accessibilité UI trouvés et comblés) · sécurité ✅ (allowlist JUEL et
contournement JDK25 revérifiés) · points d'intégration vérifiés ✅ · tests couvrent les
changements ✅ (1875 backend + 350 frontend + 71 Playwright, tous verts, preuve fraîche cette
session) · docs à jour ✅ (les 7 docs du cycle, `TODO.md` §61 clôturé, `FILE-ATTENTE.md`,
`docs/specs/{00,05,25}`, `GUIDE-CONCEPTION-BPMN.md`, `GUIDE-UTILISATEUR.md` mis à jour pour le
nouveau champ **Charger**).

**Aucun blocage.**

## Phase 8 — write tests (2026-09-11)

Coverage mesurée par classe/fichier (`jacoco.csv` backend, `test:coverage` frontend), pas
seulement à l'agrégat `BUNDLE` — sur demande explicite de l'utilisateur (« lance la phase 8 »).

**Écarts de couverture réels trouvés et comblés** (pas des bugs — code déjà correct, juste jamais
exercé) :
- `FlowableDmnParserAdapter` (fichier sécurité-critique) : 3 branches jamais exercées —
  `decision` sans attribut `id` (rejet), `decision` portant un `<literalExpression>` au lieu d'une
  `<decisionTable>` (rejet), `decisionTable` à 0 règle (rejet). 3 tests ajoutés, tous verts du
  premier coup. **1 branche investiguée et sciemment laissée non couverte**, documentée en
  commentaire plutôt que forcée : le court-circuit `rawText == null` d'`assertSafeCellText` est
  vérifié inatteignable via le chemin de parsing public — le schéma DMN 1.1 exige un `<text>` sur
  chaque `inputEntry`/`outputEntry` (`<inputEntry/>` seul échoue la validation de schéma avant
  d'atteindre ce code, prouvé par un run réel qui échoue avec `cvc-complex-type.2.4.b`).
- `useDmnDeployment.ts` (frontend) : branche de repli `?? 'Déploiement impossible'` jamais
  atteinte. Root-cause : `toProblemDetail()` ne renvoie jamais `detail: undefined/null` pour une
  erreur réseau générique (elle utilise `.message` comme repli interne) — seul un corps
  `ProblemDetail` réel sans champ `detail` déclenche la branche. 1 test ajouté avec la forme
  correcte (`response.data` sans `detail`), vert du premier coup.

**Parité vérifiée avec le précédent BPMN, pas des gaps DMN-spécifiques** (comparaison ligne à
ligne des comptes Jacoco/Vitest) : `FlowableDmnDeploymentAdapter` ≈ `FlowableWorkflowDeploymentAdapter`,
`DmnDecisionDeploymentController` ≈ `WorkflowBpmnDeploymentController`, `TenantDmnDeployment` =
`TenantProcessDeployment` (chiffres identiques), `useDmnDraft.ts` = `useBpmnDraft.ts` (chiffres
identiques). Ces écarts résiduels sont des branches défensives déjà acceptées côté BPMN (ex.
`catch (IOException)`, "classpath vide" non mockable) — non retouchés, cohérent avec la doctrine
du dépôt de ne pas chasser 100% sur du code déjà jugé acceptable ailleurs.

**T5.6 (masquage des valeurs sensibles) revérifié, pas un gap** : la contrainte requirements
« toute valeur sensible produite par une table de décision doit transiter par le masquage
`SendNotificationUseCaseImpl` » a été revérifiée contre le code réel plutôt que supposée acquise —
`redactSensitiveFields` ne s'applique qu'au `DossierFieldsSnapshot` explicitement transmis, et
aucun producteur du dépôt (DMN inclus) ne le câble aujourd'hui. Comportement déjà documenté comme
limitation connue, repo-wide, dans `GUIDE-CONCEPTION-BPMN.md` §8 — une variable produite par DMN
suit exactement le même comportement qu'une variable produite par tout autre `serviceTask`, pas de
régression ni d'écart introduit par cette feature.

Validation finale : `./mvnw verify` **1875 tests / 0 échec** (inchangé en nombre de fichiers, +3
tests dans les fichiers existants). Frontend `npm run test:coverage` **351/351**, sommaire
85.83%/79.12%/84.93%/87.63% (seuils 80/75/70/60 tenus). `npx playwright test --project=chromium`
**71/71, 0 échec** (preuve fraîche, 33.0s — le run précédent avait 2 échecs transitoires de
contention, non reproduits ici).

**Checklist :** design ✅ · pas de gap logique ✅ (0 bug de production trouvé — 4 gaps de
couverture réels trouvés et comblés, 1 branche investiguée et documentée comme inatteignable, 1
contrainte requirements revérifiée sans écart) · sécurité ✅ (le fichier sécurité-critique de la
feature est maintenant couvert à 9 instructions manquées sur 268, contre 28 avant cette phase) ·
tests couvrent les changements ✅ (1875 backend + 351 frontend + 71 Playwright, tous verts, preuve
fraîche cette session) · docs à jour ✅ (`docs/ai/testing/2026-09-11-feature-introduction-dmn.md`
réécrit — terminologie FEEL→JUEL corrigée, chaque scénario T1-T6 coché et tracé vers son test
réel, tableau de couverture par classe/fichier).

**Aucun blocage.**

## Phase 9 — revue finale (2026-09-11)

Revue holistique (au-delà du fichier-par-fichier de Phase 7 et de la couverture de Phase 8), sur
demande explicite de l'utilisateur (« lance la phase 9 ») :

- **Diff complet vérifié** (`git diff --stat b14c602 HEAD`, base = dernier commit avant le premier
  commit `introduction-dmn`) : **97 fichiers**, strictement scopé à la feature — code
  backend/frontend DMN, ses tests, et la documentation qui la référence (`README.md`,
  `docs/specs/**`, `TODO.md`/`FILE-ATTENTE.md`/`CHANGELOG.md`/`GUIDE-*`,
  `docs/ai/**/2026-09-11-feature-introduction-dmn.md`). Aucun fichier hors scope.
- **Working tree propre** (`git status -sb`) : branche à jour avec `origin`, aucun changement non
  committé.
- **Contrat API** (`openapi/openapi.yaml`) : 4 nouveaux chemins
  (`/api/dmn-decision-drafts/{decisionKey}`, `/api/dmn-decision-deployments`, `.../info`,
  `.../{decisionKey}/xml`) + 4 nouveaux schémas (`SaveDmnDraftRequest`, `DmnDraftResponse`,
  `DecisionTableDeploymentResultResponse`, `DecisionTableDeploymentInfoResponse`) documentés
  correctement. **Même imprécision cosmétique déjà connue côté BPMN** relevée pour
  `deployedXml` (`type: string, format: byte` alors que la réponse réelle est
  `text/xml`) — comportement déjà vérifié par les tests (`DmnDecisionDeploymentApiTest`), pas un
  bug fonctionnel, cohérent avec l'imprécision déjà acceptée côté
  `WorkflowBpmnDeploymentApiTest`/`edition-bpmn`, pas une régression nouvelle. `operationId`
  suffixés `_1` (mécanique springdoc à l'ajout d'un contrôleur portant des méthodes de même nom),
  sans impact.
- **Dépendance santé** : `dmn-js@17.10.2` (MIT, Camunda Services GmbH — licence vérifiée sur le
  fichier `LICENSE` réel du paquet installé), `npm ls dmn-js` propre, aucune dépendance
  `UNMET`/`invalid`, aucun conflit avec `bpmn-js@^18.24.0` déjà fixé.
- **Rollback** : migration `021-dmn-draft-tables.yaml` avec bloc `rollback: dropTable` explicite ;
  aucune modification de table existante, aucune migration de données. Revert trivial si
  nécessaire.
- **Breaking changes** : aucun — `BpmnParserPort` étendu de façon additive
  (`extractReferencedDecisionKeys`, un seul implémenteur réel,
  `FlowableBpmnParserAdapter`, vérifié) ; `DmnDeploymentPort`/`DmnDraftRepository` sont de
  nouvelles interfaces sans callers préexistants à casser ; `DeployBpmnProcessUseCaseImpl` modifié
  de façon strictement additive (`extractReferencedDecisionKeys` renvoie un ensemble vide pour
  tout process sans `serviceTask type="dmn"`, donc **aucun changement de comportement** pour les
  process BPMN existants — vérifié en relisant la boucle, pas supposé).
- **Secrets/debug** : diff complet scanné (`console.log`/`debugger`/`.only(`/`.skip(`/
  `System.out.print`/patterns de mot de passe ou clé en dur) — rien trouvé.
- **Cohérence documentaire** : recherche exhaustive des mentions résiduelles de
  `businessRuleTask` dans la documentation active — toutes dans un contexte correct (rejet
  explicite, câblé sur Drools), aucune trace de l'ancienne confusion pré-Phase-3.
- **Preuve E2E fraîche exigée par cette phase** : réutilise la preuve de Phase 8 (`71/71`, cette
  session) — aucun code frontend modifié depuis, `git status` confirmé propre.

**Checklist finale :** design ✅ (D1-D5 vérifiés Phase 7, reconfirmés) · pas de gap logique ✅
(0 nouveau bug trouvé cette phase — **3 bugs réels de production** trouvés et corrigés sur
l'ensemble du cycle M1-Phase 8 : 2 bugs de sérialisation `moddle-xml` en M7-M9, 1 bug
JDK25/Flowable en M12 ; plus 1 test de non-régression obsolète corrigé (`McpEndpointApiTest`,
M12) et plusieurs gaps de **couverture** comblés en Phase 7/8, aucun défaut de production trouvé
par ces derniers — la couverture manquante était le seul problème, précisé à chaque fois dans ce
fichier) · sécurité ✅ (allowlist JUEL, contournement
JDK25 sans impact XXE, RBAC identique BPMN sur tous les nouveaux endpoints/tools) · points
d'intégration vérifiés ✅ (contrat API, dépendances, migration, aucune rupture de contrat) · tests
couvrent les changements ✅ (1875 backend + 351 frontend + 71 Playwright, tous verts, preuve
fraîche) · docs à jour ✅ (les 7 docs du cycle + `TODO.md` §61 clôturé + `FILE-ATTENTE.md` +
`docs/specs/{00,05,25}` + `README.md` + `GUIDE-CONCEPTION-BPMN.md` + `GUIDE-UTILISATEUR.md` +
`CHANGELOG.md`).

**Aucun blocage. Feature prête à être poussée / PR à créer** (demande explicite de l'utilisateur,
pas encore formulée à ce stade).

## Revue adversariale post-Phase 9 (2026-09-11)

Sur demande explicite de l'utilisateur (« lance un subagent d'analyse adversariale »), un subagent
indépendant (modèle Opus, accès complet, aucune confiance accordée à la documentation déjà
écrite par l'agent implémenteur — vérification indépendante exigée pour chaque affirmation) a
mené une revue adversariale ciblée, avec obligation de preuve par exécution réelle (même doctrine
que le reste de ce cycle).

**1 finding bloquant trouvé et corrigé (High).** `FlowableDmnParserAdapter.assertSafeDecision` ne
validait jamais `<input><inputExpression>` — seules les cellules de règle
(`inputEntry`/`outputEntry`) passaient par l'allowlist. Or `ELInputEntryExpressionPreParser`
injecte le texte de l'inputExpression **tel quel** comme opérande gauche de l'expression JUEL
enveloppée, exactement la même surface qu'une cellule. Preuve du subagent, reproduite et
reconfirmée : une table dont toutes les cellules de règle étaient allowlist-safe mais dont
l'inputExpression valait `connectorServiceTask.getClass().getName()` passait
`extractDecisionKey` sans rejet et, exécutée sur le vrai moteur, invoquait réellement
`getClass().getName()` sur le bean Spring `connectorServiceTask` vivant — exécution de méthode
arbitraire via chaîne de navigation de propriété, au-delà de la simple résolution de bean déjà
documentée comme risque JUEL. Atteignable par `POST /api/dmn-decision-deployments`
(`TENANT_ADMIN`/`WORKFLOW_ADMIN`) et par le tool MCP `deploy_decision_table` (tout appelant M2M).

**Corrigé** : `assertSafeDecision` valide désormais chaque `InputClause.getInputExpression()`
contre un identifiant nu strict (`[A-Za-z_][A-Za-z0-9_]*`, aucun `.`/`(`/`)`/`[`/`]`/`#`/`$`/
espace toléré) — même discipline déjà appliquée aux identifiants dans
`FlowableBpmnParserAdapter.assertSafeConditionExpression` (TODO §57 H1). 6 tests ajoutés à
`FlowableDmnParserAdapterTest` (attaque exacte du subagent + navigation de propriété + appel de
fonction + EL brut + inputExpression vide, tous rejetés + non-régression sur l'identifiant nu déjà
utilisé par toutes les fixtures livrées). **Résidu accepté, pas nouveau** : un identifiant nu
légitime sans variable de process correspondante au runtime continue de résoudre contre un bean du
même nom — même risque déjà accepté pour `conditionExpression` côté BPMN (« un identifiant seul
est la même ambiguïté ») ; le correctif ferme l'escalade prouvée vers l'exécution de méthode, pas
ce résidu structurel de JUEL/UEL.

**Vérifications indépendantes du subagent qui ont confirmé l'implémentation correcte (pas de
nouveau finding)** : le contournement JDK25 (`enableSafeDmnXml=false`, `WorkflowConfig.java`) a
été revérifié à la fois par décompilation bytecode indépendante et par un déploiement réel d'une
table portant une entité XML externe — l'entité n'a pas été résolue, confirmant que la protection
anti-XXE reste bien inconditionnelle. RBAC MCP et isolation multi-tenant (`dmn_draft`, requêtes
Flowable DMN) confirmés cohérents avec la posture déjà acceptée du dépôt.

**2 constats mineurs signalés, non corrigés** (voir `TODO.md` §61 pour le détail) : absence de
limite de taille sur `DmnDraftController.save` (gap identique côté BPMN, pas introduit ici,
sévérité faible) ; redéploiement d'une table DMN prend effet immédiatement pour les instances en
cours (comportemental, même logique que BPMN, jamais explicitement tranché pour DMN).

Validation finale : `./mvnw verify` vert après correctif (aucune régression sur
`DmnJuelSecuritySmokeIT`, `DmnServiceTaskEndToEndSmokeIT`, `DmnGrilleEligibiliteSmokeIT`,
`FlowableDmnParserAdapterShippedDecisionsTest` — la fixture `grille-eligibilite-client.dmn`
utilisait déjà exclusivement des identifiants nus, aucun changement de comportement pour elle).
