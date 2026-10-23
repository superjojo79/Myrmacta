---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-24
updated: 2026-08-24
feature: edition-bpmn
phase1: 2026-08-24
phase2: 2026-08-24
todo_ref: TODO.md §39 · FILE-ATTENTE.md (candidat, prochaine phase = dev-design) · docs/specs/14-edition-bpmn.md
---

# Requirements & Problem Understanding — `edition-bpmn`

> Requirements **reviewed** (Phase 2), 7 décisions actées via
> `AskUserQuestion` (une à la fois), aucun point ouvert bloquant. Cycle
> `dev-lifecycle` lancé à la demande explicite de l'utilisateur ; mode
> **no-worktree** (session CCR verrouillée sur la branche unique
> `claude/awesome-dirac-h6zc7u`, une seule feature active dans ce
> conteneur — l'isolation par worktree n'apportait rien ici).

## Problem Statement

`deploiement-bpmn` (TODO §35, PR #67) a livré un écran d'upload +
prévisualisation (`BpmnUploadPreview.vue` / `BpmnCanvas.vue`) pour un
fichier `.bpmn20.xml` **déjà modélisé en externe**. C'était un non-goal
explicite de cette feature : « pas d'édition/design de diagramme dans
FlowForge ». Le besoin de pouvoir **construire ou modifier** un diagramme
BPMN depuis FlowForge reste donc un écart non couvert — distinct du volet
Cockpit (`DEFERRED-FEATURES.md` §1.5, hors scope produit, non remis en
cause ici) et distinct du mécanisme de déploiement (§35, qui reste
réutilisable tel quel : c'est l'entrée du diagramme qui manque, pas sa
validation/déploiement).

**Vérifié par lecture de code (2026-08-24) :**

- `frontend/package.json` déclare déjà `bpmn-js` `^18.24.0`. Le viewer
  (`BpmnViewer.vue` → `BpmnCanvas.vue`) importe dynamiquement
  `bpmn-js/lib/NavigatedViewer` (lecture seule + marqueurs d'exécution
  `activeIds`/`completedIds`/`failedIds`), avec un conteneur de hauteur
  **fixe 360px** — dimensionné pour une prévisualisation d'upload, pas
  pour un outil de modélisation.
- `bpmn-js` expose, dans le **même package**, un bundle `Modeler`
  (`bpmn-js/lib/Modeler`) avec palette drag-and-drop, édition des
  connexions/labels, et `saveXML()`/`saveSVG()`. C'est le moteur utilisé
  par bpmn.io et par Camunda Modeler, mais consommable comme simple
  librairie front — pas une application tierce à opérer.
- L'écosystème bpmn.io fournit `bpmn-js-properties-panel` (MIT, même
  éditeur, maintenu en parallèle de `bpmn-js`) pour l'édition des
  attributs BPMN standard et permet d'enregistrer des **providers
  custom** pour des attributs d'extension non standard.
- **Inventaire réel des extensions `flowable:*`** dans
  `src/main/resources/processes/*.bpmn20.xml` et
  `src/test/resources/processes/*.bpmn20.xml` (`grep` exhaustif,
  2026-08-24) : seuls `flowable:async` (bool, `serviceTask`),
  `flowable:delegateExpression` (`serviceTask`), `flowable:expression`
  (`serviceTask`) et `flowable:scope` (`signal`) sont réellement utilisés.
  Aucun `flowable:assignee`/`candidateGroups`/`formKey` en BPMN —
  `FlowableWorkflowVisualizationAdapter.reassignTask` confirme que
  l'assignation de tâche humaine se fait **au runtime** via
  `TaskService.setAssignee`, pas déclarée dans le diagramme.
- `WorkflowBpmnDeploymentController` (`GET .../info`, `POST` multipart,
  rôles `TENANT_ADMIN`/`WORKFLOW_ADMIN`) valide et déploie déjà un
  `.bpmn20.xml` — réutilisé tel quel comme cible « Déployer » depuis
  l'éditeur, sans changement du mécanisme de validation/déploiement.
- `WorkflowTypeConfig.workflowKey` reste le point de couplage entre
  configuration workflow et `processDefinitionKey` du diagramme (inchangé
  par cette feature).

## Options analysées

Quatre options ont été comparées avec l'utilisateur (détail : TODO §39) ;
seule l'option retenue est développée ici.

| Option | Retenue | Raison |
|---|---|---|
| (a) `bpmn-js` Modeler seul | Non | Pas d'édition des attributs `flowable:*` utilisés par les process réels du dépôt |
| **(b) `bpmn-js` Modeler + `bpmn-js-properties-panel` + provider Flowable custom** | **Oui** | Couvre l'édition standard **et** les extensions Flowable réellement utilisées ; zéro app tierce ; licences MIT |
| (c) `diagram-js` bas niveau, éditeur maison | Non | Réinvente ce que `bpmn-js` fait déjà ; effort disproportionné |
| (d) Flowable Modeler (webapp séparée) | Non (dernier recours écarté) | App tierce à opérer/sécuriser par tenant, contradictoire avec l'exclusion déjà actée des apps Flowable UI (§35, `DEFERRED-FEATURES.md` §1.5) |

**Aucune nouvelle dépendance runtime hors écosystème bpmn.io** : `bpmn-js`
est déjà présent ; `bpmn-js-properties-panel` est un ajout, mais du même
éditeur, même licence (MIT), même modèle de consommation (librairie
front, pas de service à déployer).

## Goals & Objectives

### Primary goals

- **Nouvel écran dédié** de construction/édition de diagramme BPMN dans
  FlowForge (D-EDIT-PLACEMENT), sans dépendance à une application connexe
  (bpmn.io `Modeler` + panel de propriétés, embarqués dans le SPA
  existant).
- **Trois points d'entrée** (D-EDIT-ENTRYPOINT) : création d'un diagramme
  vierge, import d'un fichier `.bpmn20.xml` local, édition d'un diagramme
  déjà déployé (relecture via `GET .../info`).
- Édition des attributs BPMN standard via `bpmn-js-properties-panel`, et
  des extensions `flowable:*` réellement utilisées (`async`,
  `delegateExpression`, `expression`, `scope` — D-EDIT-SCOPE) via un
  provider custom.
- **Brouillon persisté côté backend** (D-EDIT-SAVE) : un brouillon par
  clé de process (`processDefinitionKey`), écrasé à chaque sauvegarde,
  sans historique de versions (D-EDIT-DRAFT-MODEL) — nouveau composant
  backend (domaine + port + adaptateur + endpoint), à détailler en
  Phase 3.
- **Déploiement intégré** (D-EDIT-DEPLOY-FLOW) : bouton « Déployer »
  dans l'éditeur, réutilisant la logique de confirmation/redéploiement
  déjà écrite dans `BpmnUploadPreview.vue` (extraite en logique
  partagée, pas dupliquée) avant l'appel `POST` vers
  `WorkflowBpmnDeploymentController`.
- **Protection contre la perte de travail** (D-EDIT-UNSAVED) :
  sauvegarde explicite du brouillon + confirmation navigateur
  (`beforeunload`) si des modifications non enregistrées existent.

### Non-goals

- Pas de Flowable Modeler ni de Cockpit (cohérent avec §35 et
  `DEFERRED-FEATURES.md` §1.5).
- Pas de simulation/exécution de process depuis l'éditeur (hors scope
  design/BPMN, relève du moteur d'exécution).
- Pas d'**historique de versions de brouillons** : le brouillon persisté
  (D-EDIT-SAVE/D-EDIT-DRAFT-MODEL) est un slot unique par clé de process,
  écrasé à chaque sauvegarde — ce n'est pas un système de versioning. Le
  seul versionnement réel reste celui de Flowable au déploiement
  (`RepositoryService.createDeployment()`, déjà couvert par §35).
- Pas de collaboration temps réel multi-utilisateur sur un même diagramme
  (édition mono-utilisateur, un onglet à la fois — un second admin
  éditant la même clé de process écrase le brouillon du premier au
  prochain « Enregistrer », comportement accepté en V1, pas de
  verrouillage optimiste).
- Pas d'autosave périodique (D-EDIT-UNSAVED) — sauvegarde explicite
  uniquement.

## User Stories & Use Cases

1. **En tant qu'** administrateur workflow, **je veux** créer un nouveau
   diagramme BPMN depuis une page blanche dans FlowForge, **afin de** ne
   plus dépendre d'un outil externe pour la modélisation initiale.
2. **En tant qu'** administrateur workflow, **je veux** ouvrir un
   `.bpmn20.xml` existant (déjà déployé ou téléversé) dans l'éditeur pour
   le modifier, **afin de** faire évoluer un process sans repartir de
   zéro en externe.
3. **En tant qu'** administrateur workflow, **je veux** renseigner les
   attributs `flowable:*` d'une tâche de service (`async`,
   `delegateExpression`, `expression`) directement dans le panel de
   propriétés, **afin de** ne pas devoir éditer le XML à la main.
4. **En tant qu'** administrateur workflow, **je veux** enregistrer un
   brouillon en cours d'édition et le reprendre plus tard (même poste ou
   un autre), **afin de** ne pas devoir finir une modélisation en une
   seule session.
5. **En tant qu'** administrateur workflow, **je veux** enchaîner
   édition → sauvegarde de brouillon → prévisualisation → déploiement
   dans le même écran, **afin de** garder le contrôle avant tout impact
   sur le moteur Flowable (cohérent avec l'exigence de prévisualisation
   obligatoire actée en §35).

### Edge cases

- Diagramme importé contenant des extensions Flowable non couvertes par
  le provider custom (attribut plus rare que l'inventaire D-EDIT-SCOPE) →
  préservées par le moddle bpmn-js sans être exposées en UI (pas de perte
  silencieuse) ; à vérifier explicitement en Phase 3 (comportement du
  moddle sur un attribut d'extension inconnu du schéma déclaré).
- Diagramme importé structurellement invalide ou avec un `namespace`
  Flowable inattendu → réutiliser la même validation que §35 côté
  déploiement ; côté édition, `bpmn-js` peut échouer l'`importXML` —
  message d'erreur exploitable à spécifier en Phase 3.
- Élément du diagramme sans correspondance dans le sous-ensemble BPMN
  supporté par Flowable Community (le palette `bpmn-js` reste générique,
  pas filtrée par moteur) → pas de restriction de palette à la
  modélisation ; l'échec est détecté au déploiement par la validation
  existante de `WorkflowBpmnDeploymentController` (§35), avec le message
  d'erreur déjà en place. Pas de nouvelle validation de palette en V1.
- Nouveau diagramme sans `processDefinitionKey` encore défini (id BPMN
  par défaut du Modeler, ex. `Process_1`) → le brouillon est clé par
  l'id BPMN courant au moment de la sauvegarde ; l'utilisateur renomme
  l'id via le panel de propriétés standard (champ déjà géré par
  `bpmn-js-properties-panel`, pas de développement dédié) avant de
  déployer — le rejet strict d'une clé sans `WorkflowTypeConfig`
  correspondante (§35) s'applique inchangé au déploiement.
- Deux administrateurs éditent la même clé de process en parallèle → pas
  de verrouillage en V1 (cf. non-goals) ; le dernier « Enregistrer »
  écrase le brouillon existant.

## Success Criteria

- [ ] Nouvel écran dédié d'édition BPMN dans l'UI FlowForge, gardé par
      les rôles `TENANT_ADMIN`/`WORKFLOW_ADMIN` déjà en place (emplacement
      exact — route, lien depuis `/admin/workflow-types` — à préciser en
      Phase 3).
- [ ] Les 3 points d'entrée fonctionnels : création vierge, import
      fichier local, édition d'un diagramme déjà déployé (`GET .../info`).
- [ ] Panel de propriétés standard + provider custom exposant `async`,
      `delegateExpression`, `expression` (`serviceTask`) et `scope`
      (`signal`).
- [ ] Sauvegarde d'un brouillon persisté côté backend (un par
      `processDefinitionKey`, écrasé à chaque sauvegarde), repris à la
      réouverture de l'éditeur sur cette même clé.
- [ ] Bouton « Déployer » réutilisant la logique de
      confirmation/redéploiement de `BpmnUploadPreview.vue` (extraite,
      pas dupliquée) et le `POST` existant vers
      `WorkflowBpmnDeploymentController` — aucune régression sur §35.
- [ ] Confirmation navigateur (`beforeunload`) si des modifications non
      enregistrées existent en quittant l'éditeur.
- [ ] Aucune nouvelle application tierce introduite (pas de Flowable
      Modeler/Cockpit).

## Constraints & Assumptions

**Contraintes stables :**

- Aucune app Flowable UI tierce ajoutée (cohérent avec §35 et
  `DEFERRED-FEATURES.md` §1.5).
- L'écran est un outil **admin**, pas un flux métier utilisateur — mêmes
  rôles que §35.
- Le mécanisme de déploiement (`WorkflowBpmnDeploymentController`,
  `DeployBpmnProcessUseCase`) n'est pas remplacé ni dupliqué ; l'éditeur
  produit un XML consommé par ce mécanisme existant.
- `bpmn-js` et `bpmn-js-properties-panel` sont MIT — à confirmer
  explicitement en Phase 3 en lisant les `LICENSE` des packages exacts
  avant ajout au `package.json` (pas de raison de doute, mais pas encore
  vérifié fichier par fichier).
- Le SPA reste servi même origine depuis `static/` (décision n° 27) — pas
  d'app front séparée pour l'éditeur.
- Nouveau composant **backend** requis pour le brouillon persisté
  (D-EDIT-SAVE) : domaine pur (`domain.workflow` ou nouveau sous-package),
  port in/out, adaptateur JPA, migration Liquibase, endpoint REST gardé
  par les mêmes rôles — respecte l'architecture hexagonale et le
  cloisonnement multi-tenant par schéma déjà en place (aucune dérogation
  demandée par cette feature).

## Questions & Open Items

### Clos (Phase 2, 2026-08-24 — `AskUserQuestion`, une décision à la fois)

| Id | Décision | Choix |
|---|---|---|
| **D-EDIT-SCOPE** | Attributs `flowable:*` édités par le provider custom | Uniquement ceux réellement utilisés : `async`, `delegateExpression`, `expression` (`serviceTask`), `scope` (`signal`) — pas d'`assignee`/`candidateGroups`/`formKey` (gérés au runtime) |
| **D-EDIT-PLACEMENT** | Emplacement de l'écran | Nouvel écran dédié (pas une section de `/admin/workflow-types` — `BpmnCanvas.vue` actuel a une hauteur fixe 360px, pensée pour une preview, pas pour un éditeur) |
| **D-EDIT-ENTRYPOINT** | Points d'entrée V1 | Les 3 : création vierge, import fichier, édition d'un diagramme déjà déployé |
| **D-EDIT-SAVE** | Portée de « enregistrer » | Brouillon persisté côté backend (pas seulement export fichier local) |
| **D-EDIT-DRAFT-MODEL** | Modèle de données du brouillon | Un brouillon par clé de process, écrasé à chaque sauvegarde, pas d'historique |
| **D-EDIT-DEPLOY-FLOW** | Enchaînement édition → déploiement | Bouton déployer intégré ; logique de confirmation/déploiement de `BpmnUploadPreview.vue` extraite et partagée (pas dupliquée) |
| **D-EDIT-UNSAVED** | Protection perte de travail | Sauvegarde explicite + confirmation navigateur (`beforeunload`) ; pas d'autosave |

### Ouvertes

Aucune décision produit ouverte. Points techniques à trancher en design
(mécanisme, pas périmètre) :

- Nom exact de la route/écran dédié et libellé du lien d'accès depuis
  `/admin/workflow-types`.
- Forme exacte du nouveau composant backend brouillon (nom de
  l'entité/table, endpoint(s) REST, DTO) — respect strict de
  l'architecture hexagonale (`domain` pur, ports/adaptateurs) et de
  l'isolation multi-tenant par schéma.
- Mécanisme précis d'extraction de la logique de confirmation/déploiement
  partagée entre `BpmnUploadPreview.vue` et le nouvel écran (composant
  vs composable Vue).
- Comportement exact du moddle `bpmn-js` sur un attribut `flowable:*`
  non couvert par le provider custom (à vérifier empiriquement, pas
  supposé).

### Hors file

Flowable Modeler, Flowable Cockpit, simulation/exécution de process depuis
l'éditeur, collaboration temps réel multi-utilisateur, historique de
versions de brouillons, autosave périodique.

## Phase 2 — revue close (2026-08-24)

Template README : OK. **Validé :** 7 décisions produit/architecture
(D-EDIT-SCOPE, D-EDIT-PLACEMENT, D-EDIT-ENTRYPOINT, D-EDIT-SAVE,
D-EDIT-DRAFT-MODEL, D-EDIT-DEPLOY-FLOW, D-EDIT-UNSAVED) actées via
`AskUserQuestion` (une à la fois). Inventaire de code fait avant de poser
les questions (attributs `flowable:*` réellement utilisés, hauteur fixe
de `BpmnCanvas.vue`, `TaskService.setAssignee` au runtime) pour éviter de
deviner à l'aveugle. Écart notable par rapport à la recommandation
initiale : l'utilisateur a choisi la persistance backend d'un brouillon
(D-EDIT-SAVE) plutôt que l'export fichier local recommandé — élargit le
scope de cette feature à un nouveau composant backend, documenté dans les
Goals/Constraints et à détailler en Phase 3. Décisions stockées dans la
mémoire AI DevKit (`repo:flowforge`) pour réutilisation.

Status : **`reviewed`**. Prochaine phase : `dev-design` (Phase 3).
