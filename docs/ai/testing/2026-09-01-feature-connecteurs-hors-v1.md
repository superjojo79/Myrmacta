---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy

> Première passe (Phase 1), dérivée des Success Criteria des requirements — cases à cocher au fil
> de Phase 8 (`dev-testing`), pas avant.

## Test Coverage Goals
**What level of testing do we aim for?**

- 100% des branches nouvelles (3 connecteurs + job de poll + repository d'état) — cohérent avec le
  seuil Jacoco du bundle (LINE ≥85%/BRANCH ≥75%).
- Aucun test frontend nouveau attendu — feature à confirmer backend seule en Phase 2.

## Unit Tests
**What individual components need testing?**

### SchedulerConnector
- [ ] Implémente `ConnectorDefinition`/`TriggerConnector`, `getType() == "scheduler"`
- [ ] `getConfigSchema()` expose `cronExpression`/`dossierTypeCode` requis
- [ ] `validate(config)` rejette un `cronExpression` invalide
- [ ] `onEvent(instance, rawPayload)` produit un `WorkflowTriggerEvent` avec `dossierTypeCode` de
      la config, ignore `rawPayload`

### SchedulerConnectorTriggerJob
- [ ] Instance due (cron + `last_triggered_at`) → `ConnectorTriggerRuntime.onIncomingEvent` appelé,
      `connector_schedule_state` mis à jour
- [ ] Instance non due → aucun appel
- [ ] Instance désactivée (`enabled=false`) → jamais évaluée
- [ ] Premier tir (`last_triggered_at` absent) → comportement défini explicitement (pas un null
      pointer)

### ConnectorScheduleStateRepository (adaptateur JPA)
- [ ] `save`/`findByConnectorInstanceId` round-trip

### SmtpConnector
- [ ] Implémente `ConnectorDefinition`/`ServiceConnector`, `getType() == "smtp"`
- [ ] `execute(instance, {to, subject, body})` appelle `EmailPort.send` avec les bons arguments
- [ ] `to` blank ou sans « @ » → `IllegalArgumentException`, `EmailPort.send` jamais appelé
- [ ] `subject`/`body` absents → `IllegalArgumentException`

### DocumentStorageConnector
- [ ] Implémente `ConnectorDefinition`/`ServiceConnector`, `getType() == "document-storage"`
- [ ] `STORE` → `DocumentStoragePort.store` appelé, `storageRef` dans `connectorResult`
- [ ] `RETRIEVE` → `DocumentStoragePort.retrieve` appelé, contenu base64 dans `connectorResult`
- [ ] `EXISTS`/`DELETE` → délégation directe au port
- [ ] `STORE` avec contenu dépassant `maxContentSize` → `IllegalArgumentException`, aucun appel
      au port
- [ ] `operation` absent/invalide → `IllegalArgumentException`

## Integration Tests
**How do we test component interactions?**

- [ ] `scheduler` bout-en-bout : instance créée → job de poll → workflow démarré (horloge
      contrôlée/injectée, pas un vrai sleep)
- [ ] `smtp`/`document-storage` via `${connectorServiceTask}` (JavaDelegate Flowable réel)
- [ ] `smtp`/`document-storage` via `ConnectorExecuteController`/`M2mConnectorExecuteController`
      (MockMvc, sur le modèle des tests `http-rest` existants)
- [ ] Régression croisée : `http-rest`/`flowable-signal` toujours fonctionnels après l'ajout des 3
      nouveaux types (`ConnectorRegistry` sans conflit de type)

## End-to-End Tests
**What user flows need validation?**

- À confirmer en Phase 2 selon la vérification "backend-only" — probablement aucun, comme pour
  `connecteur-http-rest-service`.

## Test Data
**What data do we use for testing?**

- `ConnectorInstanceData` de test construits directement (pas de fixture DB nécessaire pour les
  tests unitaires des connecteurs).
- Horloge injectable/contrôlée pour les tests du job de poll (pas de dépendance à `Instant.now()`
  réel).

## Test Reporting & Coverage
**How do we verify and communicate test results?**

- `./mvnw verify` (Jacoco) — vérifier que les nouvelles branches sont couvertes ; inspection
  `target/site/jacoco/<pkg>/<Class>.java.html` si le bundle ne suffit pas à localiser un gap.

## Manual Testing
**What requires human validation?**

- Aucun a priori (feature backend, écran admin déjà générique) — à confirmer en Phase 2.

## Performance Testing
**How do we validate performance?**

- Non applicable — pas de changement de volumétrie ni de chemin chaud identifié.

## Bug Tracking
**How do we manage issues?**

- Suivi standard du dépôt (TODO.md §50, PR de la feature) — pas de process dédié supplémentaire.
