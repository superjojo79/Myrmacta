---
phase: implementation
title: Implementation Guide — Palette métier BPMN (LC1)
description: Ce qui a été livré pour TODO §73 (palette-metier-bpmn), écarts au design, cas limites et points de sécurité
---

# Implementation Guide — palette métier BPMN (TODO §73, LC1)

Documents liés : [requirements](../requirements/2026-09-29-feature-palette-metier-bpmn.md) ·
[design](../design/2026-09-29-feature-palette-metier-bpmn.md) ·
[planning](../planning/2026-09-29-feature-palette-metier-bpmn.md) ·
[testing](../testing/2026-09-29-feature-palette-metier-bpmn.md).

## Development Setup

- Backend : `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw verify -Dskip.frontend=true`.
- Frontend : `cd frontend && npx vitest run && npx vue-tsc --build && npx eslint .`.
- Playwright : `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium-1194/chrome-linux/chrome npx playwright test -g "E2E-PALETTE"`
  (le serveur de test construit puis sert le SPA sur `127.0.0.1:4173`, API simulée).
- Aucune variable d'environnement, aucune migration, aucun drapeau : la fonctionnalité est active dès le déploiement.

## Code Structure

### Backend (`src/main/java/com/flowforge/…`)

| Zone | Contenu |
|---|---|
| `domain/workflow/palette/` | `BuildingBlockCatalog` (VERSION 1, 11 briques, 8 sources de valeurs), `BuildingBlock`, `BlockParameter`, `BlockMarker`, `ParameterType`/`ParameterBinding`/`BlockElement`/`BlockCategory` ; `BlockAttributeRules` (validation de **forme**, dérivée du catalogue) ; `BlockReferenceVerifier` + `BlockParameterUsage` (validation d'**existence**) |
| `domain/workflow/palette/port/{in,out}` | `ListBuildingBlocksUseCase`, `ListBlockReferencesUseCase`, `BlockReferenceLookupPort`, `BlockReference` |
| `domain/workflow/usecase/` | `ListBuildingBlocksUseCaseImpl`, `ListBlockReferencesUseCaseImpl` (garde `requireTenantOrWorkflowAdminOrApiService`) ; `DeployBpmnProcessUseCaseImpl.validate(bytes, Optional<WorkflowTypeConfig>)` partagé par le déploiement et le `dryRun` MCP |
| `infrastructure/flowable/` | `DeployedActivityParameters` (lecture des attributs `flowable:<nom>` de l'activité courante) ; six delegates modifiés ; `FlowableBpmnParserAdapter` (appelle `BlockAttributeRules`, `extractBlockReferences`) |
| `infrastructure/palette/` | `BlockReferenceLookupAdapter` |
| `infrastructure/web/workflow/` | `BuildingBlockController` (`/api/bpmn-building-blocks`, `/references/{source}`), `WorkflowExceptionHandler` (404 pour `UnknownBlockSourceException` / `UnknownBlockReferenceException`) |
| `infrastructure/mcp/tool/` | `ListBpmnBuildingBlocksTool` (`list_bpmn_building_blocks`) ; `DeployWorkflowTool` (le `dryRun` appelle `validate`) |

### Frontend (`frontend/src/components/workflow/bpmn-blocks/`)

| Fichier | Rôle |
|---|---|
| `types.ts`, `catalogClient.ts`, `blockCatalogHolder.ts` | miroir du catalogue, chargement mis en cache (`null` si indisponible), dépôt module-level lu par les providers |
| `recognizeBlock.ts` | règles ordonnées de reconnaissance (délégué → `flowable:type` → `formKey` + `portalRecipient`) |
| `valueSourceResolver.ts` | sources de valeurs → `{value,label}` (cache de session, `peekValueSource` synchrone pour les diagnostics) |
| `blockValues.ts` | lecture / écriture par binding (`ATTRIBUTE`, `FIELD_ELEMENT`, `FORM_KEY_SUFFIX`, `SIGNER_ELEMENTS`), `ensureFlowableNamespace`, `toggleMulti` |
| `blockPaletteProvider.ts` | groupe « Briques métier » de la palette, `createBlockShape` |
| `blockPropertiesProvider.ts` | groupe « Brique » (+ liste des signataires) inséré après « General » |
| `blockDiagnostics.ts`, `blockContext.ts`, `blockMarkers.ts` | avertissements non bloquants, contexte (attente `signatureCompleted`, listes déjà chargées), marqueur CSS `fb-block-warning` |
| `testCatalog.ts` | catalogue de test partagé par les specs |

`BpmnEditorCanvas.vue` charge le catalogue **avant** de créer le Modeler, n'ajoute les trois modules que s'il est
disponible, renseigne la clé du process ouvert et charge les feuilles de style de `bpmn-js`.
`flowablePropertiesProvider.ts` : le groupe « Flowable » devient « Avancé » (`shouldOpen` = tâche non reconnue).

## Implementation Notes

- **Précédence (D-PALETTE-PRECEDENCE, révisée).** `DeployedActivityParameters.get(execution, name)` lit d'abord l'attribut
  de l'élément déployé (`execution.getCurrentFlowElement()`, nettoyé des espaces), puis la variable (non nulle, non blanche,
  collection/map non vide), sinon `null`.
  Aucun process existant ne change de comportement.
- **Une seule source de vérité.** `BlockAttributeRules` (forme) et `BlockReferenceVerifier` (existence) sont dérivés du
  catalogue ; `FlowableBpmnParserAdapterBlockTest` vérifie qu'un exemple XML par brique est accepté (conformité
  catalogue ⇄ parseur).
- **Existence à l'endroit qui connaît le tenant.** Le parseur ne fait que la forme ; `DeployBpmnProcessUseCaseImpl.validate`
  vérifie l'existence (port `BlockReferenceLookupPort`) avec la configuration effective du process (statuts).
  Le `dryRun` de `deploy_workflow` passe par la même méthode (parité des refus).
- **Panneau.** Chaque paramètre est une entrée `@bpmn-io/properties-panel` : `TextFieldEntry`, `SelectEntry`,
  `CheckboxEntry` (multi-sélection : une case par valeur), `ListGroup` (signataires). Les listes viennent de
  `resolveValueSourceCached` ; source non listable ou en échec ⇒ saisie libre (l'existence est vérifiée au déploiement).
- **Écriture.** Vider un champ **retire** l'attribut (jamais de chaîne vide) — seule exception : `portalRecipient` d'une
  tâche externe, dont la simple présence distingue la tâche externe d'une question utilisateur ; `formKey` garde son
  préfixe `user-question:`.
- **D-FLOWABLE-NS.** `ensureFlowableNamespace` mute `definitions.$attrs` (accesseur moddle) avant chaque écriture ; testé
  sur un vrai `bpmn-moddle` (S-UI-7) et dans le navigateur.

## Integration Points

- API : `GET /api/bpmn-building-blocks`, `GET /api/bpmn-building-blocks/references/{documentTemplates|connectorInstances|notificationWebhooks}`
  (rôles `TENANT_ADMIN`, `WORKFLOW_ADMIN`) ; les cinq autres sources utilisent les API existantes du SPA.
- MCP : nouvel outil de lecture `list_bpmn_building_blocks` ; `deploy_workflow` renvoie les mêmes refus (400 / 404)
  en `dryRun`.
- OpenAPI dorée régénérée : diff **purement additif** (deux chemins, cinq schémas).

## Error Handling

- Attribut non littéral ou mal formé : `InvalidBpmnException` → 400, message nommant l'activité et l'attribut.
- Ressource inexistante : `UnknownBlockReferenceException` → 404 (comme `UnknownDecisionKeyException`) ;
  source inconnue de l'endpoint de références : `UnknownBlockSourceException` → 404.
- Catalogue indisponible côté SPA : aucune exception, aucun module de briques ; message discret inutile (l'éditeur
  fonctionne comme avant).
- Aucun retry : lectures idempotentes.

## Performance Considerations

- Catalogue statique (pas d'E/S) servi avec `Cache-Control: no-cache` ; une requête par ouverture de l'éditeur.
- Listes de références : une requête par source et par session d'édition (cache en mémoire, vidé à l'ouverture) ;
  les échecs ne sont pas mémorisés. Volumétrie d'administration : quelques dizaines de lignes.
- Les diagnostics de canevas sont synchrones sur des listes déjà chargées (`peekValueSource`).

## Security Notes

- **Aucune expression évaluable dans les attributs** : `${…}` / `#{…}` refusés au parseur (défense en profondeur avec
  l'allowlist de délégués, TODO §55 D-EXPR-SAFETY inchangée) ; formats UUID / clé / énumération / longueur bornés.
- **Aucune donnée du tenant dans le catalogue** ; les trois listes réservées à l'administration ne renvoient que
  `{value,label}` (pas de configuration, pas de secret) et exigent `TENANT_ADMIN` ou `WORKFLOW_ADMIN`.
- Une adresse e-mail littérale dans un BPMN (`notificationTo`) reste possible mais déconseillée par l'éditeur
  (donnée personnelle versionnée dans un artefact de conception).
- Les attributs `flowable:*` d'un diagramme importé sont validés comme ceux d'un diagramme conçu dans l'éditeur.

## Journal par jalon

| Jalon | Contenu | Commit |
|---|---|---|
| M0–M1 | catalogue, API, outil MCP, gardes | `feat(palette): M0-M1 catalogue des briques, API et tool MCP` |
| M2 | `DeployedActivityParameters` + six delegates | `feat(palette): M2 paramètres littéraux lus par les six delegates` |
| M3–M4 (+M3b) | forme, existence, parité `dryRun` | `feat(palette): M3-M4 …` |
| M5 | client de catalogue, reconnaissance, sources de valeurs | `feat(palette): M5 …` |
| M6–M8 | palette, panneau, « Avancé », diagnostics, marqueurs | `feat(palette): M6-M8 …` |
| M9–M10 | intégration éditeur, feuilles de style, Playwright | `feat(palette): M9-M10 …` |
| M11–M12 | OpenAPI dorée, documentation, vérification globale | voir testing |

## Écarts au design

| # | Écart | Gravité | Justification |
|---|---|---|---|
| 1 | ~~La source `dmnTables` n'est pas listée~~ — **résolu par LC1-suite** (TODO §74, 2026-09-30) : `dmnTables` est servie par `references/dmnTables` | résolu | à l'origine, aucune API de liste n'existait ; voir [LC1-suite](2026-09-30-feature-lc1-suite-palette-dmn.md) |
| 2 | Le premier paramètre `FORM_KEY_SUFFIX` s'appelle `schemaKey` (et non `questionSchema`) | nul | nom aligné sur `TaskQuestionSchema.schemaKey` |
| 3 | `blockCatalogHolder` (module-level) + cache des listes vidé à l'appel de `setBlockCatalog` | faible | le Modeler instancie ses modules de façon synchrone ; alternative (props Vue) trop couplante |
| 4 | Feuilles de style `bpmn-js` / `diagram-js` / panneau chargées par `BpmnEditorCanvas` | **à noter** | découvert par Playwright : elles n'étaient importées nulle part, la palette (police d'icônes) était invisible et le panneau non mis en forme. Correctif de fond, hors périmètre annoncé mais nécessaire |
| 5 | Le groupe « Signataires » reste replié après « + » (le nouvel élément, lui, s'ouvre) | cosmétique | comportement de `ListGroup` avec un groupe recréé à chaque écriture ; à revoir si le retour utilisateur le demande |
| 6 | Tests Playwright ajoutés à `e2e/pilotage.spec.ts` (`E2E-PALETTE-1..6`) plutôt que dans un nouveau fichier | nul | réutilise `mockSession`/`openApp` non exportés |

## Cas limites traités

- Tâche externe fraîchement posée (`portalRecipient=""`) reconnue comme telle.
- Référence disparue signalée uniquement si la liste est **chargée** (jamais de faux positif pendant le chargement ou
  si la source est indisponible).
- Statuts de workflow : proposés depuis la configuration du process **ouvert** ; sans configuration ⇒ saisie libre.
- Import sans `xmlns:flowable`, sans DI, avec attributs inconnus : préservés.

## Points de suivi

- Endpoint de liste des tables DMN déployées (résout l'écart 1).
- LC5 (concepteur de parcours) réutilise ce catalogue ; LC2 (expressions) pourra assouplir « littéral seulement ».

## Revue Phases 7 à 9 (2026-09-30)

**Phase 7 — conformité au design** : catalogue, bindings, ordre de reconnaissance, validation en deux temps
(forme au parseur, existence au use case), parité `dryRun`, `ensureFlowableNamespace`, groupe « Brique » après
« General » et « Avancé » repliée : conformes. Écarts de nom sans effet : `BlockAttributeRules` (design :
`BuildingBlockAttributeValidator`), `ensureFlowableNamespace` dans `blockValues.ts` (design : `flowableNamespace.ts`).
Deux écarts corrigés : (1) S-UI-9 — une source de statuts/références indisponible affichait une saisie libre **sans**
le message prévu ⇒ message ajouté ; (2) marqueur d'avertissement conservé sur un élément devenu tâche brute (ex. délégué
modifié dans « Avancé ») ⇒ retiré.

**Phase 8 — tests** : spec de non-régression ajoutée pour le marqueur ; Vitest 490 verts, Playwright 104 verts,
`./mvnw verify` inchangé (2648 verts, aucun code backend modifié depuis).

**Phase 9 — revue** : aucune faille bloquante. Points vérifiés : lecture des attributs par URI d'espace de noms,
valeurs jamais évaluées, `MANAGED_ATTRIBUTES` refusés hors de leur délégué, blanc refusé au déploiement et ignoré à
l'exécution (cohérent), rollback trivial (aucune migration, aucun état). Suivis non bloquants : liste des tables DMN
(écart 1), groupe « Signataires » replié après ajout (écart 5), un E2E réel navigateur + serveur si le besoin se confirme.

## Corrections de la revue adversariale (2026-09-30)

Revue par un sous-agent (rapport : 0 bloquant, 4 importants, 8 mineurs). Corrigés : **I1** l'attribut gagne sur la
variable (une variable de complétion de tâche écrasait statut / connecteur / destinataire fixés ; décision
D-PALETTE-PRECEDENCE révisée) ; **I2** le déploiement refuse espaces de bord et retours à la ligne (hors message de
notification), l'exécution nettoie, l'éditeur n'écrit jamais d'espaces et les signale ; **I3** `xmlns:flowable` est
déclaré avant la création d'une brique depuis la palette (Playwright `E2E-PALETTE-7`) ; **I4** valeur d'énumération
invalide visible « (invalide) » + diagnostics d'énumération et de format (UUID / clé) ; **M1** exemple `CLOTURE` ;
**M2** garde du domaine alignée (`requireTenantOrWorkflowAdmin`) ; **M3** type de dossier inactif refusé (une
configuration de workflow sans BPMN déployé reste acceptée : elle peut être déployée juste après) ; **M4** avertissement
« dossier enfant qui redémarre le même process » dans l'éditeur (le serveur l'accepte : cas légitime avec condition
d'arrêt) ; **M5** `deploy_workflow` valide avant d'enregistrer la configuration ; **M6** collection / map vide = absence de
valeur ; **M7** clé du process suivie après renommage, cache des listes limité à 60 s. **M8** : le process de test
`block-attributes-smoke` est déplacé hors de `processes/` (`src/test/resources/palette/`) et déployé par le seul
`BlockAttributesEndToEndIT`, puis retiré ; le catalogue de test du frontend utilise les vrais statuts ; les tests
manquants (espaces, palette sans namespace, surcharge par variable) sont ajoutés.
