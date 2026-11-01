# Système de connecteurs

> Composant extensible permettant d'intégrer des sources de triggers
> et des services externes. Enrichissable par classpath sans modifier le core.

---

### Rôle

Un connecteur est un composant extensible qui peut jouer deux rôles :

- **Trigger** — reçoit un événement externe et démarre un workflow Flowable
- **Service** — exécute un traitement appelé depuis un `ServiceTask` BPMN ou directement via l'API

Un même connecteur peut remplir les deux rôles simultanément.

### Les trois objets fondamentaux

| Objet | Rôle |
|---|---|
| `ConnectorDefinition` | Type de connecteur — défini par la plateforme ou ajouté au classpath. Décrit les capacités et le schéma de configuration. |
| `ConnectorInstanceData` | Objet domaine représentant une instance configurée par un tenant à partir d'une définition (config déjà déchiffrée). Persisté via `ConnectorInstanceJpaEntity` — voir [« entité JPA vs objet domaine »](#connectorinstance--entité-jpa-vs-objet-domaine) plus bas. |
| `ConnectorExecution` | Exécution d'un connecteur dans un contexte donné (trigger entrant ou appel ServiceTask / API). |

### Interfaces d'extension

```java
// Capacité trigger — reçoit un événement entrant et produit un déclenchement de workflow
public interface TriggerConnector {
    // Retourne une liste — supporte la création batch de dossiers
    List<WorkflowTriggerEvent> onEvent(ConnectorInstanceData instance,
                                       Map<String, Object> rawPayload);
}

// Capacité service — exécute un traitement et retourne des variables
public interface ServiceConnector {
    Map<String, Object> execute(ConnectorInstanceData instance,
                                Map<String, Object> inputParams);
}

// Base commune — tout connecteur l'implémente
public interface ConnectorDefinition {
    String              getType();            // identifiant unique : "http-rest", "smtp"
    String              getLabel();           // libellé UI
    ConnectorCapability getCapability();      // TRIGGER, SERVICE, BOTH
    List<ConfigField>   getConfigSchema();    // description déclarative des champs de config attendus
    void                validate(Map<String, Object> config);
}
```

Le schéma de configuration est déclaratif — il décrit les champs attendus (nom, type, requis...) sans
connaître le connecteur à l'avance. **Cible produit :** un formulaire d'admin généré dynamiquement à
partir de ce schéma. **V1 livré :** `GET /api/connector-definitions` renvoie bien `configSchema` dans
la réponse JSON (voir `ConnectorDefinitionResponse`), mais rien ne le consomme encore aujourd'hui —
ni l'UI (voir « Écran d'admin » ci-dessous) ni la validation, qui reste un contrôle manuel indépendant
écrit dans chaque `validate(config)` :

```java
@Value @Builder
public class ConfigField {
    String        name;
    String        label;
    FieldType     type;        // STRING, PASSWORD, URL, INTEGER, BOOLEAN, SELECT
    boolean       required;
    String        defaultValue;
    List<String>  options;     // pour type SELECT
    String        helpText;
}
```

### Écran d'admin (V1 livré)

`ConnectorsAdminView.vue` liste les instances existantes et permet d'en créer une : un `<select>`
alimenté par `GET /api/connector-definitions` pour choisir le type. Le frontend lit `type`,
`label` et **`configSchema`** (`ConnectorDefinition` dans `frontend/src/api/admin.ts`) et génère
un formulaire champ par champ (`ConnectorConfigForm.vue`) — SELECT, PASSWORD, STRING, URL,
INTEGER, BOOLEAN — à la place du `<textarea>` JSON de la première livraison. Un type au schéma
vide (`smtp`, `document-storage`) n'affiche aucun champ de config et poste `config: {}`.
La validation métier (`ConnectorDefinition.validate(config)`, longueur de secret, cron, etc.)
reste côté serveur. Un textarea JSON n'est conservé qu'en repli si le catalogue n'a pas pu
être chargé. Pour `http-rest` / `flowable-callback`, la liste affiche l'URL d'entrée à donner
au partenaire (`POST …/api/webhooks/{id}` ou `POST …/api/callbacks/{id}`, préfixe tenant
inclus) avec un bouton Copier.

### Catalogue de connecteurs fournis

La colonne **Cible** est le catalogue d'analyse (cible produit). La colonne
**V1 livré** décrit ce qui est réellement enregistré dans `ConnectorRegistry`
aujourd'hui. Ne pas implémenter silencieusement un type « Non ».

| Type | Cible | V1 livré | Description |
|---|---|---|---|
| `http-rest` | TRIGGER + SERVICE | **TRIGGER + SERVICE** (`HttpRestConnector`, capability `BOTH` — TODO §48) | Réception webhook entrant / appel REST sortant (GET/POST/PUT/PATCH/DELETE, en-tête `Authorization` optionnel via `authHeaderValue`, ou jeton OAuth2 `client_credentials` via `authMode` — TODO §77 ; `baseUrl` + chemin relatif encodé et instance « service seul » `webhookEnabled = false` — TODO §112 ; `PATCH` et client à adresse figée — TODO §116) |
| `scheduler` | TRIGGER | **Oui** (`SchedulerConnector` — TODO §50) | Déclenchement planifié (cron, config `cronExpression`/`processKey`/`dossierTypeCode`) ; un nouveau dossier est créé à chaque déclenchement — job dédié `SchedulerConnectorTriggerJob` (`app.connector.scheduler.poll-delay`, défaut 1 min) évalue le cron par instance via un état persistant (`connector_schedule_state`) |
| `smtp` | SERVICE | **Oui** (`SmtpConnector` — TODO §50) | Envoi email ad hoc depuis un `serviceTask` (`to`/`subject`/`body` en `connectorInput`, texte brut), réutilise `EmailPort`/`SmtpEmailAdapter` déjà configuré — distinct du canal EMAIL des notifications (structuré/templé), pas une duplication |
| `document-storage` | SERVICE | **Oui** (`DocumentStorageConnector` — TODO §50, généralisé depuis le candidat `s3-minio` du catalogue initial) | Lecture/écriture de contenu (`operation` STORE/RETRIEVE/EXISTS/DELETE en `connectorInput`) via `DocumentStoragePort` — agnostique S3/filesystem, pour du contenu **non lié** à un `Dossier`/`DossierElement` (déjà couvert par les pièces jointes) ; aucune purge automatique du `storageRef`, à la charge du process BPMN |
| `flowable-signal` | TRIGGER + SERVICE | Oui (`FlowableSignalConnector`) | Émet ou reçoit un signal Flowable |
| `flowable-callback` | CALLBACK (nouvelle capability) | **Oui** (`FlowableMessageCallbackConnector` — TODO §51) | Réception d'un callback HTTP asynchrone corrélé à un appel `serviceTask` antérieur (n'importe quel connecteur SERVICE, pas couplé à `http-rest`) : reprend l'exécution en attente sur un `intermediateCatchEvent`/`receiveTask` à message BPMN dont une variable de **process** (`correlationVariableName`) correspond à la valeur extraite du payload entrant (`correlationValuePath`, JSONPath) ; endpoint dédié `POST /api/callbacks/{instanceId}` (même auth HMAC/shared-token et idempotence que `/api/webhooks/**`, mécanismes réutilisés tels quels). Ne crée et n'attache jamais de dossier (sémantique distincte de `TriggerConnector`/`ConnectorTriggerRuntimeImpl`). Mécanisme moteur : `RuntimeService.createExecutionQuery().messageEventSubscriptionName(...).processVariableValueEquals(...)` puis `messageEventReceived(...)` — `RuntimeService.createMessageCorrelation(...)` n'existe pas dans Flowable 8.0.0 Community, contrairement à ce que documentent certains tutoriels pour d'autres versions. **Important** : `correlationValue` est normalisée en `String` par le connecteur (Flowable compare les variables de process par type ET valeur) — la variable de process `correlationVariableName` posée par le `serviceTask` sortant doit donc, elle aussi, être une `String`. |
| `batch-import` | TRIGGER | **Oui** (`BatchImportConnector` — connecteurs-cible, lot B1) | Ingestion périodique de fichiers CSV/Excel déposés sur un serveur SFTP (`remoteDirectory`/`filenamePattern` glob, auth mot de passe ou clé privée SSH — `sftpHostKeyFingerprint` requis, jamais de connexion sans vérification de la clé hôte). Un dossier par ligne (mapping `mapping.fields`/`mapping.variables`, mêmes chemins `"$.colonne"` que `http-rest.mapping`), ligne invalide (champ mappé blanc) exclue sans bloquer les autres. Gardes `maxFileBytes`/`maxRows` (`app.connector.batch-import.*`) rejettent un fichier avant tout téléchargement/parsing. Fichier traité déplacé vers `archiveDirectory` (évite le double-import). Déclenchement cron dédié `BatchImportConnectorTriggerJob` (même pattern que `scheduler`, réutilise `connector_schedule_state`), isolé par instance (une panne SFTP sur une instance n'empêche pas les autres de ce tenant d'être traitées au même cycle). |
| `sftp-pdf-inbound` | TRIGGER (interrogation) | **Oui** (`SftpPdfInboundConnector` — TODO §113, D-MAILROOM-PDF-SFTP, lot 5a de #217) | Un dossier par PDF déposé dans un dossier SFTP, PDF en pièce jointe (chemin des pièces de l'e-mail : signature, antivirus). `PollingTriggerConnector` : remis au runtime avant d'être rangé (`processedDirectory`) ou refusé (`rejectedDirectory`). Index du partenaire `indexFormat` = `JSON` (compagnon de même nom, 64 Ko) / `CSV` (index par lot, colonne `csvFileColumn`, 1 Mo, 500 lignes) / `NONE` ; `requireIndex` (un PDF sans index attend) ; `mapping` sur `{fichier, index}`, `defaultFields`. Interrogé par `InboundPollingConnectorTriggerJob` (`pollSeconds`), limites `app.inbound.*`. Clé hôte vérifiée comme `batch-import`. |
| `mail-deposit` | TRIGGER (écran) | **Oui** (`MailDepositConnector` — TODO §113, D-MAILROOM-DEPOSIT, lot 5b de #217) | Point de dépôt de l'écran « Déposer » : ne se déclenche jamais seul (`onEvent` vide). `DepositMailUseCase` construit l'événement (type, process, `senderField`, `subjectField`, `defaultFields`, PDF en pièce) et le remet par `ConnectorTriggerRuntime.deliver`, une transaction par fichier. Visible des agents qui ont l'ACL EDIT du type. API `/api/mail-deposit-points`. |
| `kafka` | TRIGGER | **Oui** (`KafkaConnector` — connecteurs-cible, lot B1) | Consommation d'un topic Kafka (`bootstrapServers`/`topic`/`groupId`, `securityProtocol` PLAINTEXT ou SASL_SSL). Un message = exactement un événement (pas d'`itemsPath`, contrairement à `http-rest`), même style de mapping `"$.champ"`. Offset `latest` à la première souscription (n'importe pas l'historique du topic). Cycle de vie des consumers géré par `KafkaConsumerReconciler` (poll, démarre/arrête/redémarre un conteneur par instance selon son état activé/config), un message malformé ou en échec n'interrompt pas la consommation des suivants. Rôle SERVICE (publication) hors scope — TRIGGER seul. Nombre d'instances **activées** par tenant plafonné (`app.connector.kafka.max-instances-per-tenant`, défaut 10 — chaque instance active tient un thread/connexion réel pour toute la durée de vie de la JVM, contrairement à un connecteur cron, revue adversariale connecteurs-cible). |

### Ajout d'un connecteur custom — classpath

Un connecteur custom est un `@Component` Spring qui implémente `ConnectorDefinition` et l'une ou les deux interfaces de capacité. Il est découvert automatiquement au démarrage.

```java
@Component
public class D365ConnectorDefinition
        implements ConnectorDefinition, ServiceConnector {

    @Override public String getType()  { return "d365-fo"; }
    @Override public String getLabel() { return "Microsoft D365 F&O"; }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.SERVICE;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of(
            ConfigField.builder().name("tenantId")
                .label("Azure Tenant ID").type(FieldType.STRING).required(true).build(),
            ConfigField.builder().name("clientId")
                .label("Client ID").type(FieldType.STRING).required(true).build(),
            ConfigField.builder().name("clientSecret")
                .label("Client Secret").type(FieldType.PASSWORD).required(true).build(),
            ConfigField.builder().name("baseUrl")
                .label("D365 Base URL").type(FieldType.URL).required(true).build()
        );
    }

    @Override
    public Map<String, Object> execute(ConnectorInstanceData instance,
                                       Map<String, Object> params) {
        // Appel D365 OData / REST
    }
}
```

### ConnectorRegistry — découverte automatique

```java
@Component
@RequiredArgsConstructor
public class ConnectorRegistry {

    // Spring injecte toutes les implémentations présentes dans le classpath
    private final List<ConnectorDefinition> definitions;
    private Map<String, ConnectorDefinition> catalogue;

    @PostConstruct
    public void init() {
        catalogue = definitions.stream()
            .collect(Collectors.toMap(ConnectorDefinition::getType,
                                      Function.identity()));
    }

    public ConnectorDefinition get(String type) {
        return Optional.ofNullable(catalogue.get(type))
            .orElseThrow(() -> new UnknownConnectorException(type));
    }

    public List<ConnectorDefinition> listAll() {
        return List.copyOf(definitions);
    }
}
```

### `ConnectorInstance` — entité JPA vs objet domaine

**Problème identifié en revue :** l'entité JPA `ConnectorInstance` (annotée `@Entity`, `@Convert`) était passée directement aux ports `TriggerConnector` et `ServiceConnector`, couplant le domaine à une classe d'infrastructure — contraire à la règle énoncée dans [01-architecture-hexagonale.md](01-architecture-hexagonale.md) (« les entités JPA sont distinctes des entités domaine »).

**Décision retenue :** séparation stricte entre l'entité de persistance et l'objet domaine, sur le même modèle que `WorkflowInstance`/`WorkflowJpaEntity`. Les ports `TriggerConnector` et `ServiceConnector` (définis plus haut) consomment `ConnectorInstanceData` — objet domaine pur, sans aucune annotation JPA, dont la `config` est déjà déchiffrée.

> **Mise à jour — décision n° 17.** `ConnectorInstanceData` est devenu un `record` (accesseurs sans
> préfixe — `.id()`, `.tenantId()`…), alignée sur la convention retenue pour les autres entités du
> domaine — voir [01-architecture-hexagonale.md](01-architecture-hexagonale.md). Elle n'était pas
> restée une classe manuscrite par choix délibéré ; c'était simplement la forme montrée avant que la
> décision n° 17 ne généralise et documente cette convention.

```java
// infrastructure/persistence/connector — entité de persistance, jamais exposée au domaine
@Entity
@Table(name = "connector_instance")
public class ConnectorInstanceJpaEntity {

    @Id @GeneratedValue
    private UUID id;

    private String  tenantId;
    private String  name;           // nom donné par l'admin du tenant
    private String  connectorType;  // référence ConnectorDefinition.getType()
    private boolean enabled;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> config; // champs PASSWORD chiffrés au repos (AES-GCM)

    private Instant createdAt;
    private Instant updatedAt;
}

// domain/connector/model — pur Java, aucune annotation JPA, config déjà déchiffrée
@Builder
public record ConnectorInstanceData(
    UUID                id,
    String              tenantId,
    String              name,
    String              connectorType,
    boolean             enabled,
    Map<String, Object> config // déchiffré par l'adaptateur avant conversion
) {}
```

L'adaptateur `ConnectorInstanceJpaAdapter` (dans `infrastructure.persistence.connector`) assure la conversion, y compris le déchiffrement AES-GCM des champs `PASSWORD`, avant d'exposer un `ConnectorInstanceData` au domaine :

```java
@Repository
@RequiredArgsConstructor
public class ConnectorInstanceJpaAdapter implements ConnectorInstanceRepository {

    private final ConnectorInstanceJpaRepository jpa;
    private final ConnectorSecretCipher           cipher; // voir "Gestion des clés" ci-dessous

    @Override
    public Optional<ConnectorInstanceData> findByIdAndTenantId(UUID id, String tenantId) {
        return jpa.findByIdAndTenantId(id, tenantId)
            .map(entity -> ConnectorInstanceData.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .name(entity.getName())
                .connectorType(entity.getConnectorType())
                .enabled(entity.isEnabled())
                .config(cipher.decryptPasswordFields(entity.getTenantId(), entity.getConfig()))
                .build());
    }
}
```

### Gestion des clés de chiffrement des secrets de connecteurs

**Problème identifié en revue :** les champs `PASSWORD` sont chiffrés AES-GCM avec une « clé par tenant », mais le stockage, la rotation et l'accès à ces clés n'étaient pas documentés.

**Décision retenue — chiffrement en enveloppe :**

- Une **KEK** (Key Encryption Key) unique, gérée par un service externe (HashiCorp Vault Transit, Azure Key Vault ou AWS KMS) — jamais stockée en base ni en clair dans une variable d'environnement en production.
- Une **DEK** (Data Encryption Key) générée par tenant, chiffrée par la KEK ; c'est la DEK chiffrée qui est stockée en base à côté des données du tenant (colonne dédiée, hors table `connector_instance`).
- **Rotation KEK** : déléguée au vault, transparente grâce au versionnage natif des clés (Vault Transit / KMS key rotation).
- **Rotation DEK** : job explicite `RotateTenantEncryptionKeyUseCase`, qui déchiffre puis rechiffre les champs `PASSWORD` concernés avec la nouvelle DEK — jamais déclenché automatiquement en tâche de fond silencieuse.
- **Accès** : le rôle de service applicatif dispose d'un accès en moindre privilège au vault (déchiffrement/chiffrement uniquement, pas de lecture brute des clés), avec piste d'audit fournie par le vault (audit device Vault, CloudTrail pour KMS).
- **Exception mono-tenant / développement** : une clé maître unique via variable d'environnement reste tolérée comme compromis temporaire, à condition d'être documentée comme limitation à lever avant l'ajout d'un second tenant sensible ou une mise en production multi-tenant.

```java
@Component
@RequiredArgsConstructor
public class ConnectorSecretCipher {

    private final VaultTransitClient vault; // ou KmsClient selon le provider retenu

    public Map<String, Object> decryptPasswordFields(String tenantId, Map<String, Object> config) {
        // La DEK chiffrée du tenant est déchiffrée via la KEK côté vault (opération "decrypt"),
        // jamais matérialisée en clair côté application au-delà de la durée de l'appel.
        ...
    }
}
```

### Intégration Flowable — rôle service

Un `ServiceTask` BPMN référence une instance de connecteur par son `id` via une variable de processus :

```java
@Component("connectorServiceTask")
@RequiredArgsConstructor
public class ConnectorServiceTask implements JavaDelegate {

    private final ConnectorRegistry            registry;
    private final ConnectorInstanceRepository  instances;
    private final TenantProperties             tenantProperties; // décision n° 13 — constante de configuration, plus un contexte à résoudre

    @Override
    public void execute(DelegateExecution exec) {
        UUID instanceId = UUID.fromString(
            (String) exec.getVariable("connectorInstanceId"));

        ConnectorInstanceData instance = instances
            .findByIdAndTenantId(instanceId, tenantProperties.id())
            .orElseThrow();

        ServiceConnector connector =
            (ServiceConnector) registry.get(instance.connectorType());

        Map<String, Object> result =
            connector.execute(instance, exec.getVariables());

        // Les résultats sont injectés comme variables du processus
        result.forEach(exec::setVariable);
    }
}
```

**Implémentation (TODO §112, issue #260).** `ConnectorServiceTask` lit `connectorInstanceId` (attribut littéral ou variable ; **littéral obligatoire** avec `connectorRequest`)
et passe au connecteur soit la variable `connectorInput`, soit, si l'activité porte l'attribut **littéral**
`flowable:connectorRequest`, l'objet produit par cette expression métier bornée (LC2, racine de `BusinessExpressionContext` :
champs déclarés non sensibles, `connectorResult`, variables scalaires). L'expression est validée au déploiement
(`FlowableBpmnParserAdapter`) et revalidée à l'exécution ; un résultat qui n'est pas un objet fait échouer l'activité. Le
résultat non vide est posé dans `connectorResult` (variable réservée). Le contexte d'appel indique au connecteur si l'entrée
vient de `connectorRequest` (`ConnectorCallContext.requestBuiltByProcess`) : depuis un process, une instance `http-rest` à
`baseUrl` refuse la variable `connectorInput`. Le pseudo-code ci-dessus est celui de la conception
initiale. Contrat d'entrée de `http-rest` : [guide de conception §4](../guides/GUIDE-CONCEPTION-BPMN.md).
Tests de référence : `ConnectorServiceTaskTest`, `FlowableBpmnParserAdapterBlockTest`, `OnboardingOrchestrationExampleIT`.

### Intégration Flowable — rôle trigger

Chaque événement retourné par le connecteur produit soit la création d'un
nouveau dossier, soit le démarrage d'un nouveau workflow sur un dossier
existant — voir `WorkflowTriggerEvent.existingDossierReference` dans la
section « Lien avec les dossiers » ci-dessous.

**Décision n° 16 — option B.** Le runtime ne démarre **pas** Flowable
directement. Il crée/résout le dossier et écrit l'outbox dans la même
transaction ; le relais documenté dans
[05-workflow-engine.md](05-workflow-engine.md) réserve le lien, démarre
Flowable et confirme.

```java
@Component
@RequiredArgsConstructor
public class ConnectorTriggerRuntime {

    private final ConnectorRegistry    registry;
    private final CreateDossierUseCase createDossierUseCase;
    private final GetDossierUseCase    getDossierUseCase;
    private final OutboxPort           outboxPort;   // écriture outbox_event

    /** Phase producteur — appelée après authentification webhook (adaptateur web). */
    @Transactional
    public void onIncomingEvent(ConnectorInstanceData instance,
                                Map<String, Object> rawPayload) {

        TriggerConnector connector =
            (TriggerConnector) registry.get(instance.connectorType());

        // Un seul événement entrant peut produire N dossiers (import batch)
        // ou cibler N dossiers existants
        for (WorkflowTriggerEvent event : connector.onEvent(instance, rawPayload)) {
            UUID dossierId = resolveOrCreateDossier(instance, event);
            outboxPort.append(TriggerOutboxPayload.from(dossierId, instance, event));
        }
        // Commit → ACK webhook. Aucun démarrage Flowable ici.
    }

    private UUID resolveOrCreateDossier(ConnectorInstanceData instance,
                                        WorkflowTriggerEvent event) {
        if (event.getExistingDossierReference() != null) {
            // Décision n° 20 — résolution système, hors ACL utilisateur
            return getDossierUseCase.getByReference(
                instance.tenantId(),
                event.getExistingDossierReference(),
                AccessContext.system("connector-trigger")).id();
        }
        return createDossierUseCase.create(CreateDossierCommand.builder()
            .tenantId       (instance.tenantId())
            .dossierTypeCode(event.getDossierTypeCode())
            .fields         (event.getDossierFields())
            .createdBy      ("trigger:" + instance.id())
            .build()).id();
    }
}
```

> La collision « workflow déjà actif » n'est **plus** détectée ici — elle
> survient dans le relais au moment de `reserveOrResume`. Si elle se produit,
> l'événement outbox passe en dead-letter (le dossier existe déjà, l'ACK a été
> donné). Voir politiques retry / DLQ dans
> [05-workflow-engine.md](05-workflow-engine.md).

### Authentification des webhooks entrants

**Problème identifié en revue :** le connecteur `http-rest` en mode TRIGGER recevait des webhooks externes sans mécanisme d'authentification documenté — n'importe qui connaissant l'URL pouvait déclencher une création de dossier.

**Décision retenue :** signature **HMAC-SHA256** par défaut (modèle GitHub/Stripe), avec repli possible vers un token secret partagé ou une allowlist d'IP pour les systèmes appelants qui ne supportent pas HMAC. Le mécanisme choisi est un champ de configuration du connecteur (`webhookAuthType`), et le secret associé est un champ `PASSWORD` de la `ConnectorInstance` — donc chiffré au repos comme les autres secrets (voir « Gestion des clés » ci-dessus).

La vérification est effectuée dans l'adaptateur web (`WebhookController`, `infrastructure.web.connector`), **avant** d'atteindre `ConnectorTriggerRuntime` : c'est un détail HTTP/infrastructure (en-têtes, corps brut de la requête), pas une responsabilité du domaine.

```java
public enum WebhookAuthType { HMAC_SHA256, SHARED_SECRET_TOKEN, IP_ALLOWLIST }

public interface WebhookAuthenticator {
    void authenticate(ConnectorInstanceData instance, HttpServletRequest request, byte[] rawBody);
}

@Component
public class HmacWebhookAuthenticator implements WebhookAuthenticator {

    @Override
    public void authenticate(ConnectorInstanceData instance, HttpServletRequest request, byte[] rawBody) {
        String secret   = (String) instance.config().get("webhookSecret");
        String expected = "sha256=" + hmacSha256Hex(secret, rawBody);
        String received = request.getHeader("X-Signature-256");

        if (received == null || !MessageDigest.isEqual(
                expected.getBytes(UTF_8), received.getBytes(UTF_8))) {
            throw new WebhookAuthenticationException(instance.id());
        }
    }
}

// infrastructure/web/connector — point d'entrée HTTP des triggers webhook
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final ConnectorInstanceRepository       instances;
    private final Map<WebhookAuthType, WebhookAuthenticator> authenticators;
    private final ConnectorTriggerRuntime           triggerRuntime;
    private final TenantProperties                  tenantProperties; // décision n° 13

    @PostMapping("/{instanceId}")
    public ResponseEntity<Void> receive(@PathVariable UUID instanceId,
            HttpServletRequest request, @RequestBody byte[] rawBody) {

        ConnectorInstanceData instance = instances
            .findByIdAndTenantId(instanceId, tenantProperties.id())
            .orElseThrow();

        authenticators.get(WebhookAuthType.valueOf(
                (String) instance.config().get("webhookAuthType")))
            .authenticate(instance, request, rawBody);

        triggerRuntime.onIncomingEvent(instance, parseJson(rawBody));
        return ResponseEntity.accepted().build();
    }
}
```

Le champ `webhookAuthType` est un `ConfigField` SELECT du connecteur `http-rest` (valeurs
`HMAC_SHA256` / `SHARED_SECRET_TOKEN` / `IP_ALLOWLIST`) — stocké dans `ConnectorInstanceData.config`,
pas comme champ de premier niveau du record (décision n° 18). L'écran d'admin le saisit dans le
formulaire généré depuis `configSchema` (liste déroulante), comme les autres champs du type.

**Résolution du tenant (décision n° 14, [02-multi-tenant.md](02-multi-tenant.md#routage-vers-linstance--chemin-par-tenant-reverse-proxy-statique)).**
L'URL complète exposée au tiers pour configurer son webhook inclut le préfixe de chemin propre au
tenant, transparent pour ce contrôleur : `https://example.com/{tenantId}/api/webhooks/{instanceId}`.
Le reverse-proxy route déjà la requête vers l'instance du bon tenant avant qu'elle n'atteigne ce
code — `findByIdAndTenantId` avec `tenantProperties.id()` n'est donc plus qu'une défense en
profondeur, plus une nécessité pour éviter une résolution circulaire (l'instance ne dessert de toute
façon jamais qu'un seul schéma).

**Addendum (2026-09-03, revue de sécurité adversariale sur `connecteur-callback`, D-CALLBACK-REPLAY).**
Le schéma HMAC ci-dessus (signature du seul corps brut) permettait à quiconque capturait une fois
une paire (corps, signature) valide de la rejouer indéfiniment avec une nouvelle
`Idempotency-Key` à chaque fois, contournant totalement l'idempotence (clé fournie par l'appelant,
non couverte par la signature). Corrigé : la signature couvre désormais
`timestamp + "." + Idempotency-Key + "." + corps` (`X-Webhook-Timestamp`, secondes epoch Unix,
obligatoire pour `HMAC_SHA256`), avec une fenêtre de fraîcheur de 5 minutes — une requête
capturée n'est rejouable que dans cette fenêtre, et une `Idempotency-Key` neuve ne peut plus
réutiliser une signature capturée. **Rupture de contrat pour les appelants HMAC déjà en
production** : ils doivent désormais envoyer cet en-tête **et** l'inclure dans le calcul de
signature. `SHARED_SECRET_TOKEN` : cache anti-rejeu du corps (TTL 5 min) en plus du jeton.
Voir `HmacWebhookAuthenticator` (`infrastructure.web.connector`) pour l'implémentation réelle.

### Appel direct via API

Les connecteurs de type SERVICE sont également appelables hors contexte workflow :

```java
@RestController
@RequestMapping("/api/connectors")
@RequiredArgsConstructor
public class ConnectorExecutionController {

    private final ConnectorRegistry           registry;
    private final ConnectorInstanceRepository instances;
    private final TenantProperties            tenantProperties; // décision n° 13

    @PostMapping("/{instanceId}/execute")
    public ResponseEntity<Map<String, Object>> execute(
            @PathVariable UUID instanceId,
            @RequestBody Map<String, Object> inputParams) {

        ConnectorInstanceData instance = instances
            .findByIdAndTenantId(instanceId, tenantProperties.id())
            .orElseThrow();

        ServiceConnector connector =
            (ServiceConnector) registry.get(instance.connectorType());

        return ResponseEntity.ok(connector.execute(instance, inputParams));
    }
}
```

Cet endpoint est celui protégé par la chaîne de sécurité machine-to-machine (`client_credentials`,
décision n° 14) — voir [03-authentification.md](03-authentification.md#authentification-machine-to-machine--client_credentials).
Un appelant `API_SERVICE` l'atteint via le même chemin propre au tenant que les webhooks
(`https://example.com/{tenantId}/api/connectors/{instanceId}/execute`) — un seul mécanisme de
routage sert les deux cas, voir [02-multi-tenant.md](02-multi-tenant.md).

---

## Lien avec les dossiers

Chaque `WorkflowTriggerEvent` retourné par un `TriggerConnector` produit
soit la création d'un **nouveau dossier**, soit le démarrage d'un nouveau
workflow sur un **dossier existant** (voir
[04-dossiers.md](04-dossiers.md), section « Démarrage d'un nouveau workflow
sur un dossier existant »). L'orchestration est scindée (décision n° 16) :
`ConnectorTriggerRuntime` crée/résout le dossier et écrit l'outbox ; le
relais de [05-workflow-engine.md](05-workflow-engine.md) réserve le lien et
démarre Flowable. Un trigger qui importe un fichier CSV peut retourner N
événements → N dossiers créés en batch ; un trigger qui referme une boucle
métier (ex. réponse à un webhook de suivi) peut au contraire cibler un
dossier déjà ouvert via `existingDossierReference`.

```java
public class WorkflowTriggerEvent {
    String              processKey;
    String              dossierTypeCode;       // type de dossier à créer — ignoré si existingDossierReference est renseigné
    Map<String, Object> dossierFields;         // champs du signalétique — idem
    Map<String, Object> variables;             // variables Flowable supplémentaires

    String              existingDossierReference; // optionnel — référence d'un dossier
                                                    // existant (ex: "DOS-SINISTRE-2024-000042").
                                                    // Si renseigné, ConnectorTriggerRuntime
                                                    // résout le dossier (GetDossierUseCase) au
                                                    // lieu de CreateDossierUseCase.create(...) ;
                                                    // le relais outbox réserve ensuite le lien.
}
```
