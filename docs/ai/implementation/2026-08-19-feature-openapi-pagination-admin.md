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
phase7: 2026-08-19
phase8: 2026-08-19
phase9: 2026-08-19
---

# Implementation — `openapi-pagination-admin`

Phase 5 livrée (T1–T15). Phase 7 (contrôle vs design/requirements) **alignée**.
Breaking JSON sur les 6 GET admin uniquement ; `GET /api/dossiers` reste
`DossierPage`.

## Fichiers clés

- Domaine : `PageQuery` / `PageResult` (`of`, `empty`, `slice`, `map`)
- Infra HTTP : `PageQueryParser`, `PageResponse`, `AdminListSorts`
- Infra persistence : `SpringPages` + adapters JPA `Pageable`
- Contrôleurs GET listes → envelope ; purge `PurgeOutboxPageResponse`
  (`content` + `counts`)
- OpenAPI : springdoc 3.1.0, `app.openapi.enabled` false,
  `OpenApiEnvironmentPostProcessor` miroir vers `springdoc.*` ; golden
  `openapi/openapi.yaml` ; `@Hidden` SSE / multipart / ingress
- FE : `page.ts` (`listAllPages` size=100), `AdminPager`, `useAdminListQuery`,
  `/admin/notification-webhooks`

## Décisions respectées

- A-PAGE-TYPE : pas de fusion `DossierPage`
- A-ACL-PAGE : types paginés après filtre VIEW (`code IN` SQL)
- A-PURGE-LIMIT/SORT : size 20/100, `createdAt,desc`, counts globaux
- A-OPENAPI-HIDE : SSE, multipart attachment, `POST /api/webhooks/**`
- A-SQL : `Pageable` en infra, `PageResult` dans le domaine
- Nested lists inchangées (arrays)

## Phase 7 — revue fichier par fichier

| Zone | Verdict | Preuve |
|---|---|---|
| `PageQuery` défauts 0/20, max 100, invalide → IAE | MATCH | `domain/shared/PageQuery.java` |
| `PageResult.of/empty/slice/map` | MATCH | `domain/shared/PageResult.java` |
| Envelope JSON `content/page/size/totalElements/totalPages` | MATCH | `PageResponse` |
| Tris `AdminListSorts` + adapters `OrderBy*` | MATCH | types `code,asc` ; templates `updatedAt,desc` ; connectors/webhooks `name,asc` ; wf `workflowKey,asc` ; purge `createdAt,desc` |
| 6 GET → `PageResponse` ; purge envelope + `counts` | MATCH | contrôleurs listes + `PurgeOutboxController` |
| ACL types VIEW puis page (pas de trous) | MATCH | `ListDossierTypesUseCaseImpl` + `JpaDossierTypeRepositoryAdapter.page` |
| `includeContent` strip après slice | MATCH | `ListDocumentTemplateUseCaseImpl` |
| Nested arrays + `GET /api/dossiers` non fusionné | MATCH | `DossierPage` parallèle ; `ConnectorDefinitionController` array |
| OpenAPI flag false, rôles admin, `@Hidden`, writeOnly webhook secret | MATCH | `SecurityConfig` BFF ; `NotificationApiDtos` ; tests enabled/disabled |
| FE catalogues `listAllPages` size=100, pagers, écran webhooks | MATCH | `page.ts`, `AdminPager`, `useAdminListQuery`, route TENANT_ADMIN |
| ArchUnit domain sans springdoc/`io.swagger` | MATCH | `ArchitectureRulesTest` |
| Playwright regex query string + `envelope()` | MATCH | `frontend/e2e/pilotage.spec.ts` |

**Pas d’écart fonctionnel bloquant.** Pas de retour Phase 3/5.

## Écarts (sévérité basse — documentés, non bloquants)

- Placeholders YAML `${app.openapi.enabled}` n’éteignent pas springdoc assez
  tôt : `springdoc.*.enabled: false` en dur +
  `OpenApiEnvironmentPostProcessor` + tests enabled qui forcent
  `springdoc.api-docs.enabled=true`. Design parlait de beans
  `@ConditionalOnProperty` ; l’EPP est le correctif réel de boot.
- `OpenApiDisabledApiTest` assert l’**absence de document** OpenAPI/Swagger,
  pas un 404 strict (un 200 SPA/fallback n’est pas un document).
- Playwright : les globs `**/api/foo` **ne matchent pas** la query string
  (`?page=`). Les collections paginées utilisent une regex `/(?:\?|$)/`.
- Les vues admin ne réécrivent pas `page` depuis la réponse (évite d’écraser
  le pager si l’API/mock renvoie `page: 0`).
- OpenAPI `writeOnly` sur `config.webhookSecret` des connecteurs : le champ
  est une map `additionalProperties` ; le masquage runtime existe
  (`********`). Hors spec OpenAPI V1 (D-SECRETS vise surtout le secret
  webhook notification).

## Follow-ups Phase 8

Clos dans cette session : `AdminListSortsTest`, `SpringPagesTest`, MockMvc
`size=101` connecteurs/webhooks/wf, IT COUNT/OFFSET + tri sur adapters.

## Preuve Phase 8 (session fraîche 2026-08-19)

| Commande | Exit | Résultat |
|---|---|---|
| `./mvnw verify -Dskip.frontend=true -Dmaven.compiler.release=21` | 0 | BUILD SUCCESS, **877** tests, Jacoco OK |
| `cd frontend && npm run test` | 0 | **129** passed (45 files) |
| `cd frontend && npm run lint` | 0 | OK |
| `cd frontend && npm run test:e2e` | 0 | **35** passed |

## Phase 9 — revue finale

**Checklist :** design match · pas de trou logique bloquant · sécu ·
intégration SPA · tests · docs.

### Findings

Aucun finding bloquant.

| Sévérité | Sujet | Recommandation |
|---|---|---|
| Low | `config.webhookSecret` connecteur = map OpenAPI, pas `writeOnly` | Masquage runtime déjà testé ; schema map hors V1 |
| Low | `EnvironmentPostProcessor` deprecated Boot 4 | Même pattern que `TenantIdEnvironmentValidator` ; follow-up Boot |
| Info | `listAllPages` s’arrête sur page vide / `totalElements` | Catalogues admin petits ; plafond API 100 |

### Sécurité

- Pas de `permitAll` **nouveau** (`/api/webhooks/**` HMAC préexistant).
- `/v3/api-docs` + `/swagger-ui/**` : `TENANT_ADMIN` ou `WORKFLOW_ADMIN`.
- Flag `app.openapi.enabled` false par défaut + EPP miroir springdoc.
- Page size max 100 (`PageQuery`).
- Secrets webhook `writeOnly` + masque `********` ; connecteurs masqués en runtime.
- SSRF webhooks : `OutboundUrlGuard` inchangé.
- ArchUnit : domain sans springdoc / `io.swagger`.

### Breaking change / rollback

- Breaking JSON **in-repo** uniquement (SPA). Callers FE + E2E mis à jour.
- Pas de changeset Liquibase : rollback = redeploy de l’artefact précédent.

### Intégration

Callers `listAll*` : `CreateDossierView`, `SupervisionView`,
`StartWorkflowDialog`. Nested arrays et `GET /api/dossiers` inchangés.

Task tracing `npx ai-devkit@latest task` : **indisponible**.
Lint feature : OK (warning worktree hors nom `feature-openapi-pagination-admin`
car branche `cursor/openapi-pagination-admin-55ec`).
