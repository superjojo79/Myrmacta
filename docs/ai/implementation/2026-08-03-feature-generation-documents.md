---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: checked
created: 2026-08-03
updated: 2026-08-04
phase5: 2026-08-03
phase6: 2026-08-03
phase7: 2026-08-04
alignment: aligned
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
infrastructure/pdf       — FlyingSaucerPdfAdapter (jsoup XHTML)
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

### Deviations / notes (non bloquant)

| Sévérité | Item | Disposition |
|---|---|---|
| note | O6 borne ~1 MiB sur JSON `variables` | Non livré — follow-up phase 8 / ops soft |
| note | Filtre DQ2 dans UC (pas inline task) | Conforme « avant HTP » |
| note | `*IT.java` (ex. `DossierElementSchemaIT`) hors include surefire défaut (`*Test`) | Préexistant projet ; IT passe en `-Dtest=…` ; phase 8 peut renommer/inclure |
| note | Gaps tests MockMvc (blank templateKey 400, ACL EDIT, storageRef cassé, round-trip JPA) | Phase 8 |

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

## Next

**Phase 8 — Write Tests** (`dev-testing`) — combler gaps MockMvc / persistence
et éventuellement inclure `*IT` dans surefire ou renommer.
