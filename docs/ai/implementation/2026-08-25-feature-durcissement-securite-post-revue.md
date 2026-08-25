---
phase: implementation
title: Implementation Guide
description: Technical implementation notes
status: in-progress
created: 2026-08-25
updated: 2026-08-25
feature: durcissement-securite-post-revue
---

# Implementation Guide — `durcissement-securite-post-revue`

## Phase 5 — en cours (M1–M6 code+docs, M7 preuve)

TDD : tests processor B2 d'abord (rouge sur l'ébauche A/B1 : `#dates` /
`#vars` rejetés, `${title.class}` encore évalué via `Object.getClass`), puis
évaluateur + checker.

## Changed files

### SSTI (M1)

- `SafeThymeleafExpressionSupport` — parser allowlist B2, extraction `${}` / `*{}`
- `SafeThymeleafVariableExpressionEvaluator` — délégation
- `SafeHtmlTemplateEngineFactory` — `TemplateEngine` + `StandardDialect` sans SpEL
- `ThymeleafHtmlTemplateProcessor` / `ThymeleafTemplateSyntaxChecker` — même factory ;
  checker `parseString` + allowlist **sans** `process()`
- Tests : `ThymeleafHtmlTemplateProcessorTest`, `ThymeleafTemplateSyntaxCheckerTest`,
  `DocumentPreviewApiTest` (400 `T()` live), `DocumentTemplateApiTest` (400 à
  l'**activation** — le checker métier existant, pas au POST draft)

### Pin (M2)

- `OutboundUrlGuard.PinnedEndpoint` / `resolveSafe` (1 lookup)
- `WebhookRestAdapter` — URI IP + `Host` + TLS hostname d'origine + redirects NEVER
- `HttpRestServiceConnector` — URI pinnée, non `@Component`
- Tests guard / webhook / connector pin ; admin `assertSafe` déjà
  `NotificationWebhookAdminApiTest.create_ssrfUrl_returns400`

### Actuator / secrets / GRANT (M3–M5)

- `application.yml` `management.server.address` / `MANAGEMENT_PORT:8090` /
  `DB_USERNAME:flowforge_tenant_dev`
- `application-local.yml` + `SecretsEnvironmentPostProcessor` (denylist L, HMAC)
- `docker/postgres/init/01-init-tenant-schema.sql`, `docker-compose.yml`, `.env.example`
- `TenantRoleIsolationIT` (SKIPPED sans Docker)

### Docs (M6)

- `GETTING-STARTED.md`, `analysis doc/02` / `08` / `09`, TODO §40–44,
  note port 8090 dans le design observabilite

## Design Deviations

Aucune. B2 livré (pas l'ébauche regex `${ident.prop}` seule).

## Edge cases

- Checker : `${dossier.reference}` passe sans variables métier
- Denylist : trim + casse ignorée
- Volume PG existant : `compose down -v`
- `HttpURLConnection` côté webhook (JDK `HttpClient` refuse `Host`)
