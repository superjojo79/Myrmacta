---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-14
updated: 2026-08-14
feature: relations-dossiers
stakeholder_decisions: 2026-08-14
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
  par un utilisateur autorisé (source = `{id}`, cible = `targetDossierId`
  du body, `type` parmi les 5 valeurs).
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
  correspondante, puis démarre un workflow dédié dessus en réutilisant
  l'orchestrateur existant `ManualStartOrchestrator` (mêmes garanties
  transactionnelles/idempotence que le démarrage manuel).

### Secondary goals

- Réutiliser le DTO résumé de dossier (`DossierListItem`-like, sans
  `fields`) pour représenter le « dossier lié » dans les réponses relation
  — même principe D-ITEM que `pagination-dossiers`, aucune fuite de champ
  sensible par ce chemin.
- Index Liquibase adaptés (`source_dossier_id`, `target_dossier_id`).
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

### Alternatives écartées

| Sujet | Retenu | Écarté |
|---|---|---|
| Portée ServiceTask | Crée dossier enfant + relation + workflow dédié | Simple liaison entre deux dossiers déjà existants (plus petit scope, mais ne couvre pas le cas d'usage métier principal documenté dans `analysis doc/04`) |
| ACL relation masquée | Filtrer côté serveur (omise) | Renvoyer un résumé minimal masqué (fuite de métadonnées : existence + statut d'un dossier hors ACL de l'utilisateur) |
| Affichage | Bidirectionnel | Sortant uniquement (UX dégradée : un dossier enfant ne verrait pas son parent) |

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
- `ServiceTask` exécuté deux fois pour la même activité de process (retry
  Flowable après échec transitoire) → **risque de doublon de dossier
  enfant**, à verrouiller en Phase 2/3 design (voir Questions ouvertes).
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
      démarre le workflow dédié dans un test d'intégration BPMN.
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

### À trancher en Phase 2 (Review Requirements) / Phase 3 (Design)

- **Q1 — Idempotence du `ServiceTask`.** Si Flowable ré-exécute
  `createChildDossierTask` après un échec transitoire (le job executor
  retry est standard), comment éviter un deuxième dossier enfant / une
  deuxième relation ? Piste : dériver une clé d'idempotence déterministe de
  `(processInstanceId, activityId)`, la stocker sur la relation créée (ou
  un registre dédié), et court-circuiter si elle existe déjà — à valider en
  design.
- **Q2 — Nom exact et forme du DTO résumé de dossier lié** dans les
  réponses relation : réutiliser tel quel `DossierListItem` (couplage entre
  contextes `dossier` et `workflow`) ou dupliquer un DTO plus léger propre à
  cette feature ? À trancher en Phase 3 (design), impact architecture
  hexagonale (le contexte `workflow` ne doit pas dépendre du DTO HTTP du
  contexte `dossier`).
- **Q3 — `relationType` autorisés pour le `ServiceTask`.** Les 5 types
  restent-ils tous valides en configuration de process (y compris
  `DUPLICATE`/`MERGED_INTO`, sémantiquement étranges pour une création
  automatique), ou restreint-on le `ServiceTask` à `PARENT_CHILD` /
  `SUBSEQUENT` uniquement ? Proposition par défaut : restreindre au
  `ServiceTask` (le domaine `DossierRelation` reste générique, seule la
  validation du délégué est plus stricte) — à confirmer en Phase 2.
