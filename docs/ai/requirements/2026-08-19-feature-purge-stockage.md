---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: purge-stockage
phase1: 2026-08-19
phase2: 2026-08-19
phase2_status: in-review
todo_ref: TODO.md §22 · FILE-ATTENTE.md · DEFERRED-FEATURES.md §0 · suite de retention-dossier-type
---

# Requirements & Problem Understanding — `purge-stockage`

> Feature produit après `retention-dossier-type` (PR #55). Lot « avant
> prod » REVUE §9 / TODO §22. **Q1–Q10 actées** (Phase 1). Phase 2 :
> revue — **une** question produit restante (**Q11**) bloque `reviewed`.

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

### Non-goals (actés)

- OpenAPI exhaustif / pagination admin large (§24).
- Antivirus pièces jointes.
- Connecteur `http-rest` SERVICE ; rapports / SLA.
- Changer la politique de rétention ou l’UI admin types (déjà §21).
- Observabilité MDC complète (§29).
- Purge tenant automatisée (**Q8 = A**).
- GC orphelins store hors outbox (**Q9 = A**).
- Purge synchrone dans la requête HTTP (**Q7 ≠ C**).
- Bouton force purge / retry sur l’écran file (**Q6 = B**).

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

### Edge cases

- Objet store déjà absent → succès idempotent.
- Dossier PG déjà partiellement supprimé → finaliser outbox terminal.
- Échec mid-flight → pas de `COMPLETED` ; retry job.
- Éléments sans `storageRef` → skip store, supprimer lignes PG.
- Relations source **ou** cible.
- Process Flowable vivant → Q5 best-effort.
- Deux workers → claim atomique (A-CLAIM, Phase 3).
- `purgeEligibleAt` dans le futur vs enqueue Q7 → **Q11** (ouverte).

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

**Q1–Q10 actées.** Phase 2 en revue : **Q11 ouverte** (périmètre enqueue
vs garde `purgeEligibleAt`). Assumptions techniques proposées ci-dessous
— non bloquantes si Q11 est tranchée.

**Assumptions techniques (A-*, à confirmer en Phase 3 si besoin) :**

- **A-STATUS :** `PENDING` / `PROCESSING` / `COMPLETED` / `FAILED`.
- **A-CLAIM :** update atomique vers `PROCESSING` (anti double-purge).
- **A-RETRY :** `FAILED` repris au batch suivant (pas de bouton UI).
- **A-KEEP-OUTBOX :** ligne `COMPLETED` conservée (UNIQUE `dossier_id`).
- **A-AUTHZ :** `TENANT_ADMIN` pour GET file + mutation enqueue.
- **A-ENQUEUE-UI :** enqueue = API seulement (pas de bouton sur l’écran
  file, Q6).

## Questions & Open Items

À trancher **une par une**. Q1–Q10 clos (Phase 1).

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

### Ouvertes (Phase 2 — une à la fois)

| Id | Question | Pourquoi ça bloque | Options | Reco |
|---|---|---|---|---|
| **Q11 / D-ENQUEUE-SCOPE** | Quels dossiers l’admin peut-il enfiler, et le job honore-t-il `purgeEligibleAt` ? | Contradiction story Q7 (« sans attendre l’horloge ») vs edge « eligible dans le futur → skip » | Voir message Phase 2 | **B** |

### Hors file (pas des questions de ce lot)

OpenAPI §24, observabilité §29, antivirus, `http-rest` SERVICE.

## Phase 2 — revue (2026-08-19)

Template README : sections présentes (problem, goals, stories, success,
constraints, questions).

**Validé :** Q1–Q10 cohérents entre elles ; non-goals alignés ; stories
1–6 couvertes par success criteria (sauf Q11).

**Écart trouvé :** story 6 / Q7 (« sans attendre l’horloge ») vs garde
« `purgeEligibleAt` futur → skip ». Bloque `reviewed` jusqu’à **Q11**.

A-* techniques listés plus haut : pas de question produit supplémentaire
si Q11 est tranchée.

Status : `draft` / `phase2_status: in-review`.
