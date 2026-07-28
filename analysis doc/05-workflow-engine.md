# Moteur de workflow — Flowable

> Orchestration des flux métier via BPMN 2.0.  
> Les triggers entrants transitent par le système de connecteurs
> (voir [06-connecteurs.md](06-connecteurs.md)).

---

**Choix retenu : Flowable (Community Edition)**

| Critère | Décision |
|---|---|
| Standard | BPMN 2.0 + DMN |
| Licence | Apache 2.0 |
| Intégration Spring Boot | `flowable-spring-boot-starter` |
| Designer visuel | Flowable UI (web, embarqué) |
| Monitoring instances | Flowable UI Cockpit |

### Dépendance Maven

```xml
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
    <version>7.x.x</version>
</dependency>
```

### Alternatives écartées

- **Camunda 7** — en maintenance, migration Camunda 8 complexe (Zeebe/Kafka)
- **jBPM / Kogito** — orienté Quarkus, intégration Spring secondaire
- **Spring State Machine** — pas de designer visuel, adapté aux flux linéaires simples uniquement

---

## Déclenchement — Event Bus

**Choix retenu : Spring `ApplicationEventPublisher`**

Les workflows sont déclenchés par des événements externes matérialisés en `ApplicationEvent` Spring, puis transmis au moteur Flowable via des `Signal` ou `Message Events` BPMN.

Les sources de triggers sont encapsulées dans le **système de connecteurs** (voir section 15). Le `ConnectorTriggerRuntime` publie les événements sur le bus Spring — les triggers bruts (Kafka, webhook, scheduler…) ne sont plus câblés directement dans le moteur.

```
Connecteur Trigger  →  ConnectorTriggerRuntime  →  ApplicationEventPublisher  →  Flowable
```

---

## Ports domaine

```java
// domain/workflow/port/in
public interface StartWorkflowUseCase {
    WorkflowInstance start(StartWorkflowCommand cmd);
}

public interface CompleteTaskUseCase {
    void complete(String taskId, Map<String, Object> variables);
}

// domain/workflow/port/out
public interface WorkflowRepository {
    void save(WorkflowInstance instance);
    Optional<WorkflowInstance> findById(UUID id);
}

public interface WorkflowEventPublisher {
    void publish(WorkflowStartedEvent event);
    void publish(WorkflowCompletedEvent event);
}
```

---

## Statuts métier configurables par workflow — `WorkflowTypeConfig`

Un dossier peut être manipulé par plusieurs workflows successifs (voir
[04-dossiers.md](04-dossiers.md)) ; chaque exécution (`DossierWorkflowLink`)
porte un **statut métier** propre, distinct du statut du dossier. Ce statut
n'est pas un enum technique fixe et global : il est **configurable par
workflow**, sur le même principe que les `fieldDefinitions` d'un
`DossierType` — chaque `processDefinitionKey` déclare ses propres valeurs de
statut possibles (ex. `EN_INSTRUCTION`, `EN_ATTENTE_EXPERT` pour un workflow
sinistre ; `EN_VERIFICATION`, `VALIDE` pour un workflow subvention).

```java
@Entity
public class WorkflowTypeConfig {

    @Id @GeneratedValue
    private UUID id;

    private String tenantId;
    private String workflowKey;   // = processDefinitionKey BPMN
    private String label;

    @Convert(converter = JsonListConverter.class)
    @Column(columnDefinition = "jsonb")
    private List<WorkflowStatusDefinition> statusDefinitions;
}

@Value @Builder
public class WorkflowStatusDefinition {
    String        code;      // valeur métier : "EN_INSTRUCTION", "EN_ATTENTE_EXPERT"…
    String        label;     // libellé UI
    boolean       initial;   // statut posé automatiquement à la création du DossierWorkflowLink
    StatusOutcome outcome;   // NONE (intermédiaire) ou déclencheur de fin de l'exécution
}

public enum StatusOutcome { NONE, COMPLETED, CANCELLED, FAILED }
```

Le statut `initial` est posé automatiquement quand `AttachWorkflowUseCase.reserve(...)`
crée le `DossierWorkflowLink` (voir [04-dossiers.md](04-dossiers.md)). Dès
qu'un `ServiceTask` pose un statut dont `outcome != NONE`, le lien est
considéré terminé : `endedAt` est renseigné et l'index unique partiel
`uq_dossier_workflow_link_active` libère le dossier pour un futur workflow.

```java
// domain/workflow/port/out
public interface WorkflowTypeConfigRepository {
    Optional<WorkflowTypeConfig> findByWorkflowKey(String tenantId, String workflowKey);
}
```

### `UpdateWorkflowLinkStatusTask` — pendant du `UpdateDossierStatusTask`

```java
@Component("updateWorkflowLinkStatusTask")
@RequiredArgsConstructor
public class UpdateWorkflowLinkStatusTask implements JavaDelegate {

    private final UpdateWorkflowLinkStatusUseCase useCase;

    @Override
    public void execute(DelegateExecution exec) {
        UUID   linkId = UUID.fromString(
            (String) exec.getVariable("dossierWorkflowLinkId"));
        String status = (String) exec.getVariable("targetWorkflowStatus");

        // useCase valide `status` contre WorkflowTypeConfig.statusDefinitions
        // avant de l'appliquer — un code inconnu pour ce workflowKey est rejeté
        useCase.update(linkId, status, exec.getCurrentActivityId());
    }
}
```

---

## Lien avec les dossiers

Le workflow opère toujours dans le contexte d'un **dossier** (voir
[04-dossiers.md](04-dossiers.md)). Deux variables de processus sont
injectées à la création de l'instance et accessibles depuis tous les
`ServiceTask` :

- `dossierId` — le dossier manipulé
- `dossierWorkflowLinkId` — l'exécution courante dans l'historique du dossier

```
Trigger → Création dossier → Réservation du lien → Démarrage workflow
                                                     (variables : dossierId,
                                                      dossierWorkflowLinkId)
                                                              │
                              ┌───────────────────────────────┤
                              ▼                                ▼
                 UpdateDossierStatusTask          UpdateWorkflowLinkStatusTask
                 pilote Dossier.status             pilote DossierWorkflowLink.status
```

Un dossier peut ainsi être repris par un second workflow une fois le premier
terminé — un seul actif à la fois (voir la règle de concurrence dans
[04-dossiers.md](04-dossiers.md)). Les `ServiceTask` qui opèrent sur le
dossier étendent `DossierAwareTask` — le dossier complet (signalétique,
champs dynamiques, éléments) est chargé automatiquement avant exécution,
indépendamment du nombre de workflows qui se sont déjà succédé sur lui.
