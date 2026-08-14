---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-14
updated: 2026-08-14
feature: relations-dossiers
phase2_stakeholder_sync: 2026-08-14
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- Backend : Jacoco ≥ 80 % sur les zones touchées (convention projet), aligné
  sur `pagination-dossiers` et la remédiation sécurité P0–P3.
- Frontend : Vitest sur les composants nouveaux/modifiés + Playwright pour
  le parcours « lier un dossier » et l'affichage du panneau relations.
- Chaque critère de succès du doc de requirements doit être couvert par au
  moins un test automatisé listé ci-dessous.

## Unit Tests
**What individual components need testing?**

### `DossierRelation` (domaine)
- [ ] Construction rejette `sourceDossierId == targetDossierId`.
- [ ] Construction rejette un `tenantId` différent entre les deux dossiers
      (si vérifié au niveau domaine plutôt qu'use case).

### `CreateDossierRelationUseCaseImpl`
- [ ] Crée la relation quand l'actor a EDIT sur la source **et** VIEW sur
      la cible (D-CREATE-ACL).
- [ ] Rejette (403) si l'actor n'a pas EDIT sur le type du dossier source.
- [ ] Rejette (403) si l'actor a EDIT sur la source mais pas VIEW sur le
      type du dossier cible (D-CREATE-ACL — même convention 403 que
      `GetDossierUseCaseImpl`, pas de masquage par 404).
- [ ] Rejette (404) si le dossier cible n'existe pas ou appartient à un
      autre tenant.
- [ ] Rejette (400) l'auto-relation (`source == target`).
- [ ] Rejette (409) une relation strictement dupliquée (même source, cible,
      type) — mapping de la violation de contrainte unique.
- [ ] Accepte les 5 `RelationType` (`PARENT_CHILD`, `SUBSEQUENT`, `LINKED`,
      `DUPLICATE`, `MERGED_INTO`) — la restriction à `PARENT_CHILD`/
      `SUBSEQUENT` s'applique uniquement au `ServiceTask`, pas à ce use
      case générique.

### `ListDossierRelationsUseCaseImpl`
- [ ] Retourne les relations où le dossier interrogé est source
      (`direction = OUTGOING`).
- [ ] Retourne les relations où le dossier interrogé est cible
      (`direction = INCOMING`).
- [ ] Omet une relation si l'actor n'a pas VIEW sur le dossier « de l'autre
      côté » (D-ACL — pas d'erreur, liste juste plus courte).
- [ ] `TENANT_ADMIN`/`WORKFLOW_ADMIN` voit toutes les relations (bypass ACL,
      cohérent avec `DossierAccessPolicy`).
- [ ] `relatedDossier` ne contient jamais `fields` (vérification structurelle
      du DTO, même garantie que `DossierListItem`).
- [ ] Dossier sans aucune relation → liste vide, pas d'erreur.

### `CreateChildDossierWithRelationUseCaseImpl`
- [ ] Premier appel : crée dossier + relation (avec `idempotencyKey`) dans
      une seule transaction, renvoie le `childDossierId`.
- [ ] Deuxième appel avec la même `(tenantId, sourceDossierId,
      idempotencyKey)` : ne crée rien, renvoie le `childDossierId` déjà
      existant (lookup par `findByIdempotencyKey`, pas de second insert).
- [ ] Deux appels concurrents avec la même clé (course) : un seul aboutit,
      l'autre échoue proprement sur la contrainte unique partielle
      (`uq_dossier_relation_idempotency`) — pas de doublon possible même
      hors verrou applicatif.
- [ ] Une relation créée manuellement (`idempotencyKey = null`) n'entre
      jamais en collision avec l'index partiel.

### `CreateChildDossierDelegate` (ServiceTask)
- [ ] Variables requises manquantes (`childDossierTypeCode`,
      `workflowProcessKey`, …) → exception claire, pas de NPE.
- [ ] Dérive correctement `idempotencyKey` depuis
      `processInstanceId`/`activityId` de l'exécution Flowable.
- [ ] Démarre le workflow dédié via `ManualStartOrchestrator` avec les
      `childWorkflowVariables` fournies, en s'appuyant sur le
      `childDossierId` renvoyé par `CreateChildDossierWithRelationUseCase`
      (qu'il vienne d'une création ou d'un replay).
- [ ] Rejette (échec explicite du délégué) un `relationType` hors
      `{PARENT_CHILD, SUBSEQUENT}` (D-SERVICETASK-TYPES) — ex. `LINKED`
      configuré par erreur sur le process.
- [ ] **Idempotence bout en bout (D-IDEMPOTENCY) :** un deuxième appel du
      délégué avec la même clé ne crée ni second dossier enfant ni seconde
      relation, et relance seulement le démarrage du workflow (déjà
      protégé par l'`IdempotencyStore` existant).

## Integration Tests
**How do we test component interactions?**

- [ ] `POST /api/dossiers/{id}/relations` — 201 + shape JSON complète
      (MockMvc, H2 mode PostgreSQL).
- [ ] `GET /api/dossiers/{id}/relations` — 200, ordre stable, ACL appliquée
      bout en bout (utilisateur avec grant partiel).
- [ ] Contrainte unique DB déclenchée via deux `POST` identiques successifs
      → 409 propre (`ProblemDetail`), pas de 500.
- [ ] `WorkflowLinkVisualizationUseCaseImpl.getDossier()` — panneau dossier
      inclut désormais les relations réelles (remplace l'assertion existante
      qui vérifiait `relations` vide en dur, si elle existe).
- [ ] Test d'intégration BPMN : process avec `createChildDossierTask` →
      dossier enfant + relation + `DossierWorkflowLink` actif créés en base
      après exécution.
- [ ] Régression : `GET /api/dossiers/{id}/elements`, `PATCH .../status`,
      `GET /api/dossiers` (pagination) inchangés.

## End-to-End Tests
**What user flows need validation?**

- [ ] Parcours Playwright : ouvrir un dossier → « Lier un dossier » →
      rechercher/sélectionner une cible → choisir un type → confirmer → la
      section « Dossiers liés » affiche la nouvelle entrée sans reload.
- [ ] Parcours Playwright : ouvrir un dossier lié depuis le panneau
      « Dossiers liés » → navigation vers `DossierDetailView` du dossier
      cible.
- [ ] Parcours Playwright : panneau « dossiers liés » de la visualisation
      workflow affiche une relation réelle (remplace l'état vide actuel).
- [ ] Régression Playwright existante : `pilotage.spec.ts` (visualisation
      workflow) et parcours Supervision (`pagination-dossiers`) toujours
      verts.

## Test Data
**What data do we use for testing?**

- Fixtures : au moins deux dossiers du même tenant, de types différents,
  avec grants ACL VIEW/EDIT variés (dont un utilisateur avec VIEW sur un
  seul des deux types, pour couvrir le filtrage D-ACL).
- Process BPMN de test dédié pour `createChildDossierTask` (embarqué au
  classpath, comme le reste des process de test existants).

## Test Reporting & Coverage
**How do we verify and communicate test results?**

- `./mvnw test` (module dossier + flowable) avec rapport Jacoco.
- `npm run test -- --coverage` côté frontend pour `RelationsList.vue` /
  `LinkDossierDialog.vue`.
- `npm run test:e2e` pour les scénarios Playwright ci-dessus.

## Manual Testing
**What requires human validation?**

- Vérification visuelle du panneau « Dossiers liés » (léger, non intrusif)
  dans `DossierDetailView.vue` et dans la visualisation workflow.
- Vérification qu'un utilisateur `DOSSIER_VIEWER` (pas de droit EDIT) ne
  voit pas l'action « Lier un dossier ».

## Performance Testing
**How do we validate performance?**

- Vérifier que `findByDossier` reste une requête unique (pas de N+1) via le
  plan d'exécution / log SQL en test d'intégration, avec un dossier ayant
  plusieurs dizaines de relations.

## Bug Tracking
**How do we manage issues?**

- Suivi standard du projet (`TODO.md` / `DEFERRED-FEATURES.md` pour tout
  écart livré partiellement) — aucun outil dédié supplémentaire.
