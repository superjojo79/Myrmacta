---
phase: implementation
title: Implementation
description: Track implementation progress against the plan
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: openapi-pagination-admin
phase1: 2026-08-19
planning_ref: docs/ai/planning/2026-08-19-feature-openapi-pagination-admin.md
design_ref: docs/ai/design/2026-08-19-feature-openapi-pagination-admin.md
---

# Implementation — `openapi-pagination-admin`

Pas encore de code livré (Phase 1 docs). Suivre T1–T15 du planning, TDD.

## Notes d’implémentation (prévision)

- Ne pas annoter le domaine avec springdoc/Jackson.
- `PageResult` partagé domaine ; `Page` Spring Data **infra only**.
- Parser sort : égalité exacte de chaîne (`code,asc`), comme
  `DossierListQueryParser` / `updatedAt,desc`.
- Régénérer `openapi/openapi.yaml` dans le même commit que tout changement
  de contrôleur/DTO listé par springdoc.
- Playwright : mettre à jour **tous** les `page.route` des listes admin
  (grep `dossier-types`, `document-templates`, `connectors`,
  `workflow-type-configs`, `purge-outbox`).
