# §50 — Nouveaux connecteurs hors V1 — `scheduler`, `smtp`, `document-storage`

> **Statut :** clos · **Section :** [Points ouverts › Compléments de conception](section-points-complements-de-conception.md) · ancien `TODO.md` §50 · [registre](README.md)

**Clos** (2026-09-01, `connecteurs-hors-v1`,
[PR #99](https://github.com/superjojo79/FlowForge/pull/99)) — cycle `dev-lifecycle` complet
(Phases 1-9). Les 3 connecteurs restants du catalogue cible livrés, chacun en réutilisant un
mécanisme domaine déjà existant plutôt qu'un nouveau couplage technique — zéro modification de
`ConnectorTriggerRuntimeImpl`/`ConnectorCatalog`/`ConnectorRegistry`.

**Constat :** `06-connecteurs.md` catalogue 5 types de connecteurs cibles, seuls `http-rest` et
`flowable-signal` sont enregistrés dans `ConnectorRegistry` aujourd'hui. Les 3 restants
(`scheduler`, `smtp`, `s3-minio`) étaient explicitement hors V1 à chaque cycle précédent. Choisi
comme prochaine feature (2026-09-01) après épuisement complet de TODO.md (49/49), de la revue
sécurité (§40–46) et du sweep de dette technique (§49), parmi deux pistes proposées à
l'utilisateur (l'autre — outillage provisioning tenant — reste en réserve). Périmètre précisé par
l'utilisateur : les 3 connecteurs, avec `s3-minio` **généralisé** en `document-storage` (réutilise
le port `DocumentStoragePort` déjà agnostique S3/filesystem, cohérent avec le principe
« ne pas coupler à MinIO » de [`DEFERRED-FEATURES.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md) §0) plutôt que couplé à un client S3 direct.

**Mécanismes identifiés en analyse (pas supposés, vérifiés contre le code) :**

- `scheduler` (TRIGGER) réutilise le pipeline trigger générique existant
  (`ConnectorTriggerRuntimeImpl.onIncomingEvent`, déjà découplé de HTTP) — seule pièce nouvelle :
  un job `@Scheduled` qui décide quand l'appeler pour chaque instance selon son `cronExpression`
  (`org.springframework.scheduling.support.CronExpression`, déjà sur le classpath).
- `smtp` (SERVICE) réutilise le port `EmailPort`/`SmtpEmailAdapter` déjà livré avec `notifications`
  — envoi direct ad hoc depuis un `serviceTask`, distinct du pipeline notification
  structuré/templé/outbox existant (pas une duplication).
- `document-storage` (SERVICE) réutilise `DocumentStoragePort` (`store`/`retrieve`/`exists`/
  `delete`) pour du contenu non lié à un `Dossier`/`DossierElement` (déjà couvert par les pièces
  jointes existantes).

**Décisions actées (`AskUserQuestion`, options recommandées retenues) :**

1. **D-SCHEDULER-LASTFIRED = table dédiée** — `connector_schedule_state` (`connector_instance_id`
   PK+FK, `last_triggered_at`), séparée de `ConnectorInstanceData` (record partagé par tous les
   connecteurs).
2. **D-SCHEDULER-DOSSIER = un dossier par tir, comportement existant** — `scheduler` exige
   `dossierTypeCode` en config comme les autres connecteurs TRIGGER, zéro changement du pipeline
   trigger partagé.
3. **D-SCHEDULER-POLL = 1 minute par défaut, configurable** — `app.connector.scheduler.poll-delay`.
4. **D-SMTP-HTML = texte brut seul** — `EmailPort.send(to, subject, body)` tel quel.
5. **D-SMTP-VALIDATION = garde basique non-blank + « @ »**.
6. **D-STORAGE-SIZE-LIMIT = limite configurable** — `ConnectorProperties.DocumentStorage.
   maxContentSize` (même patron que `Webhook.maxBodyBytes`).
7. **D-STORAGE-REF-LIFECYCLE = choix assumé, documenté** — aucune purge automatique du
   `storageRef`, à la charge du process BPMN.

**Écart de design trouvé et corrigé en planning (pas en Phase 7)** : le config schema initial de
`SchedulerConnector` (`cronExpression`+`dossierTypeCode`) omettait `processKey`, pourtant requis
par `WorkflowTriggerEvent` (`Objects.requireNonNull`) et déjà présent dans `HttpRestConnector`/
`FlowableSignalConnector` — vérifié par grep avant d'écrire le code, pas découvert plus tard.

**Déviations trouvées en implémentation (Phase 5, confirmées en Phase 7)** :
- `ExecuteConnectorRequest` (DTO partagé BFF/M2M) étendu de 6 champs (`to`, `subject`, `operation`,
  `storageRef`, `content`, `contentType`) pour porter les entrées `smtp`/`document-storage` —
  même point de couture déjà rencontré pour `http-rest` (TODO §48), 2ᵉ occurrence. OpenAPI golden
  régénéré.
- `NoopEmailPortTestConfiguration` (nouveau `@TestConfiguration`, patron `OidcClientTestConfiguration`)
  pour tester `smtp` en contexte Spring complet sans serveur SMTP réel (aucun GreenMail dans le
  dépôt).

**Validation :** `./mvnw -Dmaven.compiler.release=21 verify` vert (**1486 tests**, `All coverage
checks have been met.`, seuils Jacoco maintenus sans aucun gap à combler). Feature confirmée
backend seule : `npm run test` (**247 tests**) et `npm run test:e2e` (**59 tests**) verts en
régression, zéro fichier `frontend/**` touché. Revue Phase 7 (fichier par fichier contre le
design) : aucun écart supplémentaire trouvé.

**Impacte :** `ConnectorRegistry` (découverte automatique, aucune modification), `06-connecteurs.md`,
`README.md`, nouveaux fichiers `infrastructure/connector/builtin/{SchedulerConnector,SmtpConnector,
DocumentStorageConnector}.java`, `infrastructure/connector/SchedulerConnectorTriggerJob.java`,
`domain/connector/port/out/ConnectorScheduleStateRepository.java`, migration Liquibase
`018-connector-schedule-state.yaml`, `ConnectorProperties.DocumentStorage`,
`ExecuteConnectorRequest`, [`DEFERRED-FEATURES.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md), `CHANGELOG.md`.

**Détail :** [20-connecteurs-hors-v1.md](../specs/20-connecteurs-hors-v1.md).
