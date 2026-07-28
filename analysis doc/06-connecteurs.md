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
| `ConnectorInstance` | Instance configurée par un tenant à partir d'une définition. Persistée en base avec les valeurs réelles. |
| `ConnectorExecution` | Exécution d'un connecteur dans un contexte donné (trigger entrant ou appel ServiceTask / API). |

### Interfaces d'extension

```java
// Capacité trigger — reçoit un événement entrant et produit un déclenchement de workflow
public interface TriggerConnector {
    // Retourne une liste — supporte la création batch de dossiers
    List<WorkflowTriggerEvent> onEvent(ConnectorInstance instance,
                                       Map<String, Object> rawPayload);
}

// Capacité service — exécute un traitement et retourne des variables
public interface ServiceConnector {
    Map<String, Object> execute(ConnectorInstance instance,
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
    public Map<String, Object> execute(ConnectorInstance instance,
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

### ConnectorInstance — entité tenant

```java
@Entity
public class ConnectorInstance {

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
```

Les champs de type `PASSWORD` déclarés dans `getConfigSchema()` sont chiffrés au repos via un `AttributeConverter` JPA — clé de chiffrement par tenant.

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

        ConnectorInstance instance = instances
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

Chaque événement retourné par le connecteur produit soit la création d'un
nouveau dossier, soit le démarrage d'un nouveau workflow sur un dossier
existant — voir `WorkflowTriggerEvent.existingDossierReference` dans la
section « Lien avec les dossiers » ci-dessous.

```java
@Component
@RequiredArgsConstructor
public class ConnectorTriggerRuntime {

    private final RuntimeService         runtimeService;
    private final ConnectorRegistry      registry;
    private final CreateDossierUseCase   createDossierUseCase;
    private final GetDossierUseCase      getDossierUseCase;
    private final AttachWorkflowUseCase  attachWorkflowUseCase;

    public void onIncomingEvent(ConnectorInstance instance,
                                Map<String, Object> rawPayload) {

        TriggerConnector connector =
            (TriggerConnector) registry.get(instance.getConnectorType());

        // Retourne une liste — un seul événement entrant peut produire
        // N dossiers (import batch) ou cibler N dossiers existants
        connector.onEvent(instance, rawPayload).forEach(event ->
            startWorkflowFor(instance, event));
    }

    private void startWorkflowFor(ConnectorInstance instance,
                                  WorkflowTriggerEvent event) {

        UUID dossierId = event.getExistingDossierReference() != null
            ? getDossierUseCase.getByReference(
                  instance.getTenantId(), event.getExistingDossierReference()).getId()
            : createDossierUseCase.create(CreateDossierCommand.builder()
                  .tenantId       (instance.getTenantId())
                  .dossierTypeCode(event.getDossierTypeCode())
                  .fields         (event.getDossierFields())
                  .createdBy      ("trigger:" + instance.getId())
                  .build()).getId();

        DossierWorkflowLink link = attachWorkflowUseCase.reserve(
            dossierId, event.getProcessKey(), "connector:" + instance.getId());

        ProcessInstance pi = runtimeService.createProcessInstanceBuilder()
            .processDefinitionKey(event.getProcessKey())
            .tenantId(instance.getTenantId())
            .variable("dossierId", dossierId.toString())
            .variable("dossierWorkflowLinkId", link.getId().toString())
            .variables(event.getVariables())
            .start();

        attachWorkflowUseCase.confirmStarted(link.getId(), pi.getId());
    }
}
```

> Si `existingDossierReference` référence un dossier qui a déjà un workflow
> actif, `reserve(...)` lève `WorkflowAlreadyRunningException` — au
> connecteur de décider comment réagir (rejet du message, mise en file
> d'attente, dead-letter — voir le pattern retry de
> [09-notifications.md](09-notifications.md)).

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

        ConnectorInstance instance = instances
            .findByIdAndTenantId(instanceId, tenantContext.getTenantId())
            .orElseThrow();

        ServiceConnector connector =
            (ServiceConnector) registry.get(instance.getConnectorType());

        return ResponseEntity.ok(connector.execute(instance, inputParams));
    }
}
```

---

## Lien avec les dossiers

Chaque `WorkflowTriggerEvent` retourné par un `TriggerConnector` produit
soit la création d'un **nouveau dossier**, soit le démarrage d'un nouveau
workflow sur un **dossier existant** (voir
[04-dossiers.md](04-dossiers.md), section « Démarrage d'un nouveau workflow
sur un dossier existant » — orchestration détaillée dans
`ConnectorTriggerRuntime` ci-dessus). Un trigger qui importe un fichier CSV
peut retourner N événements → N dossiers créés en batch ; un trigger qui
referme une boucle métier (ex. réponse à un webhook de suivi) peut au
contraire cibler un dossier déjà ouvert via `existingDossierReference`.

```java
public class WorkflowTriggerEvent {
    String              processKey;
    String              dossierTypeCode;       // type de dossier à créer — ignoré si existingDossierReference est renseigné
    Map<String, Object> dossierFields;         // champs du signalétique — idem
    Map<String, Object> variables;             // variables Flowable supplémentaires

    String              existingDossierReference; // optionnel — référence d'un dossier
                                                    // existant (ex: "DOS-SINISTRE-2024-000042").
                                                    // Si renseigné, ConnectorTriggerRuntime
                                                    // appelle AttachWorkflowUseCase.reserve(...)
                                                    // au lieu de CreateDossierUseCase.create(...)
}
```
