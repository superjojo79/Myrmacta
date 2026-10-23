# §25 — Modèle physique de données consolidé

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §25 · [registre](README.md)

**Énoncé :** une dizaine de tables métier, deux index uniques partiels critiques, la table
`outbox_event`, `dossier_type_acl` (décision n° 20), le stockage des DEK chiffrées, `platform.tenant_migration_status` et les tables
`ACT_*` de Flowable coexistent sans vue consolidée ni règle de répartition entre le schéma du tenant
et le schéma `platform`. En particulier, la table des tenants reflétée par le `TenantRegistry` —
deuxième barrière de sécurité de la décision n° 1 — n'est décrite nulle part, non plus que la
politique de rafraîchissement de son cache.

**À produire :** schéma consolidé (ERD ou DDL), répartition tenant / `platform`, et changelogs
Liquibase correspondants avec leurs contextes `tenant-init` / `tenant-migration`.

**Résolu (2026-08-22, feature `modele-physique-consolide`) :** écart trouvé avant de commencer —
l'énoncé lui-même était en partie obsolète. Ni le `TenantRegistry` ni les contextes Liquibase
`tenant-init`/`tenant-migration` séparés n'existent dans le code : la décision n° 1 porte une note
de mise à jour explicite disant que les deux mécanismes « n'ont plus lieu d'être » depuis la
décision n° 13 (une instance dédiée par tenant, schéma fixé au démarrage, changelog unique
appliqué à chaque démarrage). Nouveau document
[13-modele-physique.md](../specs/13-modele-physique.md) : ERD mermaid des **17** tables
métier réelles (pas « une dizaine »), **4** index uniques partiels critiques avec leur invariant
métier (pas « deux » — `uq_dossier_workflow_link_active`, `uq_template_active`,
`uq_connector_instance_tenant_name_active`, `uq_notification_webhook_tenant_name_active`),
`dossier_type_acl`/`tenant_connector_dek`/`outbox_event` documentées, répartition tenant/`platform`
clarifiée (un seul schéma par tenant aujourd'hui, `tenant_migration_status` documentée comme option
non retenue plutôt qu'implémentée). Feature strictement documentaire — aucun changelog, aucune
classe Java modifiée (confirmé par `git diff --stat`).
