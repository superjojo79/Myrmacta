---
phase: design
title: System Design & Architecture
description: Define the technical architecture, components, and data models
status: reviewed
feature: connecteurs-cible
lot: B1
---

# System Design & Architecture — `connecteurs-cible`

> Requirements : [2026-09-14-feature-connecteurs-cible.md](../requirements/2026-09-14-feature-connecteurs-cible.md)
> (Phases 1–2, 15 décisions D-ERP-SCOPE → D-BATCH-SIZE-LIMIT). 4 décisions de design supplémentaires
> tranchées ci-dessous (D-BATCH-CRON-JOB, D-KAFKA-LIFECYCLE, D-CONFIG-FIELD-SECRET-TEXTAREA,
> D-SFTP-LIB), reportées explicitement en Phase 1 car architecturales.

## Architecture Overview

Les deux connecteurs suivent le pattern déjà établi (`SchedulerConnector`/`SmtpConnector`/
`DocumentStorageConnector`, PR #99) : un nouveau `@Component` implémentant `ConnectorDefinition` +
`TriggerConnector`, auto-enregistré par `ConnectorRegistry` (injection de liste Spring), sans
modification du pipeline partagé (`ConnectorTriggerRuntimeImpl`, `WorkflowTriggerEvent`,
`CreateDossierUseCase`). Chacun a besoin d'un mécanisme d'amorçage propre côté infrastructure — ni
`http-rest` (poussé par un appelant HTTP) ni `flowable-signal` (poussé par le moteur) ne
conviennent, et `SchedulerConnectorTriggerJob` est câblé en dur sur `"scheduler"` — deux **nouveaux
composants d'infrastructure dédiés**, réutilisant le même pattern de poll périodique déjà établi
plutôt qu'un mécanisme événementiel :

```mermaid
graph TD
    subgraph Admin["Admin (TENANT_ADMIN) — écran existant, A2"]
        UI[ConnectorsAdminView.vue<br/>+ ConnectorConfigForm.vue]
    end
    UI -->|configSchema batch-import / kafka| MCI[ManageConnectorInstanceUseCaseImpl]
    MCI --> CIR[(ConnectorInstanceRepository)]

    subgraph BatchImportFlow["batch-import"]
        BJ[BatchImportConnectorTriggerJob<br/>@Scheduled poll, comme SchedulerConnectorTriggerJob] -->|instances enabled + cron dû| CIR
        BJ -->|isDue?| SSR[(ConnectorScheduleStateRepository<br/>réutilisée telle quelle)]
        BJ --> BIC[BatchImportConnector<br/>ConnectorDefinition + TriggerConnector]
        BIC -->|SFTP: list + download| SFTP[(Serveur SFTP distant)]
        BIC -->|parse CSV/XLSX, mapping colonnes,<br/>garde taille/lignes, isole lignes invalides| BIC
        BIC -->|archive fichier traité| SFTP
    end

    subgraph KafkaFlow["kafka"]
        KR[KafkaConsumerReconciler<br/>@Scheduled poll] -->|instances enabled kafka| CIR
        KR -->|démarre/arrête| MLC[MessageListenerContainer<br/>1 par instance]
        MLC -->|message reçu| KC[KafkaConnector<br/>ConnectorDefinition + TriggerConnector]
        MLC -.->|abonné à| BROKER[(Broker Kafka<br/>docker-compose local ou externe)]
    end

    BIC -->|List WorkflowTriggerEvent| CTR[ConnectorTriggerRuntimeImpl<br/>INCHANGÉ]
    KC -->|List WorkflowTriggerEvent| CTR
    CTR --> CDU[CreateDossierUseCase<br/>INCHANGÉ]
    CTR --> OUT[(OutboxRepository<br/>INCHANGÉ)]
```

**Composants clés**

| Composant | Rôle |
|---|---|
| `BatchImportConnector` | `ConnectorDefinition` + `TriggerConnector`. `onEvent(instance, rawPayload)` ignore `rawPayload` (comme `SchedulerConnector`) : se connecte en SFTP, liste `remoteDirectory` filtré par `filenamePattern`, télécharge chaque fichier sous la garde `maxFileBytes`/`maxRows`, le parse (CSV ou XLSX selon l'extension), mappe chaque ligne en `WorkflowTriggerEvent` (lignes invalides exclues + comptées), archive le fichier. |
| `BatchImportConnectorTriggerJob` | `@Scheduled(fixedDelay)`, copie du pattern `SchedulerConnectorTriggerJob` filtrée sur le type `"batch-import"`. Réutilise **telle quelle** `ConnectorScheduleStateRepository` (générique, déjà découplée du type `"scheduler"`) pour la dernière exécution par instance. |
| `KafkaConnector` | `ConnectorDefinition` + `TriggerConnector`. `onEvent(instance, rawPayload)` reçoit le payload JSON d'un message déjà consommé (poussé par le listener) et le mappe en **exactement un** `WorkflowTriggerEvent` (pas de notion `itemsPath` : 1 message Kafka = 1 événement). |
| `KafkaConsumerReconciler` | `@Scheduled(fixedDelay)`. Relit les instances `kafka` activées, compare à la carte interne `instanceId → MessageListenerContainer` : crée/démarre les manquants (bootstrap servers/topic/groupId/sécurité issus de `instance.config()`, offset `latest` au démarrage), arrête et retire ceux désactivés/supprimés/dont la config a changé (redémarrage propre). Sur réception d'un message, appelle `ConnectorTriggerRuntime.onIncomingEvent(instance, message.value())`. |
| `ConnectorConfigForm.vue` | **1 seule extension additive** : nouveau cas `SECRET_TEXTAREA` dans `inputType`/le template (rendu `<textarea>` masqué), en plus de `PASSWORD`/`URL`/`INTEGER`/`SELECT`/`BOOLEAN` déjà gérés. Aucun nouvel écran, aucune nouvelle vue. |

**Choix technologiques**

- SFTP : **SSHJ** (`com.hierynomus:sshj`) — actively maintenue, Apache 2.0 (D-SFTP-LIB).
- Excel : **Apache POI** (`org.apache.poi:poi-ooxml`), lecture DOM (`XSSFWorkbook`) — le streaming
  (`SXSSFWorkbook`/event API) n'est pas nécessaire, la garde `maxRows` (D-BATCH-SIZE-LIMIT) borne
  déjà la taille chargée en mémoire.
- Kafka : **Spring Kafka** (`spring-kafka`, déjà aligné avec la version Spring Boot du projet) —
  `KafkaConsumer` bas niveau piloté manuellement par instance (pas de `@KafkaListener` statique,
  incompatible avec des topics/consumer groups décidés à l'exécution par un administrateur).

## Data Models

**Aucune nouvelle table.** `ConnectorScheduleStateRepository` (déjà générique — la Javadoc
existante dit « relevant only to one connector type » mais l'interface elle-même est indexée par
`connectorInstanceId`, sans référence au type `"scheduler"`) est réutilisée sans modification pour
`batch-import`. Les offsets Kafka sont gérés par le broker lui-même (consumer group), pas par
FlowForge — aucun état à persister côté application pour rejouer/reprendre.

`configSchema` des deux connecteurs (`ConfigField`, mêmes types que les connecteurs existants +
1 nouveau) :

**`batch-import`**

| name | type | required | notes |
|---|---|---|---|
| `sftpHost` | STRING | oui | |
| `sftpPort` | INTEGER | non (def. 22) | |
| `sftpUsername` | STRING | oui | |
| `sftpAuthType` | SELECT (`PASSWORD`, `PRIVATE_KEY`) | oui | |
| `sftpPassword` | PASSWORD | non | requis si `sftpAuthType=PASSWORD` |
| `sftpPrivateKey` | **SECRET_TEXTAREA** (nouveau) | non | requis si `sftpAuthType=PRIVATE_KEY`, PEM multi-lignes |
| `remoteDirectory` | STRING | oui | |
| `filenamePattern` | STRING | oui | glob, ex. `*.csv`, `export-*.xlsx` |
| `archiveDirectory` | STRING | oui | déplacement post-traitement (D-BATCH-DEDUP) |
| `cronExpression` | STRING | oui | même format que `scheduler` |
| `processKey` | STRING | oui | |
| `dossierTypeCode` | STRING | oui | |
| `mapping` | STRING | non | JSON `{colonne: champDossier}`, même style que `http-rest.mapping` |

**`kafka`**

| name | type | required | notes |
|---|---|---|---|
| `bootstrapServers` | STRING | oui | `host:port,host:port` |
| `topic` | STRING | oui | |
| `groupId` | STRING | oui | |
| `securityProtocol` | SELECT (`PLAINTEXT`, `SASL_SSL`) | oui | |
| `saslUsername` | STRING | non | requis si `SASL_SSL` |
| `saslPassword` | PASSWORD | non | requis si `SASL_SSL` |
| `processKey` | STRING | oui | |
| `dossierTypeCode` | STRING | oui | |
| `mapping` | STRING | non | JSON, même style que `http-rest.mapping` |

## API Design

Aucune nouvelle route REST/MCP. Les deux connecteurs passent par les endpoints **déjà livrés**
(`POST /api/connectors/instances`, formulaire A2 généré depuis `GET /api/connector-definitions`) —
c'est précisément la valeur du pattern `ConnectorDefinition` : un nouveau type de connecteur
n'ajoute aucune surface HTTP.

## Component Breakdown

Nouveaux fichiers (backend uniquement, sauf 1 extension frontend ciblée) :

- `infrastructure/connector/builtin/BatchImportConnector.java`
- `infrastructure/connector/BatchImportConnectorTriggerJob.java`
- `infrastructure/connector/sftp/SftpClientPort.java` (+ adaptateur SSHJ) — port dédié pour mocker
  en test (D-SFTP-TEST = mocké uniquement).
- `infrastructure/connector/mapping/TabularRowMapper.java` (ou extension de
  `SimpleJsonPathMapper`) — mapping colonne → champ pour CSV/XLSX.
- `infrastructure/connector/builtin/KafkaConnector.java`
- `infrastructure/connector/kafka/KafkaConsumerReconciler.java`
- `infrastructure/connector/kafka/KafkaConsumerFactory.java` (ou équivalent) — construit un
  `KafkaConsumer`/`MessageListenerContainer` depuis `instance.config()`.
- `domain/connector/model/ConfigFieldType.java` — ajout de la valeur `SECRET_TEXTAREA`.
- `frontend/src/components/connectors/ConnectorConfigForm.vue` — 1 cas ajouté dans `inputType`
  et le template (rendu `<textarea>` masqué pour `SECRET_TEXTAREA`).
- `infrastructure/config/ConnectorProperties.java` — nouveaux records `BatchImport(maxFileBytes,
  maxRows)` et `Kafka(consumerPollTimeout, ...)`, même pattern que `Http`/`Webhook`/`DocumentStorage`.
- `docker-compose.yml` — service Kafka (image KRaft, sans Zookeeper).
- Tests unitaires/IT associés (mockés pour SFTP et Kafka, D-KAFKA-TEST/D-SFTP-TEST).

## Design Decisions

| ID | Décision | Alternative écartée | Pourquoi |
|---|---|---|---|
| **D-BATCH-CRON-JOB** | Nouveau job dédié `BatchImportConnectorTriggerJob` (confirmé utilisateur 2026-09-14) | Généraliser `SchedulerConnectorTriggerJob` | Isolation totale, zéro risque de régression sur `scheduler` déjà livré ; réutilise quand même `ConnectorScheduleStateRepository` (déjà générique). |
| **D-KAFKA-LIFECYCLE** | Reconciliateur par polling (`KafkaConsumerReconciler`, confirmé utilisateur 2026-09-14) | Hook de cycle de vie sur `ManageConnectorInstanceUseCaseImpl` | Cohérent avec le seul pattern de cycle de vie déjà établi dans ce dépôt (poll, pas événementiel) ; aucune modification d'une classe domaine partagée par tous les connecteurs. |
| **D-CONFIG-FIELD-SECRET-TEXTAREA** | Nouveau `ConfigFieldType.SECRET_TEXTAREA` (confirmé utilisateur 2026-09-14) | Revenir sur D-SFTP-AUTH (mot de passe seul) / contournement base64 | `ConfigFieldType.PASSWORD` rend un `<input type="password">` mono-ligne — une clé privée SSH (PEM multi-lignes) y est mal supportée au copier-coller. Extension additive de `ConnectorConfigForm.vue`, garde D-SFTP-AUTH tel qu'acté sans dégrader l'UX. **Amende l'objectif « zéro nouveau code frontend »** du doc requirements : la portée reste 1 cas ajouté dans un composant partagé déjà générique, aucun nouvel écran/vue. |
| **D-SFTP-LIB** | SSHJ (confirmé utilisateur 2026-09-14) | JSch | Activement maintenue, Apache 2.0, meilleur support des algorithmes récents. |
| **A-EXCEL-LIB** | Apache POI `poi-ooxml`, `XSSFWorkbook` (DOM) | `SXSSFWorkbook`/streaming | Pas de trade-off matériel pour l'utilisateur — la garde `maxRows` (D-BATCH-SIZE-LIMIT) borne déjà la mémoire, le DOM simplifie le mapping colonnes. Documenté comme hypothèse, pas soumis à question. |
| **A-NO-OUTBOUND-GUARD** | `sftpHost`/`bootstrapServers` ne passent **pas** par `OutboundUrlGuard` (SSRF) | Appliquer le même guard que `http-rest.execute()` | `OutboundUrlGuard` protège une URL **fournie à l'exécution** par un appelant (`inputParams`, potentiellement influencée en aval d'un workflow) — ici l'hôte est une donnée de **configuration d'instance**, modifiable seulement par `TENANT_ADMIN`, même niveau de confiance que `SmtpConnector.host` qui n'est pas gardé non plus (vérifié dans le code : aucun usage d'`OutboundUrlGuard` dans `SmtpConnector`). |
| **A-SECRET-DETECTION** | `ManageConnectorInstanceUseCaseImpl.isSecretKey(...)` (heuristique par nom : `password`/`secret`) détecte déjà `sftpPassword`/`saslPassword` ; `sftpPrivateKey` est détecté via `ConnectorCatalog.isPasswordField` (schéma déclaré, source de vérité unique — TODO §56) en étendant cette méthode pour traiter aussi `SECRET_TEXTAREA` comme sensible, pas seulement `PASSWORD` | Ajouter `privateKey`/`key` aux heuristiques de nom dupliquées | Cohérent avec la leçon déjà tirée de la revue adversariale `extension-mcp-flux-metier` (TODO §56) : le schéma déclaré est la source de vérité, pas une 3ᵉ liste de noms à maintenir. |

## Non-Functional Requirements

- **Sécurité** : `sftpPassword`, `sftpPrivateKey`, `saslPassword` masqués en lecture et chiffrés au
  repos comme tout champ `PASSWORD`/`SECRET_TEXTAREA` (`ConnectorCatalog.isPasswordField` étendu,
  voir A-SECRET-DETECTION). Pas de nouvelle surface HTTP anonyme (contrairement à
  `connecteur-callback`/webhooks) — SFTP et Kafka sont des connexions **sortantes** initiées par
  FlowForge, pas des endpoints entrants.
- **Fiabilité** : une ligne de fichier invalide, un message Kafka malformé, ou un fichier trop
  volumineux n'interrompent jamais le traitement des autres lignes/messages/fichiers du même poll
  (D-BATCH-ERROR-HANDLING, D-BATCH-SIZE-LIMIT). Un échec de connexion SFTP/Kafka est journalisé
  (SLF4J, pas de nouvelle UI d'alerte ce cycle) et retenté au poll suivant.
- **Performance** : `maxFileBytes`/`maxRows` (nouvelle section `ConnectorProperties.BatchImport`)
  bornent la mémoire consommée par le parsing d'un fichier, même pattern que `Http.maxResponseBytes`
  déjà établi.
- **Testabilité** : `SftpClientPort` (nouveau port dédié) et le client Kafka sont mockés en test
  (D-SFTP-TEST/D-KAFKA-TEST) — risque assumé et documenté : pas de preuve contre un vrai serveur
  SFTP ni un vrai broker Kafka ce cycle, écart avec la culture « SmokeIT réel » établie pour
  d'autres connecteurs (`CallbackSmokeIT`).
- **Couverture** : `./mvnw verify` Jacoco LINE ≥ 85 % / BRANCH ≥ 75 % tenus, comme tous les cycles
  précédents.
