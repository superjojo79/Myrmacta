---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
phase1: 2026-08-19
phase2: 2026-08-19
stakeholder_decisions: 2026-08-19
todo_ref: TODO.md §22 · FILE-ATTENTE.md · DEFERRED-FEATURES.md §0 · suite de retention-dossier-type
---

# Requirements & Problem Understanding — `purge-stockage`

> Prochaine feature produit après `retention-dossier-type` (PR #55), selon
> [FILE-ATTENTE.md](../../../FILE-ATTENTE.md). Lot « avant prod » REVUE §9 /
> TODO §22. Consomme `purge_outbox` (`DOSSIER_PURGE_REQUESTED`) produit par §21.

## Problem Statement
**What problem are we solving?**

La rétention (§21) archive les dossiers expirés et écrit une ligne
`purge_outbox` `PENDING`, **sans** supprimer les lignes PostgreSQL ni les
objets du stockage. Sans consommateur, les données personnelles et fichiers
restent indéfiniment — non conforme au droit à l’effacement / fin de
conservation.

- **Affectés :** conformité / RGPD, ops (espace object store), `TENANT_ADMIN`
  (politique déjà configurée en §21).
- **Situation actuelle :** `DocumentStoragePort` (S3 + filesystem) existe ;
  `delete` **absent** ; `PurgeOutboxStatus` = `PENDING` seul ; aucun job
  consommateur ; FK métier (éléments, relations, workflow links) sans
  cascade de purge applicative.
- **Réf. :** TODO §22, DEFERRED §0, `analysis doc/12-autorisation-conformite.md`,
  design `retention-dossier-type` (outbox reportée à §22).

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Consommer `purge_outbox` (`DOSSIER_PURGE_REQUESTED`, `PENDING`) via un
  **job planifié idempotent**.
- Purger **symétriquement** : objets `DocumentStoragePort` (refs des
  éléments `ATTACHMENT` / `PRODUCED_DOC`) **puis** graphe PostgreSQL du
  dossier (éléments, relations source/cible, workflow links, dossier,
  ligne outbox terminée).
- Étendre le port stockage avec **`delete(tenantId, storageRef)`**
  (adapters S3 + filesystem) — **jamais** MinIO couplé en dur (DEFERRED §0).
- Étendre le cycle outbox : `PENDING` → `PROCESSING` → `COMPLETED` |
  `FAILED` (rejeu sûr).
- Garantir l’**idempotence** : objet déjà absent = succès ; dossier déjà
  absent = succès ; rejeu `PROCESSING`/`FAILED` borné.

### Secondary goals

- Métriques / logs structurés (âge outbox, succès/échec) prêts pour §29
  sans livrer tout le lot observabilité.
- Documenter le runbook **suppression de tenant** (décommission instance +
  `DROP SCHEMA` + purge préfixe/bucket) — **hors code** de ce lot.
- Vitest + Playwright **uniquement si** le SPA ou un contrat API consommé
  par le SPA change ; sinon documenter l’exemption Phase 8 avec preuve
  qu’aucune surface FE n’est touchée.

### Non-goals

- Transaction atomique unique PG + object store (impossible cross-store).
- Soft-delete supplémentaire (déjà `ARCHIVE` + `purgeEligibleAt` en §21).
- Suppression de tenant automatisée / `DROP SCHEMA` applicatif.
- API HTTP « droit à l’effacement immédiat » / force purge admin.
- Écran supervision de la file outbox (aligné non-goal §21).
- Balayage orphelins stockage hors outbox (uploads échoués, etc.).
- OpenAPI exhaustif (§24), antivirus PJ, connecteur `http-rest` SERVICE.
- Changement de politique rétention / UI admin types (déjà §21).

## Alternatives considérées

| Approche | Trade-off | Verdict |
|---|---|---|
| Transaction applicative unique PG+S3 | Pas de XA fiable ; S3 non transactionnel | **Écarté** |
| Soft-delete + balayage seul | §21 a déjà marqué ; pas d’effacement effectif | **Écarté** pour le cœur |
| Job de réconciliation sur `purge_outbox` | Réutilise §21 ; ordre objets→PG ; rejeu | **Retenu (D-MECH)** |
| Nouveau port « ObjectStore » séparé de `DocumentStoragePort` | Double abstraction | **Écarté** — étendre le port existant (D-PORT) |
| Purge synchrone dans le job rétention | Couple §21/§22 ; I/O store dans scan rétention | **Écarté** — outbox dédiée déjà choisie |

## User Stories & Use Cases

1. **En tant que** plateforme, **je veux** qu’un dossier `PENDING` dans
   `purge_outbox` soit physiquement effacé (fichiers + lignes), **afin de**
   honorer la fin de rétention.
2. **En tant que** ops, **je veux** que le job soit idempotent et borné,
   **afin de** pouvoir le rejouer sans corruption ni timeout.
3. **En tant que** conformité, **je veux** qu’après `COMPLETED` le dossier
   ne soit plus lisible via l’API (404), **afin de** prouver l’effacement.
4. **En tant que** ops tenant, **je veux** un runbook documenté pour
   décommissionner une instance (schéma + objets), **afin de** ne pas
   confondre purge dossier et purge tenant.

### Edge cases

- Objet stockage déjà absent → continuer (idempotent).
- Dossier PG déjà absent / partiellement purgé → finaliser outbox
  `COMPLETED` sans erreur bloquante.
- Échec mid-flight (ex. S3 down après N deletes) → `FAILED` + retry
  ultérieur ; ne pas marquer `COMPLETED`.
- Éléments sans `storageRef` (`NOTE`, `INFORMATION`, `LINK`) → skip store,
  supprimer lignes PG.
- Relations où le dossier est **source ou cible** → supprimer les deux
  côtés pour éviter FK orphelines.
- Workflow link avec process Flowable encore vivant → terminer/supprimer
  l’instance runtime si possible, puis supprimer le link (D-WF).
- Concurrent claim du même `PENDING` → claim atomique `PENDING`→`PROCESSING`
  (une seule victoire).
- `purgeEligibleAt` dans le futur (données incohérentes) → **ne pas**
  purger ; laisser `PENDING` (A-ELIGIBLE-GUARD) ou skip jusqu’à éligibilité.
- Payload outbox incomplet → `FAILED` + log ; pas de delete partiel
  silencieux sur mauvais `dossierId`.

## Success Criteria
**How will we know when we're done?**

- [ ] Job planifié consomme `PENDING` → `PROCESSING` → `COMPLETED` (happy
      path) avec batch borné.
- [ ] Pour un dossier avec PJ/PDF : objets absents du store **et** lignes
      dossier/éléments/relations/links absentes ; GET dossier → 404.
- [ ] `DocumentStoragePort.delete` implémenté FS + S3 ; tests adaptateurs.
- [ ] Rejeu idempotent (2ᵉ passe no-op / `COMPLETED` stable).
- [ ] Échec store → `FAILED` ; pas de `COMPLETED` prématuré ; retry ok.
- [ ] Aucun couplage MinIO ; ArchUnit / hexagone respectés.
- [ ] TODO §22 / FILE-ATTENTE / DEFERRED mis à jour en fin de lot.
- [ ] `./mvnw verify` vert ; FE (Vitest+Playwright) si SPA touché, sinon
      exemption documentée Phase 8.
- [ ] Runbook tenant-delete documenté (deployment doc).

## Constraints & Assumptions

- Hexagone : domaine pur ; JPA / S3 SDK dans infrastructure ; ArchUnit CI.
- Une instance Boot = un tenant (`app.tenant.id`) — schéma courant uniquement.
- Port stockage déjà présent (`FilesystemStorageAdapter`, `S3StorageAdapter`)
  — **D-PORT** = extension, pas nouveau port.
- Outbox §21 : UNIQUE(`dossier_id`) ; payload JSON
  `{tenantId,dossierId,typeCode,purgeEligibleAt}` — réutiliser.
- SPA même origine + CSRF ; **pas de nouvelle UI** (D-UI).
- Job actor audit : `system:purge-job` (D-JOB-ACTOR).
- **A-ELIGIBLE-GUARD :** ne purger que si `purgeEligibleAt == null` **ou**
  `purgeEligibleAt <= now` (UTC) ; sinon skip (reste `PENDING`).
- **A-ORDER :** storage deletes → graphe PG → statut outbox terminal.
- Task tracing `ai-devkit task` indisponible (`unknown command 'task'`).
- Branche Cloud : `cursor/purge-stockage-05b8` (lint worktree `feature-*` =
  MISS attendu, comme rétention).

## Stakeholder decisions

| Id | Décision | Choix |
|---|---|---|
| **D-MECH** | Mécanisme §22 | Job consommateur `purge_outbox` (réconciliation) |
| **D-ORDER** | Ordre d’effacement | Objets store d’abord, puis PG, puis statut terminal |
| **D-PORT** | Stockage | Étendre `DocumentStoragePort.delete` ; adapters S3+FS |
| **D-STATUS** | Cycle outbox | `PENDING` / `PROCESSING` / `COMPLETED` / `FAILED` |
| **D-SCOPE-PG** | Graphe PG | Éléments + relations (src\|tgt) + workflow links + dossier + finaliser outbox |
| **D-WF** | Flowable | Best-effort delete/terminate PI runtime liée ; toujours supprimer `dossier_workflow_link` |
| **D-WF-HIST** | Historique ACT_HI_* | Résiduel acceptable V1 si delete runtime échoue (Q-WF-DEPTH → A) |
| **D-UI** | Surface UI | Aucune (pas d’écran file / force admin) |
| **D-HTTP** | API purge | Aucune en V1 (job only) |
| **D-FE** | Tests FE | Pas de nouveau parcours SPA ; Phase 8 = non-régression suite E2E existante + exemption nouveaux specs (Q-FE → A) |
| **D-TENANT** | Suppression tenant | Hors code ; runbook ops uniquement |
| **D-ERASURE** | Droit à l’effacement manuel | Hors scope V1 (seulement file rétention) |
| **D-ORPHAN** | Orphelins hors outbox | Hors scope V1 |
| **D-JOB-ACTOR** | Audit | `system:purge-job` |
| **D-CLAIM** | Concurrence | Update atomique `PENDING`→`PROCESSING` |
| **D-GRACE** | Délai extra | Aucun au-delà de `purgeEligibleAt` (§21) |

## Questions & Open Items

### Clos Phase 2 (2026-08-19)

Toutes les décisions D-* ci-dessus **actées** (option A / recommandations
Phase 1), y compris **D-WF-HIST** et **D-FE**.

### Assumptions techniques restantes (pas de question produit)

- **A-ELIGIBLE-GUARD**, **A-ORDER**, payload outbox §21, branche Cloud
  `cursor/purge-stockage-05b8`.

### Reporté hors feature

- Observabilité MDC complète (§29).
- OpenAPI / pagination admin (§24).
- Force purge / écran supervision.
- Purge tenant automatisée.
- Balayage orphelins stockage.
- TTL lignes `COMPLETED` outbox (DEV-PURGE).
