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
be.myrmacta
│
├── domain                          ← zéro dépendance framework
│   │
│   ├── workflow
│   │   ├── model                   # WorkflowInstance, WorkflowStatus, Task,
│   │   │                           # WorkflowTypeConfig (record, décision n° 17), WorkflowStatusDefinition, StatusOutcome…
│   │   ├── usecase                 # StartWorkflowUseCase, CompleteTaskUseCase…
│   │   └── port
│   │       ├── in                  # interfaces appelées par les adaptateurs entrants
│   │       │   ├── StartWorkflowUseCase
│   │       │   └── CompleteTaskUseCase
│   │       └── out                 # interfaces implémentées par les adaptateurs sortants
│   │           ├── WorkflowRepository
│   │           ├── WorkflowEventPublisher
│   │           └── WorkflowTypeConfigRepository  ← nouveau — statuts métier configurables par workflow
│   │
│   ├── dossier                     # entité métier centrale — voir 04-dossiers.md
│   │   ├── model                   # Dossier, DossierType, DossierElement, DossierRelation,
│   │   │                           # DossierWorkflowLink… — tous des record depuis la décision n° 17
│   │   ├── usecase                 # CreateDossierUseCase, UpdateDossierStatusUseCase,
│   │   │                           # AttachWorkflowUseCase, UpdateWorkflowLinkStatusUseCase…
│   │   └── port
│   │       ├── in
│   │       │   ├── CreateDossierUseCase
│   │       │   ├── UpdateDossierStatusUseCase
│   │       │   ├── AttachWorkflowUseCase             ← nouveau
│   │       │   ├── UpdateWorkflowLinkStatusUseCase   ← nouveau
│   │       │   └── GetDossierWorkflowHistoryUseCase  ← nouveau
│   │       └── out
│   │           ├── DossierRepository
│   │           ├── DossierWorkflowLinkRepository     ← nouveau
│   │           └── DossierReferenceGenerator
│   │
│   ├── document
│   │   ├── model                   # DocumentTemplate (record, décision n° 17), GeneratedDocument, TemplateStatus…
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
│   │   ├── model                   # ConnectorInstanceData (record), ConnectorDefinition, ConfigField…
│   │   ├── usecase                 # RegisterConnectorUseCaseImpl, ExecuteConnectorUseCaseImpl…
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
    │   ├── workflow                # WorkflowJpaEntity, WorkflowTypeConfigJpaEntity, *JpaAdapter
    │   ├── dossier                 # Dossier*JpaEntity, Dossier*JpaAdapter, SequentialReferenceGenerator
    │   ├── document                # DocumentTemplateJpaEntity, DocumentTemplateJpaAdapter
    │   └── connector               # ConnectorInstanceJpaEntity, ConnectorInstanceJpaAdapter
    │
    ├── flowable                    # Adaptateur Flowable
    │   ├── FlowableWorkflowAdapter # implements WorkflowEventPublisher
    │   ├── ConnectorServiceTask    # JavaDelegate → ConnectorExecutionPort
    │   └── OutboxRelay             # polling outbox_event → démarre Flowable (décision n° 16)
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
        ├── ConnectorTriggerRuntime # producteur : dossier + outbox (décision n° 16)
        └── builtin                 # Connecteurs fournis (http-rest, smtp, s3…)
```

### Règles d'implémentation

**Le domaine est pur Java.** Aucune annotation Spring (`@Component`, `@Service`, `@Repository`), aucune annotation JPA (`@Entity`, `@Column`), aucune dépendance Flowable dans `be.myrmacta.domain`.

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

### Règle « entités JPA distinctes des entités domaine » — appliquée à tout le domaine (décision n° 17)

La décision n° 5 avait appliqué cette règle à un seul agrégat (`ConnectorInstance` →
`ConnectorInstanceData` / `ConnectorInstanceJpaEntity`) sans la généraliser. La décision n° 17
l'étend à toutes les entités du domaine — `Dossier`, `DossierType`, `DossierElement`,
`DossierRelation`, `DossierWorkflowLink` ([04-dossiers.md](04-dossiers.md)),
`WorkflowTypeConfig` ([05-workflow-engine.md](05-workflow-engine.md)) et `DocumentTemplate`
([08-templates-documents.md](08-templates-documents.md)) — sans exception, pour éviter de
reproduire une règle énoncée une fois puis appliquée à géométrie variable.

**Convention de nommage.** Le modèle domaine garde son nom métier naturel (`Dossier`,
`DossierType`…) ; c'est l'entité de persistance qui reçoit le suffixe `JpaEntity`
(`DossierJpaEntity`, `DossierTypeJpaEntity`…) — le même principe que `WorkflowInstance` /
`WorkflowJpaEntity` ci-dessus. `ConnectorInstanceData` / `ConnectorInstanceJpaEntity` reste une
exception de nommage historique de la décision n° 5 (le nom `ConnectorInstance` était déjà pris par
l'ancienne classe fautive) — non renommée a posteriori, ce serait du churn sans bénéfice sur le nom
lui-même (sa **forme** de déclaration, en revanche, suit désormais la même convention que le reste —
voir ci-dessous).

**Le modèle domaine est un `record` Java.** Un `record` a d'abord été écarté au profit de
`@Value @Builder` (Lombok) au motif que le premier génère des accesseurs sans préfixe
(`allowUnknownFields()`, non `isAllowUnknownFields()`), incompatibles avec des points d'appel déjà
rédigés ailleurs dans la documentation. Cet argument ne tient pas : le projet est encore en phase
d'analyse, sans aucune implémentation — il n'existe pas de code réel dont les appelants seraient
figés. Les extraits illustratifs de `docs/specs/` peuvent être mis à jour au même titre que
n'importe quel autre passage de cette documentation ; leur cohérence s'entretient par l'édition, pas
en évitant la meilleure convention pour ne pas avoir à les retoucher. Une fois cette contrainte
artificielle levée, `record` redevient le choix le mieux justifié : fonctionnalité native du JDK,
sans dépendance à un processeur d'annotations pour le modèle domaine ; immuabilité garantie par le
langage ; cohérent avec l'appétit déjà montré pour Java 21 moderne (`sealed interface` de la décision
n° 6) ; et déjà utilisé ailleurs dans ces mêmes documents (`WorkflowDiagramView` dans
[10-visualisation-workflow.md](10-visualisation-workflow.md)) — ce n'est pas une convention étrangère
au projet, seulement pas encore appliquée aux agrégats.

`@Builder` (Lombok) reste utilisé au-dessus des `record` pour la construction des objets à de
nombreux champs — un `record` conserve son constructeur canonique quoi qu'il arrive, `@Builder`
n'ajoute qu'une syntaxe de construction alternative, sans renoncer aux accesseurs natifs du `record`
ni à son immuabilité. Une transition d'état (`Dossier.withStatus(...)`) s'implémente comme une
méthode d'instance ordinaire qui retourne une nouvelle instance via le constructeur canonique — voir
l'exemple sur `Dossier` dans [04-dossiers.md](04-dossiers.md). `ConnectorInstanceData` a été alignée
sur cette même convention par cohérence — laisser sa forme de déclaration seule diverger aurait
recréé exactement le type d'exception isolée que cette décision vise à éliminer.

Les entités JPA elles-mêmes ne peuvent de toute façon pas être des `record` (Hibernate a besoin d'un
constructeur sans argument et de champs mutables pour le chargement paresseux et le rehaussement de
bytecode) — seul le côté domaine est concerné par ce choix.

**Toujours un DTO à la frontière REST.** Retirer les annotations JPA du modèle domaine élimine le
risque concret (proxy Hibernate sérialisé, `LazyInitializationException`) qui existait tant que ces
classes étaient annotées `@Entity`. Cela ne dispense pas de garder le contrat d'API découplé du
modèle domaine par principe — un renommage de champ domaine ne devrait pas changer silencieusement
la réponse HTTP. C'est le sujet du point 24 (contrat d'API), pas de cette règle.

### Vérification automatique de la règle de dépendance — ArchUnit

La décision n° 9 s'en remettait à la revue de code pour faire respecter la convention `port/in` /
`usecase`, et c'est précisément cette convention qui a dérivé (voir point 17). Plutôt que de
recompter sur la revue humaine pour la règle « domaine pur », elle est vérifiée par un test
exécuté en CI à chaque build. Le projet est en **mono-module Maven** (décision n° 27) : ArchUnit
est la garantie unique de la règle de dépendance — un module `domain` séparé a été écarté comme
redondant à ce stade.

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

```java
@AnalyzeClasses(packages = "be.myrmacta")
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domain_has_no_framework_dependency = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "org.springframework..", "jakarta.persistence..", "org.flowable..");

    @ArchTest
    static final ArchRule domain_never_depends_on_infrastructure = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule usecase_impl_naming_convention = classes()
        .that().resideInAPackage("..usecase..")
        .and().areNotInterfaces()
        .should().haveSimpleNameEndingWith("Impl");
}
```

Ces trois règles auraient détecté la dérive du point 17 (entités JPA dans `domain.*.model`) et celle
de la décision n° 9 (implémentations de use case mal suffixées) dès leur introduction, plutôt qu'en
revue de conception après coup.

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
