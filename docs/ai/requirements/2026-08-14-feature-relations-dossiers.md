---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-14
updated: 2026-08-14
feature: relations-dossiers
stakeholder_decisions: 2026-08-14
phase2: 2026-08-14
todo_ref: DEFERRED-FEATURES.md §1.4 (Relations entre dossiers)
---

# Requirements & Problem Understanding

## Problem Statement
**What problem are we solving?**

`DossierRelation` est déjà **spécifié** dans `analysis doc/04-dossiers.md`
(record domaine + entité JPA + table `dossier_relation`) mais **jamais
implémenté** — `DEFERRED-FEATURES.md` §1.4 le liste comme « lot suivant ».
Conséquence concrète et déjà visible en prod : le panneau « dossiers liés »
de la visualisation workflow (mergée, PR #34/#38) renvoie toujours
`relations: []` en dur — `WorkflowLinkDossierView.relations` est typé
`List<Object>` et `WorkflowLinkVisualizationUseCaseImpl.getDossier()`
construit la vue avec `List.of()` (`WorkflowLinkVisualizationUseCaseImpl.java:94`).

- **Affectés :** `DOSSIER_MANAGER` / `WORKFLOW_ADMIN` (traçabilité entre
  dossiers liés — enfant, dossier de suite, doublon…), utilisateurs de la
  Supervision et de la visualisation workflow (panneau muet aujourd'hui).
- **Situation actuelle :** aucune table, aucun endpoint, aucun affichage.
  `analysis doc/04-dossiers.md` documente déjà le modèle cible (voir
  `DossierRelation`, 5 `RelationType` : `PARENT_CHILD`, `SUBSEQUENT`,
  `LINKED`, `DUPLICATE`, `MERGED_INTO`) et le cas d'usage métier « workflow
  supplémentaire sur le même dossier vs nouveau dossier lié ».
- **Réf. :** `DEFERRED-FEATURES.md` §1.4 ; `analysis doc/04-dossiers.md`
  (section *DossierRelation*) ; `WorkflowLinkDossierView.java` ;
  `WorkflowLinkVisualizationUseCaseImpl.java:87-95`.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Modéliser et persister `DossierRelation` conformément à
  `analysis doc/04-dossiers.md` (domaine pur, `dossier_relation` en base,
  même tenant obligatoire).
- **`POST /api/dossiers/{id}/relations`** — création manuelle d'une relation
  par un utilisateur autorisé : EDIT sur le type du dossier source **et**
  VIEW sur le type du dossier cible (D-CREATE-ACL, Phase 2), `type` parmi
  les 5 valeurs.
- **`GET /api/dossiers/{id}/relations`** — liste bidirectionnelle (le
  dossier peut être source ou cible), filtrée par ACL VIEW sur le dossier
  « de l'autre côté », avec libellé de sens adapté à l'affichage (ex.
  `PARENT_CHILD` vu du côté enfant = « Parent : … »).
- Peupler réellement `WorkflowLinkDossierView.relations` dans le panneau de
  visualisation workflow (remplace le `List.of()` actuel).
- Ajouter une section « Dossiers liés » en lecture dans
  `DossierDetailView.vue` (à côté d'`ElementsList`), plus une action « Lier
  un dossier » ouvrant un petit formulaire de création — recherche du
  dossier cible via `GET /api/dossiers` (feature `pagination-dossiers`,
  déjà livrée).
- **ServiceTask BPMN** `createChildDossierTask` : dans un process, crée un
  **nouveau** dossier enfant (`CreateDossierUseCase`), la relation
  correspondante (`type` restreint à `PARENT_CHILD`/`SUBSEQUENT`,
  D-SERVICETASK-TYPES, Phase 2), puis démarre un workflow dédié dessus en
  réutilisant l'orchestrateur existant `ManualStartOrchestrator`. Protégé
  contre les doublons par un nouveau store d'idempotence dédié
  (D-IDEMPOTENCY, Phase 2) — voir Design.

### Secondary goals

- Réutiliser le DTO résumé de dossier (`DossierListItem`-like, sans
  `fields`) pour représenter le « dossier lié » dans les réponses relation
  — même principe D-ITEM que `pagination-dossiers`, aucune fuite de champ
  sensible par ce chemin.
- Index Liquibase adaptés (`source_dossier_id`, `target_dossier_id`).
- Nouveau store d'idempotence dédié à la création du dossier enfant par le
  `ServiceTask` (D-IDEMPOTENCY), distinct de l'`IdempotencyStore` existant
  qui protège uniquement le démarrage du workflow.
- Couverture tests alignée Jacoco / Vitest projet (≥ 80 % zones touchées).
- Mettre à jour `DEFERRED-FEATURES.md` §1.4 une fois livré.

### Non-goals

- Modification / suppression d'une relation existante (immutable une fois
  créée en V1 — erreur de saisie = créer un correctif manuel plus tard,
  hors scope).
- Validation automatique des combinaisons logiquement contradictoires
  (ex. A `PARENT_CHILD` de B **et** B `PARENT_CHILD` de A stockées toutes
  les deux) — responsabilité de l'utilisateur créateur en V1.
- Relations inter-tenant (jamais autorisées, non négociable).
- Étendre l'API générique `GET /api/dossiers` (pagination-dossiers) pour
  filtrer/trier par relation.
- Écran d'administration dédié aux relations (hors panneau dossier /
  visualisation workflow).
- Édition en masse (import CSV, etc.).
- Copie automatique de `fields` du dossier parent vers le dossier enfant
  créé par le ServiceTask — les `childDossierFields` doivent être fournis
  explicitement en variable de process.

## Stakeholder decisions

| Id | Décision | Choix |
|---|---|---|
| **D-SCOPE** | Périmètre V1 | **B** — API + affichage (workflow panel + `DossierDetailView`) + formulaire de création UI (recherche via `GET /api/dossiers`) |
| **D-CREATE** | Origine de création | **B** — création manuelle (API/UI) **+** hook `ServiceTask` BPMN |
| **D-SERVICETASK** | Portée du ServiceTask | **B** — crée un **nouveau** dossier enfant + relation + démarre un workflow dédié (pas une simple liaison entre deux dossiers déjà existants) |
| **D-ACL** | Visibilité relation vs dossier lié non VIEW-accessible | **A** — filtrage serveur : la relation n'apparaît que si le dossier « de l'autre côté » est aussi VIEW-accessible (même logique que `pagination-dossiers` D-ACL : accès partiel = résultat filtré, pas d'erreur) |
| **D-DIRECTION** | Affichage panneau | **A** — bidirectionnel avec libellé inversé (le dossier montre ses relations sortantes ET entrantes) |
| **D-TYPES** | Types de relation | Réutilisation telle quelle de la liste déjà spécifiée dans `analysis doc/04-dossiers.md` : `PARENT_CHILD`, `SUBSEQUENT`, `LINKED`, `DUPLICATE`, `MERGED_INTO` |
| **D-MUTABILITY** | Cycle de vie relation | Création + lecture seule en V1 — pas d'update/delete |
| **D-IDEMPOTENCY** *(Phase 2)* | Anti-doublon `ServiceTask` | **A** — nouveau store d'idempotence dédié, même pattern que `IdempotencyStore` (DD-TX) : clé dérivée de `(processInstanceId, activityId)`, vérifiée avant `CreateDossierUseCase.create()` |
| **D-SERVICETASK-TYPES** *(Phase 2)* | Types autorisés au `ServiceTask` | **A** — restreint à `PARENT_CHILD` / `SUBSEQUENT` (seuls types cohérents avec une création automatique) ; `LINKED`/`DUPLICATE`/`MERGED_INTO` restent réservés à la création manuelle |
| **D-CREATE-ACL** *(Phase 2)* | Droits requis pour `POST .../relations` | **A** — EDIT sur le type du dossier source **et** VIEW sur le type du dossier cible (empêche de lier un dossier hors périmètre VIEW de l'actor) |
| **D-DTO** *(Phase 2, résolu par précédent)* | Forme de `relatedDossier` | Type domaine unique `DossierRelationView` dans `domain.dossier.model`, réutilisé tel quel par `workflow` — même précédent que `WorkflowLinkDossierView` qui dépend déjà de `Dossier`/`DossierElement` (types du même package). Pas de duplication de DTO ; aucune dépendance de `workflow` vers une couche HTTP. |

### Alternatives écartées

| Sujet | Retenu | Écarté |
|---|---|---|
| Portée ServiceTask | Crée dossier enfant + relation + workflow dédié | Simple liaison entre deux dossiers déjà existants (plus petit scope, mais ne couvre pas le cas d'usage métier principal documenté dans `analysis doc/04`) |
| ACL relation masquée | Filtrer côté serveur (omise) | Renvoyer un résumé minimal masqué (fuite de métadonnées : existence + statut d'un dossier hors ACL de l'utilisateur) |
| Affichage | Bidirectionnel | Sortant uniquement (UX dégradée : un dossier enfant ne verrait pas son parent) |
| Idempotence ServiceTask | Store dédié (D-IDEMPOTENCY) | Compter sur l'atomicité Flowable+JPA — rejeté, contredit la raison d'être du split reserve/confirm (DD-TX) déjà en place pour le démarrage manuel |
| Types ServiceTask | Restreint PARENT_CHILD/SUBSEQUENT | Les 5 types génériques — rejeté, `DUPLICATE`/`MERGED_INTO`/`LINKED` n'ont pas de sens pour une création automatique de dossier |
| ACL création manuelle | EDIT source + VIEW cible | EDIT source seule — rejeté, permettrait de faire apparaître un dossier hors périmètre VIEW dans les relations d'un dossier géré par quelqu'un d'autre |

## User Stories & Use Cases
**How will users interact with the solution?**

1. **En tant que** gestionnaire consultant un dossier, **je veux** voir les
   dossiers qui lui sont liés (parent, enfant, doublon…), **afin de**
   comprendre le contexte sans recherche manuelle.
2. **En tant que** gestionnaire, **je veux** lier explicitement deux
   dossiers existants avec un type de relation, **afin de** tracer un lien
   métier constaté a posteriori (ex. doublon détecté).
3. **En tant qu'** utilisateur de la visualisation workflow, **je veux** que
   le panneau « dossiers liés » affiche les relations réelles, **afin de**
   naviguer vers un dossier lié sans quitter la vue process.
4. **En tant que** concepteur de process BPMN, **je veux** un `ServiceTask`
   qui crée un dossier enfant et démarre son propre workflow, **afin
   d'**automatiser le scénario « instruction sur un aspect précis
   nécessitant un workflow dédié » (déjà décrit dans `analysis doc/04`).
5. **En tant qu'** utilisateur sans droit VIEW sur le type d'un dossier lié,
   **je ne veux pas** voir cette relation apparaître, **afin de** ne pas
   apprendre l'existence d'un dossier hors de mon périmètre.

### Edge cases

- `sourceDossierId == targetDossierId` → rejeté (pas d'auto-relation).
- Relation dupliquée exacte (même source + cible + type) → rejetée
  (contrainte d'unicité base).
- Dossier cible d'un autre tenant → 404 (jamais 403 — ne pas confirmer
  l'existence cross-tenant, même logique que le reste de l'app).
- Aucun droit VIEW sur le dossier « de l'autre côté » → relation omise de
  la liste (pas d'erreur, page silencieusement plus courte).
- `type` invalide / absent / `targetDossierId` absent → 400 `ProblemDetail`.
- `POST` par un utilisateur sans droit EDIT sur le type du dossier source →
  403 (même politique que `PATCH /status`).
- `POST` par un utilisateur avec EDIT sur la source mais sans VIEW sur le
  type du dossier cible (D-CREATE-ACL) → **403** — même convention que
  `GetDossierUseCaseImpl` (dossier du même tenant mais non VIEW-accessible
  = `AccessDeniedException`/403, pas 404 ; le 404 reste réservé au dossier
  introuvable ou d'un autre tenant, cf. `DossierNotFoundException`).
- `ServiceTask` exécuté deux fois pour la même activité de process (retry
  Flowable après échec transitoire) → protégé par le store d'idempotence
  dédié (D-IDEMPOTENCY) : le deuxième appel réutilise le dossier/la
  relation déjà créés et ne relance que le démarrage du workflow.
- `ServiceTask` configuré avec un `relationType` hors `PARENT_CHILD`/
  `SUBSEQUENT` (D-SERVICETASK-TYPES) → échec du délégué (erreur de
  configuration process, pas une entrée utilisateur).
- Dossier cible déjà `ARCHIVE`/`CLOTURE` → relation autorisée quand même
  (une relation trace un fait, pas un état courant).

## Success Criteria
**How will we know when we're done?**

- [ ] `POST /api/dossiers/{id}/relations` crée la relation et renvoie 201.
- [ ] `GET /api/dossiers/{id}/relations` renvoie les relations
      bidirectionnelles filtrées par ACL (D-ACL), avec sens/libellé corrects.
- [ ] `WorkflowLinkDossierView.relations` n'est plus jamais `[]` en dur —
      alimenté par les vraies relations du dossier, avec le même filtrage ACL.
- [ ] `DossierDetailView.vue` affiche une section « Dossiers liés » + action
      « Lier un dossier » fonctionnelle (recherche via `GET /api/dossiers`).
- [ ] `createChildDossierTask` (ServiceTask) crée dossier + relation +
      démarre le workflow dédié dans un test d'intégration BPMN, et un
      rejeu de la même activité ne crée pas de doublon (D-IDEMPOTENCY).
- [ ] `POST .../relations` refuse (403) une cible hors du périmètre VIEW de
      l'actor, même avec EDIT sur la source (D-CREATE-ACL).
- [ ] Régression : aucun endpoint existant (`elements`, `status`, liste
      paginée) n'est impacté.
- [ ] `./mvnw test` (zones touchées) + `frontend` Vitest/Playwright OK.
- [ ] `DEFERRED-FEATURES.md` §1.4 mis à jour (relations livrées).

## Constraints & Assumptions
**What limitations do we need to work within?**

- Hexagone : domaine pur pour `DossierRelation` (record, pas d'annotation
  JPA — décision n° 17) ; use case + port out ; `DossierRelationJpaAdapter`
  en infrastructure, même patron que `DossierJpaAdapter`.
- Instance dédiée par tenant (décision n° 13) ; `tenantId` toujours
  `TenantProperties.id()`, jamais dérivé de l'entrée cliente.
- ACL réutilise `DossierAccessPolicy` existant (`assertCanView` sur le
  dossier source à l'entrée, filtrage par `assertCanViewType`/équivalent
  sur chaque dossier lié pour la liste).
- Le `ServiceTask` réutilise `ManualStartOrchestrator` pour démarrer le
  workflow du dossier enfant (mêmes garanties que le démarrage manuel :
  `AttachWorkflowUseCase.reserve` → moteur → confirm, idempotence via
  `IdempotencyStore`) — **pas** de réimplémentation du démarrage de process.
- H2 mode PostgreSQL pour les IT (comme le reste de `dossiers`) ; pas de
  Testcontainers dans cette feature.
- **Assumption A1 :** le résumé de dossier lié dans les réponses relation
  réutilise la forme `DossierListItem` (id, reference, label,
  dossierTypeCode, dossierTypeLabel, status) — jamais `fields`.
- **Assumption A2 :** le `ServiceTask` lit `dossierId` (source, convention
  déjà utilisée par `UpdateDossierStatusDelegate`), `childDossierTypeCode`,
  `childDossierLabel`, `childDossierFields` (Map, optionnel), `relationType`,
  `workflowProcessKey`, `childWorkflowVariables` (Map, optionnel) en
  variables de process (pas de field-injection BPMN — même patron que les
  délégués existants).
- **Assumption A3 :** unicité relation = contrainte DB sur
  `(tenant_id, source_dossier_id, target_dossier_id, type)` — pas de
  déduplication logique des combinaisons inverses (non-goal assumé).

## Questions & Open Items
**What do we still need to clarify?**

### Clos Phase 2

| Id | Clarification |
|---|---|
| Q1 | Idempotence du `ServiceTask` → store dédié, clé `(processInstanceId, activityId)` (D-IDEMPOTENCY) |
| Q2 | `DossierRelationView` = type domaine unique, réutilisé directement par `workflow` (précédent `Dossier`/`DossierElement`) — pas de duplication de DTO (D-DTO) |
| Q3 | `ServiceTask` restreint à `PARENT_CHILD`/`SUBSEQUENT` (D-SERVICETASK-TYPES) |
| Q4 | `POST .../relations` exige EDIT source **+** VIEW cible (D-CREATE-ACL) — sinon 403, cohérent avec `GetDossierUseCaseImpl` |

### Reporté hors feature

- Suppression/modification d'une relation (V1 = création + lecture seule).
- Détection/validation des combinaisons logiquement contradictoires entre
  relations (ex. deux `PARENT_CHILD` inverses).
- Filtrage/tri de `GET /api/dossiers` (pagination-dossiers) par relation.
