---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-08-17
updated: 2026-08-17
feature: preview-documents
sequencing: layered
---

# Project Planning & Task Breakdown — `preview-documents`

## Milestones

- [ ] **M1 — Domaine** : `PreviewDocumentUseCase` live + resolve + PDF
- [ ] **M2 — API REST** : 2 endpoints, wrapper CSS, filtre taille, rate limit
- [ ] **M3 — Frontend** : API client, composable, panneau, admin templates
- [ ] **M4 — Preuves & docs** : verify backend + Vitest + Playwright, DEFERRED

## Task Breakdown

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine

- [ ] **T1. Port `PreviewDocumentUseCase`**
  - **Outcome :** interface + `PreviewCommand` dans `domain.document.port.in`.
  - **Depends on :** —
  - **Validation :** compile.
  - **Tests liés :** —

- [ ] **T2. `PreviewDocumentUseCaseImpl` — source live / resolve**
  - **Outcome :** règles D-SOURCE ; `assertHtmlSize` ; `DocumentVariableFilter` ;
    `htmlTemplateProcessor.process`. `previewHtml` / `previewPdf` partagent
    la résolution.
  - **Depends on :** T1
  - **Validation :** unitaires TDD (testing § PreviewDocumentUseCaseImpl).
  - **Tests liés :** `PreviewDocumentUseCaseImplTest`.

- [ ] **T3. Bean Spring**
  - **Outcome :** `DocumentConfig` câble le use case (même collabs que generate
    + `MAX_HTML_BYTES`).
  - **Depends on :** T2
  - **Validation :** compile / context test existant.

### M2 — API REST

- [ ] **T4. `PrintPreviewHtml`**
  - **Outcome :** wrap CSS analyse 07 ; fragment vs document complet.
  - **Depends on :** —
  - **Validation :** `PrintPreviewHtmlTest`.
  - **Tests liés :** testing § PrintPreviewHtml.

- [ ] **T5. Endpoints controller**
  - **Outcome :** `POST /preview` et `/preview/pdf` sur
    `DocumentGenerationController` ; même `@PreAuthorize` ; rate limiter ;
    PDF `Content-Disposition: inline` ; HTML via `PrintPreviewHtml`.
  - **Depends on :** T2, T4
  - **Validation :** MockMvc `DocumentPreviewApiTest`.
  - **Tests liés :** testing § Integration.

- [ ] **T6. Filtre taille preview**
  - **Outcome :** `DocumentGenerationBodySizeLimitFilter` match
    `/api/documents/preview` et `/preview/pdf`.
  - **Depends on :** —
  - **Validation :** unitaires filtre étendus.
  - **Tests liés :** testing § filter.

### M3 — Frontend

- [ ] **T7. Client API `documents.ts`**
  - **Outcome :** `previewHtml` / `previewPdf` (blob) ; types.
  - **Depends on :** T5 (contrat).
  - **Validation :** spec Vitest axios mock.

- [ ] **T8. `useTemplatePreview`**
  - **Outcome :** debounce 400 ms sur `htmlContent`+`variables` ; abort ;
    `previewPdf()` on-demand ; revoke object URL.
  - **Depends on :** T7
  - **Validation :** Vitest fake timers.
  - **Tests liés :** testing FE composable.

- [ ] **T9. `TemplatePreviewPanel.vue`**
  - **Outcome :** iframe `sandbox` + `srcdoc` ; bouton PDF ; textarea
    variables JSON (JSON invalide = erreur locale, pas d'appel).
  - **Depends on :** T8
  - **Validation :** Vitest mount.
  - **Tests liés :** testing FE panneau.

- [ ] **T10. Brancher `TemplatesAdminView`**
  - **Outcome :** panneau à côté du formulaire create ; `htmlContent` du form.
  - **Depends on :** T9
  - **Validation :** `adminViews` / `adminActions` toujours verts.

### M4 — Preuves & docs

- [ ] **T11. Playwright**
  - **Outcome :** mocks preview/pdf + scénario admin templates ; routes
    ordonnées (pdf avant preview).
  - **Depends on :** T10
  - **Validation :** `npm run test:e2e`.

- [ ] **T12. `./mvnw verify` + Vitest + docs**
  - **Outcome :** suites vertes ; testing doc coché ; DEFERRED-FEATURES
    D-PREVIEW livré ; implementation notes.
  - **Depends on :** T1–T11
  - **Validation :** commandes verify.

## Dependencies

```
T1 → T2 → T3
T4 ─┐
T2 ─┼→ T5 → T7 → T8 → T9 → T10 → T11
T6 ─┘                                      ↘
T3, T5, T6, T11 ───────────────────────────→ T12
```

T4 et T6 parallèles à T1–T3. T7 peut commencer dès le contrat T5 stable.

## Timeline & Estimates

Effort indicatif interne (pas un calendrier) : M1 petit, M2 moyen, M3 moyen,
M4 preuves. Feature bornée — pas de migration Liquibase.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| Route Playwright `**/preview**` avale `/preview/pdf` | Enregistrer `/preview/pdf` **avant** `/preview` |
| Debounce flaky en e2e | E2E mock immédiat ; debounce testé en Vitest fake timers |
| Double rate-limit HTML+PDF pendant l'édition | HTML debounce + PDF bouton ; même limiter generate (acceptable) |
| XSS via `srcdoc` | `sandbox` sans scripts ; pas de `v-html` |
| Régression generate | Tests existants `DocumentGenerationApiTest` inchangés / toujours run |

## Resources Needed

- Branche `cursor/preview-documents-dbc9` (pas de worktree `feature-*` :
  contrainte Cloud Agent, préfixe `cursor/…-dbc9`).
- JDK 25, Node/npm, Chromium Playwright.
- Skills : `tdd`, `verify`, `vue-best-practices`, `flowforge-conventions`.
- Task tracing : **indisponible** (`ai-devkit task` unknown command).

## Sequencing notes

Ordre **layered** : domaine → API → FE → preuves. TDD sur T2, T4, T6, T5.
Après chaque tâche : Phase 6 (cocher planning) puis reprise Phase 5.
Phase 7 quand T1–T10 `[x]`. Phase 8/9 après T11–T12.
