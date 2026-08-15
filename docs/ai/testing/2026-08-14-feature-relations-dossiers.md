---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: reviewed
created: 2026-08-14
updated: 2026-08-15
feature: relations-dossiers
phase2_stakeholder_sync: 2026-08-14
phase8: 2026-08-15
phase9: 2026-08-15
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

## Test files

### Backend

| Zone | Fichiers |
|---|---|
| Domaine | `src/test/java/com/flowforge/domain/dossier/model/DossierRelationTest.java` |
| Use cases | `.../usecase/CreateDossierRelationUseCaseImplTest.java`, `ListDossierRelationsUseCaseImplTest.java` |
| Persistence | `.../persistence/dossier/DossierRelationSchemaTest.java`, `DossierRelationPersistenceTest.java` |
| API | `.../web/dossier/DossierApiTest.java`, `DossierExceptionHandlerTest.java` |
| Workflow panel | `.../workflow/usecase/WorkflowLinkVisualizationUseCaseImplTest.java`, `.../web/workflow/WorkflowApiTest.java` |
| ServiceTask | `.../dossier/ChildDossierRelationConfirmerTest.java`, `.../flowable/CreateChildDossierDelegateTest.java`, `CreateChildDossierTaskIT.java` |
| Fixture BPMN | `src/main/resources/processes/create-child-dossier-smoke.bpmn20.xml` |

### Frontend

| Zone | Fichiers |
|---|---|
| Client API | `frontend/src/api/dossiers.spec.ts` |
| Store | `frontend/src/stores/dossier.spec.ts` |
| Composants | `RelationsList.spec.ts`, `LinkDossierDialog.spec.ts`, `relationLabels.spec.ts` |
| Vue | `frontend/src/views/pilotage/DossierDetailView.spec.ts` |
| E2E | `frontend/e2e/pilotage.spec.ts` (`E2E-11`–`E2E-14`) |

## Unit Tests
**What individual components need testing?**

### `DossierRelation` (domaine)
- [x] Construction rejette `sourceDossierId == targetDossierId`.
- [x] Construction rejette un `tenantId` différent entre les deux dossiers
      (si vérifié au niveau domaine plutôt qu'use case). — vérifié dans l'use case
      (`CreateDossierRelationUseCaseImpl` → 404 si cible d'un autre tenant).

### `CreateDossierRelationUseCaseImpl`
- [x] Crée la relation quand l'actor a EDIT sur la source **et** VIEW sur
      la cible (D-CREATE-ACL).
- [x] Rejette (403) si l'actor n'a pas EDIT sur le type du dossier source.
- [x] Rejette (403) si l'actor a EDIT sur la source mais pas VIEW sur le
      type du dossier cible (D-CREATE-ACL — même convention 403 que
      `GetDossierUseCaseImpl`, pas de masquage par 404).
- [x] Rejette (404) si le dossier cible n'existe pas ou appartient à un
      autre tenant.
- [x] Rejette (400) l'auto-relation (`source == target`).
- [x] Rejette (400) `type` / ids manquants.
- [x] Rejette (409) une relation strictement dupliquée (même source, cible,
      type) — mapping de la violation de contrainte unique.
- [x] Accepte les 5 `RelationType` (`PARENT_CHILD`, `SUBSEQUENT`, `LINKED`,
      `DUPLICATE`, `MERGED_INTO`) — la restriction à `PARENT_CHILD`/
      `SUBSEQUENT` s'applique uniquement au `ServiceTask`, pas à ce use
      case générique.

### `ListDossierRelationsUseCaseImpl`
- [x] Retourne les relations où le dossier interrogé est source
      (`direction = OUTGOING`).
- [x] Retourne les relations où le dossier interrogé est cible
      (`direction = INCOMING`).
- [x] Omet une relation si l'actor n'a pas VIEW sur le dossier « de l'autre
      côté » (D-ACL — pas d'erreur, liste juste plus courte).
- [x] Omet une relation si l'autre dossier a disparu.
- [x] `TENANT_ADMIN`/`WORKFLOW_ADMIN` voit toutes les relations (bypass ACL,
      cohérent avec `DossierAccessPolicy`).
- [x] `relatedDossier` ne contient jamais `fields` (vérification structurelle
      du DTO, même garantie que `DossierListItem`).
- [x] Dossier sans aucune relation → liste vide, pas d'erreur.

### `ChildDossierRelationConfirmer`
- [x] Premier appel : crée dossier + relation (avec `idempotencyKey`) dans
      une seule transaction, renvoie le `childDossierId`.
- [x] Deuxième appel avec la même `(tenantId, sourceDossierId,
      idempotencyKey)` : ne crée rien, renvoie le `childDossierId` déjà
      existant (lookup par `findByIdempotencyKey`, pas de second insert).
- [x] Deux appels concurrents avec la même clé (course) : un seul aboutit,
      l'autre échoue proprement sur la contrainte unique
      (`uq_dossier_relation_idempotency`) — pas de doublon possible même
      hors verrou applicatif.
- [x] Course sans ligne retrouvée → relance `DuplicateDossierRelationException`.
- [x] Une relation créée manuellement (`idempotencyKey = null`) n'entre
      jamais en collision avec l'unicité (IT persistence, deux NULL).

### `CreateChildDossierDelegate` (ServiceTask)
- [x] Variables requises manquantes (`childDossierTypeCode`,
      `workflowProcessKey`, …) → exception claire, pas de NPE.
- [x] `relationType` inconnu / `activityId` manquant → exception claire.
- [x] Dérive correctement `idempotencyKey` depuis
      `processInstanceId`/`activityId` de l'exécution Flowable.
- [x] Démarre le workflow dédié via `ManualStartOrchestrator` avec les
      `childWorkflowVariables` fournies, en s'appuyant sur le
      `childDossierId` renvoyé par `ChildDossierRelationConfirmer`
      (qu'il vienne d'une création ou d'un replay).
- [x] Rejette (échec explicite du délégué) un `relationType` hors
      `{PARENT_CHILD, SUBSEQUENT}` (D-SERVICETASK-TYPES) — ex. `LINKED`
      configuré par erreur sur le process.
- [x] **Idempotence bout en bout (D-IDEMPOTENCY) :** un deuxième appel du
      délégué avec la même clé ne crée ni second dossier enfant ni seconde
      relation, et relance seulement le démarrage du workflow (déjà
      protégé par l'`IdempotencyStore` existant).

## Integration Tests
**How do we test component interactions?**

- [x] `POST /api/dossiers/{id}/relations` — 201 + shape JSON complète
      (MockMvc, H2 mode PostgreSQL).
- [x] `GET /api/dossiers/{id}/relations` — 200, ordre stable, ACL appliquée
      bout en bout (utilisateur avec grant partiel).
- [x] Contrainte unique DB déclenchée via deux `POST` identiques successifs
      → 409 propre (`ProblemDetail`), pas de 500.
- [x] `WorkflowLinkVisualizationUseCaseImpl.getDossier()` — panneau dossier
      inclut désormais les relations réelles (remplace l'assertion existante
      qui vérifiait `relations` vide en dur, si elle existe).
- [x] Test d'intégration BPMN : process avec `createChildDossierTask` →
      dossier enfant + relation + `DossierWorkflowLink` actif créés en base
      après exécution.
- [x] Régression : `GET /api/dossiers/{id}/elements`, `PATCH .../status`,
      `GET /api/dossiers` (pagination) inchangés.

## End-to-End Tests
**What user flows need validation?**

- [x] Parcours Playwright : ouvrir un dossier → « Lier un dossier » →
      rechercher/sélectionner une cible → choisir un type → confirmer → la
      section « Dossiers liés » affiche la nouvelle entrée sans reload.
      (`E2E-11`)
- [x] Parcours Playwright : ouvrir un dossier lié depuis le panneau
      « Dossiers liés » → navigation vers `DossierDetailView` du dossier
      cible. (`E2E-12`)
- [x] Parcours Playwright : panneau « dossiers liés » de la visualisation
      workflow affiche une relation réelle (même page `DossierDetailView`,
      `E2E-14` avec lien workflow actif).
- [x] Régression Playwright existante : `pilotage.spec.ts` (visualisation
      workflow) et parcours Supervision (`pagination-dossiers`) toujours
      verts. (`E2E-1`–`E2E-10`)

## Test Data
**What data do we use for testing?**

- Fixtures : au moins deux dossiers du même tenant, de types différents,
  avec grants ACL VIEW/EDIT variés (dont un utilisateur avec VIEW sur un
  seul des deux types, pour couvrir le filtrage D-ACL).
- Process BPMN de test dédié pour `createChildDossierTask` (embarqué au
  classpath, comme le reste des process de test existants).

## Test Reporting & Coverage
**How do we verify and communicate test results?**

Phase 8 (2026-08-15), preuves fraîches :

| Commande | Résultat |
|---|---|
| `./mvnw test -Dskip.frontend=true -Dmaven.compiler.release=21 -Dtest='DossierRelationTest,…,WorkflowApiTest'` + `jacoco:report` | exit 0 ; **204/204** lignes (100 %) sur les classes relations |
| `cd frontend && npm run test` | exit 0, **78/78** (31 fichiers) |
| `cd frontend && npm run test:coverage` (include relations) | lines **88.32 %**, stmts **85.9 %**, branches **76.56 %** |
| `cd frontend && npm run test:e2e` | exit 0, **15/15** |

Branches FE restantes (non bloquantes) : reset `watch` du dialog à la
réouverture, `default` de `relationLabel`, chemins d'erreur start/reassign
dans `DossierDetailView`, params optionnels de `listDossiers`.

## Phase 9 — Code Review (2026-08-15)

Revue holistique **passée**. Aucun blocker. Findings basses R1–R5 dans
`docs/ai/implementation/2026-08-14-feature-relations-dossiers.md`.
Preuves relancées cette session : backend **644**, Vitest **78/78**,
Playwright **15/15**. Pas de retour Phase 8.

## Manual Testing
**What requires human validation?**

- Vérification visuelle du panneau « Dossiers liés » (léger, non intrusif)
  dans `DossierDetailView.vue` et dans la visualisation workflow.
- Vérification qu'un utilisateur `DOSSIER_VIEWER` (pas de droit EDIT) ne
  voit pas l'action « Lier un dossier ». — aussi couvert par `E2E-13`.

## Performance Testing
**How do we validate performance?**

- `findByDossier` est une requête unique (`source OR target`) — vérifié par
  le repository Spring Data + IT persistence bidirectionnelle. Pas de N+1
  SQL asserté (volume V1 faible, hypothèse design).

## Bug Tracking
**How do we manage issues?**

- Suivi standard du projet (`TODO.md` / `DEFERRED-FEATURES.md` pour tout
  écart livré partiellement) — aucun outil dédié supplémentaire.
