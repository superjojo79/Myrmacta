---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: done
created: 2026-08-17
updated: 2026-08-18
feature: preview-documents
sequencing: layered
phase4: 2026-08-17
phase5: 2026-08-17
phase6: 2026-08-17
phase7: 2026-08-18
phase8: 2026-08-18
design_aligned: 2026-08-17
---

# Project Planning & Task Breakdown — `preview-documents`

Plan initial Phase 4, calé sur requirements `reviewed` et design `reviewed`
(D-AUTHZ **B**, D-RATE-SPLIT **C**). Aucune migration Liquibase.

## Milestones

- [x] **M1 — Domaine** : `PreviewDocumentUseCase` + `isLive()` (sans rôles)
- [x] **M2 — API REST** : wrap CSS, filtre taille, limiter HTML 120/min,
      endpoints + gate live TA, PDF sur limiter generate
- [x] **M3 — Frontend** : client, composable debounce, panneau, admin
- [x] **M4 — Preuves & docs** : verify + Vitest + Playwright, DEFERRED

## Task Breakdown

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine

- [x] **T1. Port `PreviewDocumentUseCase` + `PreviewCommand.isLive()`**
  - **Outcome :** `domain/document/port/in/PreviewDocumentUseCase.java` —
    `previewHtml` / `previewPdf` ; record `PreviewCommand` avec
    `isLive()` = `htmlContent` non blank après trim.
  - **Depends on :** —
  - **Validation :** compile.
  - **Tests liés :** couverts via T2.

- [x] **T2. `PreviewDocumentUseCaseImpl` (TDD)**
  - **Outcome :** D-SOURCE ; `assertHtmlSize(MAX_HTML_BYTES)` sur live ;
    `DocumentVariableFilter` ; `resolveHtml` partagé. **Aucun** rôle /
    `AccessContext`. `previewPdf` rend l'HTML évalué (pas le wrapper CSS).
  - **Depends on :** T1
  - **Validation :** `PreviewDocumentUseCaseImplTest` (testing § unit use case,
    11 cas, exit 0).
  - **Tests liés :** testing § `PreviewDocumentUseCaseImpl`.

- [x] **T3. Bean Spring use case**
  - **Outcome :** `DocumentConfig` câble `PreviewDocumentUseCase` (resolve +
    processor + renderer + `MAX_HTML_BYTES`).
  - **Depends on :** T2
  - **Validation :** le context `DocumentPreviewApiTest` (T8) charge le bean.

### M2 — API REST

- [x] **T4. `PrintPreviewHtml` (TDD)**
  - **Outcome :** CSS analyse 07 ; fragment → `.page` ; document complet →
    `<style>` dans `<head>`.
  - **Depends on :** —
  - **Validation :** `PrintPreviewHtmlTest`.
  - **Tests liés :** testing § `PrintPreviewHtml`.

- [x] **T5. Filtre taille — matcher preview**
  - **Outcome :** `DocumentGenerationBodySizeLimitFilter` : remplacer le
    `PATH` unique par un ensemble d'égalités exactes —
    `/api/documents/generate` | `/api/documents/preview` |
    `/api/documents/preview/pdf`. Pas de prefix/glob. Le filtre est déjà
    enregistré globalement dans `SecurityConfig` (`addFilterBefore`) :
    **pas** de nouveau bean ; mettre à jour le commentaire « seul generate ».
    Plafond inchangé : `app.document.generate.max-body-bytes` = 256 Kio
    (propriété réelle au singulier `document`, pas `documents`).
  - **Depends on :** —
  - **Validation :** `DocumentGenerationBodySizeLimitFilterTest` étendu
    (preview, preview/pdf, generate, other path).
  - **Tests liés :** testing § filter (4 cas).

- [x] **T6. Limiter HTML dédié (D-RATE-SPLIT C)**
  - **Outcome :** `DocumentPreviewProperties` prefix
    `app.document.preview` ; `application.yml` frère de `generate` :
    `preview.rate-limit-per-minute: 120`. `@EnableConfigurationProperties`
    dans `DocumentConfig`. Bean limiter HTML = **autre instance** avec sa
    propre `ConcurrentHashMap`. **Piège :**
    `DocumentGenerationRateLimiter.tryConsume` est keyé **uniquement par
    principal** — réutiliser le même `@Component` avec une limite 120
    fusionnerait HTML et generate. Extraire si besoin une classe
    package-private du même algo (`WebhookRequestRateLimiter` / generate) ;
    deux beans distincts. `/preview/pdf` et `/generate` gardent le bean
    generate existant + `DocumentGenerationProperties.rateLimitPerMinute()`.
  - **Depends on :** —
  - **Validation :** unitaire fenêtre (copie de
    `DocumentGenerationRateLimiterTest`) ; MockMvc T8 avec
    `@TestPropertySource` limites = 2 (pas 120 POST en CI).
  - **Tests liés :** testing § D-RATE-SPLIT (les deux cas intégration).

- [x] **T7. Gate live D-AUTHZ B (contrôleur, avant limiter)**
  - **Outcome :** helper web : si `command.isLive()` et pas
    `ROLE_TENANT_ADMIN` → `org.springframework.security.access.AccessDeniedException`
    (« Live preview requires TENANT_ADMIN ») **avant** `tryConsume` et use
    case. VIEWER/anonyme restent le `@PreAuthorize` existant.
  - **Depends on :** T1 (`isLive()`)
  - **Validation :** MockMvc T8 (WA/DM live 403 ; DM live+key 403).
  - **Tests liés :** testing § D-AUTHZ B.

- [x] **T8. Endpoints `POST /preview` et `/preview/pdf`**
  - **Outcome :** `DocumentGenerationController` : record `PreviewRequest` ;
    même `@PreAuthorize` que generate ; ordre parse → gate T7 → limiter
    (HTML T6 / PDF generate) → use case → `PrintPreviewHtml` si HTML ;
    PDF `Content-Disposition: inline; filename="preview.pdf"`. Pas de
    `DocumentStoragePort`. `DocumentGenerationExceptionHandler` déjà
    `assignableTypes = DocumentGenerationController` — pas de nouvel
    advice. 403 live → `MethodSecurityExceptionHandler`.
  - **Depends on :** T2, T4, T5, T6, T7
  - **Validation :** `DocumentPreviewApiTest` + non-régression
    `DocumentGenerationApiTest`.
  - **Tests liés :** testing § Integration (tous les cas REST restants).

### M3 — Frontend

- [x] **T9. Client `frontend/src/api/documents.ts`**
  - **Outcome :** nouveau module (n'existe pas). `previewHtml({ htmlContent,
    variables })` → string HTML (`responseType` text) ;
    `previewPdf(...)` → `Blob`. UI n'envoie pas `templateKey` (TA live).
    Pattern axios de `admin.ts`. Couvrir dans `clients.spec.ts` et
    `apiCoverage.spec.ts`.
  - **Depends on :** T8 (contrat)
  - **Validation :** spec Vitest axios.

- [x] **T10. `useTemplatePreview` (TDD Vitest)**
  - **Outcome :** debounce **400 ms** sur `htmlContent`+`variables` ; abort
    du fetch HTML précédent ; `previewPdf()` on-demand ; revoke object URL
    au remplacement et unmount. JSON variables invalidé **en amont** du
    composable (panneau) ou dans le composable — un seul endroit, le panneau.
  - **Depends on :** T9
  - **Validation :** fake timers.
  - **Tests liés :** testing FE composable / debounce.

- [x] **T11. `TemplatePreviewPanel.vue`**
  - **Outcome :** iframe `sandbox=""` + `srcdoc` ; bouton « Aperçu PDF » ;
    textarea variables JSON (parse local, erreur sans HTTP).
  - **Depends on :** T10
  - **Validation :** Vitest mount (iframe sandbox, bouton).
  - **Tests liés :** testing FE panneau.

- [x] **T12. Brancher `TemplatesAdminView`**
  - **Outcome :** panneau à côté du formulaire create ; `htmlContent` du
    form en prop. CRUD existant inchangé.
  - **Depends on :** T11
  - **Validation :** `adminViews.spec.ts` / `adminActions.spec.ts` verts.

### M4 — Preuves & docs

- [x] **T13. Playwright**
  - **Outcome :** `page.route` `/preview/pdf` **avant** `/preview` ; scénario
    admin templates (saisie → iframe HTML ; clic PDF). Specs existantes
    vertes.
  - **Depends on :** T12
  - **Validation :** `cd frontend && npm run test:e2e`.
  - **Tests liés :** testing § E2E.

- [x] **T14. Verify + docs de clôture**
  - **Outcome :** `./mvnw verify` ; `npm run test` ; testing doc coché ;
    implementation notes ; `DEFERRED-FEATURES.md` D-PREVIEW livré.
  - **Depends on :** T1–T13
  - **Validation :** skill `verify` (preuves fraîches).

## Traceability — testing → tasks

| Scénario testing | Tâche |
|---|---|
| Use case live / resolve / filter / size / PDF | T2 |
| `PrintPreviewHtml` fragment / document | T4 |
| Filtre 413 preview, preview/pdf, generate, other | T5 |
| D-RATE-SPLIT HTML (limite test=2) puis 429 ; generate n'épuise pas HTML | T6 + T8 |
| D-RATE-SPLIT PDF+generate (limite test=2) puis 429 | T6 + T8 |
| REST 200 HTML+CSS, 200 PDF inline, 404, 400, Thymeleaf, no store | T8 |
| `@PreAuthorize` VIEWER 403 / anonyme 401 | T8 |
| D-AUTHZ B TA live 200 ; WA/DM live 403 ; DM live+key 403 ; key-only 200 | T7 + T8 |
| Generate non-régression Content-Disposition | T8 + T14 |
| Vitest API / composable / panneau / admin | T9–T12 |
| E2E admin + ordre des mocks + régression | T13 |

Tous les `- [ ]` du testing doc ont au moins une tâche.

## Dependencies

```
T1 → T2 → T3
T1 → T7
T4 ─┐
T5 ─┤
T6 ─┼→ T8 → T9 → T10 → T11 → T12 → T13
T7 ─┘                                    ↘
T2, T3, T8, T13 ──────────────────────────→ T14
```

T4, T5, T6 parallèles à T1–T3. T7 dès T1 (`isLive()`). T8 attend T2+T4+T5+T6+T7.

## Timeline & Estimates

M1 petit · M2 cœur (gate + deux limiters) · M3 moyen · M4 preuves.
Pas de calendrier externe.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| 120 POST MockMvc trop lents / bruyants | `@TestPropertySource` `app.document.preview.rate-limit-per-minute=2` (et generate=2 pour le cas PDF partagé) ; le cas « 120 » n'est pas un objectif de perf CI |
| Extraire le limiter casse generate | Garder le bean generate existant ; tests `DocumentGenerationApiTest` (429) toujours run |
| Route Playwright `**/preview**` avale `/preview/pdf` | Enregistrer `/preview/pdf` **avant** `/preview` |
| Debounce flaky e2e | Mock immédiat ; debounce uniquement Vitest fake timers |
| XSS `srcdoc` | `sandbox=""` ; pas de `v-html` |
| WA/DM live exécute quand même Thymeleaf | Gate T7 **avant** limiter et use case ; test 403 + storage inchangé |

## Resources Needed

- Branche `cursor/preview-documents-dbc9` (pas de worktree `feature-*` :
  contrainte Cloud Agent).
- JDK 25, Node/npm, Chromium Playwright.
- Skills : `tdd`, `verify`, `vue-best-practices`, `flowforge-conventions`.
- Task tracing : **indisponible** (`ai-devkit task` unknown command).

## Sequencing notes

Ordre **layered** : domaine → API → FE → preuves. TDD obligatoire sur T2,
T4, T5, T6, T7/T8 (red MockMvc authz/rate).

Après **chaque** tâche implémentée : Phase 6 (cocher ce doc) puis reprise
Phase 5. **Ne pas** enchaîner T1–T14 dans un seul sprint agent sans
repasser par le rythme phase-par-phase convenu avec l'utilisateur.

Phase 7 quand T1–T12 `[x]`. Phase 8/9 après T13–T14.

## Progress (Phase 6)

T1–T14 `[x]`. M1–M4 livrés. **Phase 7 close (2026-08-18)** : code aligné
design/requirements. **Phase 8 close (2026-08-18)** : écart PDF/JSON
invalide corrigé et testé (Vitest + E2E-21) ; preuves fraîches Vitest 94,
Playwright 22, `./mvnw verify` 762 Jacoco met. Libellé 429 HTML inchangé
(cosmétique). Prochaine phase : **9** (`dev-review`).

## Phase 4 — clôture

Plan initial **ready** (2026-08-17). 14 tâches, 4 jalons, tracé 1:1 vers
le testing draft. Décisions B + C intégrées (gate contrôleur, deux
limiters, filtre 256 Kio partagé). Hors scope inchangé : Liquibase,
OpenAPI, M2M, détail dossier, WYSIWYG, modification de `/generate`.

**Prochaine phase : 5** — TDD T1/T2 (`PreviewDocumentUseCaseImplTest`
d'abord). Pas d'implémentation tant que l'utilisateur n'a pas lancé
explicitement la Phase 5.
