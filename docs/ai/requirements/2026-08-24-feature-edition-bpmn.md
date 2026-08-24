---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-24
updated: 2026-08-24
feature: edition-bpmn
todo_ref: TODO.md §39 (nouveau) · FILE-ATTENTE.md (candidat, cycle de dev pas démarré) · analysis doc/14-edition-bpmn.md
---

# Requirements & Problem Understanding — `edition-bpmn`

> **Phase 1 uniquement — statut `draft`.** Ce document capture l'analyse
> initiale et l'orientation technique retenue en discussion avec
> l'utilisateur (option (b), cf. TODO §39). Il n'a pas encore été rejoué en
> Phase 2 (revue formelle, `AskUserQuestion` sur les points ouverts
> ci-dessous) ni placé dans `FILE-ATTENTE.md`. Aucune implémentation n'a
> démarré.

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
  `activeIds`/`completedIds`/`failedIds`).
- `bpmn-js` expose, dans le **même package**, un bundle `Modeler`
  (`bpmn-js/lib/Modeler` ou `bpmn-js/dist/bpmn-modeler.production.min.js`)
  avec palette drag-and-drop, édition des connexions/labels, et
  `saveXML()`/`saveSVG()`. C'est le moteur utilisé par bpmn.io et par
  Camunda Modeler, mais consommable comme simple librairie front — pas une
  application tierce à opérer.
- L'écosystème bpmn.io fournit `bpmn-js-properties-panel` (MIT, même
  éditeur, maintenu en parallèle de `bpmn-js`) pour l'édition des
  attributs BPMN standard (nom, doc, exécutable...) et permet d'enregistrer
  des **providers custom** pour des attributs d'extension non standard.
- Les fichiers `.bpmn20.xml` déjà présents dans le dépôt
  (`src/main/resources/processes/*.bpmn20.xml`,
  `src/test/resources/processes/*.bpmn20.xml`) utilisent des extensions
  `flowable:*` (ex. expressions, listeners, `formKey` selon les process) —
  la palette générique de `bpmn-js-properties-panel` ne les expose pas
  nativement, d'où le besoin d'un provider custom pour cette partie.
- `WorkflowBpmnDeploymentController` (`GET .../info`, `POST` multipart,
  rôles `TENANT_ADMIN`/`WORKFLOW_ADMIN`) valide et déploie déjà un
  `.bpmn20.xml` — potentiellement réutilisable tel quel comme cible
  « Déployer » depuis un futur écran d'édition, sans changement backend.
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

- Écran de construction/édition de diagramme BPMN dans FlowForge, sans
  dépendance à une application connexe (bpmn.io `Modeler` + panel de
  propriétés, embarqués dans le SPA existant).
- Édition des attributs BPMN standard (nom du process, du diagramme,
  documentation, propriétés des éléments) via `bpmn-js-properties-panel`.
- Édition des extensions `flowable:*` effectivement utilisées par les
  diagrammes du dépôt, via un provider custom du panel de propriétés
  (portée exacte à arrêter en Phase 2/3 après inventaire des attributs
  réellement présents dans `src/main/resources/processes/**` et
  `src/test/resources/processes/**`).
- Export du diagramme édité vers le mécanisme de déploiement existant
  (`WorkflowBpmnDeploymentController`), en réutilisant si possible le flux
  de prévisualisation/confirmation de `deploiement-bpmn` plutôt qu'un
  nouveau chemin de déploiement.

### Non-goals (proposés — à confirmer en Phase 2)

- Pas de Flowable Modeler ni de Cockpit (cohérent avec §35 et
  `DEFERRED-FEATURES.md` §1.5).
- Pas de simulation/exécution de process depuis l'éditeur (hors scope
  design/BPMN, relève du moteur d'exécution).
- Pas de gestion de versions/branches de diagramme dans FlowForge au-delà
  du versionnement natif Flowable (`RepositoryService.createDeployment()`,
  déjà couvert par §35) — un « brouillon » édité reste local (fichier
  téléchargé ou stockage temporaire) tant qu'il n'est pas déployé.
- Pas de collaboration temps réel multi-utilisateur sur un même diagramme
  (édition mono-utilisateur, un onglet à la fois).

## User Stories & Use Cases (draft)

1. **En tant qu'** administrateur workflow, **je veux** créer un nouveau
   diagramme BPMN depuis une page blanche dans FlowForge, **afin de** ne
   plus dépendre d'un outil externe pour la modélisation initiale.
2. **En tant qu'** administrateur workflow, **je veux** ouvrir un
   `.bpmn20.xml` existant (déjà déployé ou téléversé) dans l'éditeur pour
   le modifier, **afin de** faire évoluer un process sans repartir de
   zéro en externe.
3. **En tant qu'** administrateur workflow, **je veux** renseigner les
   attributs `flowable:*` d'une tâche (assignee, `formKey`, expressions...)
   directement dans le panel de propriétés, **afin de** ne pas devoir
   éditer le XML à la main pour les besoins Flowable courants.
4. **En tant qu'** administrateur workflow, **je veux** enchaîner
   édition → prévisualisation → déploiement dans le même écran, **afin de**
   garder le contrôle avant tout impact sur le moteur Flowable (cohérent
   avec l'exigence de prévisualisation obligatoire actée en §35).

### Edge cases (draft)

- Diagramme importé contenant des extensions Flowable non couvertes par le
  provider custom (attribut plus rare que l'inventaire initial) →
  comportement à définir : passage à travers non éditable (préserver la
  valeur sans l'exposer en UI) plutôt que perte silencieuse de données.
- Diagramme importé structurellement invalide ou avec un `namespace`
  Flowable inattendu → réutiliser la même validation que §35 côté
  déploiement ; côté édition, `bpmn-js` peut échouer l'`importXML` —
  message d'erreur exploitable à spécifier.
- Perte de travail non enregistré en cas de fermeture d'onglet /
  navigation → à trancher (confirmation navigateur, autosave local,
  aucune protection en V1).
- Élément du diagramme sans correspondance dans la palette Flowable
  (ex. élément BPMN générique non supporté par le moteur d'exécution
  Flowable Community) → à clarifier si l'éditeur doit le permettre ou le
  bloquer.

## Success Criteria (draft)

- [ ] Écran d'édition BPMN accessible depuis l'UI FlowForge (emplacement
      exact à trancher en Phase 2/3 — probablement `/admin/workflow-types`,
      par cohérence avec §35), gardé par les rôles
      `TENANT_ADMIN`/`WORKFLOW_ADMIN` déjà en place.
- [ ] Création d'un diagramme vierge et édition d'un diagramme importé
      (upload ou déjà déployé) fonctionnelles via `bpmn-js` Modeler.
- [ ] Panel de propriétés standard + provider custom Flowable permettant
      d'éditer au moins les attributs `flowable:*` déjà présents dans les
      fichiers `.bpmn20.xml` du dépôt (liste exacte à arrêter en Phase 2/3).
- [ ] Le XML produit par l'éditeur est accepté sans changement par
      `WorkflowBpmnDeploymentController` (validation + déploiement
      existants, aucune régression sur §35).
- [ ] Aucune nouvelle application tierce introduite (pas de Flowable
      Modeler/Cockpit).

## Constraints & Assumptions

- Aucune app Flowable UI tierce ajoutée (cohérent avec §35 et
  `DEFERRED-FEATURES.md` §1.5).
- L'écran est un outil **admin**, pas un flux métier utilisateur — mêmes
  rôles que §35.
- Le mécanisme de déploiement (`WorkflowBpmnDeploymentController`,
  `DeployBpmnProcessUseCase`) n'est pas remplacé ni dupliqué ; l'éditeur
  produit un XML consommé par ce mécanisme existant.
- `bpmn-js` et `bpmn-js-properties-panel` sont MIT — pas de contrainte de
  licence à valider avant intégration (à confirmer explicitement en Phase
  2 en lisant les `LICENSE` des packages exacts avant ajout au
  `package.json`).
- Le SPA reste servi même origine depuis `static/` (décision n° 27) — pas
  d'app front séparée pour l'éditeur.

## Questions & Open Items

### Ouvertes — à trancher en Phase 2 (`AskUserQuestion`, une à la fois)

- **D-EDIT-SCOPE** : périmètre exact des attributs `flowable:*` à exposer
  dans le provider custom du panel de propriétés (inventaire à établir sur
  `src/main/resources/processes/**` et `src/test/resources/processes/**`
  avant de trancher — éviter un provider générique qui expose tout le
  schéma `flowable:*` sans besoin identifié).
- **D-EDIT-PLACEMENT** : nouvel écran dédié vs nouvelle section dans
  `/admin/workflow-types` (même question tranchée pour l'upload en §35,
  probablement le même choix par cohérence, à confirmer).
- **D-EDIT-ENTRYPOINT** : création vierge, édition d'un diagramme
  téléversé, édition d'un diagramme déjà déployé (relecture via
  l'endpoint `GET .../info` existant) — un seul point d'entrée ou
  plusieurs, et lesquels en V1 ?
- **D-EDIT-SAVE** : que fait « enregistrer » avant déploiement — export
  fichier local uniquement, ou persistance d'un brouillon côté backend
  (nouvelle notion, hors scope actuel) ? Impact direct sur le non-goal
  « pas de gestion de versions de diagramme » proposé ci-dessus.
- **D-EDIT-DEPLOY-FLOW** : réutilisation directe de
  `BpmnUploadPreview.vue`/`BpmnCanvas.vue` comme étape finale (édition →
  prévisualisation → confirmation → `POST` déploiement existant) ou
  nouveau composant dédié ?
- Gestion de la perte de travail non enregistré (cf. edge case) : accepté
  comme limite V1, ou protection minimale requise ?

### Hors file

Flowable Modeler, Flowable Cockpit, simulation/exécution de process depuis
l'éditeur, collaboration temps réel multi-utilisateur, gestion de versions
de diagramme au-delà du versionnement natif Flowable.

## Prochaine étape

Phase 2 (revue requirements) : lever les points ouverts ci-dessus avec
l'utilisateur via `AskUserQuestion` (un point à la fois), inventorier
précisément les attributs `flowable:*` utilisés dans le dépôt pour cadrer
**D-EDIT-SCOPE**, puis qualifier la feature dans `FILE-ATTENTE.md` avant
tout passage en Phase 3 (design) ou implémentation.
