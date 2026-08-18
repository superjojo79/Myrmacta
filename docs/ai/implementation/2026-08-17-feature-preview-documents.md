---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: completed
created: 2026-08-17
updated: 2026-08-18
feature: preview-documents
phase5_m1: 2026-08-17
phase7: 2026-08-18
phase8: 2026-08-18
phase9: 2026-08-18
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
- Écarts bloquants : aucun (détail Phase 7).

## Phase 7 — Check Implementation (2026-08-18)

Revue fichier par fichier contre design `reviewed` et requirements
`reviewed`. Lint feature : docs OK ; worktree `feature-preview-documents`
MISS attendu (branche Cloud Agent). Task tracing indisponible.

### Intent design (rappel)

Hybride HTML+PDF, D-SOURCE live-gagne, D-AUTHZ B (gate contrôleur),
D-RATE-SPLIT C (HTML 120 dédié / PDF = generate 30), wrapper CSS web-only,
pas de store, pas de changement `GenerateDocumentUseCase`.

### Fichier par fichier

| Fichier | Verdict |
|---|---|
| `PreviewDocumentUseCase` + `isLive()` | Conforme. Domaine sans Spring. |
| `PreviewDocumentUseCaseImpl` | D-SOURCE, `assertHtmlSize`, `DocumentVariableFilter`, PDF sans CSS. |
| `DocumentConfig` | Bean câblé avec `MAX_HTML_BYTES`. Generate inchangé. |
| `DocumentGenerationController` | Ordre parse → gate → limiter → use case → wrap. Message 403 exact. |
| `PrintPreviewHtml` | CSS analyse 07 ; fragment `.page` ; document → `<style>` head. |
| `DocumentGenerationBodySizeLimitFilter` | Égalités exactes generate / preview / preview/pdf. |
| `DocumentPreviewHtmlRateLimiter` + `SlidingWindowRateLimiter` | Instance séparée ; PDF/generate partagent l'autre bean. |
| `DocumentPreviewProperties` + `application.yml` | `app.document.preview.rate-limit-per-minute=120`. |
| `GenerateDocumentUseCaseImpl` | **Aucun diff** vs `main`. |
| `api/documents.ts` | `htmlContent`+`variables` seulement (UI TA). |
| `useTemplatePreview` | Debounce 400 ms, abort, revoke URL ; `requestPdf` no-op si `enabled === false`. |
| `TemplatePreviewPanel.vue` | iframe `sandbox=""` + `srcdoc` ; pas de `v-html` ; bouton PDF disabled si JSON invalide. |
| `TemplatesAdminView.vue` | Composition : prop `htmlContent` du form. CRUD inchangé. |
| Liquibase / `DocumentStoragePort` | Absents du diff preview. |

### Coverage requirements

Tous les success criteria ont un artefact : MockMvc (`DocumentPreviewApiTest`
13 cas), unitaires use case (11), filtre, `PrintPreviewHtml`, Vitest panneau
/ composable (PDF gated), Playwright E2E-20 + E2E-21.

### Écarts

| Sévérité | Écart | Reco |
|---|---|---|
| — | ~~Bouton « Aperçu PDF » n'est pas gated par `parseError`~~ | **Corrigé Phase 8** : `isEnabled()` dans `requestPdf` + bouton `:disabled`. Vitest + E2E-21. |
| Basse | Message 429 HTML réutilise « Document generation rate limit exceeded ». | Cosmétique. |
| Info | Filtre 413 ignore le body chunked sans `Content-Length` — **préexistant** generate, documenté sur le filtre. | Inchangé. |

Aucun écart bloquant. Aucune déviation d'architecture (hexagone, rôles au
web, Thymeleaf/FS hors domain).

### Suites

**Phase 8 close (2026-08-18).** **Phase 9 close (2026-08-18) — READY.**
Aucun bloquant. Suivi produit hors V1 : preview détail dossier.

## Phase 9 — Revue de code finale (holistique)

**Méthode :** lint `--feature preview-documents` (docs OK ; git worktree
`feature-preview-documents` MISS attendu). Diff `origin/main...HEAD`
(36 fichiers, +2557/−39). Relecture des fichiers de production (pas
seulement le diff), traçage des appelants, comparaison avec
`GenerateDocumentUseCaseImpl` / `POST /generate`, grep `v-html` / `TODO` /
`FIXME` / `System.out` (aucun dans le code preview). Task tracing
indisponible (`ai-devkit task` unknown command). Branche **0** commit
derrière `origin/main`.

**Alignement design :** D-SOURCE (`isLive()` gagne), D-AUTHZ B (gate
contrôleur `ROLE_TENANT_ADMIN` **avant** limiter/use case), D-RATE-SPLIT C
(limiter HTML **autre** `ConcurrentHashMap` ; PDF partage generate),
D-CSS-WEB (`PrintPreviewHtml` hors domaine), D-IFRAME (`sandbox=""` +
`srcdoc`, pas de `v-html`), D-PDF-UX (bouton). `GenerateDocumentUseCaseImpl`
**aucun diff** vs `main`. Pas de Liquibase, pas de nouvelle dépendance
Maven/npm, pas de store.

**Appelants :** `PreviewDocumentUseCase` → bean `DocumentConfig` →
uniquement `DocumentGenerationController`. `PrintPreviewHtml` package-private
(contrôleur + tests). `TemplatePreviewPanel` → seul
`TemplatesAdminView`. `useTemplatePreview` → seul le panneau.

### Findings (par sévérité)

Aucun **Critical** / **High** / **Medium**. Aucune chaîne d'attaque.

| Sévérité | Fichier | Issue | Reco |
|---|---|---|---|
| Basse | `DocumentGenerationRateLimitExceededException` | 429 HTML réutilise le libellé generate | Cosmétique ; hors V1 |
| Info | `DocumentGenerationBodySizeLimitFilter` | 413 ignore body chunked sans `Content-Length` | Préexistant generate ; CSRF + session |
| Basse | `TemplatePreviewPanel.vue` iframe PDF | Pas de `sandbox` sur l'iframe blob PDF | Design : sandbox HTML seulement ; `RestrictedResourceUserAgent` côté rendu |
| Basse | `useTemplatePreview.requestPdf` | Double-clic concurrent possible (course object URL) | Nice-to-have : ignorer si `loading` |
| Basse | `toProblemDetail` + `responseType: 'blob'` | 4xx PDF peu lisible (body blob) | Même limite que download pièces jointes |

**Faux positifs :** HTML live Thymeleaf ≠ XSS SPA — auteur = TA (déjà
autorisé à enregistrer des templates) ; iframe `sandbox=""` ; CSRF header
axios (`configureHttpClient`). SSRF PDF bloqué par
`RestrictedResourceUserAgent` (`data:` only) — réutilisé, pas affaibli.

### Sécurité (checklist)

- Secrets : aucun.
- Authz : `@PreAuthorize` TA\|WA\|DM + gate live TA ; VIEWER 403 ;
  anonyme 401 (MockMvc). CSRF BFF inchangé.
- Injection / XSS : pas de `v-html` ; HTML dans `srcdoc` sandboxé.
- SSRF : PDF via user-agent restreint existant.
- Exhaustion : filtre 256 Kio ; HTML 120/min dédié ; PDF 30/min partagé.
- Logging : pas de log `htmlContent` / `variables`.
- Rollback : revert git, aucun état persistant.

### Checklist finale

- [x] Correspondance design (D-* ci-dessus)
- [x] Aucun trou de logique bloquant
- [x] Sécurité traitée (authz B, CSRF, sandbox, SSRF PDF, quotas)
- [x] Intégrations : generate inchangé ; filtre étendu par égalité exacte
- [x] Tests : preuves fraîches Phase 9 (ci-dessous)
- [x] Docs lifecycle + `DEFERRED-FEATURES.md` (`preview-documents` Complete)

**Preuves Phase 9 (2026-08-18, cette session) :**

- `cd frontend && npm run test` — **94** passed, exit 0
- `cd frontend && npm run build && npm run test:e2e` — **22** passed
  (E2E-20 + E2E-21), exit 0
- `./mvnw verify -Dmaven.compiler.release=21` — **762** tests, Failures 0,
  Jacoco met, BUILD SUCCESS

**Verdict :** READY. PR à marquer prête pour revue humaine.
