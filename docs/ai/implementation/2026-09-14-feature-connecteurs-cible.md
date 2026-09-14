---
phase: implementation
title: Implementation Guide
description: Technical implementation notes, patterns, and code guidelines
status: in-progress
feature: connecteurs-cible
lot: B1
---

# Implementation Guide — `connecteurs-cible`

> Planning : [2026-09-14-feature-connecteurs-cible.md](../planning/2026-09-14-feature-connecteurs-cible.md).
> Suivi milestone par milestone — mis à jour au fil de la Phase 5, pas seulement à la clôture.

## M0 — Fondations partagées (clos, 2026-09-14)

**Fichiers touchés :**

- `domain/connector/model/ConfigFieldType.java` — ajout `SECRET_TEXTAREA` (T0.1).
- `pom.xml` — ajout `spring-kafka` (géré par le BOM Spring Boot, 4.1.1 résolu), `com.hierynomus:sshj`
  (0.39.0, propriété `sshj.version`), `org.apache.poi:poi-ooxml` (5.4.1, propriété `poi.version`)
  (T0.2). Résolution vérifiée (`dependency:tree`), aucun conflit.
- `infrastructure/config/ConnectorProperties.java` — nouveaux records `BatchImport(maxFileBytes,
  maxRows)` et `Kafka(consumerPollTimeout)`, même pattern que `Http`/`Webhook`/`DocumentStorage`
  (T0.3).
- `domain/connector/port/out/ConnectorCatalog.java` — `isPasswordField` élargi pour traiter
  `SECRET_TEXTAREA` comme sensible en plus de `PASSWORD` (T0.4/T0.5 fusionnées, voir ci-dessous).
- Nouveau `domain/connector/port/out/ConnectorCatalogTest.java` (aucun test dédié n'existait pour
  cette méthode par défaut jusqu'ici, exercée seulement indirectement via `thenCallRealMethod()`
  ailleurs).
- `infrastructure/config/ConnectorPropertiesTest.java` étendu (+8 tests).

**Trouvaille en Phase 5 (T0.4/T0.5 fusionnées) :** `ConnectorCatalog.isPasswordField` est déjà la
**source de vérité unique** consultée par 4 call sites indépendants — `ConnectorApiDtos` (masquage
REST), `ConnectorInstanceMcpView` (masquage MCP), `EnvMasterKeyCipher` (chiffrement au repos),
`ManageConnectorInstanceUseCaseImpl.mergeConfig` (préservation sur update). Élargir la seule
méthode par défaut de `ConnectorCatalog` (un `||` sur `SECRET_TEXTAREA`) couvre donc les 4
comportements simultanément — confirmé en lisant chaque appelant avant de coder, pas supposé.
T0.5 (vérifier l'adaptateur de chiffrement) n'a donc nécessité **aucun changement séparé** :
`EnvMasterKeyCipher` délègue déjà à `catalog.isPasswordField(...)`.

**Bug réel trouvé et corrigé en cours de TDD (pas dans le plan initial) :** la première tentative
d'ajout de `BatchImport`/`Kafka` à `ConnectorProperties` (record `@ConfigurationProperties`)
ajoutait un **second constructeur** (4-arg, délégant au canonique 6-arg) pour éviter de casser les
8 sites d'appel positionnels existants dans le dépôt. Rouge inattendu en testant la suite de
régression complète (pas seulement les nouveaux tests) : `ApplicationContext` échouait au
démarrage avec `NoSuchMethodException: ConnectorProperties.<init>()` — le binder
`@ConfigurationProperties` de Spring Boot pour les records exige **exactement un** constructeur
pour identifier sans ambiguïté celui à utiliser pour le binding ; en présence de deux, il retombe
sur une stratégie de réflexion par constructeur sans argument, qui échoue. Corrigé en retirant le
second constructeur et en mettant à jour les 8 sites d'appel existants (7 classes de test) pour
passer explicitement `null, null` sur les 2 nouveaux paramètres — la vraie voie "breaking change,
update all callers atomically" du contrat de phase, pas le raccourci tenté d'abord. Leçon
retenue : ne **jamais** ajouter un constructeur non canonique à un record annoté
`@ConfigurationProperties`.

**Preuve fraîche :** `./mvnw test` sur `domain.connector.**`, `infrastructure.connector.**`,
`infrastructure.config.**`, `infrastructure.web.connector.**`, `infrastructure.mcp.tool.**`,
`infrastructure.persistence.connector.**` → **465 tests, 0 échec** (inclut la régression complète
des connecteurs déjà livrés — aucun `ApplicationContext` cassé par le changement de signature).
JDK 25 installé dans ce conteneur (JDK 21 seul présent par défaut, même limite déjà rencontrée et
documentée lors de `connecteur-callback` TODO §53).

## M1 — `BatchImportConnector` (clos, 2026-09-14)

**Fichiers touchés :**

- `domain/connector/model/SftpConnectionConfig.java`, `SftpConnectionException.java` (nouveaux).
- `domain/connector/port/out/SftpClientPort.java` (nouveau port — `listFiles`/`download`/
  `archive`, `RemoteFile` imbriqué avec `path`+`sizeBytes` pour la garde taille **avant**
  téléchargement).
- `infrastructure/connector/builtin/BatchImportConnector.java` (nouveau) — `ConnectorDefinition`
  + `TriggerConnector`, capability `TRIGGER`.
- `infrastructure/connector/sftp/SshjSftpClientAdapter.java` (nouveau) — adaptateur réel SSHJ.
- `pom.xml` — `commons-csv` ajouté (parsing CSV robuste face aux champs cités/virgules ; un
  splitter artisanal aurait été fragile, pas de compromis réel à trancher).
- Tests : `BatchImportConnectorTest` (18 cas), `SshjSftpClientAdapterTest` (3 cas, échec de
  connexion uniquement — pas de vrai serveur SSH, D-SFTP-TEST).

**Simplification trouvée en TDD (T1.3) :** le design prévoyait une classe `TabularRowMapper`
dédiée pour le mapping colonnes → champs. En écrivant le premier test, constat que
`SimpleJsonPathMapper.read(item, path)` navigue déjà n'importe quel `Map`/`List` — convertir
chaque ligne CSV/XLSX en `Map<String,Object>` (colonne → valeur) suffit pour réutiliser **le
même** mapper injecté que `http-rest`, avec la même syntaxe `"$.colonne"`. Aucune nouvelle classe
de mapping nécessaire.

**Bug de conception réel trouvé et corrigé en TDD (isolation ligne invalide) :** la première
version de `mapRows` copiait littéralement la logique de `HttpRestConnector.onEvent` — rejeter la
ligne si `processKey` (après résolution du chemin de mapping + repli sur la valeur d'instance)
est blanc. Écrit le test correspondant (ligne avec un chemin de processKey blanc), il **passait
déjà sans isolation réelle** : `processKey` étant un champ obligatoire de l'instance
(`validate()`), le repli sur la valeur d'instance réussit **toujours**, rendant cette branche
d'erreur strictement inatteignable — un `ConnectorValidationException("processKey is required")`
ne peut jamais être levé dans ce chemin. Root cause : la validation « champ requis » pertinente
pour un import batch (D-BATCH-ERROR-HANDLING, requirements : « champ **mappé** requis manquant »)
porte sur les **champs du dossier mappés** (`mapping.fields`), pas sur `processKey`/
`dossierTypeCode` qui ont toujours un repli d'instance. Corrigé : une ligne est invalide si
**une valeur de `mapping.fields` résout à `null`/blanc** (`hasBlankMappedField`) — testé avec 2
colonnes pour éviter l'ambiguïté avec la ligne CSV vide (Commons CSV ignore les lignes
entièrement vides par défaut, un piège pour un CSV à une seule colonne).

**Découverte de sécurité réelle en écrivant l'adaptateur SSHJ (T1.10, hors planification
initiale) :** `SSHClient` de SSHJ exige un `HostKeyVerifier` explicite — sans lui, la connexion
échoue (SSHJ ne se connecte jamais « en silence » sans vérification, contrairement à d'autres
clients SSH plus permissifs par défaut). Décision D-SFTP-HOSTKEY actée avec l'utilisateur : nouveau
champ `sftpHostKeyFingerprint` (requis), vérifié via `SSHClient.addHostKeyVerifier(String)` à
chaque connexion — API confirmée en lisant le jar réel (`javap` sur `sshj-0.39.0.jar`, pas
supposée depuis la mémoire de formation), suivant la même discipline que la vérification de l'API
Flowable réelle lors de `connecteur-callback` (TODO §51).

**Autres points d'API SSHJ vérifiés avant d'écrire l'adaptateur (`javap`, pas supposés) :**
`OpenSSHKeyFile.init(String privateKey, String publicKey, PasswordFinder)` accepte le contenu PEM
directement (pas seulement un chemin de fichier) ; `RemoteFile.RemoteFileInputStream` est une
classe interne **non statique** (`remoteFile.new RemoteFileInputStream()`, pas
`new RemoteFile.RemoteFileInputStream(remoteFile)`) — première tentative en échec de compilation,
corrigée immédiatement ; `SSHException` (base de `UserAuthException`/`TransportException`) étend
`IOException`, donc un seul bloc `catch (IOException)` couvre connexion, authentification et
erreurs SFTP.

**Limite assumée, non corrigée ce cycle (documentée, pas contournée) :** une connexion SSH/SFTP
séparée est ouverte pour chacun des 3 appels du port (`listFiles`/`download`/`archive`) plutôt que
réutilisée sur tout un cycle `onEvent`. Comportement correct, coût de connexion répété acceptable
pour un job cron s'exécutant au mieux toutes les quelques minutes — réutilisation de session
possible en follow-up si le profil de performance l'exige.

**Preuve fraîche :** `./mvnw test` sur `domain.connector.**`, `infrastructure.connector.**`,
`infrastructure.config.**` → **228 tests, 0 échec** (régression M0 incluse).

## T0.6 — round-trip secret complet (clos après M1, 2026-09-14)

`ConnectorPersistenceIT` (déjà existant pour `webhookSecret`/`PASSWORD`) étendu d'un cas
`sftpPrivateKey`/`SECRET_TEXTAREA` : `@SpringBootTest` réel (contexte complet, PostgreSQL +
Flowable démarrés, `BatchImportConnector` auto-enregistré via `ConnectorRegistry`), preuve que
l'extension `ConnectorCatalog.isPasswordField` de M0 (T0.4) fonctionne bout en bout — pas
seulement au niveau unitaire de `ManageConnectorInstanceUseCaseImpl`. Colonne DB brute vérifiée
`enc:v1:` présent, PEM en clair absent ; relecture décrypte correctement. `./mvnw test
-Dtest=ConnectorPersistenceIT` : 5/5 (1 nouveau + 4 existants inchangés).

## M2 — `BatchImportConnectorTriggerJob` (clos, 2026-09-14)

**Fichiers touchés :**

- `infrastructure/connector/BatchImportConnectorTriggerJob.java` (nouveau) — copie filtrée de
  `SchedulerConnectorTriggerJob` (poll `@Scheduled`, type `"batch-import"`), réutilise
  `ConnectorScheduleStateRepository` sans modification.
- Tests : `BatchImportConnectorTriggerJobTest` (8 cas), `BatchImportEndToEndIT` (1 cas — chaîne
  réelle `BatchImportConnectorTriggerJob` → `ConnectorTriggerRuntimeImpl` réel →
  `CreateDossierUseCase`/`GetDossierUseCase` en mémoire, seul `SftpClientPort` est un faux).

**Divergence délibérée par rapport au pattern copié (T-BIJ-ONE-INSTANCE-FAILS) :**
`SchedulerConnectorTriggerJob.process(...)` n'isole **pas** ses instances aujourd'hui — une
exception sur une instance interrompt la boucle entière pour ce poll (lu dans le code existant,
pas supposé). Le nouveau job ajoute un `try/catch(RuntimeException)` par instance (log + poursuite)
car le testing doc l'exige explicitement pour `batch-import` (une panne SFTP sur une instance ne
doit pas priver les autres instances du même tenant de leur import ce cycle-ci). `scheduler` reste
inchangé (D-BATCH-CRON-JOB : job dédié précisément pour ne pas y toucher) — cette amélioration
n'est **pas** rétroportée sur `scheduler`, documentée ici comme écart connu entre les deux jobs.

**Preuve fraîche :** `./mvnw test` sur les packages connecteurs → **237 tests, 0 échec**.

## M3 — `KafkaConnector` (clos, 2026-09-14)

**Fichiers touchés :** `infrastructure/connector/builtin/KafkaConnector.java` (nouveau) —
`ConnectorDefinition` + `TriggerConnector`, capability `TRIGGER`, un message → exactement un
`WorkflowTriggerEvent` (pas d'`itemsPath`, contrairement à `http-rest`). Réutilise le même
`SimpleJsonPathMapper` injecté. Tests : `KafkaConnectorTest` (11 cas).

**Même correction que `BatchImportConnector` (M1), appliquée directement cette fois :**
`processKey`/`dossierTypeCode` ont toujours un repli d'instance (champs requis par `validate()`),
donc un chemin de mapping résolvant à blanc ne peut jamais les rendre invalides. `hasBlankMappedField`
(sur `mapping.fields`) est le seul critère d'invalidité réel — corrigé le doc testing en
conséquence (`T-KAFKA-MALFORMED`) plutôt que de répéter l'erreur trouvée en TDD sur M1.

## M4 — `KafkaConsumerReconciler` (clos, 2026-09-14)

**Fichiers touchés :**

- `infrastructure/connector/kafka/KafkaConsumerHandle.java`,
  `infrastructure/connector/kafka/KafkaConsumerFactory.java` (nouveaux ports d'infrastructure,
  **pas** `domain.connector.port.out` — `KafkaConsumerReconciler` est un composant
  d'infrastructure de bout en bout, contrairement à `BatchImportConnector`/`SftpClientPort` qui
  sont un `ConnectorDefinition` domaine + son port). Seam introduit spécifiquement pour permettre
  un test complet du réconciliateur sans jamais instancier de vraie classe Spring Kafka
  (`KafkaMessageListenerContainer.start()` démarrerait un vrai thread de poll même contre un
  broker injoignable — non déterministe et bruyant en test).
- `infrastructure/connector/kafka/KafkaConsumerReconciler.java` (nouveau) — poll `@Scheduled`,
  diff instances `kafka` activées vs conteneurs en cours (démarrage/arrêt/redémarrage sur
  changement de config), dispatch message → `ConnectorTriggerRuntime.onIncomingEvent` avec
  isolation par message (`try/catch(RuntimeException)`, ni une erreur de désérialisation JSON ni
  une exception du runtime ne bloque les messages suivants).
- `infrastructure/connector/kafka/SpringKafkaConsumerFactory.java` (nouveau) — adaptateur réel,
  `KafkaMessageListenerContainer`/`DefaultKafkaConsumerFactory` (API vérifiée par `javap` sur le
  jar réel `spring-kafka-4.1.1.jar`, même discipline que SSHJ). `buildConsumerProperties(...)`
  extraite en méthode statique testable isolément (PLAINTEXT/SASL_SSL).
- Tests : `KafkaConsumerReconcilerTest` (11 cas, `FakeKafkaConsumerFactory`),
  `SpringKafkaConsumerFactoryTest` (3 cas, construction des propriétés uniquement), `KafkaEndToEndIT`
  (1 cas — chaîne réelle réconciliateur → `KafkaConnector` → `ConnectorTriggerRuntimeImpl` réel →
  dossier créé/lisible, seule la factory Kafka est un faux).

**Preuve fraîche (M3+M4) :** `./mvnw test` sur `infrastructure.connector.kafka.**` et
`infrastructure.connector.builtin.KafkaConnectorTest` → **26 tests, 0 échec**.

## M6 — Frontend `ConnectorConfigForm.vue` (clos, 2026-09-14)

**Fichiers touchés :**

- `frontend/src/api/admin.ts` — `ConfigFieldType` gagne `SECRET_TEXTAREA`.
- `frontend/src/components/connectors/ConnectorConfigForm.vue` — 1 cas ajouté (`<textarea>` pour
  `SECRET_TEXTAREA`, avant la branche `<input>` générique) ; aucun autre cas existant modifié.
- Tests : `ConnectorConfigForm.spec.ts` (+1 cas Vitest), `e2e/pilotage.spec.ts` — nouvelle entrée
  `batch-import` dans la liste `connectorDefinitions` partagée (`mockSession`) + nouveau test
  `E2E-CONN-BATCH-IMPORT-KEY`.

**Note d'exécution Playwright dans ce bac à sable :** le binaire `chrome-headless-shell` par défaut
n'est pas installé ; utiliser `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/pw-browsers/chromium-1194/chrome-linux/chrome`
(supporté nativement par `playwright.config.ts` de ce dépôt) pour exécuter réellement la suite
plutôt que de la déclarer non exécutable.

**Preuve fraîche :** Vitest **352/352** (régression complète) ; Playwright **72/72** (régression
complète, y compris les 3 `E2E-CONN-*` existants inchangés) ; `npm run typecheck` et `npm run lint`
propres.

## M7 — Vérification globale, Phase 7-8 (clos, 2026-09-14)

**Drift golden OpenAPI trouvé et corrigé :** `openapi/openapi.yaml` documentait
`ConfigField.type` comme un enum YAML (`STRING, PASSWORD, URL, INTEGER, BOOLEAN, SELECT`)
n'incluant pas `SECRET_TEXTAREA` (ajouté M0/T0.1). Confirmé réel (pas hypothétique) :
`./mvnw -Dtest=OpenApiContractIT` → `Tests run: 1, Failures: 1` avant correctif. Corrigé via le
mécanisme de régénération prévu par le test lui-même
(`./mvnw -Dtest=OpenApiContractIT -Dopenapi.update=true`) ; diff résultant limité à la seule ligne
attendue (`+ SECRET_TEXTAREA`), revu avant commit. Re-exécution sans le flag : vert.

**Trou de preuve comblé (T7.1, T-REG-REGISTRY-COUNT) :** aucun test existant n'exerçait
`ConnectorRegistry` avec un contexte Spring réel pour prouver l'absence de collision de type au
démarrage (un doublon ferait échouer `ConnectorRegistry.init()`, donc tout `@SpringBootTest` du
dépôt échouerait déjà indirectement — mais aucune assertion positive sur le compte exact
n'existait). Nouveau test dans `ConnectorAdminApiTest` (contexte réel, `GET
/api/connector-definitions`) : `$.totalElements` == 8. Premier jet écrit en supposant **5**
connecteurs déjà livrés (http-rest, flowable-signal, scheduler, smtp, document-storage) — rouge
réel (`expected:<7> but was:<8>`), qui a révélé l'oubli de `flowable-callback` (livré par le cycle
`connecteur-callback` précédent, TODO §51/§56). Corrigé en comptant les 6 types réels lus
directement dans le code
(`grep "String getType()"` sur `infrastructure/connector/builtin/*.java`) plutôt que de deviner à
nouveau.

**Question `*IT.java` / `mvn verify` sans Failsafe — déjà documentée, pas un trou nouveau :**
confirmé dans `TODO.md` (entrées `connecteur-callback`) que ce dépôt n'a **pas** de plugin
Failsafe lié et que les patterns d'inclusion par défaut de Surefire (`**/*Test.java`,
`**/*Tests.java`, `**/*TestCase.java`, `**/Test*.java`) n'incluent pas `*IT.java`. Comportement
confirmé à nouveau ici : `./mvnw verify` (défaut, sans `-Dtest=`) exécute uniquement les classes
`Test*`, jamais les `*IT.java`. Suivi la même convention que les cycles précédents : preuve
`Test*` via `mvn verify`, puis preuve `*IT.java` séparée via `-Dtest=` explicite ciblant le
périmètre de la feature.

**Preuve fraîche — `Test*` (via `./mvnw verify`) :** **1948 tests, 0 échec** (1947 régression +
1 nouveau `T-REG-REGISTRY-COUNT`), Jacoco « All coverage checks have been met » (LINE≥85%/
BRANCH≥75%), `ArchitectureRulesTest` 7/7 (limite hexagonale confirmée : zéro import
sshj/spring-kafka/kafka-clients/poi/commons-csv dans `domain`).

**Preuve fraîche — `*IT.java` du périmètre feature (via `-Dtest=` explicite) :**
`OpenApiContractIT` (1/1), `ConnectorPersistenceIT` (5/5), `ConnectorSchemaIT` (2/2),
`BatchImportEndToEndIT` (1/1), `KafkaEndToEndIT` (1/1) → **10/10, 0 échec**.

**Préexistant hors périmètre, non corrigé ce cycle (documenté, pas contourné) :** en exécutant
l'ensemble des `*IT.java` du dépôt (`-Dtest="*IT"`, 78 tests), **1 échec** trouvé dans
`NotificationPersistenceIT.oldestPendingAndWebhookDeadCount` — dépendance à l'ordre d'exécution
des méthodes de test au sein de la classe (une ligne `PENDING` créée par une méthode antérieure
n'est jamais nettoyée, faussant `oldestPendingCreatedAt()` pour une méthode ultérieure si l'ordre
JUnit5 — non garanti par défaut, aucun `@TestMethodOrder` déclaré — place l'une avant l'autre).
Confirmé **non lié à ce cycle** : fichier jamais touché par cette branche (`git diff
origin/main...HEAD` ne le liste pas) et reproduit à l'identique sur `origin/main` nu (vérifié via
`git worktree add` sur `origin/main`, même échec exact). 3 tests par ailleurs `Skipped` dans ce
même run global (`SequentialReferenceMultiPodIT`, `TenantRoleIsolationIT`,
`TenantSchemaIsolationIT`) — limite de bac à sable déjà connue (multi-pod/isolation nécessitant
plusieurs instances), sans rapport avec `connecteurs-cible`. Ni l'un ni l'autre ne bloque ce
cycle : aucun fichier notification/tenant touché par `connecteurs-cible`.

## Development Setup

- JDK 25 (`java.version` du `pom.xml`) — `apt-get install openjdk-25-jdk` si seul JDK 21 est
  présent par défaut sur le conteneur, puis `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64`.
- `./mvnw test -Dskip.frontend=true` pour itérer sans le build frontend (plus rapide en TDD
  backend-only).

## Code Structure

- Pattern connecteur : `infrastructure/connector/builtin/<Type>Connector.java` implémentant
  `ConnectorDefinition` (+ `TriggerConnector`/`ServiceConnector`), auto-enregistré par
  `ConnectorRegistry` (liste Spring). Zéro modification de `ConnectorCatalog`/
  `ConnectorTriggerRuntimeImpl`/`ExecuteConnectorRequest`.
- Déclenchement cron dédié : `infrastructure/connector/<Type>ConnectorTriggerJob.java`, copie du
  pattern `SchedulerConnectorTriggerJob` (poll `@Scheduled`, filtre sur le type, réutilise
  `ConnectorScheduleStateRepository`).

## Implementation Notes

_Mis à jour au fil des milestones M1–M8._

## Integration Points

_Mis à jour au fil des milestones._

## Error Handling

- Isolation par ligne/message au niveau du connecteur, pas du pipeline partagé
  (`ConnectorTriggerRuntimeImpl` inchangé) — voir D-BATCH-ERROR-HANDLING, doc requirements.

## Performance Considerations

- `ConnectorProperties.BatchImport(maxFileBytes, maxRows)` — garde mémoire sur le parsing de
  fichier, même pattern que `Http.maxResponseBytes`.

## Security Notes

- `SECRET_TEXTAREA` traité comme sensible par `ConnectorCatalog.isPasswordField` — masquage
  lecture (REST + MCP) et chiffrement au repos couverts par le même point d'extension que
  `PASSWORD`, sans dupliquer la logique (leçon TODO §56 réappliquée).
