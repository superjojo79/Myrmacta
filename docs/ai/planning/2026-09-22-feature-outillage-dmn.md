---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: draft
created: 2026-09-22
updated: 2026-09-22
feature: outillage-dmn
note: Brouillon Phase 1 — détail des tâches en Phase 4 après revue design
---

# Project Planning & Task Breakdown — `outillage-dmn`

> Esquisse pour aligner le cadrage. **Phase 4** détaillera les tâches
> TDD après revue design (Phase 3).

## Milestones (provisoires)

- [ ] **M1** — Ports + evaluate (use case, adaptateur Flowable, REST, MCP)
- [ ] **M2** — Jeu de tests JSON (`TestDecisionUseCase` + tool + REST)
- [ ] **M3** — Historique versions (list + UI)
- [ ] **M4** — Redéploiement version N (use case + REST + MCP + UI confirm)
- [ ] **M5** — Bouton Tester UI + Vitest/Playwright
- [ ] **M6** — Guide utilisateur, OpenAPI golden, verify complet

Ordre : M1 avant M2 et M5. M3 avant M4. M5 après contrats evaluate.
M6 en dernier.

## Risks (phase 1)

- API Flowable d’historique / XML par version à vérifier en Phase 3
  (ne pas inventer une table applicative).
- Confusion UX « rollback » : mitiger par libellé imposé
  (D-DMN-ROLLBACK-UX).
- Surface MCP élargie : RBAC et descriptions tools à aligner sur
  `deploy_decision_table`.

## Progress

Phase 1 (requirements) en cours de clôture 2026-09-22. Phases 2–4
suivantes.
