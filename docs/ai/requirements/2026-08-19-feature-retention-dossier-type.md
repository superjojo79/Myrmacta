---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: draft
created: 2026-08-19
updated: 2026-08-19
feature: retention-dossier-type
phase1: 2026-08-19
stakeholder_decisions: 2026-08-19
todo_ref: TODO.md §21 · FILE-ATTENTE.md · suite §22 purge-stockage
---

# Requirements & Problem Understanding — `retention-dossier-type`

> Prochaine feature produit après `edition-champs-dossier` (PR #53), selon
> [FILE-ATTENTE.md](../../../FILE-ATTENTE.md). Lot « avant prod » REVUE §9 /
> TODO §21. La purge physique PG + object store est **reportée** à
> `purge-stockage` (§22).

## Problem Statement
**What problem are we solving?**

FlowForge n’a **aucune politique de rétention** configurable : un dossier
`CLOTURE` peut rester indéfiniment sans passage automatique en `ARCHIVE` ni
marquage d’éligibilité à l’effacement. Les statuts `CLOTURE` / `ARCHIVE`
existent, mais sans horloge métier ni file pour une purge ultérieure.

- **Affectés :** `TENANT_ADMIN` (config), conformité / RGPD (conservation
  limitée), ops qui prépareront §22.
- **Situation actuelle :** `Dossier.closedAt` est posé à la clôture ;
  `DossierType` n’a pas de champs rétention ; pas de job ni d’outbox purge.
- **Réf. :** TODO §21, `analysis doc/12-autorisation-conformite.md`
  (rétention), `04-dossiers.md` (cycle de statut).

## Goals & Objectives
**What do we want to achieve?**

### Primary goals

- Configurer une **durée de rétention par défaut du tenant** (obligatoire, > 0).
- Permettre un **override par `DossierType`** : durée en jours, `NEVER`, ou
  héritage (`null`).
- À expiration (après `closedAt` + durée effective) : passer le dossier en
  **`ARCHIVE`**, poser **`purgeEligibleAt`**, écrire un événement
  **`purge_outbox`** (`DOSSIER_PURGE_REQUESTED`).
- Exposer **API + UI admin** pour le défaut tenant et les champs type.
- Ne **pas** supprimer lignes PG ni objets stockage (port S3/FS) — §22.

### Secondary goals

- Job planifié idempotent (rejeu sûr).
- Réutiliser les patterns outbox existants (workflow / notifications) sans
  coupler au worker Flowable.
- Vitest + Playwright pour la surface admin SPA.
- Documenter la suite `purge-stockage` dans DEFERRED / FILE-ATTENTE.

### Non-goals

- Purge physique PostgreSQL / object store (§22).
- Suppression de tenant (`DROP SCHEMA` + décommission instance).
- Écran supervision de la file de purge / compteurs outbox.
- Archivage anticipé « force purge » (admin).
- Antivirus PJ, OpenAPI exhaustif (§24), observabilité MDC (§29).
- Connecteur `http-rest` SERVICE ; rapports / SLA.

## Alternatives considérées

| Approche | Trade-off | Verdict |
|---|---|---|
| Rétention seule (`ARCHIVE`) sans outbox | §22 devrait recalculer | Écarté — stakeholder **B** : outbox dès ce lot |
| Purge physique dans le même lot | Couple §21–§22, port S3 | Écarté — stakeholder **A** action |
| Durée globale tenant seulement | Trop grossier | Écarté — stakeholder **B** durée |
| Horloge depuis `createdAt` | Purge de dossiers actifs | Écarté — stakeholder **A** horloge |

## User Stories & Use Cases

1. **En tant que** `TENANT_ADMIN`, **je veux** définir une durée de rétention
   par défaut du tenant, **afin que** tous les types héritent d’une règle.
2. **En tant que** `TENANT_ADMIN`, **je veux** surcharger la durée (ou
   `NEVER`) sur un `DossierType`, **afin de** coller aux délais légaux
   différents.
3. **En tant que** plateforme, **je veux** qu’un dossier `CLOTURE` dont le
   délai est écoulé passe en `ARCHIVE` avec outbox purge, **afin de**
   préparer l’effacement sans le faire encore.
4. **En tant que** concepteur BPMN, **je veux** pouvoir archiver métier
   (`CLOTURE`→`ARCHIVE`) **sans** créer d’éligibilité purge, **afin de**
   ne pas confondre archivage métier et RGPD.

### Edge cases

- Type `NEVER` : jamais d’éligibilité même après délai « théorique ».
- Override `null` : durée = défaut tenant.
- Dossier non `CLOTURE` : ignoré par le job.
- Déjà `ARCHIVE` avec `purgeEligibleAt` + outbox : no-op (idempotent).
- `ARCHIVE` via transition statut existante **sans** job : pas de
  `purgeEligibleAt` / outbox.
- Réouverture depuis `CLOTURE` : **pas de transition autorisée aujourd’hui**
  (voir A-REOPEN) ; règle métier figée pour plus tard.

## Success Criteria
**How will we know when we're done?**

- [ ] Défaut tenant persisté, obligatoire, durée > 0 (jours).
- [ ] `DossierType` : héritage / jours / `NEVER` via API create/update/get/list.
- [ ] Job : `CLOTURE` expiré → `ARCHIVE` + `purgeEligibleAt` + ligne
      `purge_outbox` `DOSSIER_PURGE_REQUESTED` `PENDING`.
- [ ] Job idempotent ; types `NEVER` exclus ; non-clos exclus.
- [ ] UI admin : écran/section défaut tenant + champs rétention sur types.
- [ ] Transition BPMN/API vers `ARCHIVE` sans job ≠ outbox purge.
- [ ] Aucune suppression objet stockage / DROP données dossier.
- [ ] Tests domaine + IT/MockMvc + Vitest + Playwright verts ;
      `./mvnw verify` non-régression.
- [ ] `TODO.md` §21 clos (décisions) ; FILE-ATTENTE / DEFERRED à jour.

## Constraints & Assumptions

- Hexagone : domaine pur ; JPA dans infrastructure ; ArchUnit CI.
- Une instance Boot = un tenant (`app.tenant.id`) — défaut « tenant » =
  settings dans le **schéma** courant.
- Unité de durée : **jours calendaires entiers > 0** (A-UNIT).
- Rôles : config rétention réservée à **`TENANT_ADMIN`** (A-AUTHZ).
- SPA même origine + CSRF (décisions n° 15 / 27).
- Port stockage abstrait S3/FS déjà figé pour §22 (DEFERRED §0) — hors code
  ici.
- **A-REOPEN :** `DossierStatusTransitions` n’autorise pas aujourd’hui
  `CLOTURE` → statut actif. Ce lot **n’ajoute pas** de réouverture. La règle
  « réouverture efface `closedAt` / annule l’horloge ; `ARCHIVE`+outbox
  immutables » est une **contrainte documentée** pour une feature future.
- **A-CLOCK :** expiration = `closedAt` + `Period.ofDays(effectiveDays)`
  en UTC (aligné références dossier).
- Task tracing `ai-devkit task` indisponible (`unknown command 'task'`).

## Stakeholder decisions

| Id | Décision | Choix |
|---|---|---|
| **D-DURATION** | Modèle de durée | Défaut tenant obligatoire + override type (jours \| `NEVER` \| héritage) |
| **D-ACTION** | À expiration | `ARCHIVE` + `purgeEligibleAt` + `purge_outbox` ; pas de purge physique |
| **D-CLOCK** | Début d’horloge | `closedAt` à l’entrée en `CLOTURE` ; non clos = pas d’expiration |
| **D-NEVER** | Illimité | Override type `NEVER` ; défaut tenant toujours > 0 |
| **D-OUTBOX** | File de purge | Table `purge_outbox` + event `DOSSIER_PURGE_REQUESTED` (worker = §22) |
| **D-UI** | Surface UI | Config défaut tenant + champs sur admin types ; pas d’écran file |
| **D-REOPEN** | Réouverture | Reset horloge si un jour autorisée ; `ARCHIVE`/outbox immutables |
| **D-FORCE** | Archivage anticipé rétention | Hors scope ; `ARCHIVE` métier ≠ éligibilité purge |

## Questions & Open Items

### Clos Phase 1 (stakeholders 2026-08-19)

Toutes les décisions D-* ci-dessus.

### Assumptions nommées (pas de question ouverte)

- A-UNIT, A-AUTHZ, A-REOPEN, A-CLOCK.

### Reporté hors feature

- Mécanisme purge cohérente PG + object store (TODO §22) — feature
  `purge-stockage`.
- Supervision outbox / force admin.
- Réouverture métier depuis `CLOTURE` / `ARCHIVE`.
