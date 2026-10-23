---
phase: implementation
title: Implementation Guide
description: Technical implementation notes for the documentary-drift chore
status: done
created: 2026-08-25
updated: 2026-08-25
feature: derive-documentaire
design_ref: docs/ai/design/2026-08-25-feature-derive-documentaire.md
planning_ref: docs/ai/planning/2026-08-25-feature-derive-documentaire.md
---

# Implementation Guide — `derive-documentaire`

Feature **doc-only**. T1.1–T4.2 exécutées. [PR #75](https://github.com/superjojo79/FlowForge/pull/75).
Aucun `.java` / `.vue` / changelog. Commentaire d'en-tête `application.yml`
uniquement.

## Development Setup

Aucun. Lecture : TODO §45, [REVUE-ALIGNEMENT-SECURITE.md](../../../REVUE-ALIGNEMENT-SECURITE.md) §3.3,
code (`TenantProperties`, `TenantResolutionFilter`, `HttpRestTriggerConnector`,
`FlowableSignalConnector`, `HttpRestServiceConnector`, `pom.xml` `jsoup.version`).

## Code Structure

| Fichier | Édition |
|---|---|
| `README.md` | Tenant instance + garde OIDC ; V1 connecteurs/DMN ; diagrammes ; jsoup 1.23.1 ; stockage port S3 ; `context-path` cible |
| `docs/specs/00-index.md` | Flowable sans DMN V1 ; stockage ; jsoup 1.23.1 |
| `docs/specs/06-connecteurs.md` | Catalogue : colonnes Cible / V1 livré |
| `docs/specs/07-generation-documents.md` | jsoup 1.23.1 ; parenthèse stockage (fichier déjà touché) |
| `docs/specs/11-frontend.md` | `context-path` = cible ops, yaml commenté |
| `docs/specs/12-autorisation-conformite.md` | `WORKFLOW_ADMIN` → `/admin/bpmn-editor` |
| `src/main/resources/application.yml` | Commentaire d'en-tête Flowable V1 |

## Implementation Notes

- **T1.1** README § Multi-tenant : `app.tenant.id` au démarrage (décision 13) ;
  claim OIDC `tenant_id` = garde (`TenantResolutionFilter`).
- **T1.2** Connecteurs V1 vs cible ; DMN non activé ; diagramme ASCII ;
  jsoup 1.23.1 ; `DocumentStoragePort` ; `context-path` commenté (TODO §31).
- **T1.3** `00-index.md` aligné.
- **T2.1** Catalogue `06` : colonne **V1 livré** (DD-06-SHAPE).
- **T2.2** jsoup 1.23.1 dans `07` ; reco stockage = port S3/filesystem.
- **T2.3** `11-frontend.md` : instance sert `/` en V1.
- **T2.4** `WORKFLOW_ADMIN` sans « accès au cockpit Flowable ».
- **T3.1** En-tête yaml : exclusions Flowable actuelles.
- **T4.1–T4.2** Greps verts ; TODO §45 `[x]` ; FILE-ATTENTE / DEFERRED / revue.

## Design Deviations

Aucune. DD-06-SHAPE, DD-README-DMN, DD-STORAGE, DD-CONTEXT suivis.

Parenthèse stockage dans `07` : prévue par les non-goals (« si on touche
déjà le fichier »).

Phase 8 : T-COCKPIT greppait `cockpit Flowable` ; une parenthèse « pas de
cockpit Flowable en V1 » faisait matcher. Retirée — la table ne revendique
plus le cockpit ; `DEFERRED-FEATURES.md` §1.5 reste la référence hors V1.

## Phase 7 — revue fichier par fichier

| Fichier | Alignement design | Écart / risque |
|---|---|---|
| `README.md` | Tenant = instance + garde ; DMN hors V1 ; V1 connecteurs ; stockage port ; context-path cible | Statut « conception — développement à démarrer » **non** touché (hors liste §45) |
| `00-index.md` | DMN exclue, jsoup 1.23.1, stockage port | Viewer BPMN sans mention éditeur — hors liste |
| `06-connecteurs.md` | Cible conservée + V1 | — |
| `07-generation-documents.md` | jsoup + parenthèse stockage | Mentions MinIO dans `04`/`12` purge **volontairement** intactes |
| `11-frontend.md` | context-path cible | — |
| `12-autorisation-conformite.md` | plus de capacité cockpit | — |
| `application.yml` | commentaire uniquement | Exclusions inchangées |

Pas de trou de logique runtime. Pas de secret. Rollback = revert git.

## Phase 9 — revue finale

- Design match : 7 fichiers de la liste, pas d'élargissement V1.
- Pas de contrat API / SPA / migration.
- Greps T-* verts (testing doc).
- `npx ai-devkit@latest lint --feature derive-documentaire` : docs OK ;
  `[MISS]` git `feature-*` accepté (branche cloud).
- Traçage `task` CLI : indisponible.
- Prêt pour revue humaine : PR #75.
