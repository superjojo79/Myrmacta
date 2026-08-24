# Construction/édition de diagrammes BPMN dans FlowForge

> **Statut : requirements Phase 2 close — non implémenté.** Ce document
> capture l'orientation technique retenue pour couvrir TODO §39. Le cycle
> `dev-lifecycle` est lancé (Phase 2 revue le 2026-08-24, 7 décisions
> actées), mais la Phase 3 (design) n'a pas démarré ; rien de décrit ici
> n'existe encore dans le code. Voir le document de travail détaillé :
> [docs/ai/requirements/2026-08-24-feature-edition-bpmn.md](../docs/ai/requirements/2026-08-24-feature-edition-bpmn.md).

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

## Points ouverts restants (Phase 3 — design, mécanisme pas périmètre)

- Nom exact de la route/écran dédié et libellé du lien d'accès depuis
  `/admin/workflow-types`.
- Forme exacte du composant backend brouillon (entité/table, endpoint(s)
  REST, DTO) — architecture hexagonale et isolation multi-tenant par
  schéma inchangées.
- Mécanisme d'extraction de la logique de confirmation/déploiement
  partagée entre `BpmnUploadPreview.vue` et le nouvel écran (composant vs
  composable Vue).
- Comportement du moddle `bpmn-js` sur un attribut `flowable:*` non
  couvert par le provider custom (à vérifier empiriquement).

## Prochaine étape

Phase 3 (`dev-design`) : traduire les 7 décisions ci-dessus en
architecture concrète (composants Vue, ports/adaptateurs backend,
migration Liquibase) avant la planification (Phase 4). Voir TODO §39 et
`FILE-ATTENTE.md` pour le suivi.
