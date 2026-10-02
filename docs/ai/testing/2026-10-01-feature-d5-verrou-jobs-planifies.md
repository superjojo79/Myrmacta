---
phase: testing
title: Testing — D5
feature: d5-verrou-jobs-planifies
---

Principe : chaque correctif a un test à **deux exécutants concurrents** (threads + verrou de départ) sur la vraie base ; les tests de concurrence ont été montrés rouges sans la correction (alertes SLA : 4 alertes pour 4 exécutants).

| Tâche | Preuves |
|---|---|
| Outbox notifications | `NotificationPersistenceIT` : prise concurrente sans doublon, bail, **exécutant en retard** (ni résurrection ni écrasement), renouvellement ; `NotificationOutboxRelayTest` : ligne reprise sautée, renouvellement avant envoi |
| Outbox workflow | `WorkflowPersistenceTest` : relais concurrents, bail échu / levé par un échec |
| Alertes SLA | `SlaAlertConcurrencyIT` (chemin CAS **et** première insertion), `EvaluateSlaAlertsUseCaseImplTest` |
| Invitations portail | `JpaPortalLinkRepositoryAdapterTest` (prise concurrente, bail, levée par transition), `SendPortalInvitesUseCaseImplTest` |
| Déclencheurs cron | `JpaConnectorScheduleStateRepositoryAdapterTest` (insertion stricte, CAS, libération), `SchedulerConnectorTriggerJobTest`, `BatchImportConnectorTriggerJobTest`, `BatchImportEndToEndIT` |
| Export BI | `BiExportCommitConcurrencyIT`, `RunBiExportUseCaseImplTest` |
| Réconciliation | `WorkflowPersistenceTest` (prise concurrente, orphelin seulement), `ReservedReconcileJobTest`, `BusinessKeyLookupIT` (historique) |
| Signatures | `DispatchSignatureOutcomesUseCaseImplTest` (deux courses réelles) |

Revue : rejouée aussi sur PostgreSQL 16 réel (READ COMMITTED) par le relecteur. Limite connue : la CI du dépôt ne tourne que sur H2.
