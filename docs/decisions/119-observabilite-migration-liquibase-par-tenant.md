# §119 — Observabilité de la migration Liquibase par instance de tenant

> **Statut :** clos · **Section :** [Décisions actées › Opérationnel](section-decisions-operationnel.md) · [registre](README.md)

**Contexte (issue #17).** Avec le modèle « une instance Spring Boot par tenant » (décision n° 13), chaque
instance migre son propre schéma au démarrage et ne démarre pas si la migration échoue. Un opérateur ne
dispose cependant d'aucun moyen simple de comparer le niveau de migration de chacune des instances
(« quel tenant est en retard ? »).

**Ce qui était envisagé (§10).** La décision n° 10 mentionne une table `platform.tenant_migration_status`
permettant un suivi cross-tenant. Avec la décision n° 13 (instance dédiée par tenant), cette table n'est plus
nécessaire pour la coordination (chaque instance gère elle-même sa migration), mais resterait utile comme
tableau de bord d'exploitation agrégé. Elle n'a pas été créée.

**Décision retenue.** MVP minimal couvrant l'issue #17 :

1. **Per-instance** : chaque instance expose son état de migration dans l'endpoint `/api/observability`
   existant (`TENANT_ADMIN`) : `appliedChangesetsCount`, `lastAppliedChangesetId`, `lastAppliedAt`.
   La requête est effectuée sur `DATABASECHANGELOG` via `DataSource` au moment du snapshot.
2. **Cross-tenant** : déféré. La table `platform.tenant_migration_status` reste une idée ouverte pour
   l'industrialisation du provisioning multi-tenant (voir §10 et risque 3 de la revue du 2026-10-22).

**Périmètre livré (issue #17) :**
- `LiquibaseMigrationReader` (infrastructure) : lit `DATABASECHANGELOG`, renvoie un `LiquibaseMigrationStatus`.
- `ObservabilitySnapshotResponse.migrationStatus` : champ ajouté.
- `ObservabilitySnapshotFacade` : incorpore le reader.
- SPA : carte KPI « Migration » dans `/admin/observabilite`.

**Impacte :** `docs/specs/02-multi-tenant.md` (section « Bootstrap et migration »),
`docs/guides/GUIDE-UTILISATEUR.md` (§ Observabilité — ajout de la carte Migration).
