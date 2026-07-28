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

---

## Déclenchement — Event Bus

**Choix retenu : Spring `ApplicationEventPublisher`**

Les workflows sont déclenchés par des événements externes matérialisés en `ApplicationEvent` Spring, puis transmis au moteur Flowable via des `Signal` ou `Message Events` BPMN.

Les sources de triggers sont encapsulées dans le **système de connecteurs** (voir section 15). Le `ConnectorTriggerRuntime` publie les événements sur le bus Spring — les triggers bruts (Kafka, webhook, scheduler…) ne sont plus câblés directement dans le moteur.

```
Connecteur Trigger  →  ConnectorTriggerRuntime  →  ApplicationEventPublisher  →  Flowable
```

### Décision — durabilité des événements de déclenchement

**Problème identifié en revue :** `ApplicationEventPublisher` est synchrone et en mémoire — un crash entre la réception d'un trigger (webhook, scheduler…) et le démarrage effectif de l'instance Flowable entraînait une perte de l'événement, sans possibilité de rejeu.

**Décision retenue — pattern outbox transactionnel.** L'événement de trigger est écrit dans une table `outbox_event` (schéma du tenant concerné) **dans la même transaction** que la création du `Dossier` associé. Un relais applicatif (scheduler de polling) lit les événements non traités et les publie sur `ApplicationEventPublisher`, avant de les marquer traités — avec une clé d'idempotence (`event_id`) pour empêcher tout double traitement en cas de retry après un crash partiel du relais lui-même.

```java
@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity {
    @Id
    private UUID   eventId;      // clé d'idempotence
    private String eventType;    // "WorkflowTriggerEvent"
    private String payloadJson;  // WorkflowTriggerEvent sérialisé
    private Instant createdAt;
    private Instant processedAt; // null tant que non traité
}

// Écriture — même transaction que CreateDossierUseCase
@Transactional
public void onIncomingEvent(ConnectorInstanceData instance, Map<String, Object> rawPayload) {
    List<WorkflowTriggerEvent> events = connector.onEvent(instance, rawPayload);
    events.forEach(event -> outboxRepository.save(OutboxEventEntity.from(event)));
    // Le dossier est créé et l'événement est journalisé de façon atomique.
}

// Relais — scheduler de polling, hors transaction métier
@Scheduled(fixedDelay = 1000)
public void relayPendingEvents() {
    outboxRepository.findUnprocessed().forEach(entry -> {
        if (!alreadyProcessed(entry.getEventId())) {
            applicationEventPublisher.publishEvent(entry.toDomainEvent());
        }
        outboxRepository.markProcessed(entry.getEventId());
    });
}
```

Cette solution est retenue car elle ne nécessite aucune nouvelle brique d'infrastructure (la table outbox vit dans PostgreSQL, déjà présent) tout en garantissant la durabilité. **Chemin d'évolution documenté** : si le volume d'événements ou le besoin de diffusion multi-consommateurs augmente, le relais de polling peut être remplacé par une capture Debezium/CDC sur la table `outbox_event`, publiant directement vers un broker externe (Kafka) sans changer le contrat d'écriture côté producteur.

---

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
    void publish(WorkflowEvent event);
}
```

### Décision — extensibilité de `WorkflowEventPublisher`

**Problème identifié en revue :** une méthode par type d'événement (`publish(WorkflowStartedEvent)`, `publish(WorkflowCompletedEvent)`) imposait de modifier l'interface — et tous ses adaptateurs — à chaque nouvel état de workflow.

**Décision retenue :** signature générique `void publish(WorkflowEvent event)` (ci-dessus), où `WorkflowEvent` est une **interface scellée** (`sealed interface`, Java 21). Un nouvel événement = un nouveau type autorisé, sans modification de `WorkflowEventPublisher` ni de ses adaptateurs existants. Les adaptateurs qui doivent router par type conservent une vérification à la compilation grâce à l'exhaustivité du `switch` sur les types permis.

```java
public sealed interface WorkflowEvent
    permits WorkflowStartedEvent, WorkflowCompletedEvent, WorkflowFailedEvent {

    UUID workflowInstanceId();
    Instant occurredAt();
}

public record WorkflowStartedEvent(UUID workflowInstanceId, Instant occurredAt,
                                    String processKey) implements WorkflowEvent {}

public record WorkflowCompletedEvent(UUID workflowInstanceId, Instant occurredAt) implements WorkflowEvent {}

public record WorkflowFailedEvent(UUID workflowInstanceId, Instant occurredAt,
                                   String reason) implements WorkflowEvent {}

// Adaptateur — routage par type avec vérification d'exhaustivité à la compilation
@Component
public class NotificationWorkflowEventListener implements WorkflowEventPublisher {

    @Override
    public void publish(WorkflowEvent event) {
        switch (event) {
            case WorkflowStartedEvent e   -> notifyStart(e);
            case WorkflowCompletedEvent e -> notifyCompletion(e);
            case WorkflowFailedEvent e    -> notifyFailure(e);
            // Le compilateur signale une erreur si un nouveau type scellé
            // est ajouté sans être traité ici.
        }
    }
}
```

## Dépendance Maven

```xml
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
    <version>7.x.x</version>
</dependency>
```

---

## Scaling horizontal et scheduler Flowable Community

**Problème identifié en revue :** Flowable Community gère les jobs asynchrones et le scheduler via une table de verrouillage en base — en déploiement multi-instances (Kubernetes), le risque de double exécution des jobs semblait imposer une contrainte forte de déploiement.

**Décision retenue :** le job executor de Flowable Community gère nativement le verrouillage optimiste au niveau ligne sur les tables `ACT_RU_JOB` / `ACT_RU_TIMER_JOB` (`lockOwner`, `lockExpirationTime`) — plusieurs moteurs Flowable pointant vers la même base peuvent cohabiter sans double exécution, à condition que les paramètres de verrouillage soient correctement dimensionnés pour la charge cible. Ce n'est donc **pas** une contrainte de « une seule instance ».

```yaml
flowable:
  async-executor-activate: true
  process:
    async:
      lock-time: 5m
      default-async-job-acquire-wait-time: 10s
      default-timer-job-acquire-wait-time: 10s
```

Le dimensionnement de `lockTimeInMillis` et des délais d'acquisition est validé par un test de charge au niveau de trafic cible avant mise en production.

**Si la contention devient un problème mesuré** (nombreuses instances qui pollent la même table de jobs), la stratégie opérationnelle de repli est une **séparation des rôles** plutôt qu'une limitation à une instance unique :

- pods **API** : `asyncExecutorActivate: false` — ne participent pas à l'acquisition de jobs, uniquement au trafic REST/BFF, librement réplicables ;
- pods **worker** : `asyncExecutorActivate: true` — exécutent les jobs asynchrones et le scheduler, eux-mêmes réplicables pour la haute disponibilité (le verrouillage optimiste natif de Flowable reste la garantie contre la double exécution entre workers).

Cette séparation permet de scaler indépendamment la capacité API et la capacité de traitement des jobs, sans jamais revenir à une contrainte de instance unique globale.

---

## Lien avec les dossiers

Le workflow opère toujours dans le contexte d'un **dossier** (voir
[04-dossiers.md](04-dossiers.md)). L'identifiant du dossier (`dossierId`)
est injecté comme variable de processus à la création de l'instance et
accessible depuis tous les `ServiceTask`.

```
Trigger → Création dossier → Démarrage workflow (variable: dossierId)
               ↑                        │
               └── statut piloté par ───┘
                   UpdateDossierStatusTask
```

Les `ServiceTask` qui opèrent sur le dossier étendent `DossierAwareTask`
— le dossier complet (signalétique, champs dynamiques, éléments) est
chargé automatiquement avant exécution.
