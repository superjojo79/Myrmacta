---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-08-03
updated: 2026-08-03
design_aligned: 2026-08-03
phase4: 2026-08-03
sequencing: layered
---

# Project Planning & Task Breakdown

Plan **phase 4** — dérivé des docs `requirements` / `design` (`reviewed`)
et `testing` (`draft`, sync phase 3).

**Séquencement (**PQ1 = 1**)** : couches (domaine → adapters → persistence →
use cases → Flowable → HTTP → preuves).  
Traçage `task` CLI : **indisponible**.

## Milestones
**What are the major checkpoints?**

- [ ] **M1 — Domaine document** : ports `PdfRenderer`, `HtmlTemplateProcessor`,
  `DocumentStoragePort`, `GenerateDocumentUseCase` + filter variables +
  exceptions
- [ ] **M2 — Adapters rendu / storage** : Thymeleaf processor, Flying Saucer
  (+ jsoup), FS + S3, config switch
- [ ] **M3 — DossierElement** : model `PRODUCED_DOC`, Liquibase 005, JPA,
  `AddDossierElementUseCase`
- [ ] **M4 — Use case génération** : `GenerateDocumentUseCaseImpl` branché
  resolver + ports
- [ ] **M5 — Flowable tasks** : `documentGenerationTask` +
  `attachGeneratedDocumentTask`
- [ ] **M6 — HTTP API** : POST generate + GET content + authz + ProblemDetail
- [ ] **M7 — Preuves & docs** : MockMvc/IT, Jacoco ≥ 80 %, ArchUnit,
  implementation notes

## Task Breakdown
**What specific work needs to be done?**

Statuts : `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked

### M1 — Domaine document

- [ ] **T1. Ports out + exceptions document**
  - **Outcome :** `PdfRenderer`, `HtmlTemplateProcessor`,
    `DocumentStoragePort` (`store` / `retrieve` / `exists`) ; exceptions
    métier rendu / storage / template processing.
  - **Depends on :** —
  - **Validation :** contrat ports (compile + test smoke types) ;
    ArchUnit domain sans deps infra.
  - **Tests liés :** Ports contract ; ArchUnit domaine.

- [ ] **T2. Port in + filtre variables (DQ2)**
  - **Outcome :** `GenerateDocumentUseCase` + `GenerateDocumentCommand` ;
    utilitaire/filtre clés plateforme
    (`dossierId`, `dossierWorkflowLinkId`, `tenantId`,
    `generatedDocumentId`, `documentType` / `Variant` / `Locale`).
  - **Depends on :** T1
  - **Validation :** unit filter (strip + conservation clés métier).
  - **Tests liés :** Variable filter (DQ2).

### M2 — Adapters rendu / storage

- [ ] **T3. ThymeleafHtmlTemplateProcessor**
  - **Outcome :** adapter `HtmlTemplateProcessor` via
    `SpringTemplateEngine` + string template ; eval expressions ;
    erreur → exception métier.
  - **Depends on :** T1
  - **Validation :** `ThymeleafHtmlTemplateProcessorTest`.
  - **Tests liés :** HtmlTemplateProcessor.

- [ ] **T4. FlyingSaucerPdfAdapter**
  - **Outcome :** `PdfRenderer` ; jsoup HTML→XHTML interne (**DQ5**) ;
    ThreadLocal/pool `ITextRenderer` ; PDF `%PDF`.
  - **Depends on :** T1 (deps pom déjà présentes)
  - **Validation :** `FlyingSaucerPdfAdapterTest`.
  - **Tests liés :** PdfRenderer Flying Saucer.

- [ ] **T5. FilesystemStorageAdapter + config**
  - **Outcome :** store/retrieve/exists ; root
    `app.storage.filesystem.root` ; anti path-traversal ; défaut
    `app.storage.type=filesystem` ; `@TempDir` IT-ready.
  - **Depends on :** T1
  - **Validation :** `FilesystemStorageAdapterTest`.
  - **Tests liés :** DocumentStoragePort filesystem.

- [ ] **T6. S3StorageAdapter**
  - **Outcome :** AWS SDK v2 ; put/get/head ; endpoint override config ;
    bean conditionnel `app.storage.type=s3` ; **pas** de type `Minio*`.
  - **Depends on :** T1, T5 (properties partagées)
  - **Validation :** unit avec `S3Client` mocké.
  - **Tests liés :** DocumentStoragePort S3 mock.

### M3 — DossierElement

- [ ] **T7. Model domaine élément + ports**
  - **Outcome :** `DossierElement`, `ElementType.PRODUCED_DOC` ;
    `DossierElementRepository` ; `AddDossierElementUseCase` (+ get by id
    si besoin download).
  - **Depends on :** —
  - **Validation :** models test + use case unit (repo mock) ;
    dossier inconnu / type invalide.
  - **Tests liés :** AddDossierElementUseCase.

- [ ] **T8. Liquibase `005-dossier-element-tables` + JPA**
  - **Outcome :** table `dossier_element` PG/H2 ; indexes ; entity +
    mapper + adapter ; metadata JSON ; FK logique seulement.
  - **Depends on :** T7
  - **Validation :** `DossierElementSchemaIT` + persistence round-trip.
  - **Tests liés :** Liquibase / persistence.

### M4 — Use case génération

- [ ] **T9. GenerateDocumentUseCaseImpl**
  - **Outcome :** `TemplateResolver` → filter vars →
    `HtmlTemplateProcessor` → `PdfRenderer` ; pas d’appel storage ;
    wiring Spring config document.
  - **Depends on :** T2, T3, T4 (mocks OK pour unit avant T3/T4)
  - **Validation :** `GenerateDocumentUseCaseImplTest` (ports mockés).
  - **Tests liés :** GenerateDocumentUseCase.

### M5 — Flowable tasks

- [ ] **T10. documentGenerationTask**
  - **Outcome :** bean `documentGenerationTask` ; lit D-VARS ; generate +
    `store` ; set `generatedDocumentId` ; `documentType` requis.
  - **Depends on :** T5/T6, T9
  - **Validation :** unit delegate mocké.
  - **Tests liés :** DocumentGenerationTask.

- [ ] **T11. attachGeneratedDocumentTask**
  - **Outcome :** bean `attachGeneratedDocumentTask` ; `exists` (**DQ3**) ;
    `AddDossierElementUseCase` + `AccessContext.system` ; label A3 ;
    metadata DQ4 ; `workflowStepId`.
  - **Depends on :** T7, T8, T5/T6
  - **Validation :** unit delegate (`exists` false → pas d’insert).
  - **Tests liés :** AttachGeneratedDocumentTask.
  - **Note :** smoke BPMN minimal **optionnel** si pattern connecteurs
    réutilisable ; sinon delegates unitaires suffisent (requirements).

### M6 — HTTP API

- [ ] **T12. POST `/api/documents/generate`**
  - **Outcome :** controller + DTO ; `hasAnyRole(TENANT_ADMIN,
    WORKFLOW_ADMIN, DOSSIER_MANAGER)` ; CSRF ; PDF bytes ; **aucun** store ;
    ProblemDetail 400/404.
  - **Depends on :** T9
  - **Validation :** `DocumentGenerationApiTest` MockMvc.
  - **Tests liés :** MockMvc generate.

- [ ] **T13. GET `/api/dossier-elements/{id}/content`**
  - **Outcome :** load élément → dossier → `assertCanView` ; retrieve ;
    `Content-Disposition` ; 403/404/409.
  - **Depends on :** T8, T5/T6
  - **Validation :** `DossierElementContentApiTest` (ACL matrix + bypass
    admin).
  - **Tests liés :** MockMvc download content.

### M7 — Preuves & docs

- [ ] **T14. Régression verify + couverture**
  - **Outcome :** `./mvnw verify` vert ; Jacoco instructions ≥ 80 % ;
    ArchUnit (domain sans Thymeleaf/jsoup/AWS).
  - **Depends on :** T1–T13
  - **Validation :** sortie Maven + rapport Jacoco.
  - **Tests liés :** Coverage target ; ArchUnit.

- [ ] **T15. Docs implementation + sync planning**
  - **Outcome :** notes implementation (packages, config yaml, preuves) ;
    planning statuses ; lint feature OK.
  - **Depends on :** T14
  - **Validation :** `npx ai-devkit lint --feature generation-documents`.
  - **Tests liés :** —

## Traceability testing → tasks

| Scénario testing | Tâche(s) |
|---|---|
| GenerateDocumentUseCase + ArchUnit domaine | T1, T2, T9, T14 |
| HtmlTemplateProcessor | T3 |
| PdfRenderer Flying Saucer | T4 |
| Storage FS store/retrieve/exists/traversal | T5 |
| Storage S3 mock | T6 |
| Variable filter DQ2 | T2, T10 |
| AddDossierElementUseCase | T7, T8 |
| DocumentGenerationTask | T10 |
| AttachGeneratedDocumentTask (+ exists) | T11 |
| MockMvc generate (rôles/CSRF/no-store) | T12 |
| MockMvc download ACL/bypass | T13 |
| Liquibase / JPA élément | T8 |
| Flowable smoke optionnel | T11 (note) |
| Jacoco ≥ 80 % / verify | T14–T15 |

## Dependencies
**What needs to happen in what order?**

```text
T1 → T2 → T9
T1 → T3, T4, T5 → T6
T7 → T8 → T11, T13
T9 → T10, T12
T5/T6 → T10, T11, T13
T1–T13 → T14 → T15
```

Externe : feature `templates-documents` (TemplateResolver) déjà sur
branche de base ; `DossierAccessPolicy` / dossiers livrés.

## Risks & Mitigation

| Risque | Mitigation |
|---|---|
| ArchUnit casse si Thymeleaf fuit dans domain | Port HTP (DQ1) ; gate T14 |
| Flying Saucer / HTML5 edge | jsoup dans adapter (DQ5) ; fixtures HTML simples |
| S3 difficile en CI | Adapter mockable ; défaut FS (A6) |
| Attach sans objet | `exists` obligatoire (DQ3) |
| task CLI absent | Suivi via cases planning uniquement |

## Resources Needed

- JDK 25 (CI/projet) ; worktree `.worktrees/feature-generation-documents`
- Deps pom déjà présentes (flying-saucer, jsoup, aws s3)
- Patterns : `UpdateDossierStatusDelegate`, `DocumentTemplate*` tests,
  Liquibase 004 dual PG/H2

## Progress summary

Phase 4 initiale créée (T1–T15, M1–M7, PQ1 couches).  
**Prochaine phase :** 5 — Execute Plan (`dev-implementation`), démarrer **T1**.

**Phase 4 close** (2026-08-03).
