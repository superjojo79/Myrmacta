# Scalabilité intra-tenant — verrou des jobs planifiés (D5)

> **Statut : livré (TODO §79, 2026-10-01)** — audit des 18 tâches planifiées et correctifs par *prise* (claim) en base, voir [`docs/ai/design/2026-10-01-feature-d5-verrou-jobs-planifies.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/design/2026-10-01-feature-d5-verrou-jobs-planifies.md) et `docs/roadmap-goal/DECISIONS.md` (D-JOBS-*). *(Ancien statut : première analyse du 2026-09-28.)* Lot **D5**
> `verrou-jobs-planifies` de la Vague D (`ROADMAP.md` Catégorie 7). Avec D0 = C
> ([31](31-positionnement-produit.md), hébergement dédié), le lot **reste à l'audit** : le verrou
> ne devient nécessaire que si un client impose plusieurs pods pour un même tenant.

---

## 1. Constat vérifié (`main` @ `e3985fe`)

**15 méthodes `@Scheduled`**, aucun verrou distribué de type ShedLock ou advisory lock.

| Job | Période par défaut | Protection vue à la lecture |
|---|---|---|
| `OutboxRelayScheduler` (événements workflow) | 1 s | à auditer |
| `NotificationOutboxRelay` | configurable | à auditer |
| `PurgeOutboxJob` | 1 h | **claim atomique** (`SpringDataPurgeOutboxRepository.claim`) |
| `EvaluateDossierRetentionJob` | 1 h | à auditer |
| `WorkflowRetentionJob` | 1 j | à auditer |
| `ReservedReconcileJob` | 5 min | à auditer (liens `RESERVED` orphelins) |
| `NotificationInboxRetentionJob` | configurable | purge en masse, idempotente a priori |
| `SchedulerConnectorTriggerJob` | configurable | état `connector_schedule_state` (`lastTriggeredAt`) — double déclenchement possible entre lecture et écriture, à vérifier |
| `BatchImportConnectorTriggerJob` | configurable | à auditer (un fichier importé deux fois ?) |
| `KafkaConsumerReconciler` | configurable | groupe de consommateurs Kafka (`groupId` de l'instance) : répartition native des partitions |
| `SlaAlertJob` | 60 s | état `sla_alert_state` (une alerte par palier) ; corrections de concurrence déjà faites en PR #132 |
| `BiExportJob` | 60 s | à auditer (fichier produit deux fois ?) |
| `PortalInviteSenderJob` | 10 s | à auditer (e-mail d'invitation envoyé deux fois ?) |
| `PortalLinkExpiryJob` | 15 min | à auditer |
| `SseNotificationAdapter` (heartbeat) | configurable | local à la JVM par nature, hors sujet |

Hors périmètre, décidé : SSE multi-pod, rate-limit distribué, Spring Session — pas de Redis dans
la stack figée (`FILE-ATTENTE.md` § *Ne pas lancer*). Le moteur Flowable a son propre exécuteur
de jobs asynchrones (TODO point 11).

## 2. Objectif

Savoir, job par job, lequel s'exécuterait en double avec deux pods, et le rendre sûr quand c'est
nécessaire, sans ajouter d'infrastructure.

## 3. Démarche proposée

1. **Audit** (taille S) : pour chaque job, effet d'une double exécution concurrente (sans effet,
   doublon visible — e-mail, fichier, dossier —, corruption). Test Testcontainers à deux
   exécutions simultanées pour les cas douteux (modèle : `SequentialReferenceMultiPodIT`).
2. **Correctif** (taille M), seulement pour les jobs à risque :
   - claim atomique en base (`UPDATE … WHERE status = … RETURNING`, ou `FOR UPDATE SKIP LOCKED`)
     quand le job traite des lignes ;
   - **advisory lock PostgreSQL** par job sinon — compatible avec la stack figée, sans Redis.

## 4. Coutures

Adaptateurs de persistance des outbox et des états de job ; aucun impact domaine attendu. H2 ne
reproduit pas les advisory locks → tests PostgreSQL réels (D-TESTCONTAINERS étendu).

## 5. Exposition MCP

Aucune : lot d'infrastructure sans artefact de configuration.

## 6. Décisions à trancher

1. **D-JOBS-TRIGGER** — lancer le correctif dès l'audit, ou attendre un client multi-pods.
2. **D-JOBS-MECHANISM** — claim par ligne systématique, ou advisory lock par job.

## 7. Documentation impactée

`DEPLOIEMENT.md` (conditions pour scaler un tenant à plusieurs pods), `02-multi-tenant.md`.
