# Dossiers

> Entité métier centrale de l'application. Tout workflow opère sur un dossier.
> Un dossier est créé à partir d'un trigger, suit le workflow associé à son type,
> et agrège l'ensemble des éléments produits ou collectés durant son traitement.

---

## Rôle dans l'architecture

```
Trigger (Connecteur)
    │
    ▼
Création dossier(s)           ← CreateDossierUseCase / CreateDossierBatchUseCase
    │
    ▼
Démarrage instance Flowable   ← variable "dossierId" injectée dans le processus
    │
    ├── ServiceTask → lit / modifie le dossier
    ├── ServiceTask → ajoute un DossierElement (document produit, note…)
    ├── ServiceTask → met à jour le statut du dossier
    └── ServiceTask → génère un document et l'attache au dossier
    │
    ▼
Clôture du dossier            ← statut CLOTURE / ARCHIVE
```

---

## Modèle de données

### `DossierType` — schéma dynamique par tenant

Chaque tenant configure ses propres types de dossiers. Le schéma des champs
dynamiques est déclaré dans `fieldDefinitions` — l'UI génère les formulaires
de saisie à partir de ce schéma, sans code supplémentaire.

```java
@Entity
public class DossierType {

    @Id @GeneratedValue
    private UUID id;

    private String  tenantId;
    private String  code;         // "SINISTRE", "DEMANDE_SUBVENTION", "RECLAMATION"
    private String  label;
    private String  workflowKey;  // clé BPMN à instancier pour ce type
    private boolean active;
    private boolean allowUnknownFields; // false par défaut — voir "Validation des champs dynamiques"

    @Convert(converter = JsonListConverter.class)
    @Column(columnDefinition = "jsonb")
    private List<FieldDefinition> fieldDefinitions;
}

@Value @Builder
public class FieldDefinition {
    String       name;         // clé technique
    String       label;        // libellé UI
    FieldType    type;         // STRING, NUMBER, DATE, BOOLEAN, SELECT, FILE
    boolean      required;
    String       defaultValue;
    List<String> options;      // pour type SELECT
    String       group;        // regroupement visuel dans l'UI
}
```

### `Dossier` — entité centrale

```java
@Entity
public class Dossier {

    @Id @GeneratedValue
    private UUID id;

    private String tenantId;

    // ── Signalétique ──────────────────────────────────────────────────────────
    private String reference;        // numéro unique généré : "DOS-2024-00042"
    private String dossierTypeCode;  // référence vers DossierType
    private String label;            // libellé libre

    @Enumerated(EnumType.STRING)
    private DossierStatus status;    // DRAFT → OUVERT → EN_COURS → CLOTURE → ARCHIVE

    // ── Champs dynamiques ─────────────────────────────────────────────────────
    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> fields;  // valeurs des FieldDefinition du DossierType

    // ── Lien Flowable ─────────────────────────────────────────────────────────
    private String processInstanceId;    // instance Flowable associée

    // ── Audit ─────────────────────────────────────────────────────────────────
    private String  createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private String  closedBy;
    private Instant closedAt;
}
```

### `DossierElement` — pièces constitutives

Un dossier agrège des éléments de types hétérogènes produits ou collectés
au fil du workflow.

```java
@Entity
public class DossierElement {

    @Id @GeneratedValue
    private UUID id;

    private UUID   dossierId;
    private String tenantId;

    @Enumerated(EnumType.STRING)
    private ElementType type;
    // ATTACHMENT    — pièce jointe déposée par un utilisateur
    // PRODUCED_DOC  — document généré par un ServiceTask
    // INFORMATION   — bloc de données structurées
    // NOTE          — commentaire libre
    // LINK          — référence vers une ressource externe

    private String label;
    private String storageRef;      // référence MinIO pour ATTACHMENT / PRODUCED_DOC

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;   // données spécifiques selon le type

    private String  addedBy;
    private Instant addedAt;
    private String  workflowStepId; // étape Flowable productrice (nullable)
}
```

### `DossierRelation` — liens entre dossiers

```java
@Entity
public class DossierRelation {

    @Id @GeneratedValue
    private UUID id;

    private UUID   sourceDossierId;
    private UUID   targetDossierId;
    private String tenantId;

    @Enumerated(EnumType.STRING)
    private RelationType type;
    // PARENT_CHILD  — hiérarchie parent / enfant
    // SUBSEQUENT    — dossier subséquent (ex: demande → recours)
    // LINKED        — lien simple sans sémantique forte
    // DUPLICATE     — doublon identifié
    // MERGED_INTO   — dossier fusionné dans un autre

    private String  createdBy;
    private Instant createdAt;
}
```

---

## Cycle de vie du statut

Le statut est une propriété du dossier, pilotée par le workflow via
un `ServiceTask` dédié. Il est indépendant de l'état interne de Flowable.

```
DRAFT → OUVERT → EN_COURS → SUSPENDU → EN_COURS → CLOTURE → ARCHIVE
                     │                                 ↑
                     └─────────── annulation ──────────┘
```

| Statut | Déclencheur |
|---|---|
| `DRAFT` | Création initiale (batch, import) |
| `OUVERT` | Soumission / démarrage du workflow |
| `EN_COURS` | Première tâche prise en charge |
| `SUSPENDU` | Attente d'élément externe (UserTask bloquante) |
| `CLOTURE` | Fin normale du workflow |
| `ARCHIVE` | Archivage post-clôture |

```java
@Component("updateDossierStatusTask")
@RequiredArgsConstructor
public class UpdateDossierStatusTask implements JavaDelegate {

    private final UpdateDossierStatusUseCase useCase;

    @Override
    public void execute(DelegateExecution exec) {
        UUID          dossierId = UUID.fromString(
            (String) exec.getVariable("dossierId"));
        DossierStatus newStatus = DossierStatus.valueOf(
            (String) exec.getVariable("targetStatus"));

        useCase.update(dossierId, newStatus, exec.getCurrentActivityId());
    }
}
```

---

## Numérotation — `DossierReferenceGenerator`

La référence unique est un port sortant — l'implémentation est configurable
par tenant (séquentiel, préfixe par type, année…).

```java
// domain/dossier/port/out
public interface DossierReferenceGenerator {
    String generate(String tenantId, String dossierTypeCode);
}

// Implémentation par défaut — séquentiel avec préfixe
// infrastructure/persistence/dossier
@Component
public class SequentialReferenceGenerator implements DossierReferenceGenerator {

    @Override
    public String generate(String tenantId, String dossierTypeCode) {
        // ex: "DOS-SINISTRE-2024-000042"
        long seq = nextSequence(tenantId, dossierTypeCode);
        return String.format("%s-%s-%tY-%06d",
            "DOS", dossierTypeCode,
            Instant.now(), seq);
    }
}
```

---

## Validation des champs dynamiques

**Problème identifié en revue :** `Dossier.fields` (`Map<String, Object>`) n'était validé nulle part contre le schéma `FieldDefinition` du `DossierType` associé, ni à la création ni à la mise à jour.

**Décision retenue — validation aux deux niveaux :**

- **Couche REST** — validation immédiate pour retourner une réponse `400` avec le détail des erreurs par champ, à des fins d'UX (formulaire de saisie généré dynamiquement depuis `fieldDefinitions`).
- **Use case (`CreateDossierUseCase`, `CreateDossierBatchUseCase`, `UpdateDossierStatusUseCase` le cas échéant)** — validation faisant autorité, car les créations issues d'un trigger ou d'un import batch ne passent jamais par la couche REST.

Un service domaine pur `DossierFieldValidator` (aucune dépendance framework) implémente la règle commune :

```java
// domain/dossier/model — pur Java
public class DossierFieldValidator {

    public List<FieldValidationError> validate(List<FieldDefinition> definitions,
            Map<String, Object> values, boolean allowUnknownFields) {

        List<FieldValidationError> errors = new ArrayList<>();

        for (FieldDefinition def : definitions) {
            Object value = values.get(def.getName());
            if (def.isRequired() && isBlank(value)) {
                errors.add(FieldValidationError.required(def.getName()));
                continue;
            }
            if (value != null && !matchesType(value, def.getType())) {
                errors.add(FieldValidationError.typeMismatch(def.getName(), def.getType()));
            }
            if (def.getType() == FieldType.SELECT && value != null
                    && !def.getOptions().contains(value)) {
                errors.add(FieldValidationError.invalidOption(def.getName(), def.getOptions()));
            }
        }

        if (!allowUnknownFields) {
            Set<String> knownNames = definitions.stream()
                .map(FieldDefinition::getName).collect(toSet());
            values.keySet().stream()
                .filter(name -> !knownNames.contains(name))
                .forEach(name -> errors.add(FieldValidationError.unknownField(name)));
        }

        return errors;
    }
}
```

`DossierType.allowUnknownFields` (nouveau champ booléen, `false` par défaut) permet à un tenant d'autoriser explicitement des champs hors schéma — cas de compatibilité ascendante volontaire lors de l'évolution d'un type de dossier sans migration immédiate des intégrations amont.

```java
public class CreateDossierUseCaseImpl implements CreateDossierUseCase {

    @Override
    public Dossier create(CreateDossierCommand cmd) {
        DossierType type = dossierTypeRepository
            .findByCode(cmd.tenantId(), cmd.dossierTypeCode())
            .orElseThrow();

        List<FieldValidationError> errors = fieldValidator.validate(
            type.getFieldDefinitions(), cmd.fields(), type.isAllowUnknownFields());

        if (!errors.isEmpty()) {
            throw new DossierFieldValidationException(errors);
        }

        // ... création du dossier
    }
}
```

La couche REST intercepte `DossierFieldValidationException` pour produire le `400` détaillé ; la création par trigger/batch (`ConnectorTriggerRuntime`) laisse l'exception se propager — un enregistrement invalide au sein d'un batch est journalisé et écarté sans bloquer les autres.

---

## Création depuis un trigger — unitaire et batch

Le `ConnectorTriggerRuntime` supporte la création batch — un seul événement
entrant peut produire N dossiers (ex: import de fichier CSV).

```java
// TriggerConnector retourne une liste — 1 événement = 1 dossier
public interface TriggerConnector {
    List<WorkflowTriggerEvent> onEvent(ConnectorInstance instance,
                                       Map<String, Object> rawPayload);
}

// ConnectorTriggerRuntime — itération et création
events.forEach(event -> {

    // 1. Création du dossier
    Dossier dossier = createDossierUseCase.create(
        CreateDossierCommand.builder()
            .tenantId       (instance.getTenantId())
            .dossierTypeCode(event.getDossierTypeCode())
            .fields         (event.getDossierFields())
            .createdBy      ("trigger:" + instance.getId())
            .build());

    // 2. Démarrage de l'instance Flowable
    ProcessInstance pi = runtimeService
        .createProcessInstanceBuilder()
        .processDefinitionKey(event.getProcessKey())
        .tenantId(instance.getTenantId())
        .variable("dossierId", dossier.getId().toString())
        .variables(event.getVariables())
        .start();

    // 3. Lien bidirectionnel dossier ↔ processus
    linkDossierToProcessUseCase.link(dossier.getId(), pi.getId());
});
```

---

## Accès au dossier depuis le workflow

Le `dossierId` est injecté comme variable de processus à la création.
Tous les `ServiceTask` qui opèrent sur le dossier étendent `DossierAwareTask`.

```java
@Component
@RequiredArgsConstructor
public abstract class DossierAwareTask implements JavaDelegate {

    private final GetDossierUseCase getDossier;

    @Override
    public final void execute(DelegateExecution exec) {
        UUID    id      = UUID.fromString((String) exec.getVariable("dossierId"));
        Dossier dossier = getDossier.get(id);
        executeWithDossier(exec, dossier);
    }

    protected abstract void executeWithDossier(DelegateExecution exec,
                                                Dossier dossier);
}

// Exemple — ServiceTask qui ajoute un document produit au dossier
@Component("attachGeneratedDocumentTask")
@RequiredArgsConstructor
public class AttachGeneratedDocumentTask extends DossierAwareTask {

    private final AddDossierElementUseCase addElement;

    @Override
    protected void executeWithDossier(DelegateExecution exec, Dossier dossier) {
        String storageRef = (String) exec.getVariable("generatedDocumentId");

        addElement.add(AddDossierElementCommand.builder()
            .dossierId     (dossier.getId())
            .type          (ElementType.PRODUCED_DOC)
            .label         ("Document généré — " + exec.getCurrentActivityName())
            .storageRef    (storageRef)
            .workflowStepId(exec.getCurrentActivityId())
            .addedBy       ("workflow")
            .build());
    }
}
```

---

## Impact sur la visualisation

Le composant `WorkflowViewer` (voir
[10-visualisation-workflow.md](10-visualisation-workflow.md)) affiche
le dossier en regard du diagramme BPMN — signalétique, statut, éléments
et relations visibles en panneau latéral.

```
┌─────────────────────────────┬──────────────────────────────────────┐
│  Diagramme BPMN             │  Dossier DOS-SINISTRE-2024-000042    │
│  (BPMN.js)                  │  ─────────────────────────────────── │
│                             │  Statut : EN_COURS                   │
│  [Réception] ──●──          │  Type   : Sinistre                   │
│  [Instruction] ───          │  Créé   : 2024-03-01                 │
│  [Décision]                 │                                      │
│  [Clôture]                  │  Éléments (3)                        │
│                             │  📎 Formulaire de déclaration        │
│                             │  📄 Rapport d'expertise              │
│                             │  📝 Note du gestionnaire             │
│                             │                                      │
│                             │  Dossiers liés (1)                   │
│                             │  → DOS-RECOURS-2024-000007           │
└─────────────────────────────┴──────────────────────────────────────┘
```

---

## Structure de packages

**Décision retenue — convention `usecase/` vs `port/in/` :** `port/in/` contient exclusivement les interfaces exposées aux adaptateurs entrants ; `usecase/` contient leurs implémentations, suffixées `Impl`. Cette convention (déjà énoncée dans [01-architecture-hexagonale.md](01-architecture-hexagonale.md)) est appliquée ici pour corriger la duplication de noms précédemment listée dans les deux dossiers, et s'applique uniformément à tous les modules du domaine.

```
domain
└── dossier
    ├── model
    │   ├── Dossier
    │   ├── DossierType
    │   ├── DossierElement
    │   ├── DossierRelation
    │   ├── DossierStatus
    │   ├── ElementType
    │   ├── RelationType
    │   ├── FieldDefinition
    │   └── DossierFieldValidator
    ├── usecase
    │   ├── CreateDossierUseCaseImpl
    │   ├── CreateDossierBatchUseCaseImpl
    │   ├── GetDossierUseCaseImpl
    │   ├── UpdateDossierStatusUseCaseImpl
    │   ├── AddDossierElementUseCaseImpl
    │   ├── LinkDossiersUseCaseImpl
    │   └── LinkDossierToProcessUseCaseImpl
    └── port
        ├── in
        │   ├── CreateDossierUseCase
        │   ├── CreateDossierBatchUseCase
        │   ├── GetDossierUseCase
        │   ├── UpdateDossierStatusUseCase
        │   ├── AddDossierElementUseCase
        │   └── LinkDossiersUseCase
        └── out
            ├── DossierRepository
            ├── DossierTypeRepository
            ├── DossierElementRepository
            ├── DossierRelationRepository
            └── DossierReferenceGenerator

infrastructure
└── persistence
    └── dossier
        ├── DossierJpaAdapter
        ├── DossierTypeJpaAdapter
        ├── DossierElementJpaAdapter
        ├── DossierRelationJpaAdapter
        └── SequentialReferenceGenerator
```
