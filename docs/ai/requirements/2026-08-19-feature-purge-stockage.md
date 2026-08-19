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
- Consommer `purge_outbox` (`DOSSIER_PURGE_REQUESTED`) via un **job de
  réconciliation** idempotent (**Q1 = B**).

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

Les non-goals **liés à §22** restants (UI file, API d’effacement manuel,
purge tenant automatisée, orphelins hors outbox) sont encore des
**questions ouvertes** (Q6–Q9). Le mécanisme (Q1) est tranché : **B**.

## Alternatives considérées (mécanisme — TODO §22)

Trois options nommées par TODO.md / FILE-ATTENTE / REVUE §9.

| Id | Approche | Trade-off | Verdict |
|---|---|---|---|
| A | **Transaction applicative** unique PG + store | Pas de XA : S3/FS non transactionnel | **Écarté** |
| B | **Job de réconciliation** sur `purge_outbox` | Réutilise §21 ; rejeu ; latence asynchrone | **Retenu (Q1, 2026-08-19)** |
| C | **Suppression logique + balayage** | Marquage §21 déjà là ; pas d’effacement réel | **Écarté** |

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
5. **En tant que** `TENANT_ADMIN`, **je veux** voir l’état de la file de
   purge (compteurs / liste lecture), **afin de** savoir si le job
   décroche — **sans** bouton force purge dans l’UI.
6. **En tant que** `TENANT_ADMIN`, **je veux** demander l’effacement d’un
   dossier via API (enfilement outbox), **afin de** honorer un droit à
   l’effacement sans attendre l’horloge rétention — le job exécute
   ensuite.

### Edge cases (à préciser avec Q2–Q5 ; le déclencheur est le job outbox)

- Objet store déjà absent.
- Dossier PG déjà partiellement supprimé.
- Échec mid-flight (store OK, PG KO ou l’inverse).
- Éléments sans `storageRef` (`NOTE`, `INFORMATION`, `LINK`).
- Relations où le dossier est source **ou** cible.
- Process Flowable encore vivant lié au dossier.
- Deux workers concurrents sur la même ligne outbox.
- `purgeEligibleAt` dans le futur (donnée incohérente).
- Droit à l’effacement hors horloge : **Q7 = B** — enfilement outbox
  (idempotent si déjà `PENDING` / déjà purgé).

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
- [ ] `./mvnw verify` vert.
- [ ] Vitest + Playwright : specs dédiés lecture file + AuthZ + enqueue
      (Q10 = B) ; suite existante (E2E-RET-*) verte.

Critères **dépendants de Q1=B** (détail cycle/claim en Phase 3) :

- [ ] Job planifié borné consomme `PENDING` jusqu’à un état terminal
      (succès ou échec rejouable).

Critères **dépendants de Q6=B** :

- [ ] UI admin lecture : compteurs (et/ou liste) `PENDING` / `FAILED` /
      `COMPLETED` ; pas d’action force/retry ; rôle `TENANT_ADMIN`.
- [ ] API **lecture** de la file (nécessaire au SPA).

Critères **dépendants de Q7=B** :

- [ ] Mutation admin (`TENANT_ADMIN`) enfile `PENDING` (202 / 204 /
      équivalent) ; **pas** de purge synchrone dans la requête.
- [ ] Idempotent si une ligne outbox existe déjà pour le dossier.

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

**Q1–Q10 actées.** Clarifications Phase 1 **terminées**. Revue = Phase 2
(pas démarrée).

## Questions & Open Items

À trancher **une par une** (Phase 1).

### Clos

| Id | Décision | Choix |
|---|---|---|
| **Q1 / D-MECH** | Mécanisme PG + store | **B** — job de réconciliation sur `purge_outbox` (2026-08-19) |
| **Q2 / D-PORT** | Suppression objet | **A** — étendre `DocumentStoragePort.delete` (S3 + FS) |
| **Q3 / D-ORDER** | Ordre d’effacement | **A** — store d’abord, puis PostgreSQL |
| **Q4 / D-SCOPE-PG** | Graphe PG | **A** — dossier + éléments + relations src\|tgt + workflow links |
| **Q5 / D-WF** | Flowable | **A** — best-effort delete PI runtime ; link métier toujours supprimé ; ACT_HI_* résiduel OK V1 |
| **Q6 / D-UI** | Surface admin | **B** — écran lecture (compteurs / liste) ; pas de force purge |
| **Q7 / D-HTTP** | Mutation d’effacement | **B** — POST/DELETE admin qui **enfile** un event `PENDING` ; le job exécute (pas de purge synchrone HTTP) |
| **Q8 / D-TENANT** | Purge tenant | **A** — hors code ; runbook ops (décommission + `DROP SCHEMA` + préfixe store) |
| **Q9 / D-ORPHAN** | Orphelins hors outbox | **A** — hors V1 ; seulement ce que `purge_outbox` pointe |
| **Q10 / D-FE** | Tests FE | **B** — specs Playwright dédiés (lecture file + AuthZ + enqueue API/mock) + suite existante verte |

### Ouvertes

Aucune. Prochaine étape cycle : **Phase 2** (revue requirements).

### Hors file (pas des questions de ce lot)

OpenAPI §24, observabilité §29, antivirus, `http-rest` SERVICE.

## Phase 1 — clarifications terminées (2026-08-19)

Shared understanding (Q1–Q10) :

- Nom : `purge-stockage`.
- Problème : outbox §21 sans effacement physique.
- Objectif : cohérence PostgreSQL + object store.
- **Q1 = B :** job consommateur `purge_outbox`.
- **Q2 = A :** `DocumentStoragePort.delete` (adapters existants).
- **Q3 = A :** store puis PostgreSQL (retry relit les `storageRef`).
- **Q4 = A :** graphe PG = dossier + éléments + relations src\|tgt +
  workflow links.
- **Q5 = A :** Flowable runtime best-effort ; historique ACT_HI_*
  résiduel acceptable V1.
- **Q6 = B :** écran admin lecture de la file ; pas de bouton force/retry.
- **Q7 = B :** mutation admin qui enfile un `PENDING` ; le job purge.
- **Q8 = A :** suppression de tenant hors code ; runbook ops uniquement.
- **Q9 = A :** pas de GC des orphelins store hors outbox (V1).
- **Q10 = B :** Playwright dédié (lecture + AuthZ + enqueue) + non-régression.

Status doc : `draft` jusqu’à la **Phase 2** (revue requirements).
