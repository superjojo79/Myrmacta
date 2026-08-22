---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-22
updated: 2026-08-22
feature: deploiement-bpmn
phase1: 2026-08-22
phase2: 2026-08-22
stakeholder_decisions: 2026-08-22
todo_ref: TODO.md §35 · FILE-ATTENTE.md (priorité 1, précédemment bloquée sur décision produit)
---

# Requirements & Problem Understanding — `deploiement-bpmn`

> Requirements **reviewed** (Phase 2), 3 décisions actées, aucun point
> ouvert bloquant.

## Problem Statement

TODO §35 documente un écart non assumé entre la vision décrite
(`README.md`, `05-workflow-engine.md` : « designer visuel intégré »,
« déployés sans redémarrage », « supervisés via le cockpit Flowable »)
et l'implémentation réelle : `pom.xml` ne dépend que du moteur
d'exécution Flowable (`flowable-spring-boot-starter`), aucune app
Flowable UI (Modeler/Cockpit/Task/IDM) n'est intégrée. Le déploiement
réel se fait par scan classpath au démarrage
(`TenantProcessDeployment`, `classpath*:/processes/**/*.bpmn20.xml`) —
un `.bpmn20.xml` modélisé en externe est déposé dans le dépôt backend
par l'équipe technique, sans écran d'upload ni de design dans l'UI
FlowForge. `GUIDE-UTILISATEUR.md` documente déjà correctement cette
limite ; `README.md`/`05-workflow-engine.md` ne l'ont jamais été mis à
jour après la décision (déjà actée ailleurs, `DEFERRED-FEATURES.md`
§1.5) d'exclure le Cockpit Flowable du scope produit, remplacé par le
viewer lecture seule Vue/`bpmn-js` (`BpmnViewer.vue`, déjà livré).

**Vérifié par lecture de code (2026-08-22) :**
- `TenantProcessDeployment` (`ApplicationRunner`) fait le seul
  déploiement existant : scan classpath, `RepositoryService.createDeployment()`,
  au démarrage uniquement.
- `WorkflowTypeConfig.workflowKey` doit correspondre au `process id` du
  BPMN déployé (déjà documenté dans `GUIDE-UTILISATEUR.md`) — c'est le
  point de couplage entre config workflow et diagramme déployé.
- `/admin/workflow-types` (rôles `TENANT_ADMIN`/`WORKFLOW_ADMIN`,
  `WorkflowConfigsAdminView.vue`) est l'écran admin qui gère déjà les
  `workflowKey` — pas d'écran dédié aux déploiements BPMN aujourd'hui.
- `BpmnViewer.vue` (`frontend/src/components/workflow/`) rend déjà un
  diagramme BPMN via `bpmn-js` `NavigatedViewer` + `importXML(xml)` à
  partir d'un `WorkflowDiagram`/XML brut — mécanisme directement
  réutilisable pour une prévisualisation avant import (sans les
  marqueurs d'exécution `activeIds`/`completedIds`/`failedIds`, qui
  n'ont pas de sens tant qu'aucune instance ne tourne).
- `DossierElementUploadController` établit déjà le patron d'upload
  multipart du projet (`MultipartFile`, allowlist de content-type,
  sniffing de contenu, validation avant tout effet de bord) — patron à
  suivre pour l'upload BPMN.

## Goals & Objectives

### Primary goals

- Écran d'upload BPMN dans `/admin/workflow-types` (nouvelle section,
  rôles `TENANT_ADMIN`/`WORKFLOW_ADMIN` déjà en place) : sélection d'un
  fichier `.bpmn20.xml`, **prévisualisation du diagramme avant tout
  import** (réutilisation du rendu `bpmn-js` de `BpmnViewer.vue`),
  confirmation explicite, puis déploiement.
- Endpoint backend de déploiement : validation (BPMN 2.0 bien formé,
  `processDefinitionKey` cohérent avec la config workflow existante),
  déploiement via `RepositoryService.createDeployment()`.
- Corriger `README.md` (section Proposition de valeur/Fonctionnalités
  principales) et `05-workflow-engine.md` pour retirer les mentions non
  livrées (« designer visuel intégré », « sans redémarrage », « cockpit
  Flowable ») et documenter le mécanisme réel (scan classpath au
  démarrage **+** upload admin ciblé).
- Clore TODO §35.

### Non-goals (actés)

- **Pas de Flowable Modeler ni de Cockpit** — écarté explicitement par
  la décision produit (option b), cohérent avec la décision déjà prise
  d'exclure le Cockpit (`DEFERRED-FEATURES.md` §1.5).
- **Pas d'édition/design de diagramme dans FlowForge** — l'écran
  prévisualise un fichier déjà modélisé en externe, il ne permet pas de
  le créer ou de le modifier.
- **Le scan classpath au démarrage (`TenantProcessDeployment`) n'est
  pas remplacé** — l'upload admin est un mécanisme **complémentaire**
  (déploiement à chaud, sans redémarrage), pas un remplacement du
  mécanisme de build/démarrage existant.

## User Stories & Use Cases

1. **En tant qu'** administrateur workflow (`WORKFLOW_ADMIN`/`TENANT_ADMIN`),
   **je veux** téléverser un fichier `.bpmn20.xml` et voir son diagramme
   rendu avant de le déployer, **afin de** vérifier visuellement qu'il
   s'agit bien du bon fichier avant d'impacter le moteur Flowable.
2. **En tant qu'** administrateur workflow, **je veux** que le fichier
   soit validé (BPMN 2.0 valide, clé cohérente avec la config workflow)
   avant déploiement, **afin de** ne pas casser un processus existant
   avec un fichier malformé ou incohérent.
3. **En tant que** mainteneur, **je veux** que la documentation
   (`README.md`, `05-workflow-engine.md`) reflète exactement ce qui est
   livré, **afin de** ne pas promettre des fonctionnalités absentes
   (designer intégré, hot-deploy sans redémarrage historiquement décrit
   comme automatique, cockpit).

### Edge cases

- Fichier non-XML ou XML malformé → rejeté avant tout appel Flowable.
- XML bien formé mais pas un BPMN 2.0 valide (namespace/schéma
  incorrect) → rejeté avec message clair.
- `processDefinitionKey` du fichier ne correspond à **aucun**
  `WorkflowTypeConfig.workflowKey` existant pour le tenant → à trancher
  en design (bloquant vs avertissement).
- Redéploiement d'une clé déjà déployée (nouvelle version d'un
  processus existant) → comportement de versionnement Flowable
  (nouvelle version, anciennes instances continuent sur l'ancienne) —
  mécanisme à documenter, pas à bloquer par défaut (comportement natif
  Flowable).
- Fichier trop volumineux → réutiliser le même type de garde-fou de
  taille que `AttachmentUploadProperties` (config dédiée, pas
  nécessairement la même valeur).

## Success Criteria

- [ ] Section upload BPMN visible dans `/admin/workflow-types`, gardée
      par les rôles `TENANT_ADMIN`/`WORKFLOW_ADMIN` existants.
- [ ] Sélection d'un fichier `.bpmn20.xml` déclenche un rendu du
      diagramme (réutilisation du mécanisme `bpmn-js` de `BpmnViewer.vue`)
      **avant** tout appel réseau de déploiement.
- [ ] Endpoint de déploiement rejette un fichier non-BPMN-2.0 valide
      avec un message d'erreur exploitable.
- [ ] Endpoint de déploiement rejette (ou avertit, selon décision
      design) un `processDefinitionKey` sans `WorkflowTypeConfig`
      correspondant.
- [ ] Déploiement réussi visible immédiatement (sans redémarrage) —
      un nouveau processus démarré avec ce `workflowKey` utilise la
      version déployée.
- [ ] `README.md` et `05-workflow-engine.md` corrigés (plus de mention
      de designer visuel intégré, hot-deploy implicite, ou cockpit
      Flowable comme livrés).
- [ ] TODO §35 clos.

## Constraints & Assumptions

**Contraintes stables :**

- Aucune app Flowable UI tierce (Modeler/Cockpit/Task/IDM) ajoutée —
  cohérent avec l'exclusion déjà actée du Cockpit.
- L'upload est un mécanisme **admin**, pas un flux utilisateur métier —
  gardé par les rôles admin existants, pas de nouvel écran public.
- Le scan classpath au démarrage reste le mécanisme de référence pour
  le déploiement initial/CI ; l'upload est un complément pour le
  déploiement à chaud en cours d'exploitation.

**Décisions produit (Phase 1, toutes actées 2026-08-22) :**

| Id | Sujet | Choix |
|---|---|---|
| **D-BPMN-TARGET** | Cible TODO §35 | Option (c) — écran d'upload minimal dans FlowForge, sans Flowable Modeler ni Cockpit |
| **D-BPMN-PREVIEW** | Prévisualisation avant import | Obligatoire — réutilisation du rendu `bpmn-js` de `BpmnViewer.vue` pour afficher le diagramme du fichier sélectionné avant toute confirmation de déploiement |
| **D-BPMN-PLACEMENT** | Emplacement de l'écran | Nouvelle section dans `/admin/workflow-types` existant (pas un nouvel écran dédié), réutilise les rôles `TENANT_ADMIN`/`WORKFLOW_ADMIN` déjà en place |

## Questions & Open Items

### Clos

| Id | Décision | Choix |
|---|---|---|
| **D-BPMN-TARGET** | Cible §35 | Option (c), écran d'upload minimal (2026-08-22) |
| **D-BPMN-PREVIEW** | Prévisualisation | Obligatoire, réutilise `BpmnViewer.vue` (2026-08-22) |
| **D-BPMN-PLACEMENT** | Emplacement | Section dans `/admin/workflow-types` (2026-08-22) |

### Ouvertes

Aucune décision produit ouverte. Points techniques à trancher en design
(mécanisme, pas périmètre) :
- Le rendu de prévisualisation doit-il réutiliser directement
  `BpmnViewer.vue` (en rendant ses marqueurs d'exécution optionnels) ou
  extraire un composant de rendu partagé plus léger ?
- `processDefinitionKey` sans `WorkflowTypeConfig` correspondant :
  bloquant (rejet strict) ou avertissement (déploiement autorisé quand
  même, ex. déploiement anticipé avant configuration du type) ?
- Redéploiement d'une clé existante : faut-il afficher les versions
  déjà déployées et leur nombre d'instances actives avant de confirmer,
  ou suffit-il de documenter le comportement natif Flowable
  (versionnement automatique, anciennes instances inchangées) ?
- Taille maximale de fichier et content-type(s) acceptés pour l'upload
  (nouvelle propriété de config dédiée, séparée d'`AttachmentUploadProperties`) ?

### Hors file

Flowable Modeler, Flowable Cockpit, édition/design de diagramme dans
FlowForge, remplacement du scan classpath au démarrage.

## Phase 2 — revue close (2026-08-22)

Template README : OK. **Validé :** 3 décisions produit (D-BPMN-TARGET,
D-BPMN-PREVIEW, D-BPMN-PLACEMENT) actées via `AskUserQuestion` (une à la
fois). Précédents de code identifiés avant de trancher (`BpmnViewer.vue`
réutilisable, `DossierElementUploadController` comme patron d'upload,
rôle `WORKFLOW_ADMIN` déjà existant) — évite de réinventer un mécanisme
déjà présent dans le codebase. 4 points techniques (mécanisme, pas
périmètre) explicitement différés à la Phase 3.

Status : **`reviewed`**. Prochaine phase : `dev-design` (Phase 3).
