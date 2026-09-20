---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
status: verified
feature: connecteurs-cible
lot: B1
---

# Testing Strategy — `connecteurs-cible`

> Requirements : [2026-09-14-feature-connecteurs-cible.md](../requirements/2026-09-14-feature-connecteurs-cible.md).
> Design : [2026-09-14-feature-connecteurs-cible.md](../design/2026-09-14-feature-connecteurs-cible.md).
> **Amendement Phase 4** : la feature n'est plus 100 % backend-only (D-CONFIG-FIELD-SECRET-TEXTAREA,
> Phase 3) — `ConnectorConfigForm.vue` change, donc **Playwright est exigé** (pas seulement en
> régression), cf. `docs/ai/testing/README.md` : « Do not mark Playwright out of scope ».

## Test Coverage Goals

- Backend : `./mvnw verify` — Jacoco LINE ≥ 85 % / BRANCH ≥ 75 % (bundle). Classes **nouvelles**
  (`BatchImportConnector`, `KafkaConnector`, `BatchImportConnectorTriggerJob`,
  `KafkaConsumerReconciler`, adaptateurs SFTP/Kafka) : viser 100 % des branches propres à la
  feature.
- Frontend : Vitest sur le nouveau cas `SECRET_TEXTAREA` de `ConnectorConfigForm.vue` + régression
  complète (`ConnectorsAdminView.spec.ts`, `connectorConfig.spec.ts`). **Playwright exigé** :
  régression complète + 1 nouveau scénario `E2E-CONN-*`.
- SFTP et Kafka **mockés uniquement** (D-SFTP-TEST/D-KAFKA-TEST) — `SftpClientPort` et le client
  Kafka sont des ports/interfaces mockés en test, aucun serveur SFTP ni broker Kafka réel. Risque
  assumé et documenté (écart avec `CallbackSmokeIT`/`DmnJuelSecuritySmokeIT`).
- ArchUnit : suite existante doit rester verte sans nouvelle règle (aucune violation hexagonale
  attendue — les nouveaux adaptateurs SSHJ/Kafka restent en `infrastructure`).

## Unit Tests

### `ConfigFieldType` (domaine)

- [ ] `T-CFT-SECRET-TEXTAREA` la nouvelle valeur `SECRET_TEXTAREA` existe et ne casse aucun
      `switch`/pattern matching existant sur `ConfigFieldType` (compilateur + tests exhaustifs
      s'il y en a).

### `BatchImportConnector`

- [ ] `T-BI-CAPABILITY` `getCapability()` == `TRIGGER`
- [ ] `T-BI-VALIDATE-REQUIRED` champs requis manquants (`sftpHost`, `remoteDirectory`,
      `filenamePattern`, `archiveDirectory`, `cronExpression`, `processKey`, `dossierTypeCode`) →
      `ConnectorValidationException`
- [ ] `T-BI-VALIDATE-AUTH-PASSWORD` `sftpAuthType=PASSWORD` sans `sftpPassword` → rejeté
- [ ] `T-BI-VALIDATE-AUTH-KEY` `sftpAuthType=PRIVATE_KEY` sans `sftpPrivateKey` → rejeté
- [ ] `T-BI-DEFAULT-PORT` `sftpPort` absent → 22
- [ ] `T-BI-CSV-OK` fichier CSV valide → N `WorkflowTriggerEvent` (mapping colonnes → champs)
- [ ] `T-BI-XLSX-OK` fichier `.xlsx` valide → même résultat que l'équivalent CSV (parité de mapping)
- [ ] `T-BI-INVALID-ROW-SKIPPED` une ligne avec un champ mappé requis manquant/mal typé est
      exclue (log + compteur) ; les autres lignes du même fichier produisent bien leur événement
      (D-BATCH-ERROR-HANDLING)
- [ ] `T-BI-ALL-ROWS-INVALID` toutes les lignes invalides → liste d'événements vide, **pas**
      d'exception (le fichier est quand même considéré traité/archivable)
- [ ] `T-BI-EMPTY-FILE` fichier sans ligne de données → liste vide, pas d'erreur
- [ ] `T-BI-SIZE-LIMIT` fichier dépassant `maxFileBytes` → rejeté avant tout parsing (log, pas
      d'événement), pas d'archivage (D-BATCH-SIZE-LIMIT)
- [ ] `T-BI-ROW-LIMIT` fichier dépassant `maxRows` → même comportement que `T-BI-SIZE-LIMIT`
- [ ] `T-BI-NO-MATCH` aucun fichier du répertoire ne matche `filenamePattern` → liste vide,
      **aucun** téléchargement SFTP tenté (vérifié sur le mock)
- [ ] `T-BI-MULTIPLE-FILES` 2 fichiers matchent au même poll → les deux sont téléchargés, parsés,
      archivés (ordre déterministe, ex. nom croissant)
- [ ] `T-BI-ARCHIVE-SUCCESS` fichier traité (même avec des lignes invalides isolées) → déplacé/
      renommé vers `archiveDirectory` une fois le traitement terminé
- [ ] `T-BI-SFTP-CONN-FAILURE` échec de connexion/auth SFTP (mock qui lève) → exception domaine
      journalisée, propagée à l'appelant (le job, pas cette classe, décide de continuer les autres
      instances)

### `BatchImportConnectorTriggerJob`

- [ ] `T-BIJ-DUE` instance `batch-import` activée, cron dû → `onIncomingEvent` appelé
- [ ] `T-BIJ-NOT-DUE` cron pas encore dû → pas d'appel
- [ ] `T-BIJ-DISABLED` `enabled=false` → ignorée
- [ ] `T-BIJ-OTHER-TYPES-IGNORED` instances `scheduler`/`http-rest`/etc. ignorées par ce job
      (garde de régression — le job ne doit filtrer que `"batch-import"`)
- [ ] `T-BIJ-FIRST-FIRE` aucun `lastTriggeredAt` connu → déclenche immédiatement (parité avec
      `SchedulerConnectorTriggerJob`)
- [ ] `T-BIJ-SCHEDULE-STATE-SHARED` après déclenchement, `ConnectorScheduleStateRepository.upsert`
      appelé — **même table/port** que `scheduler`, pas de nouvelle table (vérifie D-BATCH-CRON-JOB)
- [ ] `T-BIJ-ONE-INSTANCE-FAILS` une instance qui lève une exception (ex. SFTP down) n'empêche pas
      le poll de traiter les instances `batch-import` suivantes du même tenant

### `KafkaConnector`

- [ ] `T-KAFKA-CAPABILITY` `getCapability()` == `TRIGGER`
- [ ] `T-KAFKA-VALIDATE-REQUIRED` champs requis manquants → `ConnectorValidationException`
- [ ] `T-KAFKA-VALIDATE-SASL` `securityProtocol=SASL_SSL` sans `saslUsername`/`saslPassword` →
      rejeté ; `PLAINTEXT` ne les requiert pas
- [ ] `T-KAFKA-MAP-OK` message JSON valide → exactement **1** `WorkflowTriggerEvent` (pas de notion
      `itemsPath`, contrairement à `http-rest`)
- [ ] `T-KAFKA-EXISTING-REF` mapping avec `existingDossierReferencePath` → événement cible un
      dossier existant plutôt que d'en créer un (parité avec `http-rest`)
- [ ] `T-KAFKA-MALFORMED` un champ de `mapping.fields` résolvant à `null`/blanc →
      `ConnectorValidationException` (même correction que `BatchImportConnector` :
      `processKey`/`dossierTypeCode` ont toujours un repli d'instance obligatoire, donc ne
      peuvent jamais être blancs — seul un champ mappé peut réellement manquer). Un payload
      non-JSON est rejeté **avant** `KafkaConnector.onEvent` (désérialisation dans
      `KafkaConsumerReconciler`, voir `T-KR-MESSAGE-ERROR-ISOLATION`)

### `KafkaConsumerReconciler`

- [ ] `T-KR-START-ON-DISCOVERY` nouvelle instance `kafka` activée détectée au poll → un
      `MessageListenerContainer` démarré avec `bootstrapServers`/`topic`/`groupId` corrects et
      `auto.offset.reset=latest` (D-KAFKA-OFFSET)
- [ ] `T-KR-STOP-ON-DISABLE` instance passée à `enabled=false` → conteneur arrêté et retiré
- [ ] `T-KR-STOP-ON-DELETE` instance supprimée (soft delete) → conteneur arrêté et retiré
- [ ] `T-KR-RESTART-ON-CONFIG-CHANGE` `topic`/`bootstrapServers` modifiés sur une instance déjà
      suivie → ancien conteneur arrêté, nouveau démarré avec la config à jour
- [ ] `T-KR-IDEMPOTENT-POLL` deux polls consécutifs sans changement → aucun conteneur redémarré/
      dupliqué pour une même instance
- [ ] `T-KR-MESSAGE-DISPATCH` message reçu par le conteneur → `ConnectorTriggerRuntime.
      onIncomingEvent(instance, payload)` appelé
- [ ] `T-KR-MESSAGE-ERROR-ISOLATION` un message qui échoue au mapping (`T-KAFKA-MALFORMED`) est
      journalisé et **ne stoppe pas** la consommation des messages suivants du même conteneur
- [ ] `T-KR-START-FAILURE-ISOLATED` une instance dont la config Kafka est invalide (host
      injoignable au démarrage du conteneur) ne bloque pas la gestion des autres instances au
      même poll
- [ ] `T-KR-PLAINTEXT-PROPS` `securityProtocol=PLAINTEXT` → aucune propriété SASL sur le consumer
- [ ] `T-KR-SASL-PROPS` `securityProtocol=SASL_SSL` → propriétés `security.protocol=SASL_SSL` +
      JAAS construites depuis `saslUsername`/`saslPassword`

### `ConnectorProperties` (`BatchImport`, `Kafka`)

- [ ] `T-PROPS-BATCH-DEFAULTS` valeurs par défaut sensées si absentes de `application.yml` (même
      pattern que `Http`/`Webhook`/`DocumentStorage`)
- [ ] `T-PROPS-KAFKA-DEFAULTS` idem pour la section `Kafka`

### Masquage / chiffrement des secrets (extension)

- [ ] `T-SECRET-ISPASSWORDFIELD-KEY` `ConnectorCatalog.isPasswordField("batch-import",
      "sftpPrivateKey")` == `true` (schéma déclaré `SECRET_TEXTAREA` traité comme sensible, pas
      seulement `PASSWORD`)
- [ ] `T-SECRET-ISPASSWORDFIELD-PWD` idem pour `sftpPassword` (`batch-import`) et `saslPassword`
      (`kafka`)
- [ ] `T-SECRET-MASK-ROUNDTRIP` `GET` sur une instance avec `sftpPrivateKey` renseigné renvoie
      `SECRET_MASK` ("********"), jamais la clé en clair
- [ ] `T-SECRET-UPDATE-PRESERVE` `PATCH`/update envoyant le masque ou une valeur vide pour
      `sftpPrivateKey` préserve la valeur existante (réutilise `ManageConnectorInstanceUseCaseImpl.
      mergeConfig`, déjà testé pour `PASSWORD` — vérifier qu'il couvre aussi `SECRET_TEXTAREA`)
- [ ] `T-SECRET-ENCRYPT-AT-REST` la valeur persistée de `sftpPrivateKey` est chiffrée (même
      adaptateur/`ConnectorSecretCipher` que tout champ sensible existant)

### Garde de régression connecteurs existants

- [ ] `T-REG-SCHEDULER-UNCHANGED` `SchedulerConnectorTriggerJobTest` existants toujours verts sans
      modification (job dédié choisi précisément pour ne pas y toucher, D-BATCH-CRON-JOB)
- [ ] `T-REG-REGISTRY-COUNT` `ConnectorRegistry.listAll()` expose les connecteurs existants + 2
      (`batch-import`, `kafka`), pas de collision de type (`ConnectorRegistry.init()` lève déjà sur
      doublon — test qui instancie le contexte réel avec tous les beans)

## Integration Tests

- [ ] `IT-BI-END-TO-END` `ManageConnectorInstanceUseCase.create` (type `batch-import`) →
      `BatchImportConnectorTriggerJob.process(now)` (SFTP mocké en mémoire, ex. fake filesystem ou
      mock du port) → dossier(s) réellement créés et lisibles via `GetDossierUseCase`, comme
      `SchedulerConnectorTriggerJobTest`/`ConnectorTriggerRuntimeImplTest` existants
- [ ] `IT-KAFKA-END-TO-END` instance `kafka` créée → `KafkaConsumerReconciler` démarre un
      conteneur mocké → message simulé injecté → dossier créé et lisible
- [ ] `IT-SECRET-FULL-ROUNDTRIP` `POST` création avec `sftpPrivateKey` → `GET` masqué → update avec
      masque préservé → valeur déchiffrée utilisée par le connecteur au moment de l'exécution
      (jamais exposée via une réponse HTTP)

## End-to-End Tests

- [ ] `E2E-CONN-BATCH-IMPORT-KEY` Admin → Connecteurs : créer une instance `batch-import`,
      `sftpAuthType=PRIVATE_KEY` → le champ `sftpPrivateKey` s'affiche en `<textarea>` (pas
      `<input>`), accepte une valeur multi-lignes, la création réussit
- [ ] Régression complète des specs `ConnectorsAdminView`/`E2E-CONN-*` existants (webhook `http-rest`,
      `smtp`, `flowable-callback`) — zéro régression sur les cas `PASSWORD`/`SELECT`/`BOOLEAN`
      existants dans `ConnectorConfigForm.vue`

## Test Data

- Mock `SftpClientPort` : fixture en mémoire (liste de fichiers simulés, contenu CSV/XLSX en
  ressource de test) — pas de vrai serveur SSH.
- Mock du client Kafka (`ConsumerFactory`/`MessageListenerContainer` de test, ou double manuel) —
  pas de vrai broker.
- Fixtures CSV/XLSX de test sous `src/test/resources/connector/batch-import/` (valides, avec lignes
  invalides mêlées, vide, au-dessus de `maxRows`).
- `docker-compose.yml` : service Kafka non démarré en CI/tests (mocké), seulement pour le dev
  manuel local — pas de dépendance de test sur ce service.

## Test Reporting & Coverage

```bash
./mvnw test -Dtest=BatchImportConnectorTest,BatchImportConnectorTriggerJobTest,KafkaConnectorTest,KafkaConsumerReconcilerTest,ConnectorPropertiesTest,ManageConnectorInstanceUseCaseImplTest,ConnectorRegistryTest
./mvnw verify
cd frontend && npm run test -- --coverage
cd frontend && npm run test:e2e
```

- Jacoco LINE ≥ 85 % / BRANCH ≥ 75 % (bundle), 100 % visé sur les classes neuves.
- Vitest : régression + nouveau cas `SECRET_TEXTAREA`.
- Playwright : régression + `E2E-CONN-BATCH-IMPORT-KEY`.
- Coverage gaps attendus : code SSHJ/Kafka bas niveau (adaptateurs) testé via le port mocké, pas
  ligne à ligne sur la bibliothèque tierce elle-même.

## Manual Testing

- `docker compose config` (validation statique) — `docker compose up` réel non garanti exécutable
  dans ce bac à sable (limite déjà documentée pour A1/Docker Hub) ; à confirmer manuellement avant
  premier déploiement si l'environnement de build le permet.
- Démo manuelle recommandée avant merge (hors scope des tests automatisés) : un vrai serveur SFTP
  et un vrai broker Kafka locaux, pour valider ce que les tests mockés ne peuvent pas prouver —
  décision assumée (D-SFTP-TEST/D-KAFKA-TEST) de ne pas en faire une preuve automatisée ce cycle.

## Performance Testing

- Pas de test de charge dédié. Les gardes `maxFileBytes`/`maxRows` (D-BATCH-SIZE-LIMIT) sont
  vérifiées fonctionnellement (rejet propre), pas par un test de performance chiffré.

## Bug Tracking

- Tout écart réel trouvé pendant l'implémentation (Phase 5) ou la revue (Phase 7/8) est documenté
  dans `docs/ai/implementation/2026-09-14-feature-connecteurs-cible.md`, comme pour tous les
  cycles précédents de ce dépôt.
