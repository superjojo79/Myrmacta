# Construction/édition de diagrammes BPMN dans FlowForge

> **Statut : analyse — non implémenté.** Ce document capture l'orientation
> technique retenue pour couvrir TODO §39. Le cycle de développement
> (`dev-lifecycle`, Phases 2 à 9) n'a pas démarré ; rien de décrit ici
> n'existe encore dans le code. Voir aussi le document de travail Phase 1
> détaillé :
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

## Portée proposée

**Inclus (à confirmer en Phase 2) :**

- Création d'un diagramme vierge et édition d'un diagramme importé (upload
  ou déjà déployé, via l'endpoint `GET .../info` existant).
- Édition des attributs BPMN standard et des extensions `flowable:*`
  effectivement utilisées dans le dépôt.
- Réutilisation du flux prévisualisation → confirmation → déploiement de
  `deploiement-bpmn`.

**Non-goals proposés :**

- Pas de Flowable Modeler ni de Cockpit (cohérent avec §35 et
  `DEFERRED-FEATURES.md` §1.5).
- Pas de simulation/exécution de process depuis l'éditeur.
- Pas de gestion de versions/branches de diagramme dans FlowForge
  au-delà du versionnement natif Flowable
  (`RepositoryService.createDeployment()`, déjà couvert par §35).
- Pas de collaboration temps réel multi-utilisateur.

## Points ouverts (Phase 2)

Non tranchés — à lever via `AskUserQuestion`, un point à la fois, avant
tout design/implémentation :

- **D-EDIT-SCOPE** — inventaire exact des attributs `flowable:*` à
  exposer dans le provider custom.
- **D-EDIT-PLACEMENT** — nouvel écran dédié vs nouvelle section dans
  `/admin/workflow-types` (comme pour l'upload, §35).
- **D-EDIT-ENTRYPOINT** — création vierge / édition d'un import / édition
  d'un diagramme déjà déployé : un seul point d'entrée ou plusieurs en V1 ?
- **D-EDIT-SAVE** — « enregistrer » avant déploiement = export fichier
  local uniquement, ou brouillon persistant côté backend (notion
  nouvelle, hors scope actuel) ?
- **D-EDIT-DEPLOY-FLOW** — réutilisation directe de
  `BpmnUploadPreview.vue`/`BpmnCanvas.vue` en étape finale, ou nouveau
  composant dédié ?

## Prochaine étape

Phase 2 du cycle `dev-lifecycle` (revue requirements) — non démarrée à la
date de ce document. Voir TODO §39 pour le suivi de la décision et
`FILE-ATTENTE.md` pour le statut de file d'attente produit.
