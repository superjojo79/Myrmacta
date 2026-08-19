---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: ready
created: 2026-08-19
updated: 2026-08-19
feature: openapi-pagination-admin
phase1: 2026-08-19
phase2_sync: 2026-08-19
phase3_sync: 2026-08-19
phase4: 2026-08-19
stakeholder_decisions: 2026-08-19
---

# Project Planning & Task Breakdown — `openapi-pagination-admin`

> Phase 4 **ready** — figé contre requirements/design/testing reviewed.
> TDD : test rouge → code → preuve ciblée ; `./mvnw verify` + Vitest +
> Playwright avant claim T14/T15.
> Task tracing `ai-devkit task` : **indisponible**.

## Milestones

- [ ] **M1** — Socle page + parser + envelope HTTP (T1–T3)
- [ ] **M2** — Six listes admin paginées API (T4–T9)
- [ ] **M3** — springdoc + YAML + CI diff + sécu flag/rôles (T12–T13)
- [ ] **M4** — SPA pagers + catalogues + écran webhooks + E2E (T7b, T10, T11, T14)
- [ ] **M5** — Docs produit TODO/DEFERRED/FILE-ATTENTE (T15)

## Task Breakdown

### T1 — `PageResult` / `PageQuery` domaine
- **Status :** todo
- **Outcome :** `com.flowforge.domain.shared.PageResult<T>` + `PageQuery`
  (MAX_SIZE=100, MAX_PAGE=MAX_INT/100) ; `of` / `empty` / `slice` ; pas Spring.
- **Deps :** aucune
- **Validation :** `./mvnw test -Dtest=PageResultTest,PageQueryTest`
- **Tests :** totaux/pages, content immuable, totalElements&lt;0, slice offset

### T2 — `PageQueryParser` infra
- **Status :** todo
- **Outcome :** parse `page`/`size`/`sort` allow-list ; défauts 0/20 ; 400 via
  `IllegalArgumentException` existant. `DossierListQueryParser` réutilise
  MAX_* (refactor mineur OK).
- **Deps :** T1
- **Validation :** `./mvnw test -Dtest=PageQueryParserTest`
- **Tests :** défauts, bornes, sort exact vs autre

### T3 — DTO `PageResponse<T>`
- **Status :** todo
- **Outcome :** record infra `{ content, page, size, totalElements, totalPages }`
  + `from(PageResult)` ; JSON identique `DossierPageResponse`.
- **Deps :** T1
- **Validation :** `./mvnw test -Dtest=PageResponseTest`
- **Tests :** mapping Jackson champs envelope

### T4 — Paginer `GET /api/dossier-types`
- **Status :** todo
- **Outcome :** `list(tenant, ctx, PageQuery)` → `PageResult` **après** ACL
  VIEW (A-ACL-PAGE) ; `code,asc` ; MockMvc envelope ; client FE `Page<T>`.
- **Deps :** T2, T3
- **Validation :** `./mvnw test -Dtest=ListDossierTypesUseCaseImplTest,DossierApiTest`
- **Tests :** admin all, user VIEW, zéro grant vide, page 1, sort 400,
  envelope MockMvc ; pas de régression `GET /{code}`

### T5 — Paginer `GET /api/document-templates`
- **Status :** todo
- **Outcome :** `updatedAt,desc` + `templateKey` + `includeContent` (HTML
  page courante seulement).
- **Deps :** T2, T3
- **Validation :** `./mvnw test -Dtest=ListDocumentTemplateUseCaseImplTest,*DocumentTemplate*Test`
- **Tests :** filtre avant COUNT, includeContent false strip, envelope

### T6 — Paginer `GET /api/connectors`
- **Status :** todo
- **Outcome :** `name,asc` ; soft-deleted exclus ; masque secrets.
- **Deps :** T2, T3
- **Validation :** `./mvnw test -Dtest=ConnectorAdminApiTest`
- **Tests :** list envelope `content` ; restore/soft-delete toujours hors liste

### T7 — Paginer `GET /api/workflow-type-configs`
- **Status :** todo
- **Outcome :** `workflowKey,asc` ; GET liste OIDC inchangé.
- **Deps :** T2, T3
- **Validation :** tests MockMvc workflow-type-configs existants + envelope
- **Tests :** envelope, sort 400

### T7b — Helper FE `listAllPages`
- **Status :** todo
- **Outcome :** `frontend/src/api/page.ts` ; `size=100` ; stop page vide ;
  `listAllDossierTypes` / `listAllWorkflowTypeConfigs`.
- **Deps :** type `Page<T>`
- **Validation :** `cd frontend && npm run test -- src/api/page.spec.ts`
- **Tests :** 2 pages concaténées ; stop page vide

### T8 — Paginer `GET /api/notification-webhooks`
- **Status :** todo
- **Outcome :** `name,asc` ; secret masqué ; soft-deleted exclus.
- **Deps :** T2, T3
- **Validation :** MockMvc notification webhook list tests
- **Tests :** envelope + mask

### T9 — Paginer `GET /api/purge-outbox`
- **Status :** todo
- **Outcome :** envelope + `counts` globaux (4 statuts, ignore `status` et
  page) ; `createdAt,desc` ; size 20/100 (plus limit 50/200).
- **Deps :** T2, T3
- **Validation :** `./mvnw test -Dtest=ListPurgeOutboxUseCaseImplTest,*Purge*`
- **Tests :** counts vs filtre, tri desc, size 101 → 400

### T10 — Pager UI + sync URL
- **Status :** todo
- **Outcome :** `AdminPager` + `useAdminListQuery` (`page`, `size` si ≠ 20)
  sur types, templates, connectors, workflow-types, purge, webhooks.
- **Deps :** clients T4–T9
- **Validation :** Vitest vues admin
- **Tests :** pager bornes, `router.replace` `?page=`

### T11 — Écran `/admin/notification-webhooks`
- **Status :** todo
- **Outcome :** route, nav TENANT_ADMIN, create/toggle/delete, secret
  write-only, pager (D-WEBHOOK-UX A).
- **Deps :** T8, T10
- **Validation :** Vitest vue + client
- **Tests :** create, ProblemDetail, pas d’affichage secret

### T12 — springdoc + `app.openapi.enabled`
- **Status :** todo
- **Outcome :** `springdoc-openapi-starter-webmvc-ui` **3.1.0** (Boot 4.1) ;
  défaut flag false ; rôles admin si on ; `@Hidden` SSE / multipart
  attachment / webhook ingress ; secrets writeOnly.
- **Deps :** T4–T9 schémas stables
- **Validation :** MockMvc OpenAPI security tests
- **Tests :** enabled=false → pas 200 docs ; enabled=true manager → 403 ;
  admin → 200 ; operations hidden absentes

### T13 — `openapi/openapi.yaml` + fail-on-diff
- **Status :** todo
- **Outcome :** golden repo ; `OpenApiContractIT` diff ; CI couvert par
  `./mvnw verify` ; `openapi/README.md` régénération.
- **Deps :** T12
- **Validation :** `./mvnw test -Dtest=OpenApiContractIT`
- **Tests :** YAML identique (LF)

### T14 — Playwright + Vitest complets
- **Status :** todo
- **Outcome :** mocks envelope 6 GET ; webhooks admin E2E ; pager URL ;
  catalogues CreateDossier/Supervision.
- **Deps :** T7b, T10, T11
- **Validation :** `cd frontend && npm run test` && `npm run test:e2e`
- **Tests :** E2E-PURGE adaptés `content`+`counts` ; nav webhooks

### T15 — Docs produit
- **Status :** todo
- **Outcome :** TODO §24 reste (codegen B/C, nested, SSE spec) noté partiel
  clos pour OpenAPI+pagination admin ; DEFERRED follow-ups ; FILE-ATTENTE
  prochain = `observabilite`.
- **Deps :** T14 + `./mvnw verify`
- **Validation :** grep FILE-ATTENTE / TODO §24

## Mapping scénarios testing → tâches

| Scénario testing | Tâches |
|---|---|
| PageQueryParser bornes/sort | T2 |
| PageResult totaux | T1 |
| Use case offset / filtres / ACL types | T4–T9 |
| Counts purge globaux + tri desc | T9 |
| Masquage secrets | T6, T8, T12 |
| OpenApiProperties / 403 | T12 |
| Clients axios envelope | T4–T9 FE |
| listAllPages | T7b |
| Vues pager + URL | T10 |
| Webhooks admin vue | T11 |
| MockMvc 6 listes + connector-definitions array | T4–T9 |
| GET dossiers inchangé | T4 (régression) |
| OpenAPI contract diff | T13 |
| Playwright mocks + purge + webhooks | T14 |

Tout scénario du testing doc a ≥ 1 tâche.

## Dependencies

```text
T1 → T2 → T3 → T4..T9
T4 / T7 → T7b
T4..T9 → T10 → T11
T4..T9 → T12 → T13
T7b + T10 + T11 → T14 → T15
```

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| springdoc 3.1.0 × Boot 4.1.0 | Spike T12 immédiat ; fallback `webmvc-api` seul |
| Oubli `page.route` array | Grep E2E des 6 paths |
| Pages types trouées | Tests ACL avant OFFSET (T4) |
| Golden OpenAPI instable | Normaliser LF ; pas de timestamps |
| Secret dans spec | Assert absence motifs dans YAML |

## Resources Needed

- MockMvc admin existants, Jacoco, job CI `frontend` (verify inclut IT contrat).
- Index `idx_purge_outbox_status_created_at`.
