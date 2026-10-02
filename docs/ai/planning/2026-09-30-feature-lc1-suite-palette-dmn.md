---
phase: planning
title: Planning — LC1-suite (liste des tables DMN dans la palette)
description: Tâches et état d'avancement
status: done
created: 2026-09-30
updated: 2026-09-30
feature: lc1-suite-palette-dmn
lot: LC1-suite
requirements_ref: docs/ai/requirements/2026-09-30-feature-lc1-suite-palette-dmn.md
design_ref: docs/ai/design/2026-09-30-feature-lc1-suite-palette-dmn.md
testing_ref: docs/ai/testing/2026-09-30-feature-lc1-suite-palette-dmn.md
---

# Planning — `lc1-suite-palette-dmn`

Taille **S**. Une seule passe, TDD (rouge constaté : erreur de compilation sur le constructeur de l'adaptateur).

| Tâche | État |
|---|---|
| T1 Tests : use case (source servie), adaptateur (tri, libellé, tenant), API réelle (déploiement Flowable + rôles) | done |
| T2 `SERVED_SOURCES`, adaptateur, javadoc du port | done |
| T3 Frontend : `SERVER_SOURCES` + spec Vitest | done |
| T4 Playwright `E2E-PALETTE-8` (catalogue simulé étendu à la brique DMN) | done |
| T5 Documentation : `GUIDE-UTILISATEUR.md`, analyse 39, docs du lot §73, `CHANGELOG.md`, `TODO.md` §74 | done |
| T6 Revue adversariale (Phase 10) | voir implémentation |
