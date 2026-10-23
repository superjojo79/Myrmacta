# Palette métier dans l'éditeur BPMN (LC1)

> **Statut : livré — mergé le 2026-09-30 ([PR #151](https://github.com/superjojo79/FlowForge/pull/151), TODO §73).** Phase 1 : — décisions D-PALETTE-* tranchées, voir les [requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-29-feature-palette-metier-bpmn.md). **Correction** : les paramètres de six delegates vivent dans des variables de process, pas dans le XML ; le lot inclut donc les delegates et le parseur (§4 ci-dessous « aucun changement du parseur » est caduc). Analyse d'origine (2026-09-28) : Sous-lot
> **LC1** `palette-metier-bpmn` de la couche low-code (D0 = C, [31](31-positionnement-produit.md),
> rang 1 du classement). À affiner en `dev-lifecycle` Phase 1. Exposition MCP :
> [46](46-exposition-mcp-couche-low-code.md).

---

## 1. Constat vérifié (`main` @ `e3985fe`)

| Élément | Constat | Preuve |
|---|---|---|
| Éditeur | `bpmn-js` Modeler + `bpmn-js-properties-panel` avec un provider Flowable maison | `BpmnEditorCanvas.vue`, `flowablePropertiesProvider.ts` |
| Panneau `serviceTask` | Champs **texte libres** : `delegateExpression`, `expression`, `type`, `decisionTableReferenceKey`, case « Asynchrone » | `flowablePropertiesProvider.ts` L83–220 |
| Panneau `userTask` | Texte libre : `formKey`, `portalRecipient`, `portalLinkTtlDays` | idem L239–244 |
| Briques autorisées | 7 delegates (`connectorServiceTask`, `documentGenerationTask`, `attachGeneratedDocumentTask`, `notificationDispatchTask`, `createChildDossierTask`, `updateDossierStatusTask`, `updateWorkflowLinkStatusTask`) + `type="dmn"` + `formKey` `dossier-fields` / `document-generation` / `user-question:<clé>` | `AllowedBpmnDelegates`, §52, §55, §61 |
| Validation | Uniquement **au déploiement** (serveur) | `FlowableBpmnParserAdapter`, `BpmnExpressionSurfaceScanner` |
| Catalogues disponibles en lecture | Templates, instances de connecteurs, définitions de connecteurs, configurations de workflow (statuts), tables DMN, schémas de questions | `/api/document-templates`, `/api/connectors`, `/api/connector-definitions`, `/api/workflow-type-configs`, `/api/dmn-decision-deployments`, `/api/task-question-schemas` |
| Guide | 33 Ko de conventions à connaître pour concevoir | `docs/guides/GUIDE-CONCEPTION-BPMN.md` |

## 2. Objectif

Remplacer la saisie de noms techniques par des **briques métier** : « Générer un document »,
« Joindre le document », « Appeler un connecteur », « Notifier », « Créer un dossier enfant »,
« Changer le statut du dossier », « Décision DMN », « Question utilisateur », « Tâche externe
(portail) ». Chaque brique ouvre un panneau avec des **listes alimentées par l'API** et écrit
elle-même le XML attendu.

## 3. Périmètre à poser (Phase 1)

- **Catalogue de briques** : un descripteur par brique (élément BPMN, attributs écrits, paramètres
  typés, source des valeurs). Proposé comme **artefact du domaine**, source unique pour l'UI et
  le MCP (principe P5 de l'analyse 46, exception assumée).
- **Mécanisme UI** : les *element templates* de bpmn.io sont outillés pour le moddle Camunda ;
  pour Flowable, reproduire le principe dans le provider maison existant ou adapter la
  bibliothèque — à trancher.
- **Validation à la conception** : champ obligatoire vide, référence inexistante (template
  supprimé, statut inconnu) signalés dans l'éditeur, sans attendre le déploiement.
- **Mode expert** : les champs bruts restent accessibles pour les intégrateurs.
- **Diagrammes existants** : reconnaissance d'une brique dans un BPMN déjà déployé (pour l'afficher
  en mode brique) ou affichage brut.

## 4. Coutures et dépendances

- Frontend `bpmn-properties` principalement ; éventuel endpoint de catalogue en lecture.
- **Aucun changement du parseur serveur** : le XML produit est celui d'aujourd'hui, validé par les
  mêmes allowlists (§57, §69).
- Parallélisable avec D1 et D3a. Les briques de LC2 (conditions) et D3b (mapping) s'ajouteront
  au catalogue quand ces lots seront livrés.

## 5. Exposition MCP

`list_bpmn_building_blocks` (ou ressource MCP, D-MCP-LC-RESOURCES) servant **le même catalogue** ;
l'agent compose le BPMN à partir des briques et le vérifie par `deploy_workflow` en `dryRun`.

## 6. Risques

- Double représentation si le catalogue et l'allowlist serveur divergent → test qui vérifie que
  chaque brique produit un XML accepté par le parseur.
- Faux sentiment de simplicité : les passerelles et les événements restent du BPMN pur.

## 7. Décisions à trancher

1. **D-PALETTE-MECHANISM** — provider maison étendu ou adaptation des element templates bpmn.io.
2. **D-PALETTE-CATALOG-SOURCE** — catalogue déclaré côté serveur (servi par API) ou côté SPA.
3. **D-PALETTE-EXPERT** — mode expert toujours visible, ou réservé à un rôle.
4. **D-PALETTE-SCOPE** — briques de la première tranche.

## 8. Documentation impactée

`docs/guides/GUIDE-CONCEPTION-BPMN.md` (fortement simplifié), `docs/guides/GUIDE-UTILISATEUR.md` (éditeur BPMN),
`14-edition-bpmn.md`.
