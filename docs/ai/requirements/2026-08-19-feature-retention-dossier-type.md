---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-19
updated: 2026-08-19
feature: retention-dossier-type
phase1: 2026-08-19
phase2: 2026-08-19
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

- Configurer une **durée de rétention par défaut du tenant** (persistée,
  valeur > 0 jours) — **prérequis** pour que le job archive (D-BOOTSTRAP).
- Permettre un **override par `DossierType`** : durée en jours, `NEVER`, ou
  héritage (`null`).
- À expiration (après `closedAt` + durée effective) : passer le dossier en
  **`ARCHIVE`**, poser **`purgeEligibleAt`**, écrire un événement
  **`purge_outbox`** (`DOSSIER_PURGE_REQUESTED`) **au plus une fois** par
  dossier (idempotence).
- Exposer **API + UI admin** pour le défaut tenant et les champs type.
- Ne **pas** supprimer lignes PG ni objets stockage (port S3/FS) — §22.

### Secondary goals

- Job planifié idempotent (rejeu sûr).
- Réutiliser les **patterns** outbox (table dédiée, statuts PENDING…) sans
  coupler au relay Flowable / notifications.
- Vitest + Playwright pour la surface admin SPA.
- Documenter la suite `purge-stockage` dans DEFERRED / FILE-ATTENTE.

### Non-goals

- Purge physique PostgreSQL / object store (§22).
- Suppression de tenant (`DROP SCHEMA` + décommission instance).
- Écran supervision de la file de purge / compteurs outbox.
- Archivage anticipé « force purge » (admin).
- Seed Liquibase d’une durée « magique » (D-BOOTSTRAP).
- Antivirus PJ, OpenAPI exhaustif (§24), observabilité MDC (§29).
- Connecteur `http-rest` SERVICE ; rapports / SLA.
- Nouvelles transitions de réouverture (`CLOTURE` → actif).

## Alternatives considérées

| Approche | Trade-off | Verdict |
|---|---|---|
| Rétention seule (`ARCHIVE`) sans outbox | §22 devrait recalculer | Écarté — stakeholder **B** : outbox dès ce lot |
| Purge physique dans le même lot | Couple §21–§22, port S3 | Écarté — stakeholder **A** action |
| Durée globale tenant seulement | Trop grossier | Écarté — stakeholder **B** durée |
| Horloge depuis `createdAt` | Purge de dossiers actifs | Écarté — stakeholder **A** horloge |
| Seed Liquibase (ex. 7 ans) | Config implicite, faux sentiment de conformité | Écarté — **D-BOOTSTRAP** |
| Recalcul §22 sans outbox | Moins traçable | Écarté — outbox dès §21 |

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
- Override héritage : durée = défaut tenant.
- Dossier non `CLOTURE` : ignoré par le job.
- `CLOTURE` sans `closedAt` (données incohérentes) : **ignoré** (A-NULL-CLOSEDAT).
- Déjà `ARCHIVE` avec `purgeEligibleAt` et/ou outbox existant : **no-op**
  (idempotent ; pas de 2ᵉ event).
- `ARCHIVE` via transition statut existante **sans** job : pas de
  `purgeEligibleAt` / outbox ; le job **ne reprend pas** ces dossiers
  (uniquement `CLOTURE` expirés) — D-FORCE.
- Settings absents : job **no-op** (warn) jusqu’au premier PUT — D-BOOTSTRAP.
- Types existants après migration : colonnes rétention = héritage
  (A-MIGRATE-TYPES).
- Changement de politique (raccourcir / allonger / NEVER↔jours) : s’applique
  au **prochain** run pour les `CLOTURE` encore non éligibles ;
  dossiers déjà `ARCHIVE`+outbox **inchangés** (A-POLICY-CHANGE).
- Réouverture depuis `CLOTURE` : **pas de transition autorisée aujourd’hui**
  (A-REOPEN) ; règle métier figée pour plus tard.

## Success Criteria
**How will we know when we're done?**

- [ ] `PUT` settings tenant accepte seulement `defaultRetentionDays > 0` ;
      `GET` reflète la valeur (404 si jamais configuré — D-BOOTSTRAP).
- [ ] `DossierType` : héritage / jours / `NEVER` via API create/update/get/list.
- [ ] Job : `CLOTURE` expiré → `ARCHIVE` + `purgeEligibleAt` + **une** ligne
      `purge_outbox` `DOSSIER_PURGE_REQUESTED` `PENDING` (contrainte
      d’unicité métier par dossier).
- [ ] Job idempotent ; `NEVER` / non-clos / `closedAt` null / settings absents
      exclus ou no-op.
- [ ] UI admin : section défaut tenant + champs rétention sur types
      (A-UI-LOC).
- [ ] Transition BPMN/API vers `ARCHIVE` sans job ≠ outbox purge ; non
      reprise ultérieure par le job.
- [ ] Aucune suppression objet stockage / DROP données dossier.
- [ ] Tests domaine + IT/MockMvc + Vitest + Playwright verts ;
      `./mvnw verify` non-régression.
- [ ] Décisions §21 documentées (déjà) ; FILE-ATTENTE / DEFERRED à jour en
      fin d’implémentation ; §22 reste ouvert pour `purge-stockage`.

## Constraints & Assumptions

- Hexagone : domaine pur ; JPA dans infrastructure ; ArchUnit CI.
- Une instance Boot = un tenant (`app.tenant.id`) — settings dans le
  **schéma** courant.
- Unité de durée : **jours calendaires entiers > 0** (A-UNIT).
- Rôles : config rétention (settings + champs type) réservée à
  **`TENANT_ADMIN`** (A-AUTHZ). Les autres rôles n’administrent pas la
  politique ; le job est un acteur système (A-JOB-ACTOR).
- SPA même origine + CSRF (décisions n° 15 / 27).
- Port stockage abstrait S3/FS déjà figé pour §22 (DEFERRED §0) — hors code
  ici.
- **A-REOPEN :** pas de nouvelle transition de réouverture dans ce lot. Si
  une feature future autorise `CLOTURE` → actif : effacer `closedAt`,
  annuler l’horloge ; `ARCHIVE`+outbox déjà écrits restent immutables.
- **A-CLOCK :** expiration = `closedAt.plus(Period.ofDays(effectiveDays))`
  en UTC ; candidat si `expiration <= now`.
- **A-NULL-CLOSEDAT :** `CLOTURE` + `closedAt == null` → skip job.
- **A-MIGRATE-TYPES :** migration ajoute colonnes avec défaut héritage
  (`retentionNever=false`, `retentionDays=null`).
- **A-POLICY-CHANGE :** config mutable ; éligibilité déjà matérialisée
  (`ARCHIVE`+outbox) non recalculée / non annulée.
- **A-JOB-ACTOR :** `updatedBy` / audit de l’archivage rétention =
  identifiant système stable (ex. `system:retention-job`), pas un user OIDC.
- **A-UI-LOC :** UI = bloc « Rétention tenant » accessible depuis l’admin
  (même zone que les types ou entrée admin dédiée minimale) + champs sur
  create/edit type dans `DossierTypesAdminView` (ou sous-composant). Pas de
  nouvelle app shell.
- Task tracing `ai-devkit task` indisponible (`unknown command 'task'`).
- Branche Cloud : `cursor/retention-dossier-type-9ed2` (lint worktree
  `feature-*` = MISS attendu).

## Stakeholder decisions

| Id | Décision | Choix |
|---|---|---|
| **D-DURATION** | Modèle de durée | Défaut tenant (> 0) + override type (jours \| `NEVER` \| héritage) |
| **D-ACTION** | À expiration | `ARCHIVE` + `purgeEligibleAt` + `purge_outbox` ; pas de purge physique |
| **D-CLOCK** | Début d’horloge | `closedAt` à l’entrée en `CLOTURE` ; non clos = pas d’expiration |
| **D-NEVER** | Illimité | Override type `NEVER` ; défaut tenant toujours > 0 une fois configuré |
| **D-OUTBOX** | File de purge | Table `purge_outbox` + `DOSSIER_PURGE_REQUESTED` ; **≤ 1** event utile / dossier ; worker = §22 |
| **D-UI** | Surface UI | Config défaut tenant + champs sur admin types ; pas d’écran file |
| **D-REOPEN** | Réouverture | Règle documentée ; pas d’implémentation de transition |
| **D-FORCE** | Archivage anticipé rétention | Hors scope ; `ARCHIVE` métier ≠ éligibilité ; job ne reprend pas ces dossiers |
| **D-BOOTSTRAP** | Settings initiaux | **Pas de seed** ; GET 404 si absent ; job no-op jusqu’au premier PUT admin |

## Questions & Open Items

### Clos Phase 2 (2026-08-19)

Toutes les décisions D-* (stakeholders Phase 1 + **D-BOOTSTRAP** tranché
en revue). Assumptions A-* ci-dessus — aucune question produit ouverte.

### Reporté hors feature

- Mécanisme purge cohérente PG + object store (TODO §22) — `purge-stockage`
  (transaction vs job vs soft-delete ; consommation outbox).
- Supervision outbox / force admin.
- Réouverture métier depuis `CLOTURE` / `ARCHIVE`.
- OpenAPI formel, pagination admin large (§24).
