---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
---

# Project Planning & Task Breakdown

## Milestones
**What are the major checkpoints?**

- [x] M1 : `smtp` (SERVICE) — réutilise `EmailPort`, indépendant des autres milestones
- [x] M2 : `document-storage` (SERVICE) — réutilise `DocumentStoragePort`, indépendant
- [x] M3 : `scheduler` (TRIGGER) + `connector_schedule_state` + job de poll — le plus gros lot,
      seule migration Liquibase de la feature
- [x] M4 : Documentation — `06-connecteurs.md`, `README.md` (dépend M1–M3, comportement final requis)
- [ ] M5 : Vérification globale + clôture (Phases 7–9)

## Task Breakdown
**What specific work needs to be done?**

### M1 — `smtp` (SERVICE)

- [x] T1.1 : `SmtpConnector` — `implements ConnectorDefinition, ServiceConnector`, `getType() ==
      "smtp"`, `getConfigSchema()` vide (aucun champ requis), `validate(config)` no-op.
      Dépendance : aucune. Validation : T-SMTP-1.
- [x] T1.2 : `SmtpConnector.execute(instance, connectorInput)` — lit `to`/`subject`/`body`
      (requis), garde `to` non-blank + contient « @ » (`IllegalArgumentException` sinon), appelle
      `EmailPort.send(to, subject, body)`, retourne `Map.of("sent", true)`. Dépendance : T1.1.
      Validation : T-SMTP-2 à T-SMTP-6.
- [x] T1.3 (régression anti-fuite) : `connectorResult` ne contient jamais `instance.config()` —
      même garantie que les autres connecteurs SERVICE. Dépendance : T1.2. Validation : T-SMTP-7.
- [x] T1.4 : `ConnectorConfig` — bean `SmtpConnector` (constructeur injecte `EmailPort`, déjà bean
      Spring existant). Dépendance : T1.1–T1.2.

### M2 — `document-storage` (SERVICE)

- [x] T2.1 : `ConnectorProperties.DocumentStorage(long maxContentSize)` — nouveau sous-record,
      défaut (ex. 5 Mo), même patron que `Webhook.maxBodyBytes`. Dépendance : aucune. Validation :
      T-PROP-1, T-PROP-2.
- [x] T2.2 : `DocumentStorageConnector` — `implements ConnectorDefinition, ServiceConnector`,
      `getType() == "document-storage"`, `getConfigSchema()` vide. Dépendance : aucune. Validation :
      T-STORAGE-1.
- [x] T2.3 : `DocumentStorageConnector.execute` — lit `operation` (`STORE`/`RETRIEVE`/`EXISTS`/
      `DELETE`, requis), `storageRef` (requis sauf `STORE`), `content`/`contentType` (requis pour
      `STORE`) ; route vers `DocumentStoragePort` correspondant, résultat en `connectorResult`
      (`storageRef` pour `STORE`, `content` base64 pour `RETRIEVE`, booléen pour `EXISTS`, ack pour
      `DELETE`). Dépendance : T2.2. Validation : T-STORAGE-2 à T-STORAGE-5, T-STORAGE-7, T-STORAGE-8.
- [x] T2.4 : garde de taille — rejette `STORE` si le contenu base64 décodé dépasse
      `ConnectorProperties.DocumentStorage.maxContentSize`, avant tout appel au port. Dépendance :
      T2.1, T2.3. Validation : T-STORAGE-6.
- [x] T2.5 : `ConnectorConfig` — bean `DocumentStorageConnector` (injecte `DocumentStoragePort`,
      `ConnectorProperties`). Dépendance : T2.1–T2.4.

### M3 — `scheduler` (TRIGGER) + état persistant + job de poll

- [x] T3.1 : migration Liquibase `018-connector-schedule-state.yaml` — table
      `connector_schedule_state` (`connector_instance_id` UUID PK+FK → `connector_instance(id)`,
      `last_triggered_at` timestamp nullable), rollback `dropTable`. Dépendance : aucune.
- [x] T3.2 : `ConnectorScheduleStateRepository` (port `port.out`) — `findByConnectorInstanceId`,
      `upsert(connectorInstanceId, triggeredAt)`. Dépendance : aucune (port peut précéder T3.1).
- [x] T3.3 : `JpaConnectorScheduleStateRepositoryAdapter` + entité JPA — implémente T3.2 contre la
      table de T3.1. Dépendance : T3.1, T3.2. Validation : T-STATE-1, T-STATE-2.
- [x] T3.4 : `SchedulerConnector` — `implements ConnectorDefinition, TriggerConnector`, `getType()
      == "scheduler"`, `getConfigSchema()` = `cronExpression` + `processKey` + `dossierTypeCode`
      (tous requis — **`processKey` ajouté en Phase 4**, absent du design initial : `HttpRestConnector`
      et `FlowableSignalConnector` l'ont tous deux comme champ de config requis, indispensable pour
      construire un `WorkflowTriggerEvent`, qui le `requireNonNull`/garde blank), `validate(config)`
      rejette un cron invalide (`CronExpression.parse` échoue), `processKey` ou `dossierTypeCode`
      blank. Dépendance : aucune. Validation : T-SCHED-1 à T-SCHED-4.
- [x] T3.5 : `SchedulerConnector.onEvent(instance, rawPayload)` — retourne
      `List.of(new WorkflowTriggerEvent(processKey, dossierTypeCode, dossierFields=Map.of(),
      variables=Map.of(), existingDossierReference=null))`, les deux valeurs lues depuis
      `instance.config()`. Dépendance : T3.4. Validation : T-SCHED-5.
- [x] T3.6 : `SchedulerConnectorTriggerJob` (`@Scheduled(fixedDelayString =
      "${app.connector.scheduler.poll-delay:1m}")`) — liste les instances `scheduler` activées via
      `ConnectorInstanceRepository.listByTenantId` (déjà filtré `deletedAtIsNull`), pour chacune :
      lit l'état via T3.2, évalue si due (`CronExpression`, absent = due), si oui appelle
      `ConnectorTriggerRuntime.onIncomingEvent(instance, Map.of())` puis `upsert`. Dépendance :
      T3.3, T3.4, T3.5. Validation : T-JOB-1 à T-JOB-6.
- [x] T3.7 : `ConnectorConfig` — bean `SchedulerConnector`. `ConnectorProperties` — nouveau
      sous-champ `scheduler.pollDelay` si besoin de configuration au-delà du défaut YAML.
      Dépendance : T3.4–T3.6.

### M4 — Documentation

- [x] T4.1 : `06-connecteurs.md` — tableau catalogue : `scheduler`/`smtp` passent de `Non` à
      enregistrés ; `s3-minio` remplacé par `document-storage` avec description généralisée
      (réutilise `DocumentStoragePort`, pas de couplage S3). Dépendance : M1–M3 livrés
      (comportement final à décrire).
- [x] T4.2 : `README.md` § Système de connecteurs — retire les mentions `Non`/`s3-minio`.
      Dépendance : T4.1 (même passage de revue).

### M5 — Vérification globale + clôture (Phases 7–9)

- [x] T5.1 : Phase 7 (check implementation) — relecture fichier par fichier des 3 connecteurs +
      job + repository d'état contre le design ; chercher les écarts réels (pattern historique du
      dépôt : la plupart des features précédentes trouvent 1-2 écarts réels à cette étape).
      Dépendance : M1–M4. **Fait** : aucun écart nouveau trouvé au-delà des 2 déjà enregistrés en
      Phase 5 (DTO `ExecuteConnectorRequest`, `NoopEmailPortTestConfiguration`) — voir
      implementation doc §Phase 7.
- [x] T5.2 : Phase 8 (couverture) — balayage Jacoco classe par classe sur les fichiers
      touchés/nouveaux, combler les lacunes réelles trouvées. Dépendance : T5.1. Validation :
      seuils bundle maintenus (LINE ≥ 85% / BRANCH ≥ 75%). **Fait** : `./mvnw verify` (Phase 5)
      rapporte déjà `All coverage checks have been met.` sur 1486 tests — aucun gap à combler.
- [x] T5.3 : régression frontend complète (`npm run test`, `npm run test:e2e`) — preuve de
      non-impact, pas de nouveau scénario attendu. Dépendance : T5.1. **Fait** : Vitest 247/247,
      Playwright 59/59, zéro fichier `frontend/**` touché par la feature.
- [ ] T5.4 : Phase 9 (revue finale) — alignement design confirmé, migration Liquibase rollback
      sûre, `CHANGELOG.md` mis à jour, PR prête. Dépendance : T5.1–T5.3.

## Dependencies
**What needs to happen in what order?**

```
T1.1 → T1.2 → T1.3 ─┐
              T1.4 ──┤
                      │
T2.1 → T2.3 → T2.4 ──┤
      T2.2 ───┘   T2.5 ┤
                      ├→ T4.1 → T4.2 → T5.1 → T5.2 / T5.3 → T5.4
T3.1 ─┐               │
T3.2 ─┼→ T3.3 ─┐       │
      │        ├→ T3.6 → T3.7 ──┘
T3.4 → T3.5 ────┘
```

- M1, M2, M3 sont mutuellement indépendants (aucun fichier partagé entre les 3 connecteurs) —
  parallélisables sans risque de conflit de merge, comme M1/M2 du cycle
  `assainissement-dette-technique`.
- M3 est le plus gros lot (7 tâches, 1 migration, 1 nouveau port+adaptateur) — à séquencer en
  premier si un seul agent traite la feature, pour détecter tôt le point T3.5 à clarifier
  (`processKey` manquant dans le config schema proposé).
- Aucune dépendance externe (`org.springframework.scheduling.support.CronExpression` déjà sur le
  classpath Spring, aucune nouvelle librairie).
- 1 seule migration Liquibase (T3.1, `018-connector-schedule-state.yaml`) — aucun changement de
  schéma pour `connector_instance`/M1/M2.

## Timeline & Estimates
**When will things be done?**

- Feature de taille moyenne (3 connecteurs indépendants + 1 migration + 1 job) — pas d'estimation
  en jours/points, découpage en 5 milestones/23 tâches TDD suffisant pour le suivi. M1/M2/M3
  parallélisables (le cœur fonctionnel, M3 le plus gros) ; M4 la doc ; M5 la clôture standard.

## Risks & Mitigation
**What could go wrong?**

- **Écart de design trouvé et corrigé en planning (pas en Phase 7)** : le config schema initial de
  `SchedulerConnector` (`cronExpression`+`dossierTypeCode`) omettait `processKey`, pourtant requis
  par `WorkflowTriggerEvent` (`Objects.requireNonNull`) et déjà présent comme champ de config
  requis dans les 2 autres connecteurs TRIGGER (`HttpRestConnector`, `FlowableSignalConnector`) —
  vérifié par grep avant d'écrire T3.4, pas découvert plus tard. Corrigé directement dans T3.4/T3.5
  ci-dessus plutôt que laissé comme risque ouvert.
- **Risque de double-tir sur upsert concurrent** (T3.6) — si le job de poll se chevauche avec un
  run précédent encore actif (poll-delay trop court vs temps de traitement d'un tenant avec
  beaucoup d'instances), un même tir pourrait être compté deux fois. Mitigation : `@Scheduled`
  Spring par défaut ne parallélise pas les exécutions du même job (un seul thread séquentiel sauf
  configuration explicite `TaskScheduler` autrement) — à confirmer en implémentation, pas assumé.
- **Risque de régression sur `ConnectorRegistry`** — 3 nouveaux types enregistrés simultanément,
  risque de collision de `getType()` entre eux ou avec l'existant. Mitigation : `getType()` de
  chacun est un literal distinct (`"scheduler"`, `"smtp"`, `"document-storage"`), vérifié contre
  `06-connecteurs.md` (aucun ne collisionne avec `http-rest`/`flowable-signal`).

## Resources Needed
**What do we need to succeed?**

- Aucune ressource externe — `org.springframework.scheduling.support.CronExpression` déjà sur le
  classpath Spring, `EmailPort`/`DocumentStoragePort` déjà des beans configurés, aucun nouveau
  service tiers ni infrastructure supplémentaire.
- Référence de comportement : `WebhookController`/`FlowableSignalEventListener` comme précédents
  d'appelants de `ConnectorTriggerRuntime.onIncomingEvent` depuis l'infra (T3.6) ;
  `HttpRestConnectorTest` comme modèle de structure de test pour les 3 nouveaux connecteurs.
