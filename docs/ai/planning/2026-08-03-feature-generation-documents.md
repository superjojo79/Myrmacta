---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: completed
created: 2026-08-03
updated: 2026-08-07
design_aligned: 2026-08-03
phase4: 2026-08-03
phase5: 2026-08-03
phase6: 2026-08-03
a8_fonts: 2026-08-07
sequencing: layered
---

# Project Planning & Task Breakdown

Plan **phase 4–6** — dérivé des docs `requirements` / `design` (`reviewed`)
et `testing` (`draft` → scenarios cochés phase 5).

**Phase 5–6 done** — T1–T15. Traçage `task` CLI : **indisponible**.  
Preuve T14 : `./mvnw verify` → **353 / 0**, Jacoco instructions **87,20 %**.  
**Follow-up A8** — T16 (polices classpath) en cours / livré sur branche feature.

## Milestones
**What are the major checkpoints?**

- [x] **M1 — Domaine document** : ports + filtre variables
- [x] **M2 — Adapters rendu / storage** : Thymeleaf, Flying Saucer, FS, S3
- [x] **M3 — DossierElement** : model, Liquibase 005, JPA, Add UC
- [x] **M4 — Use case génération** : GenerateDocumentUseCaseImpl
- [x] **M5 — Flowable tasks** : documentGeneration + attachGenerated
- [x] **M6 — HTTP API** : POST generate + GET content
- [x] **M7 — Preuves & docs** : verify, Jacoco, docs

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine document

- [x] **T1. Ports out + exceptions document**
  - **Validation :** `DocumentGenerationPortsContractTest` 2/0.

- [x] **T2. Port in + filtre variables (DQ2)**
  - **Validation :** `DocumentVariableFilterTest` 2/0.

### M2 — Adapters rendu / storage

- [x] **T3. ThymeleafHtmlTemplateProcessor**
  - **Validation :** `ThymeleafHtmlTemplateProcessorTest` 2/0.

- [x] **T4. FlyingSaucerPdfAdapter**
  - **Validation :** `FlyingSaucerPdfAdapterTest` 2/0.

- [x] **T5. FilesystemStorageAdapter + config**
  - **Validation :** `FilesystemStorageAdapterTest` 4/0.

- [x] **T6. S3StorageAdapter**
  - **Validation :** `S3StorageAdapterTest` 2/0.

### M3 — DossierElement

- [x] **T7. Model domaine élément + ports**
  - **Validation :** `AddDossierElementUseCaseImplTest` 3/0.

- [x] **T8. Liquibase `005-dossier-element-tables` + JPA**
  - **Validation :** `DossierElementSchemaIT` 2/0.

### M4 — Use case génération

- [x] **T9. GenerateDocumentUseCaseImpl**
  - **Validation :** `GenerateDocumentUseCaseImplTest` 2/0.

### M5 — Flowable tasks

- [x] **T10. documentGenerationTask**
  - **Validation :** `DocumentGenerationTaskTest` 2/0.

- [x] **T11. attachGeneratedDocumentTask**
  - **Validation :** `AttachGeneratedDocumentTaskTest` 2/0.
  - **Note :** smoke BPMN optionnel non requis — delegates unitaires OK.

### M6 — HTTP API

- [x] **T12. POST `/api/documents/generate`**
  - **Validation :** `DocumentGenerationApiTest` 4/0.

- [x] **T13. GET `/api/dossier-elements/{id}/content`**
  - **Validation :** `DossierElementContentApiTest` 4/0.

### M7 — Preuves & docs

- [x] **T14. Régression verify + couverture**
  - **Validation :** `./mvnw verify` 353/0 ; Jacoco **87,20 %** ; ArchUnit OK.

- [x] **T15. Docs implementation + sync planning**
  - **Validation :** lint feature + planning statuses.

### M8 — Écart A8 polices classpath

- [x] **T16. ClasspathFontRegistrar + Liberation Sans (A8 / DD-FONTS-A8)**
  - **Outcome :** scan `classpath:/fonts/**` TTF/OTF ; register dans
    `FlyingSaucerPdfAdapter.newRenderer()` ; WOFF ignorés ; CDN toujours
    bloqué via `RestrictedResourceUserAgent`.
  - **Validation :** `ClasspathFontRegistrarTest` +
    `FlyingSaucerPdfAdapterTest#classpathTtfFont_isEmbeddedWhenReferencedByCssFontFamily`.

## Traceability testing → tasks

| Scénario testing | Tâche(s) | Statut |
|---|---|---|
| GenerateDocumentUseCase + ArchUnit | T1, T2, T9, T14 | done |
| HtmlTemplateProcessor | T3 | done |
| PdfRenderer Flying Saucer | T4 | done |
| A8 fonts classpath TTF/OTF | T16 | done |
| Storage FS / S3 | T5, T6 | done |
| Variable filter DQ2 | T2, T10 | done |
| AddDossierElement | T7, T8 | done |
| Flowable tasks | T10, T11 | done |
| MockMvc generate / download | T12, T13 | done |
| Liquibase élément | T8 | done |
| Jacoco ≥ 80 % | T14–T15 | done |

## Progress summary

Feature **generation-documents** implémentée bout-en-bout (PDF + storage
S3/FS + 2 ServiceTasks + PRODUCED_DOC + HTTP). Aucune tâche nouvelle
découverte en phase 5.  
**Prochaine phase lifecycle :** 7 — Check Implementation.
