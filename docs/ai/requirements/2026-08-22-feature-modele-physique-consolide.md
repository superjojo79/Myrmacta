---
phase: requirements
title: Requirements & Problem Understanding
description: Clarify the problem space, gather requirements, and define success criteria
status: reviewed
created: 2026-08-22
updated: 2026-08-22
feature: modele-physique-consolide
phase1: 2026-08-22
phase2: 2026-08-22
stakeholder_decisions: 2026-08-22
todo_ref: TODO.md §25 · FILE-ATTENTE.md (priorité 1, dernière feature de la file)
---

# Requirements & Problem Understanding — `modele-physique-consolide`

> Requirements **reviewed** (Phase 2), 3 décisions actées, aucun point
> ouvert bloquant. Feature **doc pure** — aucun changement de code, de
> schéma, ou de comportement.

## Problem Statement

TODO §25 constate qu'une dizaine de tables métier, des index uniques
partiels critiques, `outbox_event`, `dossier_type_acl` (décision n° 20),
le stockage des DEK chiffrées et les tables `ACT_*` de Flowable
coexistent sans vue consolidée ni règle de répartition explicite entre
le schéma du tenant et un schéma `platform`. Le TODO demande aussi de
documenter un `TenantRegistry` (« registre des tenants actifs ») et sa
politique de rafraîchissement de cache.

**Vérifié par lecture de code (2026-08-22) — écart trouvé avant de
commencer :**

- **17 tables métier** existent réellement dans les changelogs
  Liquibase (`connector_instance`, `document_template`, `dossier`,
  `dossier_element`, `dossier_relation`, `dossier_type`,
  `dossier_type_acl`, `dossier_workflow_link`, `notification_outbox`,
  `notification_webhook`, `outbox_event`, `purge_outbox`,
  `tenant_connector_dek`, `tenant_retention_settings`,
  `webhook_idempotency`, `workflow_idempotency`, `workflow_type_config`)
  — toutes dans le schéma unique du tenant (`app.tenant.id`), aucun
  schéma `platform` n'existe dans le code.
- **4 index uniques partiels** existent (`uq_dossier_workflow_link_active`,
  `uq_template_active`, `uq_notification_webhook_tenant_name_active`,
  `uq_connector_instance_tenant_name_active`), pas « deux » comme
  l'énoncé du TODO le laissait entendre.
- **Aucune classe `TenantRegistry` n'existe dans le code.** La décision
  n° 1 du TODO porte une note de mise à jour explicite : le « registre
  des tenants actifs consulté à chaque résolution » qu'elle décrivait
  à l'origine **« n'a plus lieu d'être »** depuis la décision n° 13
  (une instance Spring Boot dédiée par tenant, schéma fixé une fois au
  démarrage — voir
  [02-multi-tenant.md](../../specs/02-multi-tenant.md)).
  Le TODO §25 n'a jamais été mis à jour après cette note — écart de
  documentation pure, pas un gap de code.
- **Aucun contexte Liquibase `tenant-init`/`tenant-migration` séparé
  n'existe.** Un seul changelog unifié (`db.changelog-master.yaml`)
  est appliqué à chaque démarrage d'instance ; `02-multi-tenant.md`
  documente déjà explicitement que cette distinction « n'a plus lieu
  d'être » depuis la décision n° 13 (suivi natif `DATABASECHANGELOG`,
  idempotent que le schéma soit neuf ou déjà à jour).
- **`platform.tenant_migration_status`** est mentionnée dans
  `02-multi-tenant.md` comme une option d'observabilité possible
  (« peut néanmoins être conservée »), jamais actée ni implémentée —
  pas de schéma `platform`, pas de changelog correspondant.

## Goals & Objectives

### Primary goals

- Produire un modèle physique consolidé (ERD mermaid) des 17 tables
  métier réellement existantes : colonnes clés, clés étrangères,
  les 4 index uniques partiels et leur rôle métier, répartition
  actuelle (un seul schéma par tenant, pas de `platform`).
- Documenter `dossier_type_acl` (décision n° 20), le stockage chiffré
  des DEK (`tenant_connector_dek`, décision n° 4), `outbox_event`
  (relais événementiel), dans ce modèle consolidé.
- Corriger l'écart de documentation trouvé : retirer la mention d'un
  `TenantRegistry` actif et de contextes Liquibase séparés, remplacés
  par un renvoi explicite vers la décision n° 13 et son état actuel.
- Mentionner les tables `ACT_*`/`ACT_RU_*`/`ACT_HI_*` de Flowable sans
  les énumérer (schéma géré entièrement par `flowable-engine`, hors
  périmètre des changelogs FlowForge) — renvoi vers
  [05-workflow-engine.md](../../specs/05-workflow-engine.md).
- Clore TODO §25.

### Non-goals (actés)

- **Aucun changement de code, de schéma, ou de comportement** — feature
  strictement documentaire (Phase 1, confirmé avec l'utilisateur).
- **Pas de création de `platform.tenant_migration_status`** — resterait
  une option non retenue, documentée comme telle plutôt qu'implémentée
  (chevaucherait `observabilite`, PR #59, déjà livrée).
- **Pas de dump SQL consolidé** — le DDL exécutable existant
  (changelogs Liquibase) reste l'unique source de vérité ; le dupliquer
  créerait un risque de divergence à chaque changeset futur.
- **Pas de modification de `02-multi-tenant.md`** au-delà d'un lien
  croisé — le nouveau document se concentre sur l'inventaire physique
  complet, pas sur la stratégie multi-tenant déjà documentée.

## User Stories & Use Cases

1. **En tant que** développeur rejoignant le projet, **je veux** une
   vue d'ensemble du schéma physique (tables, clés, index critiques)
   **afin de** comprendre la structure des données sans devoir
   reconstituer le schéma changeset par changeset.
2. **En tant que** mainteneur, **je veux** que la documentation
   n'affirme plus l'existence d'un `TenantRegistry` ou de contextes
   Liquibase abandonnés, **afin de** ne pas induire en erreur sur
   l'architecture réellement en place.
3. **En tant qu'** opérateur évaluant une évolution du modèle de
   données, **je veux** savoir précisément quels index uniques
   partiels sont critiques et pourquoi, **afin de** ne pas les casser
   accidentellement lors d'une migration future.

### Edge cases

Aucun — feature documentaire, pas de chemin d'exécution à couvrir.

## Success Criteria

- [ ] Nouveau document `docs/specs/13-modele-physique.md` : ERD
      mermaid des 17 tables, référencé dans `00-index.md`.
- [ ] Les 4 index uniques partiels décrits avec leur rôle métier
      (quelle invariant chacun protège).
- [ ] `dossier_type_acl`, `tenant_connector_dek`, `outbox_event`
      documentés avec renvoi vers leurs décisions respectives (n° 20,
      n° 4) et leur doc d'origine.
- [ ] Répartition tenant/`platform` clarifiée : un seul schéma par
      tenant aujourd'hui, pas de schéma `platform` en place ;
      `tenant_migration_status` documentée comme option non retenue.
- [ ] Aucune mention active d'un `TenantRegistry` consulté à chaque
      requête ni de contextes Liquibase `tenant-init`/`tenant-migration`
      séparés — remplacée par un renvoi à la décision n° 13.
- [ ] TODO §25 clos.

## Constraints & Assumptions

**Contraintes stables :**

- Documentation uniquement — aucun changelog Liquibase, aucune classe
  Java, aucun test à ajouter ou modifier.
- Le DDL exécutable (changelogs) reste l'unique source de vérité ; le
  nouveau document renvoie vers les fichiers de changelog plutôt que
  de reproduire leur contenu SQL.

**Décisions produit (Phase 1, toutes actées 2026-08-22) :**

| Id | Sujet | Choix |
|---|---|---|
| **D-MPC-SCOPE** | Écart `TenantRegistry`/contextes Liquibase | Corriger le scope de §25 : documenter l'architecture réelle (décision n° 13), pas les mécanismes abandonnés |
| **D-MPC-PLATFORM** | Table `platform.tenant_migration_status` | Documentation seule — option non retenue, pas d'implémentation dans cette feature |
| **D-MPC-FORMAT** | Format et emplacement du livrable | Nouveau document `docs/specs/13-modele-physique.md`, ERD mermaid, référencé dans `00-index.md` |

## Questions & Open Items

### Clos

| Id | Décision | Choix |
|---|---|---|
| **D-MPC-SCOPE** | Écart TenantRegistry | Corriger, documenter l'architecture réelle (2026-08-22) |
| **D-MPC-PLATFORM** | Table platform | Documentation seule, pas d'implémentation (2026-08-22) |
| **D-MPC-FORMAT** | Format/emplacement | Nouveau doc 13, ERD mermaid (2026-08-22) |

### Ouvertes

Aucune décision produit ouverte. Un point de formatage mineur tranché
par défaut (pas une décision matérielle) : les tables Flowable
`ACT_*`/`ACT_RU_*`/`ACT_HI_*` sont mentionnées sans être énumérées
(schéma géré par `flowable-engine`, pas par les changelogs FlowForge),
avec renvoi vers `05-workflow-engine.md`.

### Hors file

Implémentation de `platform.tenant_migration_status`, tout changement
de schéma ou de code, modification de la stratégie multi-tenant
elle-même (déjà actée en décision n° 13).

## Phase 2 — revue close (2026-08-22)

Template README : OK (feature purement documentaire, sections
adaptées en conséquence). **Validé :** 3 décisions actées via
`AskUserQuestion` (une à la fois), dont une trouvée avant même
d'écrire le problem statement — l'écart entre TODO §25 (qui suppose un
`TenantRegistry` actif) et la décision n° 1 mise à jour /
`02-multi-tenant.md` (qui déclarent ce mécanisme obsolète depuis la
décision n° 13), signalé et tranché avant de commencer plutôt que
documenté silencieusement comme si le TODO avait raison. Précédent de
la feature `fiabilisation-tests-plateforme` (TODO §26, deux points sur
trois déjà résolus et jamais mis à jour) directement applicable ici.

Status : **`reviewed`**. Prochaine phase : `dev-design` (Phase 3).
