---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: completed
created: 2026-08-17
updated: 2026-08-17
feature: preview-documents
phase5_m1: 2026-08-17
---

# Implementation Guide — `preview-documents`

## Development Setup

Même stack que `generation-documents` / admin templates. Branche de session
`cursor/preview-documents-dbc9` (pas de worktree séparé — préfixe Cloud Agent).

```bash
./mvnw test -Dtest=PreviewDocumentUseCaseImplTest
cd frontend && npm run test
cd frontend && npm run test:e2e
```

## Code Structure

Fichiers prévus (créés au fil des tâches) :

**Backend**

- `domain/document/port/in/PreviewDocumentUseCase.java`
- `domain/document/usecase/PreviewDocumentUseCaseImpl.java`
- `infrastructure/config/DocumentConfig.java` (bean use case)
- `infrastructure/config/DocumentPreviewProperties.java` (rate-limit HTML 120)
- `infrastructure/web/document/PrintPreviewHtml.java`
- `infrastructure/web/document/DocumentGenerationController.java` (étendu : gate + 2 POST)
- `infrastructure/web/document/DocumentGenerationBodySizeLimitFilter.java` (matcher)
- Limiter HTML dédié (2ᵉ instance du même algorithme que generate)

**Frontend**

- `frontend/src/api/documents.ts`
- `frontend/src/composables/useTemplatePreview.ts`
- `frontend/src/components/document/TemplatePreviewPanel.vue`
- `frontend/src/views/admin/TemplatesAdminView.vue`
- `frontend/e2e/pilotage.spec.ts`

## Implementation Notes

### Core Features

- **D-SOURCE :** `PreviewCommand.isLive()` court-circuite resolve.
- **D-AUTHZ B :** gate contrôleur si `isLive()` et pas `ROLE_TENANT_ADMIN`
  → Spring `AccessDeniedException` **avant** limiter et use case.
- **Taille HTML live :** `DocumentTemplateSupport.assertHtmlSize` +
  `DocumentConfig.MAX_HTML_BYTES` (5 Mio). Plafond HTTP réel = filtre 256 Kio.
- **PDF :** `pdfRenderer.render(processedHtml)` — **pas** le HTML wrappé CSS.
- **HTML HTTP :** `PrintPreviewHtml.wrap(processedHtml)` puis
  `text/html;charset=UTF-8`.
- **D-RATE-SPLIT C :** `/preview` → limiter HTML 120/min (**autre
  instance** / autre `ConcurrentHashMap`) ; `/preview/pdf` → limiter
  generate 30/min (partagé avec `/generate`). Ne pas réutiliser le
  `@Component` generate pour l'HTML : `tryConsume` est keyé uniquement
  par principal.
- **FE :** pas de `v-html` ; iframe `sandbox=""` + `srcdoc`.
- **Debounce :** 400 ms ; `AbortController` sur le fetch HTML. PDF on-demand.
- **Variables JSON :** parse local ; JSON invalide → message, pas d'HTTP.

### Patterns

- Use case `*Impl` dans `usecase/`, interface dans `port/in/` (décision n° 9).
- Domaine pur : zéro Spring/JPA/Thymeleaf.
- Composition API `<script setup lang="ts">` ; vue admin = surface de
  composition ; logique async dans le composable.

## Integration Points

- `HtmlTemplateProcessor` / `PdfRenderer` / `ResolveDocumentTemplateUseCase`
  existants.
- `DocumentGenerationExceptionHandler` déjà `assignableTypes =
  DocumentGenerationController` — couvre les nouveaux mappings.
- Filtre taille enregistré dans `SecurityConfig` — même bean, matcher élargi.

## Error Handling

| Cas | Exception | HTTP |
|---|---|---|
| Source manquante / HTML trop gros | `TemplateValidationException` | 400 |
| Thymeleaf | `TemplateProcessingException` | 400 |
| Render PDF | `DocumentRenderException` | 400 |
| Template key inconnu | `TemplateNotFoundException` | 404 |
| Rate limit | `DocumentGenerationRateLimitExceededException` | 429 |
| Body trop gros | filtre | 413 |

## Performance Considerations

HTML = Thymeleaf only. PDF on-demand. Debounce + abort. Pas de store.

## Security Notes

- `@PreAuthorize` identique generate, **plus** gate live TA (D-AUTHZ B).
- CSRF BFF.
- Iframe sandbox (pas de scripts).
- SSRF PDF : `RestrictedResourceUserAgent` inchangé.
- 403 live : `MethodSecurityExceptionHandler` (Spring `AccessDeniedException`).
- Pas de secrets dans les templates preview (même surface admin existante).

## Implementation log

### T1–T3 (M1)

- `PreviewDocumentUseCase` + `PreviewCommand.isLive()` (trim/`isBlank`).
- `PreviewDocumentUseCaseImpl` : D-SOURCE, `assertHtmlSize`,
  `DocumentVariableFilter`, `resolveHtml` partagé. Aucun rôle.
- Bean `previewDocumentUseCase` dans `DocumentConfig` (`MAX_HTML_BYTES`).
- Tests : `PreviewDocumentUseCaseImplTest` (11 cas).
- Écart design : aucun.

### T4–T8 (M2)

- `PrintPreviewHtml` : fragment → `.page` ; document complet → `<style>`
  dans `<head>` (créé au besoin).
- Filtre taille : égalités exactes generate / preview / preview/pdf.
- `SlidingWindowRateLimiter` extrait ; `DocumentPreviewHtmlRateLimiter`
  instance séparée ; `app.document.preview.rate-limit-per-minute=120`.
- Gate live TA avant limiter ; 403 Spring `AccessDeniedException`.
- `POST /preview` (`text/html`) et `/preview/pdf` (`inline; filename="preview.pdf"`).

### T9–T13 (M3–M4)

- `api/documents.ts`, `useTemplatePreview` (400 ms, abort, revoke URL),
  `TemplatePreviewPanel` (iframe `sandbox=""`, JSON local),
  `TemplatesAdminView` composition.
- Playwright E2E-20 ; mocks `/preview/pdf` avant `/preview` dans
  `mockSession`. `npm run build` requis avant `vite preview` (port 4173).

### T14

- Docs cochés ; D-PREVIEW marqué livré dans `DEFERRED-FEATURES.md`.
- Écarts : aucun. Hors scope respecté.

## Phase 7 / 9 — alignment

- Design D-SOURCE / D-AUTHZ B / D-RATE-SPLIT C / D-CSS-WEB respectés.
- `GenerateDocumentUseCase` et `POST /generate` inchangés (Disposition non inline).
- Pas de Liquibase, pas de store, pas de `v-html`.
- Finding bloquant : aucun. Suivi non bloquant : preview depuis le détail
  dossier (hors V1).
