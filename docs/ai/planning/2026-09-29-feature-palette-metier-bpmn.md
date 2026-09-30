---
phase: planning
status: reviewed
title: Project Planning & Task Breakdown
description: Plan de la palette métier BPMN (LC1)
---

# Project Planning & Task Breakdown — `palette-metier-bpmn` (LC1)

> **Phase 4 (planning) close le 2026-09-29** : les 62 scénarios du testing sont tous tracés vers au moins une tâche (contrôle scripté), dépendances et chemin critique vérifiés contre le design `reviewed`.
> **Phase 1 du 2026-09-29.** Plan dérivé des [requirements](../requirements/2026-09-29-feature-palette-metier-bpmn.md), du
> [design](../design/2026-09-29-feature-palette-metier-bpmn.md) et des scénarios du
> [testing](../testing/2026-09-29-feature-palette-metier-bpmn.md). Taille estimée **M–L** (le lot dépasse le
> « frontend seul » de l'analyse : delegates + parseur + catalogue). Livraison en **une PR** à jalons internes.

## Milestones

- [x] **J1 — Serveur** : M0–M4 dont M3b (catalogue, API/MCP, paramètres littéraux, validation de forme et d'existence, parité `dryRun`, conformité). Exécutable sans UI : un BPMN à attributs se déploie et s'exécute.
- [x] **J2 — Éditeur** : M5–M9 (client de catalogue, reconnaissance, palette, panneau, diagnostics, « Avancé »).
- [x] **J3 — Clôture** : M10–M12 (Playwright, docs, vérification globale).

## Task Breakdown

### M0 — Catalogue du domaine
- [x] **T0.1** Records/enums (`BuildingBlock`, `BlockParameter`, `ParameterType`…) dans `domain.workflow.palette`. *scénarios :* S-CAT-2. *preuve :* tests des invariants ; ArchUnit vert.
- [x] **T0.2** `BuildingBlockCatalog` : les 11 briques. *dép. :* T0.1. *scénarios :* S-CAT-1, S-CAT-2, S-CAT-3.

### M1 — API et MCP
- [x] **T1.1** `ListBuildingBlocksUseCase` + `GET /api/bpmn-building-blocks` (droits éditeur). *dép. :* T0.2. *scénarios :* S-CAT-4, T-SEC-1.
- [x] **T1.3** `ListBlockReferencesUseCase` + `GET /api/bpmn-building-blocks/references/{source}` (3 sources, `{value,label}`). *dép. :* T1.1. *scénarios :* S-REF-1..S-REF-4.
- [x] **T1.2** Tool MCP `list_bpmn_building_blocks` ; golden OpenAPI. *dép. :* T1.1. *scénarios :* S-CAT-5, S-CAT-6.

### M2 — Paramètres littéraux à l'exécution
- [x] **T2.1** `DeployedActivityParameters` (variable → attribut relu du BPMN déployé → absent). *scénarios :* S-RT-1..S-RT-6.
- [x] **T2.2** Adapter `UpdateDossierStatusDelegate` et `UpdateWorkflowLinkStatusDelegate`. *dép. :* T2.1.
- [x] **T2.3** Adapter `DocumentGenerationTask` et `ConnectorServiceTask`. *dép. :* T2.1.
- [x] **T2.4** Adapter `NotificationDispatchTask` et `CreateChildDossierDelegate`. *dép. :* T2.1.
- [x] **T2.5** Test moteur réel de bout en bout sans variables. *dép. :* T2.2–T2.4. *scénarios :* S-RT-7.

### M3 — Validation au déploiement
- [x] **T3.1** `BuildingBlockAttributeValidator` dérivé du catalogue (autorisation par delegate, littéral, formes). *dép. :* T0.2. *scénarios :* S-PARSE-2..S-PARSE-4, S-PARSE-9.
- [x] **T3.2** Branchement dans `FlowableBpmnParserAdapter` ; non-régression des `processes/*`. *dép. :* T3.1. *scénarios :* S-PARSE-5..S-PARSE-8, T-SEC-2, T-SEC-3.

### M3b — Existence des références et parité `dryRun`
- [x] **T3b.1** Port `BlockReferenceLookupPort` + adaptateur d'infrastructure (templates actifs, instances de connecteur SERVICE, types de dossier, configurations de workflow, webhooks). *dép. :* T0.2. *scénarios :* S-REFCHK-1, S-REFCHK-2.
- [x] **T3b.2** `BlockReferenceVerifier` + `UnknownBlockReferenceException` (404) ; branchement dans `DeployBpmnProcessUseCaseImpl` avec l'extraction `extractBlockParameters` du parseur. *dép. :* T3b.1, T3.2. *scénarios :* S-REFCHK-1..S-REFCHK-4.
- [x] **T3b.3** `DeployBpmnProcessUseCase.validate(xml, config)` ; `deploy` l'appelle ; `DeployWorkflowTool` en `dryRun` l'appelle avec la configuration soumise. *dép. :* T3b.2. *scénarios :* S-DRY-1, S-DRY-2.

### M4 — Conformité catalogue ↔ parseur
- [x] **T4.1** Test : un XML par brique généré depuis le catalogue est accepté ; chaque règle de forme a un cas refusé. *dép. :* T3.2. *scénarios :* S-PARSE-1.

### M5 — Client de catalogue et reconnaissance (frontend)
- [x] **T5.1** `catalogClient` + store avec repli. *scénarios :* S-FE-1, S-FE-4.
- [x] **T5.2** `recognizeBlock` (11 motifs). *dép. :* T5.1. *scénarios :* S-FE-2.
- [x] **T5.3** `valueSourceResolver` (8 sources, modules d'API existants). *dép. :* T5.1. *scénarios :* S-FE-3.

### M6 — Palette
- [x] **T6.0** **Spike** (avant M7) : `PaletteProvider` via `additionalModules`, création d'un élément avec attributs `flowable:*`, groupe repliable, marqueur CSS ; consigner le résultat dans l'implémentation. *dép. :* T5.1.
- [x] **T6.1** `blockPaletteProvider` : groupe « Briques métier », création d'éléments pré-configurés. *dép. :* T5.1. *scénarios :* S-UI-1.

### M7 — Panneau de brique
- [x] **T7.1** Champs typés (texte, énumération, multi-sélection, sélecteur alimenté), écriture/retrait d'attribut. *dép. :* T5.2, T5.3. *scénarios :* S-UI-2, S-UI-3.
- [x] **T7.2** Signataires (`SIGNERS`) pour « Demander une signature », en réutilisant le comportement de `flowable:signer`. *dép. :* T7.1.
- [x] **T7.4** `ensureFlowableNamespace` avant toute écriture de brique ; contexte de process pour `workflowStatuses`. *dép. :* T7.1. *scénarios :* S-UI-7, S-UI-9.
- [x] **T7.3** Section « Avancé » repliée (entrées brutes actuelles) et repli pour tâche non reconnue. *dép. :* T7.1. *scénarios :* S-UI-4, S-UI-5, S-UI-6, S-UI-8.

### M8 — Diagnostics
- [x] **T8.1** `blockDiagnostics` (obligatoire, référence absente, valeur non littérale, compagnons signature / tâche externe, variable prioritaire) + marqueur d'élément + texte de panneau. *dép. :* T7.1. *scénarios :* S-DIAG-1..S-DIAG-8.

### M9 — Intégration à l'éditeur
- [x] **T9.1** Enregistrement des providers dans `BpmnEditorCanvas` ; import/export sans perte. *dép. :* T6.1, T7.3, T8.1.

### M10 — Playwright
- [x] **T10.1** Parcours S-PW-1..S-PW-5. *dép. :* T9.1.
- [x] **T10.2** (optionnel) E2E réel serveur + navigateur. *dép. :* T2.5. *scénarios :* S-E2E-1.

### M11 — Documentation
- [x] **T11.1** `GUIDE-CONCEPTION-BPMN.md` (briques, attributs, précédence variable/attribut), `GUIDE-UTILISATEUR.md` (éditeur), `14-edition-bpmn.md`, `CHANGELOG.md`.
- [x] **T11.2** Statuts : TODO (nouveau §73), `FILE-ATTENTE.md`, `ROADMAP.md`, `DEFERRED-FEATURES.md`, analyse 39.

### M12 — Vérification globale
- [x] **T12.1** `./mvnw verify`, Vitest, Playwright verts ; chiffres consignés dans le testing.
- [x] **T12.2** Revue de sécurité du parseur (attributs littéraux) et analyse adversariale ; corriger avant PR.

## Dependencies

- Aucune dépendance externe. **Prérequis livré** : `signature-electronique` (brique 11, attributs de signature déjà validés).
- Ordre critique : M0 → M3 → M4 (validation serveur) ; M5 → M7 → M9 (éditeur). M2 (delegates) indépendant de M5–M9.
- Parallélisable avec **LC2** et **D1** (surfaces disjointes) ; LC2 et D3b ajouteront leurs briques au catalogue plus tard.

## Timeline & Estimates

M0 **S** · M1 **M** · M2 **M** (six delegates) · M3 **M** · M3b **M** · M4 **S** · M5 **S** · M6 **S** · M7 **M** · M8 **S** · M9 **S** · M10 **S** · M11 **S** · M12 **M**. Total **M–L**. Chemin critique : M0 → M3 → M3b → M4 → J1, puis M5 → M7 → M9 → M10.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Divergence catalogue / parseur | Le parseur **dérive** du catalogue ; test de conformité (T4.1) |
| Régression des delegates (variables) | Tests S-RT-1 par delegate avant toute modification (TDD) ; variable gagnante |
| API `PaletteProvider` de bpmn-js peu documentée en Vue | Spike en M6 avant M7 ; repli : entrées de palette via `contextPad`/menu « Remplacer » |
| Ambiguïté de reconnaissance (tâche externe vs question) | Règles ordonnées + cas de test S-FE-2 |
| Source d'options lente ou indisponible | Chargement à la demande, saisie libre en repli, pas de faux avertissement |
| Faux sentiment de simplicité (BPMN reste du BPMN) | Rappel dans le panneau (éléments à ajouter) et guide |

## Resources Needed

Aucune ressource externe. Compétences : bpmn-js / properties-panel, Flowable (`ProcessDefinitionUtil`), Vue 3.

## Traçabilité scénarios → tâches

| Famille | Tâches |
|---|---|
| S-CAT-1..3 | T0.1, T0.2 |
| S-CAT-4, T-SEC-1 | T1.1 |
| S-CAT-5, S-CAT-6 | T1.2 |
| S-REF-1..4 | T1.3 |
| S-RT-1..6 | T2.1 (+ T2.2–T2.4 par delegate) |
| S-RT-7 | T2.5 |
| S-PARSE-2..4, 9 | T3.1 |
| S-PARSE-5..8, T-SEC-2, T-SEC-3 | T3.2 |
| S-REFCHK-1..4 | T3b.1, T3b.2 |
| S-DRY-1, S-DRY-2 | T3b.3 |
| S-PARSE-1 | T4.1 |
| S-FE-1, S-FE-4 | T5.1 ; S-FE-2 → T5.2 ; S-FE-3 → T5.3 |
| S-UI-1 | T6.1 ; S-UI-2, S-UI-3 → T7.1 ; S-UI-7, S-UI-9 → T7.4 ; S-UI-4..6, S-UI-8 → T7.3 |
| S-DIAG-1..8 | T8.1 |
| S-PW-1..5 | T10.1 ; S-E2E-1 → T10.2 |

## Bilan d'avancement (Phase 5 close, 2026-09-29)

Les treize jalons M0–M12 sont livrés sur la branche `claude/dreamy-davinci-4ddp96` (commits par jalon). Écarts
au plan : `dmnTables` en saisie libre faute d'API de liste (implementation, écart n°1) ; feuilles de style de
`bpmn-js` ajoutées à l'éditeur (découvertes manquantes par Playwright, écart n°4) ; pas d'E2E réel navigateur + serveur
(T10.2 couvert au niveau moteur). Reste : Phases 6–9 (mise à jour du planning, testing, revue) puis PR — non ouverte.
