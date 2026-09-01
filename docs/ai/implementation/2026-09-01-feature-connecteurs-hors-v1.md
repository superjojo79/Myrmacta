---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
---

# Implementation Guide

## Development Setup
**How do we get started?**

- Aucun prérequis nouveau : `CronExpression` (Spring), `EmailPort`/`SmtpEmailAdapter` et
  `DocumentStoragePort` sont déjà sur le classpath et déjà des beans configurés.
- Migration Liquibase `018-connector-schedule-state.yaml` appliquée automatiquement au démarrage
  (H2 en test, Postgres en tenant réel).

## Code Structure
**How is the code organized?**

- `infrastructure/connector/builtin/SmtpConnector.java` — `ConnectorDefinition` + `ServiceConnector`.
- `infrastructure/connector/builtin/DocumentStorageConnector.java` — `ConnectorDefinition` +
  `ServiceConnector`.
- `infrastructure/connector/builtin/SchedulerConnector.java` — `ConnectorDefinition` +
  `TriggerConnector`.
- `infrastructure/connector/SchedulerConnectorTriggerJob.java` — job `@Scheduled`, placé directement
  dans `infrastructure.connector` (pas `.builtin`) car c'est un appelant de
  `ConnectorTriggerRuntime`, pas une définition de connecteur — même niveau que
  `WebhookController`/`FlowableSignalEventListener`.
- `domain/connector/port/out/ConnectorScheduleStateRepository.java` (port) +
  `infrastructure/persistence/connector/{ConnectorScheduleStateJpaEntity,
  SpringDataConnectorScheduleStateRepository, JpaConnectorScheduleStateRepositoryAdapter}.java`.
- `infrastructure/config/ConnectorProperties.java` — 4ᵉ sous-record `DocumentStorage(long
  maxContentSize)`, même patron que `Webhook.maxBodyBytes`.

## Implementation Notes
**Key technical details to remember:**

### Core Features
- `smtp` : réutilise `EmailPort` tel quel (texte brut, aucune nouvelle config SMTP). Garde `to`
  non-blank + contient « @ », `subject`/`body` requis, retourne `Map.of("sent", true)`.
- `document-storage` : route sur `operation` (`STORE`/`RETRIEVE`/`EXISTS`/`DELETE`) vers
  `DocumentStoragePort`. `STORE` décode le contenu base64, vérifie la taille contre
  `ConnectorProperties.documentStorage().maxContentSize()` **avant** d'appeler le port.
  Généralisation depuis le candidat initial « s3-minio » du catalogue — pas de couplage à MinIO/S3
  (conforme `DEFERRED-FEATURES.md` §0).
- `scheduler` : aucun changement à `ConnectorTriggerRuntimeImpl`/`ConnectorCatalog` — 3ᵉ appelant de
  `onIncomingEvent`, après `WebhookController` (http-rest) et `FlowableSignalEventListener`
  (flowable-signal). Le job (`SchedulerConnectorTriggerJob`) décide *quand* appeler ; toute la
  mécanique dossier/outbox reste dans `ConnectorTriggerRuntimeImpl`, déjà testée une fois.
- État de dernier tir : table `connector_schedule_state` à clé naturelle
  (`connector_instance_id` PK+FK, pas d'UUID de substitution — relation strictement 1:1, même
  patron que `bpmn_draft`). Upsert paresseux au premier tir plutôt que création à la création de
  l'instance connecteur (aucun couplage à `ManageConnectorInstanceUseCaseImpl`).

### Patterns & Best Practices
- Test « distinguishing failure » network-free pour prouver qu'un champ atteint `execute()` sans
  dépendance externe réelle — deux variantes utilisées dans le dépôt : URL bloquée SSRF pour
  `http-rest` (cycle précédent), bean de test `@Primary` no-op pour `smtp` (ce cycle, voir Design
  Deviations).
- Horloge explicite (`Instant now` en paramètre) pour `SchedulerConnectorTriggerJob.process`,
  jamais `Instant.now()` global dans le corps testé — même patron que `PurgeOutboxJob`.

## Integration Points
**How do pieces connect?**

- `ConnectorConfig` (config Spring) enregistre les 3 nouveaux beans connecteur — aucun changement à
  `ConnectorRegistry`/`ConnectorCatalog` eux-mêmes (mécanisme déjà générique par `getType()`).
- `ExecuteConnectorRequest` (`infrastructure/web/connector/ConnectorApiDtos.java`) — DTO partagé par
  `ConnectorExecuteController` (BFF) et `M2mConnectorExecuteController` (M2M) pour tous les
  connecteurs SERVICE. Étendu de 7 à 13 champs (voir Design Deviations).

## Error Handling
**How do we handle failures?**

- `smtp`/`document-storage` : `IllegalArgumentException` pour toute validation d'entrée manquante
  (`to`/`subject`/`body`, `operation`/`storageRef`/`content`/`contentType` selon l'opération),
  remontée en HTTP 400 par le mécanisme d'exception déjà existant des contrôleurs connecteur.
  Aucun nouveau handler ajouté.
- `scheduler` : `validate(config)` lève `ConnectorValidationException` si `cronExpression` n'est pas
  parsable par `CronExpression.parse`, ou si `processKey`/`dossierTypeCode` est blank.

## Performance Considerations
**How do we keep it fast?**

- Poll par défaut `1m` (`app.connector.scheduler.poll-delay`), configurable. `@Scheduled` Spring par
  défaut n'exécute pas deux tirs du même job en parallèle (thread unique séquentiel) — pas de garde
  supplémentaire ajoutée contre le double-tir concurrent, risque documenté en planning.

## Security Notes
**What security measures are in place?**

- `document-storage` : garde de taille (`maxContentSize`, défaut 5 Mo) avant tout appel au port,
  empêche un contenu base64 surdimensionné d'atteindre le stockage.
- Anti-fuite : `connectorResult` ne contient jamais `instance.config()` pour `smtp` (T-SMTP-7),
  même garantie que les connecteurs SERVICE existants.

## Design Deviations
**Écarts trouvés en implémentation, non prévus par le design initial :**

1. **`ExecuteConnectorRequest` incomplet pour les nouveaux types SERVICE** — le DTO partagé
   (`ConnectorApiDtos.java`) ne portait que les champs `http-rest`/`flowable-signal`
   (`signalName`, `processInstanceId`, `variables`, `url`, `method`, `body`, `headers`). Détecté par
   un vrai rouge TDD en écrivant `ConnectorExecuteApiTest` pour `smtp`/`document-storage` (400 au
   lieu de 200) avant de toucher le DTO. Corrigé en ajoutant 6 champs (`to`, `subject`, `operation`,
   `storageRef`, `content`, `contentType`) et leur logique `toInputParams()` (même garde
   blank-exclu que les champs existants). **C'est la deuxième occurrence du même écart** — le cycle
   précédent (`connecteur-http-rest-service`) avait déjà dû étendre ce DTO pour `http-rest`. Ce DTO
   « superset plat » est un point de couture récurrent à surveiller : chaque nouveau type SERVICE
   avec des champs d'entrée propres devra probablement l'étendre à nouveau. Candidat à noter pour
   une future checklist de revue de design (pas d'action prise au-delà de cette note).
2. **Test SMTP full-context sans serveur SMTP réel** — `ConnectorExecuteApiTest` charge un contexte
   Spring complet incluant un vrai `JavaMailSenderImpl` (aucun serveur SMTP de test type GreenMail
   dans le dépôt). Un premier essai (attendre `5xxServerError`) a échoué : l'exception
   (`MailSendException`/`ConnectException`) n'est pas convertie en réponse HTTP par
   `ConnectorExceptionHandler` (aucun handler pour ce type) et remonte hors de
   `mockMvc.perform()` lui-même (JUnit « Errors », pas « Failures », `.andExpect` jamais atteint).
   Résolu par `src/test/java/com/flowforge/support/NoopEmailPortTestConfiguration.java` — un
   `@TestConfiguration` avec `@Bean @Primary EmailPort emailPort()` no-op, même patron que
   `OidcClientTestConfiguration` déjà présent dans le dépôt, importé dans `ConnectorExecuteApiTest`
   aux côtés de `OidcClientTestConfiguration`. Chemin de succès propre et déterministe, réutilisable
   pour tout futur test plein-contexte évitant un envoi d'e-mail réel.

## Phase 7 — Revue d'implémentation (Check Implementation)

**Relecture fichier par fichier contre le design, 2026-09-01.**

- `SmtpConnector`/`DocumentStorageConnector`/`SchedulerConnector` : conformes au design (config
  schema, capability, garde d'entrée). `SchedulerConnector.getConfigSchema()` expose bien
  `cronExpression`+`processKey`+`dossierTypeCode`, tous requis — l'écart trouvé en Phase 4 (`processKey`
  manquant du premier jet) est bien reflété dans le code livré.
- `SchedulerConnectorTriggerJob.isDue` : vérifié contre `JpaConnectorInstanceRepositoryAdapter.
  listByTenantId` — filtre bien `deletedAtIsNull` côté requête SQL (`findByTenantIdAndDeletedAtIsNullOrderByNameAsc`),
  aucun filtre supplémentaire nécessaire côté job, confirmant la lecture faite en design (§Component
  Breakdown). Un tir manqué n'est jamais rattrapé plus d'une fois par poll (`cron.next(lastFired)`
  comparé à `now`) — cohérent avec "un dossier par tir", pas de rafale de rattrapage au redémarrage.
- `connector_schedule_state` (migration 018) : clé naturelle `connector_instance_id` (PK+FK), pas de
  `tenant_id` dupliqué — conforme à la décision de design (isolation portée par `connector_instance`).
  Rollback `dropTable` explicite présent.
- `ExecuteConnectorRequest`/`NoopEmailPortTestConfiguration` : écarts déjà documentés ci-dessus,
  aucun autre écart de design trouvé en relecture.
- Aucun changement à `ConnectorConfig` — les 3 connecteurs sont `@Component` et découverts
  automatiquement par le scan `@PostConstruct` de `ConnectorRegistry` (vérifié : aucune référence à
  `SmtpConnector`/`DocumentStorageConnector`/`SchedulerConnector` dans `ConnectorConfig.java`), donc
  aucune tâche T1.4/T2.5/T3.7 « bean registration » distincte n'était réellement nécessaire au-delà de
  l'annotation `@Component` déjà posée sur chaque classe.
- `06-connecteurs.md`/`README.md` : diff relu ligne à ligne, plus aucune référence résiduelle à
  `s3-minio`/« hors V1 » pour les 3 types livrés.

**Alignement : conforme au design, aucune déviation majeure trouvée en Phase 7** au-delà des 2 déjà
enregistrées en Phase 5 (DTO `ExecuteConnectorRequest`, `NoopEmailPortTestConfiguration`). Aucune
faille de sécurité, aucun test manquant, aucune mise à jour de doc en retard identifiée.

**Reste ouvert (hors périmètre Phase 7, prévu M5/T5.4 — Phase 9)** :
- `CHANGELOG.md` — entrée à ajouter pour cette feature.
- Confirmation finale de la sécurité du rollback de migration (018) en conditions réelles Postgres
  (déjà vérifiée par construction — `dropTable` explicite — mais pas exercée en pratique dans cette
  feature).
- Régression frontend complète (`npm run test`/`npm run test:e2e`) — T5.3, pas encore lancée.
