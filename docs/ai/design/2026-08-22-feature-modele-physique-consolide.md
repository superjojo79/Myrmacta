---
phase: design
title: System Design & Architecture
description: Define the technical architecture, components, and data models
status: reviewed
created: 2026-08-22
updated: 2026-08-22
feature: modele-physique-consolide
phase3: 2026-08-22
requirements_ref: docs/ai/requirements/2026-08-22-feature-modele-physique-consolide.md
---

# System Design & Architecture — `modele-physique-consolide`

> Design **reviewed** (Phase 3). Feature documentaire — le "design"
> ici est la structure du document `13-modele-physique.md` lui-même
> (regroupement, notation, contenu), pas une architecture de code.
> Aucun point ouvert bloquant ; aucune décision technique
> supplémentaire nécessaire au-delà des 3 décisions produit actées en
> Phase 1.

## Contenu extrait des changelogs (source de vérité, vérifié 2026-08-22)

17 tables regroupées par domaine fonctionnel (regroupement utilisé pour
l'ERD, pas un schéma physique — un seul schéma par tenant) :

**Dossiers** — `dossier`, `dossier_type`, `dossier_type_acl`,
`dossier_element`, `dossier_relation`
**Workflow** — `dossier_workflow_link`, `workflow_type_config`,
`workflow_idempotency`, `outbox_event`
**Connecteurs** — `connector_instance`, `tenant_connector_dek`,
`webhook_idempotency`
**Documents** — `document_template`
**Notifications** — `notification_outbox`, `notification_webhook`
**Rétention / purge** — `tenant_retention_settings`, `purge_outbox`

**Clés étrangères déclarées au niveau DB** (`addForeignKeyConstraint`) —
seules deux existent, toutes deux sur `dossier_relation` :
`fk_dossier_relation_source`/`fk_dossier_relation_target` →
`dossier.id`. **Toutes les autres relations inter-tables
(`dossier_id`, `tenant_id`, etc.) sont des clés étrangères logiques,
non contraintes au niveau base** — cohérent avec l'architecture
hexagonale (le domaine ne dépend pas d'un schéma relationnel rigide) ;
à noter explicitement dans l'ERD pour ne pas laisser croire à une
intégrité référentielle DB qui n'existe pas.

**4 index uniques partiels** (rôle métier vérifié dans les
changelogs) :

| Index | Table | Condition | Invariant protégé |
|---|---|---|---|
| `uq_dossier_workflow_link_active` | `dossier_workflow_link` | `WHERE ended_at IS NULL` | Un seul workflow **actif** à la fois par dossier (le bug réel trouvé en `fiabilisation-tests-plateforme` — `DataIntegrityViolationException` non traduite — violait cet invariant sous concurrence) |
| `uq_connector_instance_tenant_name_active` | `connector_instance` | `WHERE deleted_at IS NULL` | Nom de connecteur unique par tenant parmi les instances non supprimées (soft-delete) |
| `uq_notification_webhook_tenant_name_active` | `notification_webhook` | `WHERE deleted_at IS NULL` | Nom de webhook unique par tenant parmi les webhooks non supprimés |
| `uq_template_active` | `document_template` | sur `(tenant_id, active_template_key, active_variant_key, active_locale)` (colonnes générées, actives uniquement) | Une seule version **active** par (tenant, clé, variante, locale) de template document |

**Autres tables citées explicitement par TODO §25 :**

- `dossier_type_acl` — décision n° 20 (`AccessContext` + policy
  d'autorisation par type de dossier) ; `uq_dossier_type_acl_subject`
  garantit une entrée ACL unique par (tenant, type, sujet).
- `tenant_connector_dek` — décision n° 4 (chiffrement en enveloppe,
  DEK par tenant chiffrée par la KEK) ; PK = `tenant_id` (une seule
  DEK active par tenant, `key_version` pour la rotation).
- `outbox_event` — relais événementiel générique (pattern outbox),
  distinct de `notification_outbox`/`purge_outbox` qui sont des
  outbox spécialisées par domaine (notification, purge) — même
  pattern répété 3 fois, à noter comme une observation, pas un défaut
  à corriger dans cette feature documentaire.

**Répartition tenant/`platform` — état réel (pas celui supposé par
TODO §25) :** les 17 tables vivent toutes dans le schéma unique du
tenant courant (`app.tenant.id`, décision n° 13). Aucun schéma
`platform` n'existe. `platform.tenant_migration_status` reste une
option d'observabilité documentée mais non retenue (D-MPC-PLATFORM,
Phase 1) — mentionnée dans l'ERD comme telle, pas comme une table
existante.

**Tables Flowable (`ACT_*`)** — gérées entièrement par
`flowable-engine` (schéma `flowable.database-type: postgres`),
colocalisées dans le même schéma tenant mais hors du contrôle des
changelogs FlowForge. Mentionnées dans l'ERD sans énumération
(dizaines de tables internes à Flowable, propriété de la librairie,
pas de FlowForge) — renvoi vers
[05-workflow-engine.md](../../specs/05-workflow-engine.md).

## Structure du document `13-modele-physique.md`

1. **Intro** — 1 schéma par tenant (décision n° 13), pas de schéma
   `platform`, DDL exécutable = source de vérité (changelogs), ce
   document est une vue consolidée en lecture seule.
2. **ERD mermaid** — les 17 tables groupées par domaine (6 groupes
   ci-dessus), colonnes clés (PK, colonnes de jointure logique),
   annotées `[FK logique]` vs `[FK DB]` selon le cas.
3. **Index uniques partiels critiques** — le tableau ci-dessus,
   invariant métier par invariant métier.
4. **Répartition tenant / `platform`** — état réel + note sur
   `tenant_migration_status` (option non retenue).
5. **Tables notables** — `dossier_type_acl` (décision n° 20),
   `tenant_connector_dek` (décision n° 4), `outbox_event` et ses deux
   cousines spécialisées.
6. **Tables Flowable** — mention sans énumération, renvoi
   `05-workflow-engine.md`.
7. **Écart de documentation corrigé** — note explicite : TODO §25
   mentionnait un `TenantRegistry` et des contextes Liquibase
   `tenant-init`/`tenant-migration` séparés, tous deux obsolètes
   depuis la décision n° 13 (renvoi vers la note de mise à jour de la
   décision n° 1 et `02-multi-tenant.md`) — évite qu'un futur lecteur
   retombe sur la même confusion en repartant du TODO seul.

## Requirements Coverage

| Success criterion (requirements doc) | Couvert par |
|---|---|
| Nouveau doc `13-modele-physique.md`, ERD mermaid, référencé dans `00-index.md` | Section "Structure du document" ci-dessus |
| 4 index uniques partiels avec rôle métier | Tableau "4 index uniques partiels" ci-dessus |
| `dossier_type_acl`/`tenant_connector_dek`/`outbox_event` documentés | Section "Tables notables" |
| Répartition tenant/`platform` clarifiée | Section "Répartition tenant/platform" |
| Écart `TenantRegistry`/contextes Liquibase corrigé | Section "Écart de documentation corrigé" |
| TODO §25 clos | Tâche dédiée en planning (Phase 4) |

Toutes les success criteria de la Phase 2 ont une couverture explicite
ci-dessus ; aucun gap.

## Phase 3 — revue close (2026-08-22)

Aucune décision technique supplémentaire nécessaire — les 3 décisions
produit de Phase 1 (D-MPC-SCOPE, D-MPC-PLATFORM, D-MPC-FORMAT)
couvrent l'intégralité du périmètre. Contenu extrait et vérifié
directement depuis les changelogs Liquibase (parsing structuré des 12
fichiers `changes/*.yaml`) plutôt que résumé de mémoire — 2 écarts
supplémentaires trouvés en creusant : seules 2 FK sur 17 tables sont
réellement contraintes au niveau DB (le reste est logique, à
documenter explicitement pour ne pas induire en erreur sur
l'intégrité référentielle réelle) ; le pattern outbox est dupliqué 3
fois (`outbox_event`, `notification_outbox`, `purge_outbox`) — observé
et noté, pas un défaut à corriger dans une feature documentaire.

Status : **`reviewed`**. Prochaine phase : `dev-planning` (Phase 4).
