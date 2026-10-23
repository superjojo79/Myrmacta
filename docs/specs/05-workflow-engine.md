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
| Designer visuel | Éditeur intégré FlowForge (`bpmn-js` Modeler + panel de propriétés Flowable custom, `/admin/bpmn-editor`, feature `edition-bpmn`) **+** éditeur DMN (`dmn-js` Modeler, `/admin/dmn-editor`, feature `introduction-dmn`) — pas Flowable UI/Modeler/Cockpit, explicitement hors scope produit ([`DEFERRED-FEATURES.md`](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/DEFERRED-FEATURES.md) §1.5) |
| Déploiement des processus | Scan classpath au build/démarrage (`TenantProcessDeployment`) **+** upload admin à chaud avec prévisualisation (`/admin/workflow-types`, feature `deploiement-bpmn`) **+** déploiement direct depuis l'éditeur (`/admin/bpmn-editor`, feature `edition-bpmn`) |
| Déploiement des tables de décision | Scan classpath au build/démarrage (`TenantDmnDeployment`, miroir de `TenantProcessDeployment`) **+** déploiement direct depuis l'éditeur DMN (`/admin/dmn-editor`) **+** MCP (`deploy_decision_table`) — feature `introduction-dmn` |
| Monitoring instances | Viewer intégré FlowForge (`bpmn-js`, `GET …/diagram`) — pas de cockpit Flowable |

### Dépendance Maven

```xml
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
    <version>8.0.x</version>
</dependency>
```

### Alternatives écartées

- **Camunda 7** — en maintenance, migration Camunda 8 complexe (Zeebe/Kafka)
- **jBPM / Kogito** — orienté Quarkus, intégration Spring secondaire
- **Spring State Machine** — pas de designer visuel, adapté aux flux linéaires simples uniquement

---

## Tables de décision — DMN

**Choix retenu : moteur DMN Flowable, activé (feature `introduction-dmn`, voir
[25-introduction-dmn.md](25-introduction-dmn.md), TODO §61).** Un
`serviceTask flowable:type="dmn"` (une extension native Flowable, pas le `businessRuleTask`
standard — celui-ci est câblé sur un moteur Drools absent de ce dépôt) invoque une table de
décision déployée séparément via `decisionTableReferenceKey`, un champ d'extension `flowable:field`
portant la clé de la décision.

**Sécurité — le risque dominant, pas un détail d'implémentation.** Contrairement à ce que le
standard DMN laisse penser, Flowable n'implémente **pas FEEL** : ses cellules de table de décision
sont évaluées en **JUEL**, le même moteur d'expression déjà durci pour les `conditionExpression`
BPMN (`FlowableBpmnParserAdapter.assertSafeConditionExpression`, TODO §57 H1). Une cellule laissée
libre expose donc la même classe de vulnérabilité que H1-bis (résolution d'un identifiant non
qualifié contre un bean Spring du même nom) — vérifié par décompilation du moteur
`flowable-dmn-engine` 8.0.0 réel, pas supposé. `FlowableDmnParserAdapter` applique en conséquence,
au moment du déploiement, une allowlist stricte sur chaque cellule d'entrée/sortie (littéral entre
guillemets, nombre, booléen, `-`, ou l'un de ces littéraux précédé d'un opérateur de comparaison) —
tout le reste (EL brut `#{}`/`${}`, navigation de propriété `.xxx`) est rejeté. Preuve d'exécution
réelle sur le moteur (pas seulement une lecture statique) : `DmnJuelSecuritySmokeIT`.

**Scénario illustratif livré :** `grille-eligibilite-client.dmn`
(`src/main/resources/decisions/`) + `dmn-grille-eligibilite-smoke.bpmn20.xml`
(`src/main/resources/processes/`), auto-déployés au démarrage comme les process `-smoke` existants
— cas générique (pas un besoin métier réel identifié), preuve de bout en bout que
`serviceTask type="dmn"` alimente des variables de process réelles (`DmnGrilleEligibiliteSmokeIT`).

---

## Déclenchement — Event Bus

**Choix retenu : Spring `ApplicationEventPublisher`**

Les workflows sont déclenchés par des événements externes matérialisés en événements
domaine, puis transmis au moteur Flowable via des `Signal` ou `Message Events` BPMN.

Les sources de triggers sont encapsulées dans le **système de connecteurs**
(voir [06-connecteurs.md](06-connecteurs.md)). Le `ConnectorTriggerRuntime` crée/résout le dossier
et journalise un événement outbox ; un relais démarre ensuite Flowable — décision n° 16.

```
Connecteur Trigger  →  ConnectorTriggerRuntime (dossier + outbox)  →  Relais  →  Flowable
```

### Décision — durabilité des événements de déclenchement

**Problème identifié en revue :** `ApplicationEventPublisher` est synchrone et en mémoire — un crash
entre la réception d'un trigger (webhook, scheduler…) et le démarrage effectif de l'instance Flowable
entraînait une perte de l'événement, sans possibilité de rejeu.

**Décision retenue — pattern outbox transactionnel (décision n° 7), articulé en option B
(décision n° 16).** Le dossier est créé ou résolu **dans la même transaction** que l'écriture dans
`outbox_event`. Un relais applicatif (scheduler de polling) lit les événements `PENDING`, démarre
Flowable, puis marque l'événement traité — uniquement en cas de succès, dans la même transaction.
Clé d'idempotence : `event_id`.

```java
public enum OutboxStatus { PENDING, PROCESSED, DEAD }

@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity {
    @Id
    private UUID         eventId;             // clé d'idempotence
    private String       eventType;           // "TriggerOutboxPayload"
    private String       payloadJson;         // TriggerOutboxPayload sérialisé
    private OutboxStatus status;              // PENDING | PROCESSED | DEAD
    private int          attempts;
    private String       lastError;           // nullable
    private Instant      createdAt;
    private Instant      processedAt;         // null tant que non traité
}

/** Contenu stocké — événement déjà normalisé + dossier résolu. */
public record TriggerOutboxPayload(
    UUID                dossierId,
    UUID                connectorInstanceId,
    String              processKey,
    Map<String, Object> variables,
    String              triggerRef            // "connector:{instanceId}"
) {}
```

**Phase producteur** — détail dans [06-connecteurs.md](06-connecteurs.md) (`ConnectorTriggerRuntime`) :

```java
@Transactional
public void onIncomingEvent(ConnectorInstanceData instance, Map<String, Object> rawPayload) {
    List<WorkflowTriggerEvent> events = connector.onEvent(instance, rawPayload);
    for (WorkflowTriggerEvent event : events) {
        UUID dossierId = resolveOrCreateDossier(instance, event); // jamais de Flowable ici
        outboxRepository.save(OutboxEventEntity.pending(
            TriggerOutboxPayload.from(dossierId, instance, event)));
    }
    // Transaction commit → ACK webhook. Dossier(s) + outbox atomiques.
}
```

**Phase relais** — démarre Flowable, corrige les défauts du pseudo-code initial :

```java
@Scheduled(fixedDelay = 1000)
public void relayPendingEvents() {
    // Mono-tenant par construction (décision n° 13).
    // FOR UPDATE SKIP LOCKED seulement si le tenant est scalé sur plusieurs répliques.
    for (OutboxEventEntity entry : outboxRepository.lockNextPending(batchSize)) {
        processOne(entry);
    }
}

@Transactional
void processOne(OutboxEventEntity entry) {
    TriggerOutboxPayload payload = entry.toPayload();
    try {
        DossierWorkflowLink link = attachWorkflowUseCase
            .reserveOrResume(payload.dossierId(), payload.processKey(), payload.triggerRef());
        // reserveOrResume : si un lien RESERVED existe déjà pour ce dossier+process (rejeu),
        // le reprend ; si ACTIVE avec processInstanceId, no-op → markProcessed ;
        // si un AUTRE workflow est ACTIVE → WorkflowAlreadyRunningException → dead-letter.

        if (link.processInstanceId() == null) {
            ProcessInstance pi = runtimeService.createProcessInstanceBuilder()
                .processDefinitionKey(payload.processKey())
                .tenantId(tenantProperties.tenantId())
                .variable("dossierId", payload.dossierId().toString())
                .variable("dossierWorkflowLinkId", link.id().toString())
                .variables(payload.variables())
                .start();
            attachWorkflowUseCase.confirmStarted(link.id(), pi.getId());
        }

        outboxRepository.markProcessed(entry.getEventId()); // uniquement après succès
    } catch (WorkflowAlreadyRunningException ex) {
        outboxRepository.markDead(entry.getEventId(), ex.getMessage());
    } catch (Exception ex) {
        outboxRepository.recordFailure(entry.getEventId(), ex.getMessage());
        // attempts++ ; si attempts >= maxAttempts → DEAD, sinon reste PENDING pour retry
    }
}
```

Politiques actées : **retry** avec plafond d'`attempts` ; **dead-letter** (`status = DEAD`) pour les
échecs définitifs et les collisions de workflow actif ; **purge** des `PROCESSED` au-delà d'une
rétention configurable (job périodique). Le bus `ApplicationEventPublisher` n'est **plus** le
vecteur de durabilité des triggers — il reste disponible pour les événements domaine post-démarrage
(`WorkflowEvent`, décision n° 6).

Cette solution ne nécessite aucune nouvelle brique d'infrastructure (la table outbox vit dans
PostgreSQL, déjà présent). **Chemin d'évolution documenté** : si le volume ou le besoin de diffusion
multi-consommateurs augmente, le relais de polling peut être remplacé par une capture Debezium/CDC
sur `outbox_event`, publiant vers un broker externe (Kafka) sans changer le contrat d'écriture côté
producteur.

> **Mise à jour — décision n° 13 ([02-multi-tenant.md](02-multi-tenant.md)).** Avec une instance
> dédiée par tenant, le relais n'interroge jamais que le schéma de cette instance — nativement
> mono-tenant. `FOR UPDATE SKIP LOCKED` ne redevient utile que si ce tenant est scalé sur plusieurs
> répliques (voir « Scaling horizontal d'un tenant » dans [02-multi-tenant.md](02-multi-tenant.md)).

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
// domain/workflow/model — pur Java, aucune annotation JPA (décision n° 17,
// voir 01-architecture-hexagonale.md) — record, accesseurs sans préfixe (.id(), .tenantId()…)
@Builder
public record WorkflowTypeConfig(
    UUID                           id,
    String                         tenantId,
    String                         workflowKey,   // = processDefinitionKey BPMN
    String                         label,
    List<WorkflowStatusDefinition> statusDefinitions
) {}

@Value @Builder
public class WorkflowStatusDefinition {
    String        code;      // valeur métier : "EN_INSTRUCTION", "EN_ATTENTE_EXPERT"…
    String        label;     // libellé UI
    boolean       initial;   // statut posé automatiquement à la création du DossierWorkflowLink
    StatusOutcome outcome;   // NONE (intermédiaire) ou déclencheur de fin de l'exécution
}

public enum StatusOutcome { NONE, COMPLETED, CANCELLED, FAILED }
```

```java
// infrastructure/persistence/workflow — entité de persistance, jamais exposée au domaine
@Entity
@Table(name = "workflow_type_config")
public class WorkflowTypeConfigJpaEntity {

    @Id @GeneratedValue
    private UUID id;

    private String tenantId;
    private String workflowKey;
    private String label;

    @Convert(converter = JsonListConverter.class)
    @Column(columnDefinition = "jsonb")
    private List<WorkflowStatusDefinition> statusDefinitions;

    // conversion assurée par WorkflowTypeConfigJpaAdapter — même principe que
    // DossierJpaAdapter, voir 04-dossiers.md
}
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

## Scaling horizontal et scheduler Flowable Community

**Problème identifié en revue :** Flowable Community gère les jobs asynchrones et le scheduler via une table de verrouillage en base — le risque de double exécution des jobs semblait imposer une contrainte forte de déploiement dès lors que plusieurs instances de l'application pouvaient exister.

**Décision retenue (mise à jour par la décision n° 13, [02-multi-tenant.md](02-multi-tenant.md)).**
Avec une instance Spring Boot dédiée par tenant, un moteur Flowable donné ne gère jamais que les
jobs de son propre tenant : aucune contention n'est possible **entre tenants**, puisqu'aucune table
de jobs n'est jamais partagée entre deux instances de tenants différents (chaque schéma a ses
propres tables `ACT_RU_JOB` / `ACT_RU_TIMER_JOB`). Ce n'est donc plus, par défaut, un sujet à
trancher au niveau de la plateforme.

```yaml
flowable:
  async-executor-activate: true
  process:
    async:
      lock-time: 5m
      default-async-job-acquire-wait-time: 10s
      default-timer-job-acquire-wait-time: 10s
```

**Si un tenant donné est scalé sur plusieurs répliques** (voir « Scaling horizontal d'un tenant » dans
[02-multi-tenant.md](02-multi-tenant.md)), le job executor de Flowable Community gère nativement le
verrouillage optimiste au niveau ligne (`lockOwner`, `lockExpirationTime`) entre les répliques de ce
tenant — plusieurs moteurs Flowable pointant vers le même schéma peuvent cohabiter sans double
exécution, à condition que les paramètres de verrouillage soient dimensionnés pour la charge de ce
tenant. Le dimensionnement de `lockTimeInMillis` et des délais d'acquisition est alors validé par un
test de charge, pour ce tenant.

**Si la contention devient un problème mesuré** dans le pool de répliques d'un tenant scalé, la
stratégie opérationnelle de repli reste une **séparation des rôles**, appliquée à ce seul tenant :

- répliques **API** : `asyncExecutorActivate: false` — ne participent pas à l'acquisition de jobs, uniquement au trafic REST/BFF, librement réplicables ;
- répliques **worker** : `asyncExecutorActivate: true` — exécutent les jobs asynchrones et le scheduler, elles-mêmes réplicables pour la haute disponibilité (le verrouillage optimiste natif de Flowable reste la garantie contre la double exécution entre workers).

Cette séparation permet de scaler indépendamment la capacité API et la capacité de traitement des
jobs **d'un tenant donné**, sans jamais concerner les autres tenants.

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
