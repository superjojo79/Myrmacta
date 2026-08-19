---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: openapi-pagination-admin
phase1: 2026-08-19
stakeholder_decisions: 2026-08-19
---

# Project Planning & Task Breakdown — `openapi-pagination-admin`

> Découpage initial Phase 1 (sera figé en Phase 4 après revue
> requirements/design). TDD : test rouge → code → preuve `verify` / Vitest /
> Playwright.

## Milestones

- [ ] M1 — Socle page + parser + envelope HTTP
- [ ] M2 — Six listes admin paginées (API)
- [ ] M3 — springdoc + YAML + CI diff + sécu flag/rôles
- [ ] M4 — SPA pagers + URL + écran webhooks + E2E
- [ ] M5 — Docs TODO/DEFERRED/FILE-ATTENTE + Phase 7–9

## Task Breakdown

### T1 — `PageResult` / `PageQuery` domaine
- **Outcome :** records pur Java + tests unitaires totaux/pages.
- **Deps :** aucune.

### T2 — `PageQueryParser` infra
- **Outcome :** page/size/sort allow-list ; 400 via handlers existants ;
  réutiliser bornes `SearchDossiersQuery`.
- **Deps :** T1.

### T3 — DTO `PageResponse<T>`
- **Outcome :** JSON envelope unique ; tests Jackson/`MockMvc` minimal.
- **Deps :** T1.

### T4 — Paginer `GET /api/dossier-types`
- **Outcome :** use case + adapter SQL `code ASC` ; MockMvc envelope ;
  FE client + vue + E2E mock.
- **Deps :** T2, T3.

### T5 — Paginer `GET /api/document-templates`
- **Outcome :** `updatedAt DESC` + `templateKey` + `includeContent` ;
  MockMvc + FE `admin.ts` + vue.
- **Deps :** T2, T3.

### T6 — Paginer `GET /api/connectors`
- **Outcome :** `name ASC` ; masque secrets inchangé ; FE Connecteurs.
- **Deps :** T2, T3.

### T7 — Paginer `GET /api/workflow-type-configs`
- **Outcome :** `workflowKey ASC` ; FE configs.
- **Deps :** T2, T3.

### T8 — Paginer `GET /api/notification-webhooks`
- **Outcome :** `name ASC` ; masque secret ; API seulement d’abord.
- **Deps :** T2, T3.

### T9 — Paginer `GET /api/purge-outbox`
- **Outcome :** envelope + `counts` globaux ; `createdAt DESC` ; plus `limit`/`items` ;
  FE purge + E2E-PURGE.
- **Deps :** T2, T3.

### T10 — Pager UI partagé + sync URL
- **Outcome :** composant/composable ; branché sur les 5 vues existantes
  (T4–T7, T9).
- **Deps :** clients T4–T7, T9.

### T11 — Écran `/admin/notification-webhooks`
- **Outcome :** route, nav TENANT_ADMIN, create/toggle/delete, secret write-only,
  pager + URL (D-WEBHOOK-UX A).
- **Deps :** T8, T10.

### T12 — springdoc + `app.openapi.enabled`
- **Outcome :** dépendance Boot 4.1 ; routes conditionnelles ; 403 non-admin ;
  annotations writeOnly secrets ; exclusion SSE/multipart/ingress.
- **Deps :** listes T4–T9 stables (schémas).

### T13 — `openapi/openapi.yaml` + IT/CI fail-on-diff
- **Outcome :** golden file ; commande régénération ; step CI.
- **Deps :** T12.

### T14 — Playwright + Vitest complets
- **Outcome :** mocks envelope partout ; scénario webhooks ; pager URL ;
  `npm run test` + `test:e2e` verts.
- **Deps :** T10, T11.

### T15 — Docs produit
- **Outcome :** TODO §24 partiel/clos selon livré ; DEFERRED follow-ups B/C
  codegen ; FILE-ATTENTE prochain = `observabilite`.
- **Deps :** T14 + `./mvnw verify`.

## Dependencies

```text
T1 → T2 → T3 → T4..T9 (parallélisables)
T4..T9 → T10 → T11
T4..T9 → T12 → T13
T10, T11 → T14 → T15
```

## Timeline & Estimates

Pas d’estimations calendaires. Lot transverse API+SPA+CI ; risque principal =
mocks E2E et drift OpenAPI.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| springdoc × Boot 4.1 incompatible | Pin version dès T12 ; spike test context |
| Oubli d’un `page.route` array | Grep E2E `JSON.stringify([` sur les 6 paths |
| `counts` purge mal compris | Tests : counts globaux vs page filtrée |
| Swagger exposé en prod | Défaut `enabled=false` + test négatif |
| Secret dans spec/exemples | Review D-SECRETS + test absence de motifs |

## Resources Needed

- Suite existante MockMvc admin, Jacoco, job CI `frontend`.
- Index purge déjà livré (`011-purge-outbox-worker.yaml`).
