---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
phase1: 2026-08-19
todo_ref: TODO.md §22 · FILE-ATTENTE.md · DEFERRED-FEATURES.md §0 · suite de retention-dossier-type
---

# Requirements & Problem Understanding — `purge-stockage`

> Feature produit après `retention-dossier-type` (PR #55), selon
> [FILE-ATTENTE.md](../../../FILE-ATTENTE.md). Lot « avant prod » REVUE §9 /
> TODO **§22 encore ouvert**. Phase 1 = capturer le problème et **trancher**
> les arbitrages ; rien n’est acté tant que les questions ci-dessous n’ont
> pas de réponse stakeholder.

## Problem Statement
**What problem are we solving?**

La rétention (§21) archive les dossiers expirés (`ARCHIVE` +
`purgeEligibleAt`) et écrit une ligne `purge_outbox`
(`DOSSIER_PURGE_REQUESTED`, `PENDING`). **Aucun consommateur** n’efface les
lignes PostgreSQL ni les objets du store. Les données personnelles et
fichiers restent donc indéfiniment après la fin de conservation.

Le même écart existe pour le **droit à l’effacement** et pour la
**suppression d’un tenant** (`DROP SCHEMA` + objets), énoncés ensemble dans
TODO §22.

- **Affectés :** conformité / RGPD ; ops (espace object store) ;
  `TENANT_ADMIN` (politique déjà configurable en §21).
- **Situation actuelle :**
  - `DocumentStoragePort` (adapters **S3** + **filesystem**) : `store` /
    `retrieve` / `exists` — **pas de `delete`**.
  - `PurgeOutboxStatus` = `PENDING` seulement ; UNIQUE(`dossier_id`).
  - Pas de cascade applicative (éléments, relations, workflow links).
- **Réf. :** TODO §22, DEFERRED §0 (jamais MinIO en dur),
  `analysis doc/12-autorisation-conformite.md`,
  `analysis doc/07-generation-documents.md`.

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Effacer **conjointement** le graphe PostgreSQL d’un dossier éligible et
  les objets de stockage correspondants (`storageRef` des éléments
  `ATTACHMENT` / `PRODUCED_DOC`).
- Rendre l’opération **rejouable** (idempotente) : objet ou ligne déjà
  absents = succès, pas d’état « à moitié purgé » durable sans retry.
- Respecter DEFERRED §0 : port abstrait **S3 + filesystem**, aucun
  couplage MinIO.
- Consommer le signal déjà produit par §21 (`purge_outbox`) — sauf si un
  arbitrage Phase 1 choisit explicitement un autre déclencheur.

### Secondary goals

- Documenter (runbook) la purge **tenant** vs purge **dossier** pour ne
  pas les confondre (décision n° 13 : 1 instance = 1 tenant).
- Laisser des logs / accroches métriques utilisables par `observabilite`
  (§29) sans livrer ce lot.

### Non-goals (hors débat §22 — déjà hors V1 produit)

- OpenAPI exhaustif / pagination admin large (§24).
- Antivirus pièces jointes.
- Connecteur `http-rest` SERVICE ; rapports / SLA.
- Changer la politique de rétention ou l’UI admin types (déjà §21).
- Observabilité MDC complète (§29).

Les non-goals **liés à §22** (UI file, API d’effacement manuel, purge
tenant automatisée, orphelins hors outbox, mécanisme exact) sont des
**questions ouvertes**, pas des exclusions actées.

## Alternatives considérées (mécanisme — TODO §22)

Trois options nommées par TODO.md / FILE-ATTENTE / REVUE §9. **Aucun
verdict Phase 1 tant que Q1 n’est pas tranchée.**

| Id | Approche | Trade-off |
|---|---|---|
| A | **Transaction applicative** unique PG + store | Simple à raisonner ; pas de XA : S3/FS non transactionnel → orphelins ou rollback partiel |
| B | **Job de réconciliation** sur `purge_outbox` | Réutilise §21 ; rejeu ; latence asynchrone ; besoin de claim / statuts |
| C | **Suppression logique + balayage** | §21 a déjà `ARCHIVE` + `purgeEligibleAt` ; sans delete physique, l’effacement n’est pas réel |

Recommandation **à confirmer** : **B** (le job consomme l’outbox déjà
livrée). A est fragile cross-store. C ne clôt pas le droit à l’effacement.

## User Stories & Use Cases

1. **En tant que** plateforme, **je veux** qu’un dossier en fin de
   rétention disparaisse réellement (fichiers + lignes), **afin de**
   honorer la conservation limitée.
2. **En tant que** ops, **je veux** pouvoir rejouer un échec de purge,
   **afin de** ne pas laisser d’orphelins silencieux.
3. **En tant que** conformité, **je veux** qu’après purge réussie le
   dossier ne soit plus lisible via l’API, **afin de** prouver
   l’effacement.
4. **En tant que** ops, **je veux** distinguer purge dossier et
   décommission tenant, **afin de** ne pas utiliser le mauvais outil.

### Edge cases (à figer après Q1)

- Objet store déjà absent.
- Dossier PG déjà partiellement supprimé.
- Échec mid-flight (store OK, PG KO ou l’inverse).
- Éléments sans `storageRef` (`NOTE`, `INFORMATION`, `LINK`).
- Relations où le dossier est source **ou** cible.
- Process Flowable encore vivant lié au dossier.
- Deux workers concurrents sur la même ligne outbox.
- `purgeEligibleAt` dans le futur (donnée incohérente).
- Droit à l’effacement **sans** passer par l’horloge rétention (si Q
  « erasure » = in scope).

## Success Criteria
**How will we know when we're done?**

Critères **stables** (indépendants du mécanisme exact) :

- [ ] Un dossier éligible + PJ/PDF : objets absents du store **et**
      lignes dossier / éléments / relations le touchant / workflow links
      absentes ; `GET /api/dossiers/{id}` → 404.
- [ ] Rejeu : 2ᵉ passe sans corruption (idempotente).
- [ ] Échec store ou PG : pas de succès terminal prématuré ; retry possible.
- [ ] Aucun couplage MinIO ; hexagone / ArchUnit respectés.
- [ ] TODO §22 / FILE-ATTENTE / DEFERRED mis à jour **après** arbitrages
      + implémentation.
- [ ] `./mvnw verify` vert ; FE selon impact SPA (Vitest + Playwright si
      contrat/UI touchés).

Critères **dépendants de Q1** (à réécrire une fois tranché) : job vs
transaction vs balayage ; cycle de statuts outbox ; surface HTTP/UI.

## Constraints & Assumptions

**Contraintes stables (déjà actées ailleurs) :**

- Hexagone : domaine pur ; JPA / SDK S3 dans infrastructure.
- Une instance Boot = un tenant (`app.tenant.id`) — schéma courant.
- DEFERRED §0 : adapters S3 + filesystem existants ; pas MinIO en dur.
- Outbox §21 : UNIQUE(`dossier_id`) ; payload
  `{tenantId,dossierId,typeCode,purgeEligibleAt}`.
- SPA même origine + CSRF (décisions n° 15 / 27).
- Task tracing `ai-devkit task` indisponible.
- Branche Cloud : `cursor/purge-stockage-05b8` (lint `feature-*` = MISS
  attendu).

**Pas d’assumption produit sur le mécanisme** tant que Q1 n’est pas
tranchée. Ne pas traiter la mémoire « job + delete acté Phase 2 » comme
une décision stakeholder.

## Questions & Open Items

À trancher **une par une** (Phase 1). Rien n’est clos.

| Id | Question | Pourquoi ça compte | Options | Reco |
|---|---|---|---|---|
| **Q1 / D-MECH** | Quel mécanisme pour la cohérence PG + store ? | Cœur de TODO §22 | A transaction · B job outbox · C soft-delete + balayage | **B** |
| **Q2 / D-PORT** | Comment supprimer un objet ? | DEFERRED §0 | Étendre `DocumentStoragePort.delete` · nouveau port | Étendre le port existant |
| **Q3 / D-ORDER** | Ordre d’effacement store vs PG ? | Orphelins / retry | Store puis PG · PG puis store · best-effort parallèle | Store puis PG |
| **Q4 / D-SCOPE-PG** | Quel graphe PG supprimer ? | FK / RGPD | Dossier seul · + éléments/relations/links | Graphe complet du dossier |
| **Q5 / D-WF** | Que faire des process Flowable ? | Runtime vs historique | Best-effort runtime · bloquer si PI vivante · ignorer | Best-effort runtime |
| **Q6 / D-UI** | Surface admin file / force purge ? | Parcours utilisateur | Aucune · écran lecture · force admin | Aucune V1 (aligné §21) |
| **Q7 / D-HTTP** | API d’effacement immédiat ? | Droit à l’effacement hors rétention | Aucune · `DELETE` admin · job only | Aucune V1 |
| **Q8 / D-TENANT** | Purge tenant dans ce lot ? | §22 mélange dossier et `DROP SCHEMA` | Hors code + runbook · automatiser | Hors code + runbook |
| **Q9 / D-ORPHAN** | Balayer orphelins hors outbox ? | Uploads échoués | Hors V1 · job séparé | Hors V1 |
| **Q10 / D-FE** | Nouveaux E2E Playwright ? | Conventions FE | Exemption si zéro SPA · specs dédiés | Non-régression suite existante |

### Hors file (pas des questions de ce lot)

OpenAPI §24, observabilité §29, antivirus, `http-rest` SERVICE.

## Phase 1 — clarification en cours

Shared understanding **avant** toute clôture :

- Nom : `purge-stockage`.
- Problème : outbox §21 sans effacement physique.
- Objectif : cohérence PostgreSQL + object store.
- Mécanisme **non tranché** (Q1).

Prochaine clarification : **Q1 / D-MECH** uniquement.
