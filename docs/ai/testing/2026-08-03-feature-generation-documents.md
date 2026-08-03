---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-03
updated: 2026-08-03
design_aligned: 2026-08-03
phase3_sync: 2026-08-03
phase5_evidence: 2026-08-03
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- **Unitaires domaine :** `GenerateDocumentUseCase` (resolve + merge +
  render mocké) ; `AddDossierElementUseCase` PRODUCED_DOC ; validation
  variables / template manquant.
- **Unitaires infra :** `FlyingSaucerPdfAdapter` HTML simple → PDF `%PDF` ;
  `FilesystemStorageAdapter` store/retrieve + path traversal refusé ;
  `S3StorageAdapter` avec client mocké.
- **Delegates :** `DocumentGenerationTask` / `AttachGeneratedDocumentTask`
  avec ports mockés (variables D-VARS).
- **IT H2 + MockMvc :** POST generate (PDF, authz, CSRF) ; GET content
  (ACL VIEW/EDIT/403) ; Liquibase `dossier_element`.
- **Régression :** templates, dossiers, workflow, connecteurs, ArchUnit,
  Jacoco ≥ 80 %.
- **Hors scope :** Testcontainers MinIO/S3 réel, Playwright, preview UI,
  purge §22, Keycloak réel, charge multi-thread PDF.

Aligné requirements success criteria + design DD-*.

## Unit Tests
**What individual components need testing?**

### GenerateDocumentUseCase
- [x] Resolve ACTIVE → `HtmlTemplateProcessor` → `PdfRenderer`
- [x] Template introuvable → exception métier
- [x] Erreur processor → exception métier
- [x] Ne appelle **pas** DocumentStoragePort
- [x] Domaine sans dépendance Thymeleaf/jsoup/AWS (ArchUnit)

### HtmlTemplateProcessor (Thymeleaf adapter)
- [x] Évalue `th:text` avec variables
- [x] Expression invalide → erreur métier

### PdfRenderer (Flying Saucer)
- [x] HTML5 simple (jsoup interne) → bytes `%PDF`
- [x] Document multi-pages simple (smoke)
- [x] Pool/ThreadLocal : deux appels successifs OK

### DocumentStoragePort — filesystem
- [x] store → retrieve round-trip
- [x] `exists` true/false
- [x] storageRef préfixe tenant (A2)
- [x] Path traversal (`../`) → rejet
- [x] retrieve clé inconnue → exception

### DocumentStoragePort — S3 (mock client)
- [x] putObject / getObject mapping storageRef
- [x] `exists` via headObject (mock)
- [x] contentType `application/pdf` propagé

### Variable filter (DQ2)
- [x] Strip `dossierId` / `generatedDocumentId` / `documentType`… avant merge
- [x] HTTP variables sans clés plateforme inchangées

### AddDossierElementUseCase
- [x] PRODUCED_DOC + storageRef + metadata DQ4 → persisté
- [x] dossier inconnu → erreur
- [x] type autre que PRODUCED_DOC refusé V1 (si tenté)

### DocumentGenerationTask
- [x] Variables D-VARS filtrées → generate + store → `generatedDocumentId`
- [x] `documentType` absent → échec

### AttachGeneratedDocumentTask
- [x] `exists` false → échec (pas d’insert)
- [x] `dossierId` + ref existante → élément PRODUCED_DOC + metadata DQ4
- [x] Label A3 contient activityName
- [x] `workflowStepId` = activityId

## Integration Tests
**What end-to-end flows need testing?**

### MockMvc generate
- [x] POST generate 200 `application/pdf` + magic `%PDF`
- [x] Rôles `TENANT_ADMIN` / `WORKFLOW_ADMIN` / `DOSSIER_MANAGER` → 200
- [x] `DOSSIER_VIEWER` → 403
- [x] Anonyme → 401 ; CSRF manquant → 403
- [x] templateKey inconnu → 404
- [ ] Body invalide (blank templateKey) → 400
- [x] Aucun objet créé dans storage FS après POST

### MockMvc download content
- [x] GET content 200 pour user avec ACL VIEW
- [ ] ACL EDIT → 200
- [x] Sans ACL → 403
- [x] Élément inconnu → 404
- [ ] storageRef cassé → 404
- [x] Bypass `TENANT_ADMIN` → 200

### Liquibase / persistence
- [x] Table `dossier_element` après migrate
- [ ] Round-trip JPA metadata JSON + null workflow_step_id

### Flowable smoke (si pattern existant connecteurs)
- [x] Tests unitaires beans delegate (BPMN smoke optionnel — non requis V1)

## Test files

| Suite | Path |
|---|---|
| Ports | `DocumentGenerationPortsContractTest` |
| Filter | `DocumentVariableFilterTest` |
| Generate UC | `GenerateDocumentUseCaseImplTest` |
| Add element | `AddDossierElementUseCaseImplTest` |
| Thymeleaf | `ThymeleafHtmlTemplateProcessorTest` |
| PDF | `FlyingSaucerPdfAdapterTest` |
| FS / S3 | `FilesystemStorageAdapterTest`, `S3StorageAdapterTest` |
| Tasks | `DocumentGenerationTaskTest`, `AttachGeneratedDocumentTaskTest` |
| Schema | `DossierElementSchemaIT` |
| API | `DocumentGenerationApiTest`, `DossierElementContentApiTest` |

## Test Data & Fixtures

- Template ACTIVE minimal : `<html><body><span th:text="${title}">x</span></body></html>`
- Variables : `{ "title": "Hello" }`
- `TenantProperties` = `tenant_dev`
- Dossier + `DossierType` + ACL VIEW fixture (patterns dossiers IT)
- Root FS tmp / `app.storage.filesystem.root` test properties

## Mocks & tools

- Mockito : TemplateResolver, PdfRenderer, DocumentStoragePort, repos
- Spring Security Test : `oidcLogin()` + rôles
- AWS SDK : mock `S3Client`
- ArchUnit : domain sans deps framework / infra

## Coverage Target

- Jacoco **instructions ≥ 80 %** (module) — **preuve phase 5 : 87,20 %**
- Priorité lignes : use cases, adapters FS/PDF, tasks, controllers,
  exception handlers

## Traceability

| Success criteria (requirements) | Couverture test |
|---|---|
| GenerateDocumentUseCase PDF | Unit + MockMvc generate |
| FS + S3 adapters | Unit FS + S3 mock |
| documentGenerationTask | Unit delegate |
| attach → dossier_element | Unit delegate + schema IT |
| POST sans store | MockMvc + assert FS vide |
| GET + ACL | MockMvc ACL + bypass admin |
| Liquibase 005 | Schema IT |
| verify / Jacoco / ArchUnit / lint | `./mvnw verify` 353/0 |

**Phase 5 evidence** (2026-08-03) — majorité des cases verts ; gaps mineurs
(blank templateKey 400, ACL EDIT, storageRef cassé, persistence round-trip)
→ **Phase 8**.
