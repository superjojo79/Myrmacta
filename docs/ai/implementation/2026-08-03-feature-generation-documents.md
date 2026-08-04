---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: reviewed
created: 2026-08-03
updated: 2026-08-04
phase5: 2026-08-03
phase6: 2026-08-03
phase7: 2026-08-04
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
→ npx ai-devkit lint --feature generation-documents → OK
```

## Phase 7 — Check Implementation (2026-08-04)

Revue code Java/YAML vs design `docs/ai/design/2026-08-03-feature-generation-documents.md`.

| # | Exigence | Statut |
|---|---|---|
| 1 | HtmlTemplateProcessor port + Thymeleaf adapter (DQ1) | ALIGNED |
| 2 | DocumentVariableFilter clés plateforme (DQ2) | ALIGNED |
| 3 | DocumentStoragePort store/retrieve/exists (DQ3) | ALIGNED |
| 4 | Metadata attach templateKey/contentType/variant?/locale? (DQ4) | ALIGNED |
| 5 | jsoup uniquement FlyingSaucerPdfAdapter (DQ5) | ALIGNED |
| 6 | GenerateDocumentUseCase → byte[], pas de store | ALIGNED |
| 7 | Add/Get DossierElement ; ElementType PRODUCED_DOC only | ALIGNED |
| 8 | ThymeleafHtmlTemplateProcessor évalue expressions | ALIGNED |
| 9 | FlyingSaucerPdfAdapter jsoup + ITextRenderer | ALIGNED |
| 10 | FilesystemStorageAdapter anti-traversal + `{tenant}/{uuid}.pdf` | ALIGNED |
| 11 | S3StorageAdapter HeadObject ; pas Minio* | ALIGNED |
| 12 | StorageConfig ConditionalOnProperty filesystem\|s3, défaut FS | ALIGNED |
| 13 | documentGenerationTask generate+store+generatedDocumentId | ALIGNED |
| 14 | attachGeneratedDocumentTask exists + system + label A3 | ALIGNED |
| 15 | POST /api/documents/generate PreAuthorize + PDF mémoire | ALIGNED |
| 16 | GET content assertCanView + attachment + 404/409/403 | ALIGNED |
| 17 | Pas d’HTTP create élément | ALIGNED |
| 18 | Liquibase 005 colonnes/index, pas unique storage_ref, pas FK SQL | ALIGNED |
| 19 | Pas de table generated_document | ALIGNED |
| 20 | app.storage.* dans yaml | ALIGNED |

### Écarts / notes

| Sévérité | Item | Détail |
|---|---|---|
| note | #13 filtre DQ2 | Filtrage dans `GenerateDocumentUseCaseImpl` (pas inline dans la task) — conforme « avant HtmlTemplateProcessor ». |
| note | O6 | Borne taille JSON `variables` (ex. 1 MiB) non implémentée — open design item. |
| note | AddDossierElement | `AccessContext` reçu mais non consommé (ACL) — OK V1 car pas d’HTTP create, seul Flowable system. |
| note | Tests | Pas de test API dédié 409 sur GET content (handler présent). |

### Hors V1 — non introduit

Pas de `delete` storage, Playwright, adapter Minio*, table registre, HTTP attach/store, enum `ATTACHMENT`.

## Design alignment notes

- DQ1–DQ5 respectés (ports HTP/exists, filter vars, metadata, jsoup-in-PDF).
- Pas de table `generated_document` ; pas d’HTTP attach/store.
- BPMN smoke optionnel : delegates unitaires seulement.
- Phase 7 : **20/20 ALIGNED** ; écarts mineurs/notes uniquement.

## Next

**Phase 8 — Testing** (`dev-testing`) : combler scénarios 409 content + O6 si retenu.
