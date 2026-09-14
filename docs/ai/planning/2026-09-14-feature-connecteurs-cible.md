---
phase: planning
title: Project Planning & Task Breakdown
description: Break down work into actionable tasks and estimate timeline
status: planned
feature: connecteurs-cible
lot: B1
---

# Project Planning & Task Breakdown — `connecteurs-cible`

> Requirements : [2026-09-14-feature-connecteurs-cible.md](../requirements/2026-09-14-feature-connecteurs-cible.md).
> Design : [2026-09-14-feature-connecteurs-cible.md](../design/2026-09-14-feature-connecteurs-cible.md).
> Testing : [2026-09-14-feature-connecteurs-cible.md](../testing/2026-09-14-feature-connecteurs-cible.md).
> Un seul cycle, une PR (FILE-ATTENTE B1) — les deux connecteurs partagent le même
> enregistrement/tests d'infrastructure, pas 2 features concurrentes.

## Milestones

- [ ] **M0** — Fondations partagées : `ConfigFieldType.SECRET_TEXTAREA` (domaine + frontend),
  `ConnectorProperties.BatchImport`/`Kafka`, extension masquage/chiffrement secrets.
- [ ] **M1** — `batch-import` : connecteur (validate/capability/mapping/gardes), tests unitaires.
- [ ] **M2** — `batch-import` : job de déclenchement cron dédié + IT bout-en-bout.
- [ ] **M3** — `kafka` : connecteur (validate/capability/mapping), tests unitaires.
- [ ] **M4** — `kafka` : reconciliateur de cycle de vie des consumers + IT bout-en-bout.
- [ ] **M5** — `docker-compose.yml` (service Kafka) + doc catalogue connecteurs.
- [ ] **M6** — Frontend : `ConnectorConfigForm.vue` (`SECRET_TEXTAREA`) + Vitest + Playwright.
- [ ] **M7** — Vérification globale (Phase 7-8), comblement de trous de couverture Jacoco.
- [ ] **M8** — Revue holistique (Phase 9), docs projet (`06-connecteurs.md`,
  `GUIDE-CONCEPTION-BPMN.md`, `CHANGELOG.md`, `TODO.md`/`FILE-ATTENTE.md`/`DEFERRED-FEATURES.md`).

## Task Breakdown

### M0 — Fondations partagées (bloquant M1–M4)

- [ ] **T0.1** `ConfigFieldType.SECRET_TEXTAREA` — nouvelle valeur d'enum domaine.
  Dépend de : rien. Couvre : `T-CFT-SECRET-TEXTAREA`.
- [ ] **T0.2** `pom.xml` — ajouter `com.hierynomus:sshj`, `org.apache.poi:poi-ooxml`,
  `spring-kafka` (versions alignées BOM Spring Boot du projet). Dépend de : rien.
- [ ] **T0.3** `ConnectorProperties.BatchImport(maxFileBytes, maxRows)` et
  `ConnectorProperties.Kafka(...)` avec défauts, même pattern que `Http`/`Webhook`. Dépend de :
  T0.1 non requis. Couvre : `T-PROPS-BATCH-DEFAULTS`, `T-PROPS-KAFKA-DEFAULTS`.
- [ ] **T0.4** `ManageConnectorInstanceUseCaseImpl.isSecretKey` / `ConnectorCatalog.
  isPasswordField` — étendre pour traiter `SECRET_TEXTAREA` comme un type sensible (masquage
  lecture + préservation sur update), pas seulement `PASSWORD`. Dépend de : T0.1. Couvre :
  `T-SECRET-ISPASSWORDFIELD-KEY`, `T-SECRET-ISPASSWORDFIELD-PWD`, `T-SECRET-MASK-ROUNDTRIP`,
  `T-SECRET-UPDATE-PRESERVE`.
- [ ] **T0.5** Vérifier `ConnectorSecretCipher`/l'adaptateur de persistance chiffre bien tout champ
  détecté sensible par T0.4 sans modification de code (déjà générique par construction — sinon,
  écart réel à documenter). Couvre : `T-SECRET-ENCRYPT-AT-REST`.
- [ ] **T0.6** IT round-trip complet d'un secret `SECRET_TEXTAREA` : create (`sftpPrivateKey`) →
  get (masqué) → update (masque préservé) → valeur déchiffrée utilisée par le connecteur à
  l'exécution. Dépend de : T0.4, T1.2 (nécessite le connecteur `batch-import` enregistré pour
  avoir un champ `SECRET_TEXTAREA` réel à exercer) — exécuté après M1, pas bloquant pour la suite
  de M0. Couvre : `IT-SECRET-FULL-ROUNDTRIP`.

### M1 — `BatchImportConnector` (dépend de M0)

- [ ] **T1.1** `SftpClientPort` (port domaine) + DTO minimal (liste fichiers, download, move/
  rename). Dépend de : rien (peut démarrer en parallèle de M0 selon TDD).
- [ ] **T1.2** `BatchImportConnector.validate()`/`getCapability()`/`getConfigSchema()`. Dépend
  de : T0.1 (config schema référence `SECRET_TEXTAREA`). Couvre : `T-BI-CAPABILITY`,
  `T-BI-VALIDATE-REQUIRED`, `T-BI-VALIDATE-AUTH-PASSWORD`, `T-BI-VALIDATE-AUTH-KEY`,
  `T-BI-DEFAULT-PORT`.
- [ ] **T1.3** Mapping colonnes CSV → champs (parseur CSV + `TabularRowMapper`). Dépend de :
  T1.1. Couvre : `T-BI-CSV-OK`, `T-BI-EMPTY-FILE`.
- [ ] **T1.4** Mapping colonnes XLSX → champs (Apache POI, `XSSFWorkbook`). Dépend de : T1.3
  (même mapper réutilisé). Couvre : `T-BI-XLSX-OK`.
- [ ] **T1.5** Isolation ligne invalide (log + compteur, pas de blocage). Dépend de : T1.3/T1.4.
  Couvre : `T-BI-INVALID-ROW-SKIPPED`, `T-BI-ALL-ROWS-INVALID`.
- [ ] **T1.6** Garde `maxFileBytes`/`maxRows` (rejet propre avant parsing). Dépend de : T0.3, T1.3.
  Couvre : `T-BI-SIZE-LIMIT`, `T-BI-ROW-LIMIT`.
- [ ] **T1.7** Listing répertoire + motif glob + téléchargement multi-fichiers. Dépend de : T1.1.
  Couvre : `T-BI-NO-MATCH`, `T-BI-MULTIPLE-FILES`.
- [ ] **T1.8** Archivage post-traitement (move/rename SFTP). Dépend de : T1.5, T1.7. Couvre :
  `T-BI-ARCHIVE-SUCCESS`.
- [ ] **T1.9** Gestion échec connexion/auth SFTP (exception domaine propre, journalisée). Dépend
  de : T1.1. Couvre : `T-BI-SFTP-CONN-FAILURE`.
- [ ] **T1.10** Adaptateur SSHJ réel de `SftpClientPort` (utilisé seulement en prod ; les tests
  ci-dessus utilisent un mock/faux du port). Dépend de : T1.1, T0.2.

### M2 — `BatchImportConnectorTriggerJob` (dépend de M1)

- [ ] **T2.1** Job dédié, copie filtrée du pattern `SchedulerConnectorTriggerJob` (type
  `"batch-import"`, réutilise `ConnectorScheduleStateRepository` tel quel). Dépend de : M1.
  Couvre : `T-BIJ-DUE`, `T-BIJ-NOT-DUE`, `T-BIJ-DISABLED`, `T-BIJ-OTHER-TYPES-IGNORED`,
  `T-BIJ-FIRST-FIRE`, `T-BIJ-SCHEDULE-STATE-SHARED`.
- [ ] **T2.2** Isolation par instance au sein d'un même poll (une instance en échec n'empêche pas
  les autres). Dépend de : T2.1. Couvre : `T-BIJ-ONE-INSTANCE-FAILS`.
- [ ] **T2.3** IT bout-en-bout `batch-import` (SFTP mocké → dossiers réellement créés/lisibles).
  Dépend de : T2.1, T1.*. Couvre : `IT-BI-END-TO-END`.
- [ ] **T2.4** Garde de régression : suite `SchedulerConnectorTriggerJobTest` existante inchangée
  et verte. Dépend de : T2.1. Couvre : `T-REG-SCHEDULER-UNCHANGED`.

### M3 — `KafkaConnector` (dépend de M0, indépendant de M1/M2)

- [ ] **T3.1** `validate()`/`getCapability()`/`getConfigSchema()`. Dépend de : T0.1. Couvre :
  `T-KAFKA-CAPABILITY`, `T-KAFKA-VALIDATE-REQUIRED`, `T-KAFKA-VALIDATE-SASL`.
- [ ] **T3.2** Mapping JSON message → 1 `WorkflowTriggerEvent` (réutilise le style
  `SimpleJsonPathMapper`). Dépend de : T3.1. Couvre : `T-KAFKA-MAP-OK`, `T-KAFKA-EXISTING-REF`.
- [ ] **T3.3** Rejet propre message malformé (`ConnectorValidationException`). Dépend de : T3.2.
  Couvre : `T-KAFKA-MALFORMED`.

### M4 — `KafkaConsumerReconciler` (dépend de M3)

- [ ] **T4.1** Boucle de réconciliation (`@Scheduled`) : diff instances activées vs conteneurs en
  cours. Dépend de : M3, T0.2. Couvre : `T-KR-START-ON-DISCOVERY`, `T-KR-IDEMPOTENT-POLL`.
- [ ] **T4.2** Arrêt propre sur désactivation/suppression d'instance. Dépend de : T4.1. Couvre :
  `T-KR-STOP-ON-DISABLE`, `T-KR-STOP-ON-DELETE`.
- [ ] **T4.3** Redémarrage sur changement de config (topic/bootstrapServers). Dépend de : T4.1.
  Couvre : `T-KR-RESTART-ON-CONFIG-CHANGE`.
- [ ] **T4.4** Construction config consumer PLAINTEXT/SASL_SSL + `auto.offset.reset=latest`.
  Dépend de : T4.1. Couvre : `T-KR-PLAINTEXT-PROPS`, `T-KR-SASL-PROPS`.
- [ ] **T4.5** Dispatch message reçu → `ConnectorTriggerRuntime.onIncomingEvent`. Dépend de :
  T4.1, M3. Couvre : `T-KR-MESSAGE-DISPATCH`.
- [ ] **T4.6** Isolation erreurs : un message malformé ou une instance en échec de démarrage
  n'affecte pas les autres. Dépend de : T4.5, T3.3. Couvre : `T-KR-MESSAGE-ERROR-ISOLATION`,
  `T-KR-START-FAILURE-ISOLATED`.
- [ ] **T4.7** IT bout-en-bout `kafka` (conteneur mocké → dossier créé/lisible). Dépend de :
  M4, M3. Couvre : `IT-KAFKA-END-TO-END`.

### M5 — Infra & doc catalogue (dépend de M2 + M4 pour la doc, indépendant pour Compose)

- [ ] **T5.1** `docker-compose.yml` : service Kafka (image KRaft, sans Zookeeper). Dépend de :
  rien (peut démarrer tôt). Validation : `docker compose config`.
- [ ] **T5.2** `GETTING-STARTED.md` mis à jour si le démarrage local change. Dépend de : T5.1.
- [ ] **T5.3** `06-connecteurs.md` / `GUIDE-CONCEPTION-BPMN.md` : catalogue + exemples de mapping
  pour `batch-import`/`kafka`. Dépend de : M2, M4 (comportement final connu).

### M6 — Frontend (indépendant de M1–M5, dépend de M0.T0.1)

- [ ] **T6.1** `ConnectorConfigForm.vue` : cas `SECRET_TEXTAREA` dans `inputType`/template
  (`<textarea>`). Dépend de : T0.1.
- [ ] **T6.2** Vitest `connectorConfig.spec.ts`/`ConnectorConfigForm.spec.ts` : nouveau cas +
  régression `PASSWORD`/`SELECT`/`BOOLEAN`.
- [ ] **T6.3** Playwright `E2E-CONN-BATCH-IMPORT-KEY` + régression `E2E-CONN-*` existants. Dépend
  de : T6.1, backend M1 (le connecteur `batch-import` doit exister pour être configurable en E2E).

### M7 — Vérification globale (Phase 7-8, dépend de M1–M6)

- [ ] **T7.1** Revue fichier par fichier du diff complet (callers tracés, ArchUnit). Inclut
  `ConnectorRegistry.listAll()` compte exactement les connecteurs existants + 2, sans collision
  de type au démarrage du contexte réel. Couvre : `T-REG-REGISTRY-COUNT`.
- [ ] **T7.2** Balayage Jacoco classe par classe — combler les trous réels trouvés.
- [ ] **T7.3** `./mvnw verify` + Vitest + Playwright — preuve fraîche, tout vert.

### M8 — Clôture (Phase 9)

- [ ] **T8.1** Revue holistique (secrets, migrations, dépendances nouvelles).
- [ ] **T8.2** `CHANGELOG.md`, `TODO.md`, `FILE-ATTENTE.md`, `DEFERRED-FEATURES.md` mis à jour.
- [ ] **T8.3** PR (sur demande explicite de l'utilisateur — pas créée automatiquement).

## Dependencies

- M0 bloque M1, M3, M6 (toutes référencent `ConfigFieldType.SECRET_TEXTAREA` ou
  `ConnectorProperties`).
- M2 dépend de M1 (le connecteur doit exister avant son job de déclenchement).
- M4 dépend de M3 (même raison côté Kafka).
- M1/M2 (`batch-import`) et M3/M4 (`kafka`) sont **indépendants entre eux** — peuvent s'enchaîner
  dans n'importe quel ordre, voire en parallèle par milestone si un TDD strict séquentiel n'est
  pas requis. Aucun fichier partagé entre les deux hors M0.
- M6 (frontend) dépend de M0 et d'au moins un connecteur backend livré pour l'E2E T6.3 (ordre
  recommandé : après M1, avant ou après M3/M4 sans importance).
- M5.T5.3 (doc catalogue) dépend du comportement final de M2 et M4 — dernier avant M7.
- M7/M8 après tout le reste.

## Timeline & Estimates

Pas d'estimation en jours/heures (convention de ce dépôt : les cycles précédents ne datent pas en
temps, seulement en milestones/phases). Ordre recommandé d'exécution TDD :
T0.1 → T0.3 → T0.4/T0.5 → (M1 en entier) → M2 → (M3 en entier) → M4 → M6 → M5.T5.1 tôt (indépendant)
→ M5.T5.3 tard → M7 → M8.

## Risks & Mitigation

- **Tests mockés uniquement (SFTP/Kafka)** — risque de bug d'intégration réel non détecté (comme
  `connecteur-callback` l'a montré avec `createMessageCorrelation`, absent de la vraie API
  Flowable). Mitigation : lire la Javadoc/les sources réelles de SSHJ et de l'API Kafka consumer
  Java avant d'écrire les adaptateurs (pas seulement leur doc en ligne), comme fait pour
  `connecteur-callback` Phase 3. Documenter la limite si un build/test réel s'avère impossible
  dans ce bac à sable (comme A1/Docker Hub).
- **`KafkaConsumerReconciler` est un nouveau pattern** (pas de précédent exact dans ce dépôt pour
  un cycle de vie de ressource externe géré par polling) — risque de fuite de ressources
  (conteneurs jamais arrêtés) si la logique de diff est incorrecte. Mitigation : test dédié
  `T-KR-IDEMPOTENT-POLL` + `T-KR-STOP-ON-DELETE`/`T-KR-STOP-ON-DISABLE` avant tout autre test du
  reconciliateur.
- **Nouvelles dépendances** (SSHJ, Apache POI, Spring Kafka) — risque de conflit de version avec
  le BOM Spring Boot existant. Mitigation : vérifier `./mvnw dependency:tree` après ajout,
  résoudre tout conflit avant d'écrire le premier test.
- **Docker Hub potentiellement inaccessible** dans ce bac à sable (constat systémique lors de A1)
  — `docker compose up` réel pour le service Kafka peut ne pas être validable ici. Mitigation :
  valider `docker compose config` au minimum, documenter la limite sans la contourner (même
  traitement que A1).

## Resources Needed

- Aucune ressource externe/humaine — cycle solo comme tous les précédents de ce dépôt.
- Accès réseau Maven Central pour les 3 nouvelles dépendances (à vérifier tôt, T0.2, avant
  d'investir dans le reste du plan).
