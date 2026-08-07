---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: checked
created: 2026-08-03
updated: 2026-08-07
phase5: 2026-08-03
phase6: 2026-08-03
phase7: 2026-08-04
phase8: 2026-08-04
phase9: 2026-08-04
a8_fonts: 2026-08-07
alignment: aligned
review: ready
---

# Implementation Guide

## Development Setup

- Worktree `.worktrees/feature-generation-documents`
- JDK 25 — `JAVA_HOME=/home/ubuntu/java/jdk-25.0.4+7`
- Deps pom déjà présentes : flying-saucer-pdf, jsoup, aws sdk s3

## Code Structure

```
domain/document/port/out — PdfRenderer, HtmlTemplateProcessor, DocumentStoragePort
domain/document/port/in  — GenerateDocumentUseCase
domain/document/usecase  — GenerateDocumentUseCaseImpl
domain/document          — DocumentVariableFilter
domain/dossier/model     — DossierElement, ElementType.PRODUCED_DOC
domain/dossier/port+uc   — Add/Get DossierElement
infrastructure/document  — ThymeleafHtmlTemplateProcessor
infrastructure/pdf       — FlyingSaucerPdfAdapter (jsoup XHTML) + ClasspathFontRegistrar (A8)
resources/fonts          — LiberationSans-Regular.ttf (+ LICENSE SIL OFL)
infrastructure/storage   — FilesystemStorageAdapter, S3StorageAdapter
infrastructure/flowable  — DocumentGenerationTask, AttachGeneratedDocumentTask
infrastructure/web/document — DocumentGenerationController, DossierElementContentController
infrastructure/config    — StorageConfig / StorageProperties ; beans Document/Dossier
db/changelog/changes/005-dossier-element-tables.yaml
```

## Config

```yaml
app.storage.type: filesystem|s3   # défaut filesystem
app.storage.filesystem.root: ...
app.storage.s3.bucket / region / endpoint / credentials
```

## Phase 7 — Check Implementation (2026-08-04)

### Alignment status: **aligned**

Revue fichier-par-fichier vs design/requirements `reviewed` : **20/20**
points de contrôle ALIGNED (ports DQ1–DQ5, UC, adapters, Flowable, HTTP,
persistence, config). Aucun écart bloquant. Pas de retour design requis.

### Checklist design → code

| Id | Exigence | Verdict |
|---|---|---|
| DQ1 | Port `HtmlTemplateProcessor` + adapter Thymeleaf | aligned |
| DQ2 | Filtre clés plateforme avant merge | aligned (`DocumentVariableFilter` dans UC) |
| DQ3 | `DocumentStoragePort.exists` ; attach l’utilise | aligned |
| DQ4 | Metadata fixe templateKey/contentType/variant?/locale? | aligned |
| DQ5 | jsoup dans `FlyingSaucerPdfAdapter` | aligned |
| DD-HTTP-MEM | POST generate sans store | aligned |
| DD-ACL-VIEW | `assertCanView` + bypass admin | aligned |
| DD-NO-REG | Pas de table `generated_document` | aligned |
| DD-ENUM-V1 | `PRODUCED_DOC` seul | aligned |
| DD-TASK-NAMES | beans documentGeneration / attachGenerated | aligned |
| A2–A4 / A9–A13 / A15 | storageRef, ProblemDetail, System ctx, disposition | aligned |
| A8 / DD-FONTS-A8 | Polices TTF/OTF classpath via `ClasspathFontRegistrar` | aligned (corrigé 2026-08-07) |

### Deviations / notes (non bloquant)

| Sévérité | Item | Disposition |
|---|---|---|
| note | O6 borne ~1 MiB sur JSON `variables` | Soft / hors V1 — non livré |
| note | Filtre DQ2 dans UC (pas inline task) | Conforme « avant HTP » |
| note | Schema surefire | Phase 8 : `DossierElementSchemaTest` (ex-`*IT`) inclus |
| note | Gaps MockMvc / JPA | Phase 8 clos |
| fixed | A8 polices classpath non enregistrées | T16 — `ClasspathFontRegistrar` + Liberation Sans |

### Evidence (phase 7, fresh)

```text
JAVA_HOME=…/jdk-25.0.4+7 ./mvnw clean verify
→ Tests run: 353, Failures: 0, Errors: 0
→ Jacoco INSTRUCTION 12423/14247 = 87.20%
→ ArchUnit OK
→ npx ai-devkit lint --feature generation-documents → All checks passed
→ ./mvnw -Dtest=DossierElementSchemaIT test → 2/0 (Liquibase 005 colonnes)
```

### Changed files (livré phase 5, vérifié phase 7)

Ports document, filtre variables, Generate UC, adapters Thymeleaf/PDF/FS/S3,
`StorageConfig`, modèle `DossierElement` + Liquibase 005 + JPA, 2 JavaDelegates
Flowable, controllers generate + content + exception handler, beans config,
tests associés.

## Phase 8

Gaps tests clos + `./mvnw clean verify` → **362 / 0**, Jacoco **87,29 %**.

## Phase 9 — Code Review (2026-08-04)

### Verdict: **READY**

| Sévérité | Finding | Disposition |
|---|---|---|
| blocking | — | aucun |
| important | `AccessContext` no-op dans Add UC | **Fixed** — `assertCanEdit` |
| important | `storageRef` non validé A2 à l’add | **Fixed** — préfixe tenant / `..` |
| important | Get UC sans check tenant dossier | **Fixed** — 404 mismatch |
| important | Gaps tests WORKFLOW_ADMIN / 409 | **Fixed** |
| important | Doc rollback Liquibase 005 | **Fixed** (deployment) |
| nice | O6 borne variables, 500 vs 404 storage, truncate label | Follow-up post-merge |

### Checklist

- [x] Design match DQ1–DQ5 / DD-*
- [x] Pas de trou authz HTTP V1 ; Add UC aligné policy
- [x] Path traversal FS/S3
- [x] Integration Flowable / JPA / Liquibase
- [x] Tests + Jacoco ≥ 80 %
- [x] Docs feature + rollback 005
- [x] Fresh `./mvnw clean verify` → **366 / 0**, Jacoco **87,35 %**

Traçage `task` CLI : indisponible.
