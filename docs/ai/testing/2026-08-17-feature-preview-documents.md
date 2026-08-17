---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: reviewed
created: 2026-08-17
updated: 2026-08-17
feature: preview-documents
---

# Testing Strategy — `preview-documents`

## Test Coverage Goals

- Unit/domain : `PreviewDocumentUseCaseImpl` (live vs resolve, filtre
  variables, erreurs, taille HTML).
- Intégration MockMvc : les 2 endpoints, authz, Content-Type / Disposition,
  non-persistance, 400/404.
- Filtre taille : unitaires sur matcher preview.
- Frontend : composable debounce (fake timers), panneau iframe sandbox,
  vue admin câblée.
- E2E Playwright : parcours admin templates, mocks des nouveaux POST.
- Aligné requirements success criteria et design D-*.

## Unit Tests

### `PreviewDocumentUseCaseImpl`

- [x] `htmlContent` live → process sans appeler resolve ; HTML évalué retourné
- [x] `htmlContent` live + `templateKey` → resolve **non** appelé (live gagne)
- [x] `templateKey` seul → resolve + process `template.htmlContent()`
- [x] les deux blank → `TemplateValidationException`
- [x] template manquant → `TemplateNotFoundException` propagée
- [x] `previewPdf` appelle `pdfRenderer.render` avec l'HTML évalué (pas le wrapper CSS)
- [x] clés plateforme (`dossierId`, …) absentes du map passé au processor
- [x] `variables` null → process avec map vide
- [x] `htmlContent` au-delà de `MAX_HTML_BYTES` → `TemplateValidationException`
- [x] `htmlContent` blank (espaces) + `templateKey` valide → chemin resolve

### `PrintPreviewHtml`

- [x] fragment → document avec CSS analyse 07 et wrapper `.page`
- [x] document complet (`<html>`) → `<style>` injecté, contenu conservé

### `DocumentGenerationBodySizeLimitFilter`

- [x] POST `/api/documents/preview` oversized → 413
- [x] POST `/api/documents/preview/pdf` oversized → 413
- [x] POST `/api/documents/generate` oversized → 413 (non-régression)
- [x] autre path oversized → chain continues

## Integration Tests

Étendre `DocumentGenerationApiTest` (ou classe sœur `DocumentPreviewApiTest`) :

- [x] POST `/preview` live `htmlContent` + variable → 200 `text/html` ; body
      contient le texte mergé **et** le CSS print-preview ; `countStoredFiles`
      inchangé
- [x] POST `/preview` `templateKey` ACTIVE → 200 ; contenu issu du template
- [x] POST `/preview/pdf` → 200 PDF `%PDF` ; `Content-Disposition` contient
      `inline` ; pas de store
- [x] POST `/preview` template inconnu → 404
- [x] POST `/preview` body vide → 400
- [x] `@PreAuthorize` : VIEWER → 403 ; anonyme → 401 (un des deux endpoints)
- [x] D-AUTHZ B live : TA + `htmlContent` → 200 ; WA + `htmlContent` → 403 ;
      DM + `htmlContent` → 403 (aucun process / store)
- [x] D-AUTHZ B live gagne : DM + `htmlContent` **et** `templateKey` → 403
- [x] D-AUTHZ B publié : TA / WA / DM + `templateKey` seul → 200
- [x] Thymeleaf cassé → 400
- [x] D-RATE-SPLIT HTML : `@TestPropertySource`
      `app.document.preview.rate-limit-per-minute=2` — 2 POST `/preview`
      OK puis 429 ; un `/generate` intercalé **ne** consomme **pas** ce
      quota HTML. Ne pas envoyer 120 POST en CI.
- [x] D-RATE-SPLIT PDF partagé : `@TestPropertySource`
      `app.document.generate.rate-limit-per-minute=2` — mélange
      `/preview/pdf` + `/generate` (même principal) puis 429 sur l'un
      ou l'autre. Ne pas envoyer 30 POST en CI.
- [x] generate existant toujours 200 PDF **sans** `inline` obligatoire
      (non-régression Content-Disposition)

## End-to-End Tests

- [x] E2E admin templates : ouvrir `/admin/templates`, saisir HTML dans le
      formulaire, attendre l'aperçu (mock `POST **/api/documents/preview**`
      → HTML), iframe visible
- [x] E2E bouton « Aperçu PDF » mocke `POST **/api/documents/preview/pdf**`
      (blob) — le bouton est cliqué, pas d'erreur UI
- [x] Mocks globaux `mockSession` : les nouveaux paths ne tombent pas dans un
      catch-all qui casserait les specs existantes
- [x] Specs E2E existantes toujours vertes

## Test Data

- HTML live : `<p th:text="${title}">x</p>` + `variables.title=Hello`
- Template ACTIVE créé via le même helper `createActiveTemplate()` que
  generate
- Playwright : `page.route('**/api/documents/preview/pdf**')` **avant**
  `**/api/documents/preview**` (préfixe) pour ne pas avaler le PDF
- MockMvc rate-limit : `@TestPropertySource` avec limites = 2 (les
  défauts 120 / 30 restent la config runtime, pas l'objectif CI)

## Test Reporting & Coverage

```bash
./mvnw test -Dtest=PreviewDocumentUseCaseImplTest,PrintPreviewHtmlTest,DocumentGenerationBodySizeLimitFilterTest,DocumentPreviewApiTest,DocumentGenerationApiTest
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test
cd frontend && npm run test:e2e
```

Seuils : Jacoco projet (≥ 80 %) ; Vitest lines ≥ 80 % ; Playwright 0 fail.

## Session evidence (Phase 8)

- `./mvnw test -Dskip.frontend=true -Dmaven.compiler.release=21 -Dtest=PrintPreviewHtmlTest,DocumentGenerationBodySizeLimitFilterTest,DocumentPreviewHtmlRateLimiterTest,DocumentGenerationRateLimiterTest,DocumentPreviewApiTest,DocumentGenerationApiTest` — **33** tests, exit 0.
- `cd frontend && npm run test` — **93** tests, 34 files, exit 0.
- `cd frontend && npm run build && npm run test:e2e` — **21** passed (E2E-20 preview), exit 0. Playwright sert `dist/` : rebuild obligatoire après changement FE.
- `./mvnw verify -Dmaven.compiler.release=21` — **762** tests, Failures 0, Jacoco checks met, BUILD SUCCESS.

## Manual Testing

- Chrome : iframe HTML A4 visuel ; PDF inline lisible.
- Hors scope CI : Keycloak réel.

## Performance Testing

Hors V1 (même A14 que generate). Debounce 400 ms vérifié en fake timers.

## Bug Tracking

Écarts → implementation doc § Deviations ; blockers → planning `[!]`.
