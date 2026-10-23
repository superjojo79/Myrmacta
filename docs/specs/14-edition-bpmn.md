# Construction/édition de diagrammes BPMN dans FlowForge

> **Statut : implémenté (2026-08-24), TODO §39 clos.** Ce document
> capture l'orientation technique retenue et l'implémentation livrée pour
> couvrir TODO §39. Cycle `dev-lifecycle` complet (Phase 2 requirements,
> Phase 3 design, Phase 4 planning, Phase 5 implémentation — voir
> « Implémentation livrée » ci-dessous). Voir les documents de travail
> détaillés :
> [docs/ai/requirements/2026-08-24-feature-edition-bpmn.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-24-feature-edition-bpmn.md),
> [docs/ai/design/2026-08-24-feature-edition-bpmn.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-08-24-feature-edition-bpmn.md),
> [docs/ai/planning/2026-08-24-feature-edition-bpmn.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-08-24-feature-edition-bpmn.md) et
> [docs/ai/implementation/2026-08-24-feature-edition-bpmn.md](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-08-24-feature-edition-bpmn.md).

## Constat

[05-workflow-engine.md](05-workflow-engine.md) et
[10-visualisation-workflow.md](10-visualisation-workflow.md) documentent
l'existant : un moteur d'exécution Flowable (sans app Flowable UI) et un
viewer BPMN lecture seule (`BpmnViewer.vue` / `BpmnCanvas.vue`, `bpmn-js`
`NavigatedViewer`). La feature `deploiement-bpmn` (TODO §35, PR #67) a
ajouté l'upload + prévisualisation d'un `.bpmn20.xml` **déjà modélisé en
externe**, avec pour non-goal explicite « pas d'édition/design de
diagramme dans FlowForge ».

Le besoin de **construire ou modifier** un diagramme BPMN depuis
FlowForge reste donc un écart non couvert — distinct du volet Cockpit
(`DEFERRED-FEATURES.md` §1.5, hors scope produit, non remis en cause ici)
et distinct du mécanisme de déploiement (§35, réutilisable tel quel : ce
qui manque est la *saisie* du diagramme, pas sa validation/déploiement).

## Options comparées

| Option | Retenue | Raison |
|---|---|---|
| (a) `bpmn-js` Modeler seul | Non | Pas d'édition des attributs `flowable:*` utilisés par les process réels du dépôt |
| **(b) `bpmn-js` Modeler + `bpmn-js-properties-panel` + provider Flowable custom** | **Oui** | Couvre l'édition BPMN standard **et** les extensions Flowable réellement utilisées ; zéro app tierce ; licences MIT |
| (c) `diagram-js` bas niveau, éditeur maison | Non | Réinvente ce que `bpmn-js` fait déjà ; effort disproportionné |
| (d) Flowable Modeler (webapp séparée) | Non — dernier recours écarté | App tierce à opérer/sécuriser par tenant, contradictoire avec l'exclusion déjà actée des apps Flowable UI (§35, `DEFERRED-FEATURES.md` §1.5) |

## Approche retenue

`frontend/package.json` déclare déjà `bpmn-js` (`^18.24.0`), utilisé
aujourd'hui via `bpmn-js/lib/NavigatedViewer` (lecture seule). Le même
package expose un bundle `Modeler` (`bpmn-js/lib/Modeler`) — palette
drag-and-drop, édition des connexions/labels, `saveXML()`/`saveSVG()`.
C'est le moteur consommé par bpmn.io et par Camunda Modeler, mais utilisé
ici comme simple librairie front, pas comme application à opérer.

`bpmn-js-properties-panel` (même éditeur bpmn.io, MIT) complète l'édition
avec :

- le panel standard (nom, documentation, propriétés BPMN génériques des
  éléments) ;
- un **provider custom** enregistré à côté du provider standard, pour
  exposer les attributs d'extension `flowable:*` réellement présents dans
  les diagrammes du dépôt (`src/main/resources/processes/*.bpmn20.xml`,
  `src/test/resources/processes/*.bpmn20.xml`) — assignee, `formKey`,
  expressions, listeners selon l'inventaire à faire en Phase 2/3. Pas de
  provider générique exposant tout le schéma `flowable:*` sans besoin
  identifié.

Le XML produit (`modeler.saveXML()`) est destiné à être injecté dans le
flux existant de `deploiement-bpmn` — prévisualisation
(`BpmnCanvas.vue`), confirmation explicite, puis `POST` vers
`WorkflowBpmnDeploymentController` (`DeployBpmnProcessUseCase`), **sans
changement du mécanisme de validation/déploiement backend**.

Aucune nouvelle dépendance hors écosystème bpmn.io : `bpmn-js` est déjà
présent ; `bpmn-js-properties-panel` est un ajout, du même éditeur, même
licence (MIT), même modèle de consommation (librairie front, pas de
service à déployer).

## Portée retenue (Phase 2, 2026-08-24)

Sept décisions actées via `AskUserQuestion` (une à la fois, cf. doc
requirements pour le détail) :

- **D-EDIT-PLACEMENT** — nouvel écran dédié (pas une section de
  `/admin/workflow-types` : `BpmnCanvas.vue` a une hauteur fixe 360px,
  pensée pour une preview d'upload, pas pour un éditeur).
- **D-EDIT-ENTRYPOINT** — 3 points d'entrée en V1 : création vierge,
  import fichier local, édition d'un diagramme déjà déployé (`GET
  .../info`).
- **D-EDIT-SCOPE** — provider custom limité aux attributs `flowable:*`
  réellement utilisés dans le dépôt : `async`, `delegateExpression`,
  `expression` (`serviceTask`), `scope` (`signal`). Pas
  d'`assignee`/`candidateGroups`/`formKey` — l'assignation de tâche
  humaine reste gérée au runtime (`TaskService.setAssignee`), pas
  déclarée en BPMN.
- **D-EDIT-SAVE** — brouillon **persisté côté backend** (écart par
  rapport à la recommandation initiale d'export fichier local). Élargit
  le scope à un nouveau composant backend (domaine, port, adaptateur,
  migration, endpoint).
- **D-EDIT-DRAFT-MODEL** — un brouillon par clé de process
  (`processDefinitionKey`), écrasé à chaque sauvegarde, pas d'historique.
- **D-EDIT-DEPLOY-FLOW** — bouton « Déployer » intégré à l'éditeur ; la
  logique de confirmation/redéploiement de `BpmnUploadPreview.vue` est
  extraite et partagée (pas dupliquée) plutôt que ré-écrite.
- **D-EDIT-UNSAVED** — sauvegarde explicite du brouillon + confirmation
  navigateur (`beforeunload`) si des modifications ne sont pas
  enregistrées ; pas d'autosave périodique.

**Non-goals actés :**

- Pas de Flowable Modeler ni de Cockpit (cohérent avec §35 et
  `DEFERRED-FEATURES.md` §1.5).
- Pas de simulation/exécution de process depuis l'éditeur.
- Pas d'**historique** de versions de brouillons (un slot unique par clé
  de process) — le seul vrai versionnement reste celui de Flowable au
  déploiement (§35).
- Pas de collaboration temps réel multi-utilisateur (pas de verrouillage
  optimiste ; dernier « Enregistrer » gagne).
- Pas d'autosave périodique.

## Architecture retenue (Phase 3, 2026-08-24)

Trois décisions techniques actées via `AskUserQuestion` (détail : doc
design) :

- **D-DESIGN-DRAFT-KEY** — clé composite `(tenant_id,
  process_definition_key)` via `@IdClass` sur `BpmnDraftJpaEntity`, même
  pattern que `WorkflowIdempotencyJpaEntity`/`WebhookIdempotencyJpaEntity`
  (déjà utilisé 2x dans le dépôt) — pas d'UUID généré + contrainte
  unique.
- **D-DESIGN-ROUTE** — une seule route SPA `/admin/bpmn-editor` avec
  `processDefinitionKey` en query param optionnel (pas de routes
  séparées création/édition).
- **D-DESIGN-DRAFT-LIFECYCLE** — le brouillon est supprimé
  automatiquement après un déploiement réussi de sa clé (évite une
  divergence silencieuse entre un brouillon oublié et ce qui est
  réellement déployé).

**Gap réel trouvé en exploration** (pas supposé) : aucun mécanisme
existant ne renvoyait le XML brut d'une définition de process déployée
par clé (`GET .../info`, §35, ne renvoie que des métadonnées). Nouvelle
méthode `WorkflowDeploymentPort.findDeployedXml` (implémentée via
`RepositoryService.getProcessModel`), nécessaire pour le 3ᵉ point
d'entrée de l'éditeur (édition d'un diagramme déjà déployé).

**Composants principaux** (détail complet, mermaid, DTOs : doc design) :
`BpmnEditorView.vue` + `BpmnEditorCanvas.vue` (Modeler + properties
panel) côté frontend ; `BpmnDraftController` +
`SaveBpmnDraftUseCase`/`GetBpmnDraftUseCase` +
`BpmnDraftRepository`/`BpmnDraftJpaEntity` (nouveau, architecture
hexagonale respectée) côté backend ; `DeployBpmnProcessUseCaseImpl`
modifié pour supprimer le brouillon après déploiement réussi.

## Plan d'implémentation (Phase 4, 2026-08-24)

8 milestones séquencés par dépendance (détail complet : doc planning) :
M1 domaine backend brouillon → M2 persistence (migration
`013-bpmn-draft-tables.yaml`) → M3 `findDeployedXml` / M4 endpoint
brouillon + suppression post-déploiement (parallélisables) → M5 canvas
éditeur (`bpmn-js` Modeler + `bpmn-js-properties-panel`, nouvelle
dépendance frontend) → M6 composables partagés (`useBpmnDraft`,
`useBpmnDeployment`) → M7 écran dédié + intégration admin → M8
vérification globale (4 nouveaux scénarios E2E). Couverture
testing→planning vérifiée exhaustive, aucun scénario orphelin.

## Implémentation livrée (Phase 5, 2026-08-24)

M1-M4 (backend) et M5-M7 (frontend, hors E2E) implémentés **en
parallèle** par deux agents dans des worktrees git isolés (contrat API
déjà figé par le design), fusionnés puis M8 (vérification globale +
E2E) exécuté par l'orchestrateur sur l'arbre fusionné.

- **Backend** : `BpmnDraft`/`BpmnDraftRepository`/`SaveBpmnDraftUseCase`/
  `GetBpmnDraftUseCase` (domaine pur), `BpmnDraftJpaEntity` (clé
  composite `@IdClass`), `BpmnDraftController`
  (`GET`/`PUT /api/workflow-bpmn-drafts/{key}`),
  `WorkflowDeploymentPort.findDeployedXml` +
  `GET /api/workflow-bpmn-deployments/{key}/xml`,
  `DeployBpmnProcessUseCaseImpl` modifié (supprime le brouillon après
  déploiement réussi).
- **Frontend** : `BpmnEditorCanvas.vue` (`bpmn-js` Modeler +
  `bpmn-js-properties-panel` + `flowablePropertiesProvider.ts` custom),
  composables `useBpmnDraft.ts`/`useBpmnDeployment.ts` (ce dernier
  extrait de `BpmnUploadPreview.vue`, contrat public inchangé),
  `BpmnEditorView.vue` (route `/admin/bpmn-editor`), lien « Éditer »
  dans `WorkflowConfigsAdminView.vue`.
- **2 bugs réels trouvés et corrigés** (voir le doc implementation pour
  le détail) : `modeler.get('commandStack').on(...)` — `CommandStack`
  de diagram-js n'a pas de `.on()` propre (événements via l'`eventBus`),
  masqué par des tests Vitest mockant entièrement `bpmn-js`, trouvé en
  écrivant l'E2E (vrai navigateur) ; race condition de montage
  `BpmnEditorCanvas`/`useBpmnDraft.load()`, trouvée et corrigée en TDD.
- **Validation** : `./mvnw verify` — 1106 tests, 0 échec, LINE
  93.86%/BRANCH 77.14%. Frontend — 193 tests Vitest, typecheck/lint
  propres, 47/47 Playwright (4 nouveaux `E2E-EDIT-BPMN-1..4`).
- `README.md`, `05-workflow-engine.md` (ce même dossier) et
  `GUIDE-UTILISATEUR.md` corrigés — retrait des mentions « pas de
  designer visuel/éditeur BPMN » devenues fausses, nouveau mécanisme
  documenté.

## Évolution — briques métier (TODO §73, LC1)

Le provider Flowable volontairement restreint (D-EDIT-SCOPE) est complété, sans le remplacer, par une
**palette métier** : groupe « Briques métier » (`blockPaletteProvider`), groupe « Brique » du panneau
inséré après « General » (`blockPropertiesProvider`), section « Avancé » (l'ancien groupe « Flowable »,
repliée pour une brique reconnue), diagnostics non bloquants et marqueur de canevas (`blockMarkers`),
`ensureFlowableNamespace` (D-FLOWABLE-NS). Le catalogue vient du serveur ; l'éditeur charge aussi
désormais les feuilles de style `bpmn-js` / `diagram-js` / panneau de propriétés, absentes jusque-là
(palette et panneau apparaissaient sans mise en forme). Détail : [`docs/ai/design/2026-09-29-feature-palette-metier-bpmn.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-09-29-feature-palette-metier-bpmn.md).

## Prochaine étape

TODO §39 clos. Prochaine étape sur demande explicite de l'utilisateur :
`dev-review` (Phase 9), puis PR. Voir `FILE-ATTENTE.md` pour le suivi.
