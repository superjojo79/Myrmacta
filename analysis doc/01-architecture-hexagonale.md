# Architecture hexagonale — Ports & Adapters

> Fondation structurelle de l'application. Toutes les autres fonctionnalités
> s'inscrivent dans ce modèle.

---

### Principe

Le domaine métier est isolé de toute technologie. Il ne connaît ni Spring, ni JPA, ni Flowable, ni HTTP. Les frameworks et librairies sont des détails d'implémentation branchés sur des **ports** via des **adaptateurs**.

La règle de dépendance est stricte et unidirectionnelle — tout pointe vers le domaine, jamais l'inverse :

```
[Adaptateurs entrants]  →  [Ports entrants]  →  [Domaine]  →  [Ports sortants]  ←  [Adaptateurs sortants]

Exemples entrants :            Exemples sortants :
  REST Controller                WorkflowRepository
  ConnectorTriggerRuntime        PdfRenderer
  Flowable ServiceTask           NotificationPort
  Scheduler                      ConnectorExecutionPort
```

### Structure de packages

```
com.monapp
│
├── domain                          ← zéro dépendance framework
│   │
│   ├── workflow
│   │   ├── model                   # WorkflowInstance, WorkflowStatus, Task…
│   │   ├── usecase                 # StartWorkflowUseCase, CompleteTaskUseCase…
│   │   └── port
│   │       ├── in                  # interfaces appelées par les adaptateurs entrants
│   │       │   ├── StartWorkflowUseCase
│   │       │   └── CompleteTaskUseCase
│   │       └── out                 # interfaces implémentées par les adaptateurs sortants
│   │           ├── WorkflowRepository
│   │           └── WorkflowEventPublisher
│   │
│   ├── dossier                     # entité métier centrale — voir 04-dossiers.md
│   │   ├── model                   # Dossier, DossierType, DossierElement, DossierRelation…
│   │   ├── usecase                 # CreateDossierUseCase, UpdateDossierStatusUseCase…
│   │   └── port
│   │       ├── in
│   │       │   ├── CreateDossierUseCase
│   │       │   └── UpdateDossierStatusUseCase
│   │       └── out
│   │           ├── DossierRepository
│   │           └── DossierReferenceGenerator
│   │
│   ├── document
│   │   ├── model                   # DocumentTemplate, GeneratedDocument, TemplateStatus…
│   │   ├── usecase                 # GenerateDocumentUseCase, PublishTemplateUseCase…
│   │   └── port
│   │       ├── in
│   │       │   ├── GenerateDocumentUseCase
│   │       │   └── PublishTemplateUseCase
│   │       └── out
│   │           ├── DocumentTemplateRepository
│   │           ├── PdfRenderer             ← Flying Saucer ou Playwright
│   │           └── DocumentStoragePort     ← MinIO ou filesystem
│   │
│   ├── connector
│   │   ├── model                   # ConnectorInstance, ConnectorDefinition, ConfigField…
│   │   ├── usecase                 # RegisterConnectorUseCase, ExecuteConnectorUseCase…
│   │   └── port
│   │       ├── in
│   │       │   ├── ExecuteConnectorUseCase
│   │       │   └── RegisterConnectorInstanceUseCase
│   │       └── out
│   │           ├── ConnectorInstanceRepository
│   │           └── ConnectorDefinitionRegistry
│   │
│   └── notification
│       ├── model                   # NotificationEvent, NotificationChannel…
│       ├── usecase                 # SendNotificationUseCase
│       └── port
│           ├── in
│           │   └── SendNotificationUseCase
│           └── out
│               ├── EmailPort
│               ├── WebhookPort
│               └── BrowserPushPort
│
└── infrastructure                  ← dépend du domaine, jamais l'inverse
    │
    ├── web                         # Adaptateurs entrants — Spring MVC REST
    │   ├── workflow
    │   ├── document
    │   ├── connector
    │   └── notification
    │
    ├── persistence                 # Adaptateurs sortants — Spring Data JPA
    │   ├── workflow                # WorkflowJpaAdapter implements WorkflowRepository
    │   ├── dossier                 # DossierJpaAdapter, SequentialReferenceGenerator
    │   ├── document                # DocumentTemplateJpaAdapter
    │   └── connector               # ConnectorInstanceJpaAdapter
    │
    ├── flowable                    # Adaptateur Flowable
    │   ├── FlowableWorkflowAdapter # implements WorkflowEventPublisher
    │   ├── ConnectorServiceTask    # JavaDelegate → ConnectorExecutionPort
    │   └── ConnectorTriggerRuntime # écoute Flowable → publie sur le domaine
    │
    ├── pdf                         # Adaptateurs sortants — rendu PDF
    │   ├── FlyingSaucerPdfAdapter  # implements PdfRenderer (défaut)
    │   └── PlaywrightPdfAdapter    # implements PdfRenderer (repli CSS3)
    │
    ├── storage                     # Adaptateurs sortants — stockage fichiers
    │   ├── MinioStorageAdapter     # implements DocumentStoragePort
    │   └── FilesystemStorageAdapter
    │
    ├── notification                # Adaptateurs sortants — canaux de notification
    │   ├── SmtpEmailAdapter        # implements EmailPort
    │   ├── WebhookRestAdapter      # implements WebhookPort
    │   └── SseNotificationAdapter  # implements BrowserPushPort
    │
    └── connector                   # Infrastructure connecteurs
        ├── registry                # ConnectorRegistry — découverte classpath
        └── builtin                 # Connecteurs fournis (http-rest, smtp, s3…)
```

### Règles d'implémentation

**Le domaine est pur Java.** Aucune annotation Spring (`@Component`, `@Service`, `@Repository`), aucune annotation JPA (`@Entity`, `@Column`), aucune dépendance Flowable dans `com.monapp.domain`.

**Les entités JPA sont distinctes des entités domaine.** Une entité domaine `WorkflowInstance` a son équivalent `WorkflowJpaEntity` dans `infrastructure.persistence`. L'adaptateur JPA assure la conversion.

```java
// domain/workflow/model — pur Java
public class WorkflowInstance {
    private UUID   id;
    private String processKey;
    private String tenantId;
    private WorkflowStatus status;

    public void complete() {
        if (status != WorkflowStatus.RUNNING)
            throw new InvalidStateException("Workflow non actif");
        this.status = WorkflowStatus.COMPLETED;
    }
}

// domain/workflow/port/out — interface définie par le domaine
public interface WorkflowRepository {
    void save(WorkflowInstance instance);
    Optional<WorkflowInstance> findById(UUID id);
}

// infrastructure/persistence/workflow — implémentation JPA
@Repository
@RequiredArgsConstructor
public class WorkflowJpaAdapter implements WorkflowRepository {
    private final WorkflowJpaRepository jpa;

    @Override
    public void save(WorkflowInstance instance) {
        jpa.save(WorkflowJpaEntity.from(instance));
    }

    @Override
    public Optional<WorkflowInstance> findById(UUID id) {
        return jpa.findById(id).map(WorkflowJpaEntity::toDomain);
    }
}
```

**Les use cases sont des services domaine.** Ils reçoivent leurs dépendances (ports sortants) par injection de constructeur. Spring câble l'implémentation concrète sans que le use case le sache.

```java
// domain/workflow/usecase — pur Java, pas @Service Spring
public class StartWorkflowUseCaseImpl implements StartWorkflowUseCase {

    private final WorkflowRepository      repository;    // port sortant
    private final WorkflowEventPublisher  publisher;     // port sortant

    public StartWorkflowUseCaseImpl(WorkflowRepository repository,
                                    WorkflowEventPublisher publisher) {
        this.repository = repository;
        this.publisher  = publisher;
    }

    @Override
    public WorkflowInstance start(StartWorkflowCommand cmd) {
        WorkflowInstance instance = WorkflowInstance.create(cmd);
        repository.save(instance);
        publisher.publish(new WorkflowStartedEvent(instance));
        return instance;
    }
}
```

**Les connecteurs sont des adaptateurs sortants.** `ConnectorDefinition` / `TriggerConnector` / `ServiceConnector` sont des ports sortants du domaine `connector`. Chaque connecteur (D365, HTTP REST, SMTP…) est un adaptateur dans `infrastructure.connector.builtin`.

### Bénéfices concrets pour ce projet

**Testabilité totale du domaine** — les use cases sont testés sans Spring, sans base, sans Flowable. Les ports sortants sont mockés.

```java
@Test
void should_complete_workflow() {
    // Arrange — mocks des ports, pas de Spring context
    WorkflowRepository  repo      = mock(WorkflowRepository.class);
    WorkflowEventPublisher pub    = mock(WorkflowEventPublisher.class);
    StartWorkflowUseCase useCase  =
        new StartWorkflowUseCaseImpl(repo, pub);

    // Act
    WorkflowInstance instance =
        useCase.start(new StartWorkflowCommand("mon-processus", "tenant-a"));

    // Assert
    assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
    verify(repo).save(instance);
}
```

**Remplacement du moteur PDF transparent** — `FlyingSaucerPdfAdapter` et `PlaywrightPdfAdapter` implémentent tous les deux `PdfRenderer`. Le switch se fait par configuration Spring, le domaine ne change pas.

**Isolation de Flowable** — si Flowable évolue ou est remplacé, seul `infrastructure.flowable` est impacté. Le domaine métier est protégé.

---
