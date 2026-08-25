---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks
status: done
created: 2026-08-25
updated: 2026-08-25
feature: derive-documentaire
---

# Planning — `derive-documentaire`

## Phase 4: Create Initial Plan

Tâches T1.1–T4.2 ci-dessous (figées avant implémentation).

## Phase 6: Update Planning

## Milestones

- [x] **M1** README + `00-index` (tenant, DMN, connecteurs, stockage, jsoup, diagrammes)
- [x] **M2** `analysis doc/` 06, 07, 11, 12
- [x] **M3** `application.yml` en-tête
- [x] **M4** Grep de contrôle + clôture TODO §45 / FILE-ATTENTE / registre revue

## Task Breakdown

### M1 — README / index

- [x] **T1.1** Réécrire `README.md` § Multi-tenant (décision 13 + garde OIDC).
      Validation : T-TENANT, T-GARDE.
- [x] **T1.2** README proposition de valeur, BPMN/DMN, diagrammes ASCII, table stack
      (jsoup 1.23.1, stockage S3-compatible / filesystem).
      Validation : T-DMN-README, T-JSOUP.
- [x] **T1.3** `analysis doc/00-index.md` : Flowable sans DMN V1, stockage, jsoup 1.23.1.

### M2 — analysis doc ciblés

- [x] **T2.1** `06-connecteurs.md` : colonne V1 sur le catalogue.
      Validation : T-CATALOG.
- [x] **T2.2** `07-generation-documents.md` : jsoup 1.23.1.
- [x] **T2.3** `11-frontend.md` : context-path = cible, yaml commenté.
- [x] **T2.4** `12-autorisation-conformite.md` : WORKFLOW_ADMIN sans cockpit.
      Validation : T-COCKPIT.

### M3 — yaml

- [x] **T3.1** Remplacer le commentaire d'en-tête `application.yml`.
      Validation : T-YAML.

### M4 — clôture

- [x] **T4.1** Exécuter les grep du testing doc ; `git diff --stat` (T-DIFF).
- [x] **T4.2** TODO §45 `[x]` ; FILE-ATTENTE (déplacer vers récemment livré ;
      prochain = `durcissement-securite-post-revue` en attente d'arbitrages) ;
      DEFERRED §3 ligne 45 ; note dans la revue si besoin.

## Progress (Phase 6)

M1–M4 clos. PR #75.

## Dependencies

M1 ∥ M2 ∥ M3 (fichiers disjoints) → M4.

## Risks

- Toucher trop large (`04`, `12` purge MinIO) : rester sur la liste design.
- Contredire `02-multi-tenant.md` (déjà correct) : README doit **pointer** vers lui.

## Timeline

N/A — chore docs, une passe.
