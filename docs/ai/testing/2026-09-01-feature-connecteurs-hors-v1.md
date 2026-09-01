---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy

> Deuxième passe (Phase 4, préparation planning) — cases identifiées avec des IDs stables
> (`T-<PREFIX>-N`) pour traçabilité avec le plan de tâches ; cochées au fil de Phase 8
> (`dev-testing`), pas avant.

## Test Coverage Goals
**What level of testing do we aim for?**

- 100% des branches nouvelles (3 connecteurs + job de poll + repository d'état) — cohérent avec le
  seuil Jacoco du bundle (LINE ≥85%/BRANCH ≥75%).
- Aucun test frontend nouveau — feature confirmée backend seule (Phase 2).

## Unit Tests
**What individual components need testing?**

### SchedulerConnector
- [ ] T-SCHED-1 : implémente `ConnectorDefinition`/`TriggerConnector`, `getType() == "scheduler"`
- [ ] T-SCHED-2 : `getConfigSchema()` expose `cronExpression`/`dossierTypeCode` requis
- [ ] T-SCHED-3 : `validate(config)` rejette un `cronExpression` invalide (format non-cron)
- [ ] T-SCHED-4 : `validate(config)` rejette `dossierTypeCode` blank/absent
- [ ] T-SCHED-5 : `onEvent(instance, rawPayload)` produit un `WorkflowTriggerEvent` avec
      `dossierTypeCode` de la config, ignore `rawPayload` (même résultat `null`/`Map.of()`)

### SchedulerConnectorTriggerJob
- [ ] T-JOB-1 : instance due (cron dû depuis `last_triggered_at`, ou jamais tirée) →
      `ConnectorTriggerRuntime.onIncomingEvent` appelé une fois
- [ ] T-JOB-2 : instance non due → aucun appel `onIncomingEvent`
- [ ] T-JOB-3 : instance désactivée (`enabled=false`) → jamais dans la liste évaluée (héritée de
      `listByTenantId`, pas de filtre à écrire dans le job — non-régression, pas un nouveau test
      de filtre)
- [ ] T-JOB-4 : premier tir (`connector_schedule_state` absent) → traité comme dû (pas de
      `NullPointerException`), upsert crée la ligne après le tir
- [ ] T-JOB-5 : après un tir, `connector_schedule_state.last_triggered_at` mis à jour (upsert,
      pas un second insert)
- [ ] T-JOB-6 : 2 instances `scheduler` distinctes dans le même tenant, l'une due l'autre non →
      seule celle due déclenche `onIncomingEvent`

### ConnectorScheduleStateRepository (adaptateur JPA)
- [ ] T-STATE-1 : `findByConnectorInstanceId` — absent avant tout tir
- [ ] T-STATE-2 : `upsert` — insert au premier appel, update (pas de conflit de clé) au second

### SmtpConnector
- [ ] T-SMTP-1 : implémente `ConnectorDefinition`/`ServiceConnector`, `getType() == "smtp"`
- [ ] T-SMTP-2 : `execute(instance, {to, subject, body})` appelle `EmailPort.send(to, subject,
      body)` avec les arguments du `connectorInput`
- [ ] T-SMTP-3 : `to` blank → `IllegalArgumentException`, `EmailPort.send` jamais appelé
- [ ] T-SMTP-4 : `to` sans « @ » → `IllegalArgumentException`, `EmailPort.send` jamais appelé
- [ ] T-SMTP-5 : `subject` absent → `IllegalArgumentException`
- [ ] T-SMTP-6 : `body` absent → `IllegalArgumentException`
- [ ] T-SMTP-7 (régression anti-fuite, même patron que `http-rest`) : `connectorResult` ne
      contient jamais `instance.config()`

### DocumentStorageConnector
- [ ] T-STORAGE-1 : implémente `ConnectorDefinition`/`ServiceConnector`,
      `getType() == "document-storage"`
- [ ] T-STORAGE-2 : `operation=STORE` → `DocumentStoragePort.store` appelé, `storageRef` dans
      `connectorResult`
- [ ] T-STORAGE-3 : `operation=RETRIEVE` → `DocumentStoragePort.retrieve` appelé, contenu base64
      dans `connectorResult`
- [ ] T-STORAGE-4 : `operation=EXISTS` → délégation directe, booléen dans `connectorResult`
- [ ] T-STORAGE-5 : `operation=DELETE` → délégation directe à `DocumentStoragePort.delete`
- [ ] T-STORAGE-6 : `STORE` avec contenu base64 dépassant `maxContentSize` →
      `IllegalArgumentException`, `DocumentStoragePort.store` jamais appelé
- [ ] T-STORAGE-7 : `operation` absent/invalide → `IllegalArgumentException`
- [ ] T-STORAGE-8 : `RETRIEVE`/`EXISTS`/`DELETE` sans `storageRef` → `IllegalArgumentException`
      avant tout appel au port

### ConnectorProperties.DocumentStorage
- [ ] T-PROP-1 : défaut `maxContentSize` appliqué si absent de la config
- [ ] T-PROP-2 : override partiel — valeur fournie respectée, reste au défaut

## Integration Tests
**How do we test component interactions?**

- [ ] T-INT-1 : `scheduler` bout-en-bout — instance créée avec un cron, job de poll (horloge
      contrôlée/injectée, pas un vrai sleep) → workflow démarré, `connector_schedule_state` à jour
- [ ] T-INT-2 : `smtp`/`document-storage` via `${connectorServiceTask}` (JavaDelegate Flowable
      réel), sur le modèle des tests d'intégration existants pour `http-rest`
- [ ] T-INT-3 : `smtp`/`document-storage` via `ConnectorExecuteController`/
      `M2mConnectorExecuteController` (MockMvc, sur le modèle de `ConnectorExecuteApiTest`)
- [ ] T-INT-4 (régression croisée) : `http-rest`/`flowable-signal` toujours fonctionnels après
      l'ajout des 3 nouveaux types — `ConnectorRegistry` sans conflit de type
- [ ] T-INT-5 (régression globale) : `ArchitectureRulesTest` complet vert

## End-to-End Tests
**What user flows need validation?**

- Aucun — feature backend seule (confirmé Phase 2), aucun flux utilisateur visible modifié.

## Test Data
**What data do we use for testing?**

- `ConnectorInstanceData` de test construits directement (pas de fixture DB nécessaire pour les
  tests unitaires des connecteurs).
- Horloge injectable/contrôlée pour les tests du job de poll (pas de dépendance à `Instant.now()`
  réel) — même patron que les tests `PurgeOutboxJob`/`ProcessPurgeOutboxUseCaseImplTest`
  (paramètre `Instant now` explicite plutôt qu'horloge globale).

## Test Reporting & Coverage
**How do we verify and communicate test results?**

- `./mvnw verify` (Jacoco) — vérifier que les nouvelles branches sont couvertes ; inspection
  `target/site/jacoco/<pkg>/<Class>.java.html` si le bundle ne suffit pas à localiser un gap.
- Pas de commande frontend concernée (feature backend seule).

## Manual Testing
**What requires human validation?**

- Aucun — écran admin connecteurs déjà générique (confirmé Phase 2), pas de surface UI/UX
  modifiée.

## Performance Testing
**How do we validate performance?**

- Non applicable — pas de changement de volumétrie ni de chemin chaud identifié.

## Bug Tracking
**How do we manage issues?**

- Suivi standard du dépôt (TODO.md §50, PR de la feature) — pas de process dédié supplémentaire.
