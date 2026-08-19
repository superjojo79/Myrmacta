---
phase: implementation
title: Implementation
description: Track implementation progress against the plan
status: complete
created: 2026-08-19
updated: 2026-08-19
feature: openapi-pagination-admin
phase1: 2026-08-19
planning_ref: docs/ai/planning/2026-08-19-feature-openapi-pagination-admin.md
design_ref: docs/ai/design/2026-08-19-feature-openapi-pagination-admin.md
---

# Implementation — `openapi-pagination-admin`

Phase 5 livrée (T1–T15). Breaking JSON sur les 6 GET admin uniquement ;
`GET /api/dossiers` reste `DossierPage`.

## Fichiers clés

- Domaine : `PageQuery` / `PageResult` (`of`, `empty`, `slice`, `map`)
- Infra HTTP : `PageQueryParser`, `PageResponse`, `AdminListSorts`, `SpringPages`
- Contrôleurs GET listes → envelope ; purge `PurgeOutboxPageResponse` (`content` + `counts`)
- OpenAPI : springdoc 3.1.0, `app.openapi.enabled` false, `OpenApiEnvironmentPostProcessor`
  miroir vers `springdoc.*` ; golden `openapi/openapi.yaml` ; `@Hidden` SSE / multipart / ingress
- FE : `page.ts` (`listAllPages` size=100), `AdminPager`, `useAdminListQuery`,
  `/admin/notification-webhooks`

## Décisions respectées

- A-PAGE-TYPE : pas de fusion `DossierPage`
- A-ACL-PAGE : types paginés après filtre VIEW
- A-PURGE-LIMIT/SORT : size 20/100, `createdAt,desc`, counts globaux
- A-OPENAPI-HIDE : chemins exclus de la spec
- Nested lists inchangées (arrays)

## Écarts

- Placeholders YAML `${app.openapi.enabled}` n’éteignent pas springdoc assez tôt :
  `springdoc.*.enabled: false` en dur + post-processor + tests enabled qui forcent
  `springdoc.api-docs.enabled=true`.
- Playwright : les globs `**/api/foo` **ne matchent pas** la query string
  (`?page=`). Les collections paginées utilisent une regex `/(?:\?|$)/`.
- Les vues admin ne réécrivent pas `page` depuis la réponse (évite d’écraser le pager).

## Preuve

- `./mvnw verify -Dskip.frontend=true -Dmaven.compiler.release=21` — 870 tests, Jacoco OK
- `cd frontend && npm run test` — 129
- `cd frontend && npm run lint` — OK
- `cd frontend && npm run test:e2e` — 35 (JDK local 21 : flag `maven.compiler.release=21`)
