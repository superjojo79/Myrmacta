---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: not-started
created: 2026-08-27
updated: 2026-08-27
feature: serveur-mcp
phase1: 2026-08-27
---

# Project Planning & Task Breakdown — `serveur-mcp`

Squelette **init-feature**. Le plan détaillé (milestones / tâches TDD)
est la **Phase 4** (`dev-planning`), après Phase 2 (requirements
reviewed) et Phase 3 (design reviewed).

Ne pas implémenter à partir de ce fichier.

## Milestones
**What are the major checkpoints?**

Indicatif (à détailler en Phase 4) :

- [ ] M1 — SDK MCP + endpoint `/api/m2m/mcp` + JWT
- [ ] M2 — Gardes domaine `API_SERVICE` (policy + create/update type)
- [ ] M3 — Tools lecture
- [ ] M4 — Tools écriture + `dryRun` + ordre deploy
- [ ] M5 — `query_sla_summary`
- [ ] M6 — IT MockMvc + ArchUnit + doc README
- [ ] M7 — `./mvnw verify` + régression FE

## Task Breakdown

Rempli en Phase 4. Toutes les tâches **not started**.

## Dependencies

- `reporting-sla` **livré** (PR #80) — plus un bloqueur.
- Use cases existants listés au design.
- Pin Spring AI (preuve BOM) en Phase 3.

## Timeline & Estimates

Sans calendrier (agent). Séquencement technique : M1→M2→M3/M4
parallélisables après M2→M5→M6→M7.

## Risks & Mitigation

Voir requirements D-SDK (BOM) et design ordre parser/config/deploy.

## Resources Needed

- JWT M2M de test déjà présent pour `/api/m2m/connectors/**`.
- Fixtures BPMN `deploiement-bpmn`.
