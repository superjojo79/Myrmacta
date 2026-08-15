---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: reviewed
created: 2026-08-14
updated: 2026-08-15
feature: relations-dossiers
phase7: 2026-08-15
phase9: 2026-08-15
---

# Implementation Guide

## Development Setup

- JDK 21 local (`-Dmaven.compiler.release=21`) ou JDK 25 CI (`pom.xml`).
- `./mvnw test -Dskip.frontend=true -Dmaven.compiler.release=21` pour le backend.
- `cd frontend && npm run test` puis `npm run test:e2e`.

## Design summary (Phase 7)

Deux chemins convergent vers `DossierRelation` : création manuelle (API/UI) et
`ServiceTask` `createChildDossierTask` (dossier enfant + relation + workflow).
ACL : EDIT source + VIEW cible à la création ; liste bidirectionnelle filtrée
VIEW. Idempotence ServiceTask via `idempotency_key` +
`ChildDossierRelationConfirmer` (`@Transactional` en infrastructure).
`DossierRelationView` est le type domaine unique, réutilisé par `workflow`.

## Code Structure

Domaine (`com.flowforge.domain.dossier`) — pur Java, aucune annotation Spring/JPA/Flowable :
- `model/{DossierRelation, RelationType, DossierRelationView, RelationDirection, DuplicateDossierRelationException}`
- `port.in/{CreateDossierRelationUseCase, ListDossierRelationsUseCase}`
- `port.out.DossierRelationRepository`
- `usecase/{CreateDossierRelationUseCaseImpl, ListDossierRelationsUseCaseImpl}`

Infrastructure :
- Liquibase `009-dossier-relation-tables.yaml`
- `DossierRelationJpaEntity` / `SpringDataDossierRelationRepository` / `DossierRelationJpaAdapter`
- `DossierController` `GET/POST /api/dossiers/{id}/relations`
- `DossierExceptionHandler` : `DuplicateDossierRelationException` → 409
- `ChildDossierRelationConfirmer` (`@Transactional`)
- `CreateChildDossierDelegate` bean `createChildDossierTask`

Frontend :
- `api/dossiers.ts` `listDossierRelations` / `createDossierRelation`
- `RelationsList.vue`, `LinkDossierDialog.vue`, `relationLabels.ts`
- section dans `DossierDetailView.vue` + store `relations` / `linkDossier`

## File-by-file review (Phase 7)

| Fichier | Intent design | Verdict |
|---|---|---|
| `DossierRelation` / `RelationType` (5 valeurs) / `DossierRelationView` / `RelationDirection` | Records domaine + `idempotencyKey` nullable | Aligné. Auto-relation rejetée au constructeur. |
| `DuplicateDossierRelationException` | Mapping 409 | Aligné (package `model`, pas `exception`). |
| Ports in/out | `create` / `list` / `save` / `findByDossier` / `findByIdempotencyKey` | Aligné. |
| `CreateDossierRelationUseCaseImpl` | EDIT source + VIEW cible ; 400 auto-relation ; tenant via command | Aligné. `tenantId` du POST = `TenantProperties.id()`. |
| `ListDossierRelationsUseCaseImpl` | Bidirectionnel, omit si pas VIEW, `DossierListItem` sans `fields` | Aligné. Isolation tenant = schéma (même patron que `GetDossierUseCaseImpl`). |
| `009-dossier-relation-tables.yaml` | CHECK anti-self, UK paire+type, index source/target, clé idempotence | Aligné avec écart #1 (UNIQUE au lieu d'index partiel). Pas de `ON DELETE CASCADE`. |
| `DossierRelationJpaAdapter` | `DataIntegrityViolationException` → exception domaine | Aligné. `findByDossier` = une requête `source OR target`. |
| `DossierController` + DTOs | 201/200, `relatedDossier` = `DossierListItem` | Aligné. |
| `WorkflowLinkDossierView` + `getDossier()` | `List<DossierRelationView>` via le use case de liste | Aligné. Plus de `List.of()`. |
| `ChildDossierRelationConfirmer` | Lookup clé → replay ; sinon create dossier+relation même TX | Aligné. Course : catch 409 → re-lookup. |
| `CreateChildDossierDelegate` | Variables process A2 ; types `PARENT_CHILD`/`SUBSEQUENT` ; `ManualStartOrchestrator` | Aligné avec écart #2 (ouverture DRAFT→OUVERT). |
| `RelationsList` / `LinkDossierDialog` / store | Section détail + recherche `GET /api/dossiers` | Aligné. Découverte : une seule page UI. |
| `canLink` dans `DossierDetailView` | Visible si EDIT sur le type | Écart #4 (rôle, pas grant type). L'API reste le garde-fou. |

## Implementation Notes

### Core Features

- Création manuelle : EDIT source + VIEW cible ; auto-relation 400 ; doublon 409.
- Liste bidirectionnelle : `OUTGOING`/`INCOMING`, filtrage ACL silencieux, `DossierListItem` sans `fields`.
- Panneau workflow : `WorkflowLinkDossierView.relations` = `List<DossierRelationView>` via le même use case de liste.
- ServiceTask : types `PARENT_CHILD`/`SUBSEQUENT` uniquement ; clé `processInstanceId:activityId` ; replay sans second insert.

### Patterns

- `@Transactional` uniquement en infrastructure (`ChildDossierRelationConfirmer`), jamais dans le domaine.
- `AccessContext.system("flowable-delegate")` pour le chemin BPMN (bypass ACL).
- `DossierListItem.from(Dossier, label)` pour le résumé lié.

### Décisions / écarts

| # | Écart | Sévérité | Justification |
|---|---|---|---|
| 1 | Unicité idempotence via UNIQUE `(tenant_id, source_dossier_id, idempotency_key)` au lieu d'un index partiel `WHERE idempotency_key IS NOT NULL` | Basse | H2 2.4 refuse `CREATE UNIQUE INDEX ... WHERE`. NULLs distincts : plusieurs relations manuelles (`key = null`) restent possibles (IT dédiée). |
| 2 | Enfant `DRAFT` → `OUVERT` avant `ManualStartOrchestrator.start` | Moyenne (nécessaire) | `CreateDossierUseCase` crée toujours un `DRAFT`. `AttachWorkflowUseCase.reserve` refuse `DRAFT` sur les configs smoke (`OUVERT`/`EN_COURS`/`SUSPENDU`). |
| 3 | SPA n'appelle pas `GET /api/workflow-links/{id}/dossier` | Basse (découverte planning) | La visualisation vit déjà dans `DossierDetailView`. Endpoint peuplé pour clients futurs. Preuve : `WorkflowApiTest.visualizationDossierPanelIncludesRealRelations` + `E2E-14`. |
| 4 | Bouton « Lier un dossier » basé sur le rôle (`TENANT_ADMIN` / `WORKFLOW_ADMIN` / `DOSSIER_MANAGER`), pas sur le grant EDIT du type | Basse | L'API refuse toujours 403 sans EDIT. Un manager sans grant type voit le bouton puis 403. Suivi UX possible en Phase 8/hors V1. |

Pas d'écart majeur. Pas de retour Phase 3 (design) ni Phase 5 (réimplémentation).

## Alignment vs requirements

| Critère de succès | Statut | Preuve |
|---|---|---|
| POST 201 | OK | `DossierApiTest.createAndListRelations_bidirectionalAclAndDuplicate409` |
| GET bidirectionnel + ACL | OK | même IT + `ListDossierRelationsUseCaseImplTest` |
| `WorkflowLinkDossierView.relations` réel | OK | `WorkflowApiTest.visualizationDossierPanelIncludesRealRelations` |
| UI section + lier | OK | Vitest `DossierDetailView` / `LinkDossierDialog` ; E2E-11/12/13 |
| ServiceTask + replay | OK | `CreateChildDossierTaskIT` |
| POST 403 sans VIEW cible | OK | `DossierApiTest.createRelation_withoutViewOnTarget_returns403` |
| Régression elements/status/liste | OK | assertions dans `DossierApiTest` + E2E-1–10 |
| Suites vertes | OK | voir Verification |
| `DEFERRED-FEATURES.md` §1.4 | OK | **Livré** |

## Integration Points

- `DossierConfig` câble les deux use cases.
- `WorkflowConfig` injecte `ListDossierRelationsUseCase` dans la visualisation.
- Process de test : `create-child-dossier-smoke.bpmn20.xml`.

## Error Handling

| Cas | Exception | HTTP |
|---|---|---|
| Auto-relation / type manquant | `IllegalArgumentException` | 400 |
| ACL intra-tenant | `AccessDeniedException` | 403 |
| Absent / autre tenant | `DossierNotFoundException` | 404 |
| Doublon unique | `DuplicateDossierRelationException` | 409 |

## Security Notes

- Tenant toujours `TenantProperties.id()` sur le POST.
- VIEW cible obligatoire à la création (D-CREATE-ACL).
- Liste : relation omise si l'autre dossier n'est pas VIEW-accessible.
- Isolation inter-tenant : schéma dédié (décision n° 13), même patron que `GetDossierUseCaseImpl`.
- `relatedDossier` n'expose jamais `fields`.

## Follow-ups

- Affiner `canLink` avec le grant EDIT du type (écart #4) si l'API expose un résumé de permission.
- Jacoco / couverture ciblée : **faite Phase 8** (204/204 lignes classes relations ; FE lines 88.32 %).
- Consommer `GET /api/workflow-links/{id}/dossier` seulement si un second client UI apparaît.
- Mapper `DataIntegrityViolationException` plus finement (FK vs unique) pour éviter un 409 « duplicate » sur une cible disparue entre check et insert.
- Déplacer `create-child-dossier-smoke.bpmn20.xml` hors de `src/main/resources/processes` (même dette que `smoke-process.bpmn20.xml`).

## Phase 9 — Code review (2026-08-15)

Revue holistique du diff `feature-relations-dossiers...cursor/relations-dossiers-impl-5f98` (60 fichiers).

### Design match

Hexagone respecté : records domaine sans Spring/JPA/Flowable ; ports in/out ; adaptateur JPA ;
`@Transactional` seulement sur `ChildDossierRelationConfirmer`. Contrats API et
`DossierRelationView` uniques conformes au design. Callers : `DossierController`,
`WorkflowLinkVisualizationUseCaseImpl`, `ChildDossierRelationConfirmer` /
`CreateChildDossierDelegate`. Pas de duplication de `DossierListItem`.

### Security

| Contrôle | Verdict |
|---|---|
| Auth endpoints | Chaîne existante `/api/dossiers/**` + CSRF sur POST (MockMvc `.with(csrf())`) |
| ACL serveur | EDIT source + VIEW cible ; liste omise si pas VIEW ; pas de trust UI |
| Tenant | `TenantProperties.id()` sur POST ; schéma dédié ; 404 si cible hors tenant |
| IDOR / fields | `relatedDossier` = `DossierListItem` sans `fields` ; pas de `v-html` |
| Injection | JPQL paramétré ; `RelationType` enum (pas de string SQL) |
| Course ServiceTask | UNIQUE `idempotency_key` + catch duplicate → re-lookup |
| Secrets | Aucun |

Pas de finding Critical/High.

### Findings (non bloquants)

| ID | Sévérité | Fichier | Issue | Reco |
|---|---|---|---|---|
| R1 | Basse | `DossierRelationJpaAdapter.java:27-28` | Toute `DataIntegrityViolationException` → 409 duplicate | Distinguer UK vs FK |
| R2 | Basse | `DossierDetailView.vue:44-46` | `canLink` = rôle, pas grant EDIT type | Suivi UX (écart #4) |
| R3 | Basse | `create-child-dossier-smoke.bpmn20.xml` | Process smoke déployé avec le classpath prod | Déplacer en test (dette existante) |
| R4 | Basse | `DossierController.java:155-158` | POST re-liste puis 500 si la relation créée n'est pas visible | Ne devrait pas arriver (VIEW cible déjà exigé) |
| R5 | Info | `ListDossierRelationsUseCaseImpl` | N+1 findById/type par relation | Accepté V1 (volume faible) |

### Checklist finale

- [x] Design match
- [x] Pas de trou de logique bloquant
- [x] Sécurité adressée (ACL, tenant, pas de fuite `fields`)
- [x] Points d'intégration vérifiés (config, visualisation, Flowable)
- [x] Tests couvrent les changements (unit + IT + Playwright)
- [x] Docs à jour (implementation/planning/testing/requirements/DEFERRED)
- [x] Preuve Playwright fraîche cette session
- [x] Migration additive rollback-safe (table nouvelle, pas de CASCADE)

**Verdict : prêt pour revue humaine.** Aucun blocker. Pas de retour Phase 5/8.

## Verification (Phase 9 — 2026-08-15)

| Commande | Résultat |
|---|---|
| `./mvnw test -Dskip.frontend=true -Dmaven.compiler.release=21` | exit 0, **644** tests, 0 failure |
| `cd frontend && npm run test` | exit 0, **78/78** |
| `cd frontend && npm run test:e2e` | exit 0, **15/15** |
| `npx ai-devkit@latest lint --feature relations-dossiers` | OK |

Task tracing : indisponible (`npx ai-devkit@latest task` → `unknown command 'task'`, 0.50.0).
