---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
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

- [ ] `htmlContent` live → process sans appeler resolve ; HTML évalué retourné
- [ ] `htmlContent` live + `templateKey` → resolve **non** appelé (live gagne)
- [ ] `templateKey` seul → resolve + process `template.htmlContent()`
- [ ] les deux blank → `TemplateValidationException`
- [ ] template manquant → `TemplateNotFoundException` propagée
- [ ] `previewPdf` appelle `pdfRenderer.render` avec l'HTML évalué (pas le wrapper CSS)
- [ ] clés plateforme (`dossierId`, …) absentes du map passé au processor
- [ ] `variables` null → process avec map vide
- [ ] `htmlContent` au-delà de `MAX_HTML_BYTES` → `TemplateValidationException`
- [ ] `htmlContent` blank (espaces) + `templateKey` valide → chemin resolve

### `PrintPreviewHtml`

- [ ] fragment → document avec CSS analyse 07 et wrapper `.page`
- [ ] document complet (`<html>`) → `<style>` injecté, contenu conservé

### `DocumentGenerationBodySizeLimitFilter`

- [ ] POST `/api/documents/preview` oversized → 413
- [ ] POST `/api/documents/preview/pdf` oversized → 413
- [ ] POST `/api/documents/generate` oversized → 413 (non-régression)
- [ ] autre path oversized → chain continues

## Integration Tests

Étendre `DocumentGenerationApiTest` (ou classe sœur `DocumentPreviewApiTest`) :

- [ ] POST `/preview` live `htmlContent` + variable → 200 `text/html` ; body
      contient le texte mergé **et** le CSS print-preview ; `countStoredFiles`
      inchangé
- [ ] POST `/preview` `templateKey` ACTIVE → 200 ; contenu issu du template
- [ ] POST `/preview/pdf` → 200 PDF `%PDF` ; `Content-Disposition` contient
      `inline` ; pas de store
- [ ] POST `/preview` template inconnu → 404
- [ ] POST `/preview` body vide → 400
- [ ] `@PreAuthorize` : VIEWER → 403 ; anonyme → 401 (un des deux endpoints)
- [ ] D-AUTHZ B live : TA + `htmlContent` → 200 ; WA + `htmlContent` → 403 ;
      DM + `htmlContent` → 403 (aucun process / store)
- [ ] D-AUTHZ B live gagne : DM + `htmlContent` **et** `templateKey` → 403
- [ ] D-AUTHZ B publié : TA / WA / DM + `templateKey` seul → 200
- [ ] Thymeleaf cassé → 400
- [ ] generate existant toujours 200 PDF **sans** `inline` obligatoire
      (non-régression Content-Disposition)

## End-to-End Tests

- [ ] E2E admin templates : ouvrir `/admin/templates`, saisir HTML dans le
      formulaire, attendre l'aperçu (mock `POST **/api/documents/preview**`
      → HTML), iframe visible
- [ ] E2E bouton « Aperçu PDF » mocke `POST **/api/documents/preview/pdf**`
      (blob) — le bouton est cliqué, pas d'erreur UI
- [ ] Mocks globaux `mockSession` : les nouveaux paths ne tombent pas dans un
      catch-all qui casserait les specs existantes
- [ ] Specs E2E existantes toujours vertes

## Test Data

- HTML live : `<p th:text="${title}">x</p>` + `variables.title=Hello`
- Template ACTIVE créé via le même helper `createActiveTemplate()` que
  generate
- Playwright : `page.route('**/api/documents/preview/pdf**')` **avant**
  `**/api/documents/preview**` (préfixe) pour ne pas avaler le PDF

## Test Reporting & Coverage

```bash
./mvnw test -Dtest=PreviewDocumentUseCaseImplTest,PrintPreviewHtmlTest,DocumentGenerationBodySizeLimitFilterTest,DocumentPreviewApiTest,DocumentGenerationApiTest
./mvnw verify -Dmaven.compiler.release=21
cd frontend && npm run test
cd frontend && npm run test:e2e
```

Seuils : Jacoco projet (≥ 80 %) ; Vitest lines ≥ 80 % ; Playwright 0 fail.

## Manual Testing

- Chrome : iframe HTML A4 visuel ; PDF inline lisible.
- Hors scope CI : Keycloak réel.

## Performance Testing

Hors V1 (même A14 que generate). Debounce 400 ms vérifié en fake timers.

## Bug Tracking

Écarts → implementation doc § Deviations ; blockers → planning `[!]`.
