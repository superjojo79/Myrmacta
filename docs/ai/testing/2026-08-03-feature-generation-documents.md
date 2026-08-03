---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: draft
created: 2026-08-03
updated: 2026-08-03
design_aligned: 2026-08-03
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
- [ ] Resolve ACTIVE + variables → appelle PdfRenderer avec XHTML
- [ ] Template introuvable → exception métier
- [ ] Thymeleaf evaluation error → exception métier
- [ ] Ne appelle **pas** DocumentStoragePort

### PdfRenderer (Flying Saucer)
- [ ] HTML/XHTML minimal → bytes commencent par `%PDF`
- [ ] Document multi-pages simple (smoke)
- [ ] Pool/ThreadLocal : deux appels successifs OK

### DocumentStoragePort — filesystem
- [ ] store → retrieve round-trip
- [ ] storageRef préfixe tenant (A2)
- [ ] Path traversal (`../`) → rejet
- [ ] retrieve clé inconnue → exception

### DocumentStoragePort — S3 (mock client)
- [ ] putObject / getObject mapping storageRef
- [ ] contentType `application/pdf` propagé

### AddDossierElementUseCase
- [ ] PRODUCED_DOC + storageRef → persisté
- [ ] dossier inconnu → erreur
- [ ] type autre que PRODUCED_DOC refusé V1 (si tenté)

### DocumentGenerationTask
- [ ] Variables D-VARS → generate + store → `generatedDocumentId` set
- [ ] `documentType` absent → échec
- [ ] Propagate variables d’exécution au merge

### AttachGeneratedDocumentTask
- [ ] `dossierId` + `generatedDocumentId` → élément PRODUCED_DOC
- [ ] Label A3 contient activityName
- [ ] Variable storage absente → échec
- [ ] `workflowStepId` = activityId

## Integration Tests
**What end-to-end flows need testing?**

### MockMvc generate
- [ ] POST generate 200 `application/pdf` + magic `%PDF`
- [ ] Rôles `TENANT_ADMIN` / `WORKFLOW_ADMIN` / `DOSSIER_MANAGER` → 200
- [ ] `DOSSIER_VIEWER` / `API_SERVICE` → 403
- [ ] Anonyme → 401 ; CSRF manquant → 403
- [ ] templateKey inconnu → 404
- [ ] Body invalide (blank templateKey) → 400
- [ ] Aucun objet créé dans storage FS après POST

### MockMvc download content
- [ ] GET content 200 pour user avec ACL VIEW
- [ ] ACL EDIT → 200
- [ ] Sans ACL → 403
- [ ] Élément inconnu → 404
- [ ] storageRef cassé → 404

### Liquibase / persistence
- [ ] Table `dossier_element` après migrate
- [ ] Round-trip JPA metadata JSON + null workflow_step_id

### Flowable smoke (si pattern existant connecteurs)
- [ ] Déploiement BPMN minimal generate→attach OU test
      `@SpringBootTest` des beans delegate branchés

## Test Data & Fixtures

- Template ACTIVE minimal : `<html><body><span th:text="${title}">x</span></body></html>`
- Variables : `{ "title": "Hello" }`
- `TenantProperties` = `tenant_dev`
- Dossier + `DossierType` + ACL VIEW fixture (patterns dossiers IT)
- Root FS tmp JUnit `@TempDir` pour `app.storage.filesystem.root`

## Mocks & tools

- Mockito : TemplateResolver, PdfRenderer, DocumentStoragePort, repos
- Spring Security Test : `oidcLogin()` + rôles
- AWS SDK : mock `S3Client` ou wrapper testable
- ArchUnit : domain document/dossier sans deps pdf/aws

## Coverage Target

- Jacoco **instructions ≥ 80 %** (module)
- Priorité lignes : use cases, adapters FS/PDF, tasks, controllers,
  exception handlers

## Traceability

| Success criteria (requirements) | Couverture test |
|---|---|
| GenerateDocumentUseCase PDF | Unit + MockMvc generate |
| FS + S3 adapters | Unit FS + S3 mock |
| documentGenerationTask | Unit delegate (+ smoke) |
| attach → dossier_element | Unit delegate + schema IT |
| POST sans store | MockMvc + assert FS vide |
| GET + ACL | MockMvc ACL matrix |
| Liquibase 005 | Schema IT |
| verify / Jacoco / ArchUnit / lint | CI locale phase 5/8 |

**Phase 1** — stratégie draft. Cases cochés en phases 5/8.
