---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: draft
created: 2026-08-17
updated: 2026-08-17
feature: preview-documents
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
- `infrastructure/config/DocumentConfig.java` (bean)
- `infrastructure/web/document/PrintPreviewHtml.java`
- `infrastructure/web/document/DocumentGenerationController.java` (étendu)
- `infrastructure/web/document/DocumentGenerationBodySizeLimitFilter.java` (matcher)

**Frontend**

- `frontend/src/api/documents.ts`
- `frontend/src/composables/useTemplatePreview.ts`
- `frontend/src/components/document/TemplatePreviewPanel.vue`
- `frontend/src/views/admin/TemplatesAdminView.vue`
- `frontend/e2e/pilotage.spec.ts`

## Implementation Notes

### Core Features

- **D-SOURCE :** `htmlContent` non blank (après trim) court-circuite resolve.
- **Taille HTML live :** `DocumentTemplateSupport.assertHtmlSize` +
  `DocumentConfig.MAX_HTML_BYTES` (5 Mio), identique create/update template.
- **PDF :** `pdfRenderer.render(processedHtml)` — **pas** le HTML wrappé CSS.
- **HTML HTTP :** `PrintPreviewHtml.wrap(processedHtml)` puis
  `text/html;charset=UTF-8`.
- **Rate limit :** `rateLimiter.tryConsume` sur les deux nouveaux POST,
  même quota que generate (fenêtre partagée par principal).
- **FE :** pas de `v-html` ; iframe `sandbox` (attribut vide) + `srcdoc`.
- **Debounce :** 400 ms dans le composable, `AbortController` sur le fetch
  HTML. PDF uniquement via action explicite.
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

- `@PreAuthorize` identique generate.
- CSRF BFF.
- Iframe sandbox (pas de scripts).
- SSRF PDF : `RestrictedResourceUserAgent` inchangé.
- Pas de secrets dans les templates preview (même surface admin existante).
