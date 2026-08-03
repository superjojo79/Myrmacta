---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: draft
created: 2026-08-03
updated: 2026-08-03
phase5: 2026-08-03
phase6: 2026-08-03
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

## Evidence (phase 5)

```text
JAVA_HOME=…/jdk-25.0.4+7 ./mvnw verify
→ Tests run: 353, Failures: 0, Errors: 0
→ Jacoco INSTRUCTION 12423/14247 = 87.20%
→ ArchUnit OK
→ npx ai-devkit lint --feature generation-documents → (à confirmer T15)
```

## Design alignment notes

- DQ1–DQ5 respectés (ports HTP/exists, filter vars, metadata, jsoup-in-PDF).
- Pas de table `generated_document` ; pas d’HTTP attach/store.
- BPMN smoke optionnel : delegates unitaires seulement.
- Écarts tests mineurs restants → phase 8.

## Next

**Phase 7 — Check Implementation** (`dev-implementation` check).
