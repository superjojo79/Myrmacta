---
phase: deployment
title: Deployment Strategy
description: Define deployment process, infrastructure, and release procedures
status: reviewed
updated: 2026-08-04
phase9: 2026-08-04
---

# Deployment Strategy

## Feature-specific notes (`generation-documents`)

### Config

```yaml
app.storage.type: filesystem   # or s3
app.storage.filesystem.root: ...
app.storage.s3.bucket / region / endpoint / credentials
```

Défaut **filesystem** (dev/IT). Prod typique : `s3` + endpoint compatible.

### Database — Liquibase `005-dossier-element-tables`

- ChangeSet additif : `createTable dossier_element` + 2 indexes.
- Pas de FK SQL sur `dossier_id` (pattern 001–004).
- Pas d’unicité `storage_ref` (A15).

### Rollback

1. **Schema :** `liquibase rollbackCount 1` (ou rollback au tag précédent) —
   Liquibase auto-rollback sur `createTable` / `createIndex` (pas de bloc
   `rollback:` SQL custom requis, contrairement à certains changesets 001).
2. **Données :** rollback droppe `dossier_element` → perte des métadonnées
   d’éléments PRODUCED_DOC.
3. **Objets storage :** **non purgés** par le rollback Liquibase (FS/S3
   orphelins possibles — TODO §22 / purge différée).
4. **App :** retirer/désactiver les beans Flowable
   `documentGenerationTask` / `attachGeneratedDocumentTask` et endpoints
   generate/content si rollback applicatif partiel.

### Post-deploy checks

- `POST /api/documents/generate` (rôle admin) → PDF
- Smoke BPMN avec les 2 ServiceTasks si processus déployés
- `GET /api/dossier-elements/{id}/content` avec ACL VIEW
