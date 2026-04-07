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
    void publish(WorkflowStartedEvent event);
    void publish(WorkflowCompletedEvent event);
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
