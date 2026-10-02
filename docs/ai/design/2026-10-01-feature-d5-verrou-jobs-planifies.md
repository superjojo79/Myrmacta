---
phase: design
title: Design — D5 (jobs planifiés sûrs sous double exécution) — audit et mécanisme
status: reviewed
created: 2026-10-01
feature: d5-verrou-jobs-planifies
---

# Design — D5

## Audit (lecture seule, 18 méthodes `@Scheduled`, recoupé par relecture du code)
Aucun verrou distribué (ni ShedLock, ni `pg_try_advisory_*`, ni `FOR UPDATE`/`SKIP LOCKED`, ni `@Lock`) ; seul `@Version` JPA : `DossierJpaEntity`. Dans un pod, deux ticks d'un même job ne se
chevauchent pas (`fixedDelay`, planificateur Spring par défaut). Les méthodes `lockNextPending` des deux outbox sont de simples `SELECT` malgré leur nom.

| Job | Période | Protection avant D5 | Double exécution | Classe | Traitement D5 |
|---|---|---|---|---|---|
| `NotificationOutboxRelay` | 1 s | aucune | e-mail et webhook en double (quasi systématique à 1 s) ; remise en PENDING possible après succès | DOUBLON | bail `next_attempt_at` (sans migration) |
| `OutboxRelayScheduler` | 1 s | partielle (index unique du lien actif) | deux instances Flowable pour une business key, événement DEAD à tort | **CORRUPTION** | bail `claimed_until` (027) |
| `ReservedReconcileJob` | 5 min | aucune | deux process démarrés ; `abortReserved` après un `start` réussi | **CORRUPTION** | prise du lien (029) + jamais d'abandon après démarrage |
| `SlaAlertJob` | 60 s | PK de `sla_alert_state` (1ʳᵉ insertion seulement) | alertes (e-mail/webhook/navigateur) en double sur transition | DOUBLON | compare-and-set de l'état avant notification |
| `BiExportJob` | 60 s | aucune | deux jeux de CSV catalogués | DOUBLON | avancement de `last_bi_export_at` par CAS, 1ʳᵉ écriture du commit |
| `SchedulerConnectorTriggerJob` | 1 min | aucune | dossier et workflow en double par échéance | DOUBLON | créneau pris par CAS (`claimTrigger`) |
| `BatchImportConnectorTriggerJob` | 1 min | renommage SFTP atomique (accidentel) | perte possible de lignes si listings divergents | sûr/accidentel | même prise de créneau |
| `PortalInviteSenderJob` | 10 s | CAS de statut **après** l'envoi | deux e-mails, un lien mort | DOUBLON | prise du lien avant envoi (028) |
| `SignatureOutcomeDispatcherJob` | 15 s | souscription Flowable consommée une fois | fausse alerte `dispatchGaveUp` | IDEMPOTENT (alerte) | relecture avant de compter une tentative |
| `SignatureInviteSenderJob`, `SignatureExpiryJob`, `PortalLinkExpiryJob`, `PurgeOutboxJob`, `EvaluateDossierRetentionJob` | – | CAS de version/statut, claim atomique, `@Version` + index unique | rien | **SÛR** | aucun |
| `WorkflowRetentionJob`, `NotificationInboxRetentionJob` | 1 j | DELETE en masse | rien d'observable | IDEMPOTENT | aucun |
| `SseNotificationAdapter` (heartbeat), `KafkaConsumerReconciler` | – | local JVM ; groupe Kafka | – | LOCAL-JVM | hors périmètre (Kafka : au moins une fois) |

## Mécanisme (D-JOBS-MECHANISM)
**Prise par ligne** (`UPDATE … WHERE <état observé>` puis test « 1 ligne modifiée »), déjà le schéma de `PurgeOutbox`, du portail et de la signature : portable H2/PostgreSQL, sans dépendance, sans transaction longue.
Écartés : l'**advisory lock** PostgreSQL (absent de H2 ⇒ deux implémentations ; il vit le temps d'une transaction, donc il faudrait tenir une transaction ouverte pendant un envoi SMTP ou un rendu BI) et **ShedLock** (nouvelle
dépendance, et ne paralléliserait pas les relais d'outbox).
Chaque prise est **son propre UPDATE atomique** — jamais plusieurs lignes verrouillées dans une même transaction (interblocage prouvé en test sous H2 lors du premier essai).

Bails : notifications 5 min, outbox workflow 5 min, portail 2 min, réconciliation 2 min. Un échec signalé (`recordFailure`, transition de statut) lève le bail : le réessai reste immédiat comme avant.

## Correctifs par tâche
Voir TODO §79. Aucune API publique ne change ; les ports gagnent des méthodes de prise (`claimForSend`, `claimTrigger/releaseTrigger`, `claimForReconcile`, `advanceLastBiExportAt`, `transition` SLA) ; `lockNextPending` garde son nom et sa
signature mais renvoie désormais les lignes **prises par l'appelant**.
