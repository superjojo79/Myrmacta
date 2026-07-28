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
    List<ConfigField>   getConfigSchema();    // schéma pour génération du formulaire UI
    void                validate(Map<String, Object> config);
}
```

Le schéma de configuration est déclaratif — l'UI génère le formulaire dynamiquement sans connaître le connecteur à l'avance :

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

### Catalogue de connecteurs fournis

| Type | Capacité | Description |
|---|---|---|
| `http-rest` | TRIGGER + SERVICE | Réception webhook entrant / appel REST sortant |
| `scheduler` | TRIGGER | Déclenchement planifié (cron) |
| `smtp` | SERVICE | Envoi email |
| `s3-minio` | SERVICE | Lecture / écriture fichiers |
| `flowable-signal` | TRIGGER + SERVICE | Émet ou reçoit un signal Flowable |

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
public class ConnectorInstanceData {
    private final UUID                 id;
    private final String               tenantId;
    private final String               name;
    private final String               connectorType;
    private final boolean              enabled;
    private final Map<String, Object>  config; // déchiffré par l'adaptateur avant conversion

    // getters, pas de setters — objet immuable
}
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
    private final TenantContext                tenantContext;

    @Override
    public void execute(DelegateExecution exec) {
        UUID instanceId = UUID.fromString(
            (String) exec.getVariable("connectorInstanceId"));

        ConnectorInstanceData instance = instances
            .findByIdAndTenantId(instanceId, tenantContext.getTenantId())
            .orElseThrow();

        ServiceConnector connector =
            (ServiceConnector) registry.get(instance.getConnectorType());

        Map<String, Object> result =
            connector.execute(instance, exec.getVariables());

        // Les résultats sont injectés comme variables du processus
        result.forEach(exec::setVariable);
    }
}
```

### Intégration Flowable — rôle trigger

```java
@Component
@RequiredArgsConstructor
public class ConnectorTriggerRuntime {

    private final RuntimeService  runtimeService;
    private final ConnectorRegistry registry;

    public void onIncomingEvent(ConnectorInstanceData instance,
                                Map<String, Object> rawPayload) {

        TriggerConnector connector =
            (TriggerConnector) registry.get(instance.getConnectorType());

        WorkflowTriggerEvent event = connector.onEvent(instance, rawPayload);

        runtimeService.createProcessInstanceBuilder()
            .processDefinitionKey(event.getProcessKey())
            .tenantId(instance.getTenantId())
            .variables(event.getVariables())
            .start();
    }
}
```

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
        String secret   = (String) instance.getConfig().get("webhookSecret");
        String expected = "sha256=" + hmacSha256Hex(secret, rawBody);
        String received = request.getHeader("X-Signature-256");

        if (received == null || !MessageDigest.isEqual(
                expected.getBytes(UTF_8), received.getBytes(UTF_8))) {
            throw new WebhookAuthenticationException(instance.getId());
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

    @PostMapping("/{instanceId}")
    public ResponseEntity<Void> receive(@PathVariable UUID instanceId,
            HttpServletRequest request, @RequestBody byte[] rawBody) {

        ConnectorInstanceData instance = instances.findById(instanceId).orElseThrow();

        authenticators.get(instance.getWebhookAuthType())
            .authenticate(instance, request, rawBody);

        triggerRuntime.onIncomingEvent(instance, parseJson(rawBody));
        return ResponseEntity.accepted().build();
    }
}
```

Le champ `webhookAuthType` est ajouté au `getConfigSchema()` du connecteur `http-rest` (type `SELECT`, valeurs `HMAC_SHA256` / `SHARED_SECRET_TOKEN` / `IP_ALLOWLIST`), permettant à l'UI de générer dynamiquement le formulaire de configuration correspondant (secret HMAC, token, ou liste d'IP autorisées).

### Appel direct via API

Les connecteurs de type SERVICE sont également appelables hors contexte workflow :

```java
@RestController
@RequestMapping("/api/connectors")
@RequiredArgsConstructor
public class ConnectorExecutionController {

    private final ConnectorRegistry           registry;
    private final ConnectorInstanceRepository instances;
    private final TenantContext               tenantContext;

    @PostMapping("/{instanceId}/execute")
    public ResponseEntity<Map<String, Object>> execute(
            @PathVariable UUID instanceId,
            @RequestBody Map<String, Object> inputParams) {

        ConnectorInstanceData instance = instances
            .findByIdAndTenantId(instanceId, tenantContext.getTenantId())
            .orElseThrow();

        ServiceConnector connector =
            (ServiceConnector) registry.get(instance.getConnectorType());

        return ResponseEntity.ok(connector.execute(instance, inputParams));
    }
}
```

---

---

## Lien avec les dossiers

Chaque `WorkflowTriggerEvent` retourné par un `TriggerConnector` produit
la création d'un **dossier** et le démarrage de son instance workflow
(voir [04-dossiers.md](04-dossiers.md)). Un trigger qui importe un fichier
CSV peut retourner N événements → N dossiers créés en batch.

```java
public class WorkflowTriggerEvent {
    String              processKey;
    String              dossierTypeCode; // type de dossier à créer
    Map<String, Object> dossierFields;   // champs du signalétique
    Map<String, Object> variables;       // variables Flowable supplémentaires
}
```
