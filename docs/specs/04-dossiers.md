# Dossiers

> Entité métier centrale de l'application. Un dossier est créé à partir d'un
> trigger et peut être manipulé par **plusieurs workflows successifs** au
> cours de sa vie — un seul actif à la fois. Chaque exécution est tracée
> dans un historique complet (`DossierWorkflowLink`), tandis que le dossier
> conserve un statut métier qui lui est propre, indépendant du statut de
> l'exécution en cours. Il agrège l'ensemble des éléments produits ou
> collectés durant son traitement, quel que soit le workflow qui les a
> produits.

---

## Rôle dans l'architecture

```
Trigger (Connecteur)
    │
    ▼
Création dossier               ← CreateDossierUseCase / CreateDossierBatchUseCase
    │
    ▼
Workflow #1 — DossierWorkflowLink #1     ← variables "dossierId" + "dossierWorkflowLinkId"
    ├── ServiceTask → lit / modifie le dossier
    ├── ServiceTask → ajoute un DossierElement (document produit, note…)
    ├── ServiceTask → met à jour le statut du dossier          (Dossier.status)
    ├── ServiceTask → met à jour le statut de l'exécution      (DossierWorkflowLink.status)
    └── ServiceTask → génère un document et l'attache au dossier
    │
    ▼  statut terminal atteint → DossierWorkflowLink #1 clos (endedAt renseigné)
    │
    ▼  nouveau déclenchement — manuel, connecteur ou chaînage (voir plus bas)
Workflow #2 — DossierWorkflowLink #2      ← un seul workflow actif à la fois sur le dossier
    │
    ▼
    …
    │
    ▼
Clôture du dossier             ← Dossier.status = CLOTURE / ARCHIVE, piloté indépendamment
                                   des statuts des workflows qui l'ont manipulé
```

> **Règle de concurrence :** un dossier ne peut avoir qu'**un seul**
> `DossierWorkflowLink` actif (non terminal) à la fois. Un nouveau workflow
> ne peut être démarré sur un dossier que si le précédent est déjà clos —
> voir [Démarrage d'un nouveau workflow sur un dossier existant](#démarrage-dun-nouveau-workflow-sur-un-dossier-existant).

---

## Modèle de données

> **Depuis la décision n° 17** ([registre des décisions](../decisions/017-entites-jpa-dans-le-domaine-regle-appliquee.md)), toutes les classes ci-dessous sont des
> `record` Java — objets domaine purs, aucune annotation JPA, accesseurs sans préfixe (`.id()`,
> `.tenantId()`…). `@Builder` (Lombok) reste utilisé au-dessus du `record` pour la construction à
> de nombreux champs — un `record` conserve son constructeur canonique quoi qu'il arrive, `@Builder`
> n'ajoute qu'une syntaxe de construction alternative. Chacune a son entité de persistance homonyme
> suffixée `JpaEntity` (`DossierJpaEntity`, `DossierTypeJpaEntity`…), confinée à
> `infrastructure.persistence.dossier` et jamais exposée au domaine — voir « Structure de packages »
> en fin de document pour la liste complète et un exemple d'adaptateur assurant la conversion.

### `DossierType` — schéma dynamique par tenant

Chaque tenant configure ses propres types de dossiers. Le schéma des champs
dynamiques est déclaré dans `fieldDefinitions` — l'UI génère les formulaires
de saisie à partir de ce schéma, sans code supplémentaire.

```java
// domain/dossier/model — pur Java, aucune annotation JPA
@Builder
public record DossierType(
    UUID                   id,
    String                 tenantId,
    String                 code,         // "SINISTRE", "DEMANDE_SUBVENTION", "RECLAMATION"
    String                 label,
    String                 workflowKey,  // clé BPMN à instancier pour ce type
    boolean                active,
    boolean                allowUnknownFields, // false par défaut — voir "Validation des champs dynamiques"
    List<FieldDefinition>  fieldDefinitions
) {}

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

```java
// infrastructure/persistence/dossier — entité de persistance, jamais exposée au domaine
@Entity
@Table(name = "dossier_type")
public class DossierTypeJpaEntity {

    @Id @GeneratedValue
    private UUID id;

    private String  tenantId;
    private String  code;
    private String  label;
    private String  workflowKey;
    private boolean active;
    private boolean allowUnknownFields;

    @Convert(converter = JsonListConverter.class)
    @Column(columnDefinition = "jsonb")
    private List<FieldDefinition> fieldDefinitions;

    // getters/setters standards (JPA en a besoin) — conversion assurée par
    // DossierTypeJpaAdapter, même principe que DossierJpaAdapter ci-dessous
}
```

### `Dossier` — entité centrale

```java
// domain/dossier/model — pur Java, aucune annotation JPA (décision n° 17)
@Builder
public record Dossier(
    UUID   id,
    String tenantId,

    // ── Signalétique ──────────────────────────────────────────────────────────
    String reference,        // numéro unique généré : "DOS-2024-00042"
    String dossierTypeCode,  // référence vers DossierType
    String label,            // libellé libre

    DossierStatus status,    // DRAFT → OUVERT → EN_COURS → CLOTURE → ARCHIVE

    // ── Champs dynamiques ─────────────────────────────────────────────────────
    Map<String, Object> fields,  // valeurs des FieldDefinition du DossierType

    // ── Lien Flowable ─────────────────────────────────────────────────────────
    // Un dossier peut être manipulé par plusieurs workflows successifs — voir
    // DossierWorkflowLink ci-dessous, qui porte l'historique complet. Ce champ
    // n'est qu'un pointeur dénormalisé de confort vers le workflow actif ;
    // il vaut null si aucun workflow n'est en cours sur ce dossier.
    UUID currentWorkflowLinkId,  // nullable — DossierWorkflowLink actif

    // ── Audit ─────────────────────────────────────────────────────────────────
    String  createdBy,
    Instant createdAt,
    String  updatedBy,  // auteur de la dernière mutation (décision n° 18)
    Instant updatedAt,
    String  closedBy,   // nullable
    Instant closedAt    // nullable
) {
    /** Transition de statut — un record est immuable, l'use case persiste la copie retournée. */
    public Dossier withStatus(DossierStatus newStatus, String updatedBy, Instant at) {
        return new Dossier(id, tenantId, reference, dossierTypeCode, label,
            newStatus, fields, currentWorkflowLinkId, createdBy, createdAt,
            updatedBy, at, closedBy, closedAt);
    }
}
```

```java
// infrastructure/persistence/dossier — entité de persistance, jamais exposée au domaine
@Entity
@Table(name = "dossier")
public class DossierJpaEntity {

    @Id @GeneratedValue
    private UUID id;

    private String tenantId;
    private String reference;
    private String dossierTypeCode;
    private String label;

    @Enumerated(EnumType.STRING)
    private DossierStatus status;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> fields;

    private UUID currentWorkflowLinkId;

    private String  createdBy;
    private Instant createdAt;
    private String  updatedBy;
    private Instant updatedAt;
    private String  closedBy;
    private Instant closedAt;

    // getters/setters standards (JPA en a besoin) — omis pour la lisibilité
}
```

```java
// infrastructure/persistence/dossier — assure la conversion dans les deux sens
@Repository
@RequiredArgsConstructor
public class DossierJpaAdapter implements DossierRepository {

    private final DossierJpaRepository jpa;

    @Override
    public void save(Dossier dossier) {
        jpa.save(toEntity(dossier));
    }

    @Override
    public Optional<Dossier> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    private Dossier toDomain(DossierJpaEntity e) {
        return Dossier.builder()
            .id(e.getId()).tenantId(e.getTenantId()).reference(e.getReference())
            .dossierTypeCode(e.getDossierTypeCode()).label(e.getLabel())
            .status(e.getStatus()).fields(e.getFields())
            .currentWorkflowLinkId(e.getCurrentWorkflowLinkId())
            .createdBy(e.getCreatedBy()).createdAt(e.getCreatedAt())
            .updatedBy(e.getUpdatedBy()).updatedAt(e.getUpdatedAt())
            .closedBy(e.getClosedBy())
            .closedAt(e.getClosedAt())
            .build();
    }

    private DossierJpaEntity toEntity(Dossier d) {
        DossierJpaEntity e = new DossierJpaEntity();
        e.setId(d.id());
        e.setTenantId(d.tenantId());
        e.setReference(d.reference());
        e.setDossierTypeCode(d.dossierTypeCode());
        e.setLabel(d.label());
        e.setStatus(d.status());
        e.setFields(d.fields());
        e.setCurrentWorkflowLinkId(d.currentWorkflowLinkId());
        e.setCreatedBy(d.createdBy());
        e.setCreatedAt(d.createdAt());
        e.setUpdatedBy(d.updatedBy());
        e.setUpdatedAt(d.updatedAt());
        e.setClosedBy(d.closedBy());
        e.setClosedAt(d.closedAt());
        return e;
    }
}
```

### `DossierElement` — pièces constitutives

Un dossier agrège des éléments de types hétérogènes produits ou collectés
au fil du workflow.

```java
// domain/dossier/model — pur Java, aucune annotation JPA (décision n° 17)
@Builder
public record DossierElement(
    UUID   id,
    UUID   dossierId,
    String tenantId,

    ElementType type,
    // ATTACHMENT    — pièce jointe déposée par un utilisateur
    // PRODUCED_DOC  — document généré par un ServiceTask
    // INFORMATION   — bloc de données structurées
    // NOTE          — commentaire libre
    // LINK          — référence vers une ressource externe

    String label,
    String storageRef,      // référence MinIO pour ATTACHMENT / PRODUCED_DOC

    Map<String, Object> metadata,   // données spécifiques selon le type

    String  addedBy,
    Instant addedAt,
    String  workflowStepId  // étape Flowable productrice (nullable)
) {}
```

```java
// infrastructure/persistence/dossier — entité de persistance
@Entity
@Table(name = "dossier_element")
public class DossierElementJpaEntity {

    @Id @GeneratedValue
    private UUID id;

    private UUID   dossierId;
    private String tenantId;

    @Enumerated(EnumType.STRING)
    private ElementType type;

    private String label;
    private String storageRef;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    private String  addedBy;
    private Instant addedAt;
    private String  workflowStepId;

    // conversion assurée par DossierElementJpaAdapter — même principe que DossierJpaAdapter
}
```

> Un `DossierElement` reste rattaché au **dossier**, pas à un workflow en
> particulier — `workflowStepId` (l'activité BPMN productrice) suffit à
> tracer son origine. Les éléments d'un dossier restent donc visibles et
> consultables quel que soit le nombre de workflows qui se sont succédé.

### Types de pièces justificatives (TODO §106, issue #211, lot P)

Un `DossierType` déclare ses **types de pièces** (`pieceTypes`, D-PIECE-TYPES) : clé
(`^[a-z][a-zA-Z0-9_]{0,59}$`, unique), libellé (100 caractères au plus) et `sensitive` (le contenu d'une pièce de ce
type n'est jamais lu par un traitement automatisé, comme la pré-vérification IA). 30 au plus ; colonne
`dossier_type.piece_types` (changeSet `039`) ; même règle « absent = inchangé » que `documentTemplateKeys` en
`PATCH /api/dossier-types/{code}` et dans `define_dossier_type` ; exportés par la promotion de configuration.

Une pièce jointe porte son type dans la métadonnée **`pieceKey`** (D-PIECE-CLASSIFY) :

- **Au dépôt** — paramètre `pieceKey` facultatif de `POST /api/dossiers/{id}/elements/attachment`, ou métadonnée de
  `add_dossier_element` (MCP) ; une clé que le type du dossier ne déclare pas est refusée (400) **avant** toute écriture
  dans le stockage. Seul un `ATTACHMENT` peut en porter une.
- **Après coup** — `PUT /api/dossiers/{id}/elements/{elementId}/piece-type` `{ "pieceKey": "…" | null }` : droit EDIT
  sur le type, dossier non clos, élément du dossier (sinon 404). Tracé sur l'élément (`pieceClassifiedBy`,
  `pieceClassifiedAt`) et dans le journal de sécurité (`event=attachment_classified`, clés seulement).
- **Non classée** — une pièce sans `pieceKey`, ou dont le type a été retiré du type de dossier, n'est d'aucun type ; elle
  n'est pas modifiée par le retrait.
- **Portail** — le tiers choisit parmi les types proposés par sa tâche (`flowable:portalPieceTypes`,
  [26-portail-externe.md §6](26-portail-externe.md#6-types-de-pièces-proposés-au-tiers-todo-106)).

Le caractère **obligatoire** d'une pièce n'est pas porté par le type de dossier : il est déclaré, pour un stade du
processus, par la brique de pré-vérification (lot B de #211).

### `DossierWorkflowLink` — historique des workflows exécutés sur le dossier

Un dossier peut être manipulé par plusieurs workflows au fil de sa vie
(contrôle qualité, réouverture, escalade…), mais **un seul à la fois**.
Chaque exécution — passée ou en cours — est représentée par une ligne
`DossierWorkflowLink`, ce qui constitue l'historique complet des workflows
ayant opéré sur le dossier.

```java
// domain/dossier/model — pur Java, aucune annotation JPA (décision n° 17)
@Builder
public record DossierWorkflowLink(
    UUID   id,
    UUID   dossierId,
    String tenantId,

    String processInstanceId,     // nullable — ID Flowable, renseigné par confirmStarted(...)
    String processDefinitionKey,  // clé BPMN — référence un WorkflowTypeConfig (voir 05-workflow-engine.md)
    int    sequence,              // 1, 2, 3… — ordre des exécutions sur ce dossier

    String status,   // statut métier courant — code libre, validé contre
                      // WorkflowTypeConfig.statusDefinitions (voir 05-workflow-engine.md)

    String  triggerRef, // origine du démarrage : "connector:<id>", "manual:<userId>",
                         // "api", "workflow:<processInstanceId d'origine>" (chaînage)

    Instant startedAt,
    Instant endedAt,    // nullable — renseigné quand le statut courant atteint un outcome terminal
    String  endReason   // nullable — motif de fin (message d'erreur, annulation…)
) {}
```

```java
// infrastructure/persistence/dossier — entité de persistance
@Entity
@Table(name = "dossier_workflow_link")
public class DossierWorkflowLinkJpaEntity {

    @Id @GeneratedValue
    private UUID id;

    private UUID   dossierId;
    private String tenantId;
    private String processInstanceId;
    private String processDefinitionKey;
    private int    sequence;
    private String status;
    private String triggerRef;
    private Instant startedAt;
    private Instant endedAt;
    private String  endReason;

    // conversion assurée par DossierWorkflowLinkJpaAdapter — même principe que DossierJpaAdapter
}
```

**Règle de concurrence — un seul workflow actif par dossier.** Elle est
garantie au niveau base de données par un index unique partiel, sur le même
principe que la version `ACTIVE` unique des templates de documents (voir
[08-templates-documents.md](08-templates-documents.md)) :

```sql
CREATE UNIQUE INDEX uq_dossier_workflow_link_active
    ON dossier_workflow_link (dossier_id)
    WHERE ended_at IS NULL;
```

Toute tentative de créer un second lien actif pour le même dossier échoue en
base — l'use case applicatif traduit cette violation de contrainte en
exception métier (`WorkflowAlreadyRunningException`), sans dépendre d'une
vérification préalable non atomique.

### `DossierRelation` — liens entre dossiers

```java
// domain/dossier/model — pur Java, aucune annotation JPA (décision n° 17)
@Builder
public record DossierRelation(
    UUID   id,
    UUID   sourceDossierId,
    UUID   targetDossierId,
    String tenantId,

    RelationType type,
    // PARENT_CHILD  — dossier enfant créé pour traiter un aspect précis nécessitant
    //                 un workflow dédié, distinct du dossier principal
    // SUBSEQUENT    — dossier de suite créé lorsque des éléments du dossier initial
    //                 s'avèrent erronés ou incomplets et doivent être repris à neuf
    // LINKED        — lien logique simple : B est une conséquence de A, sans
    //                 hiérarchie ni notion de correction
    // DUPLICATE     — doublon identifié
    // MERGED_INTO   — dossier fusionné dans un autre

    String  createdBy,
    Instant createdAt,
    String  idempotencyKey
    // null si créée manuellement ; sinon "processInstanceId:activityId"
    // du ServiceTask (D-IDEMPOTENCY). Unicité (tenant_id, source, key)
    // via contrainte UNIQUE — NULLs distincts (H2 n'accepte pas l'index partiel).
) {}
```

```java
// infrastructure/persistence/dossier — entité de persistance
@Entity
@Table(name = "dossier_relation")
public class DossierRelationJpaEntity {

    @Id @GeneratedValue
    private UUID id;

    private UUID   sourceDossierId;
    private UUID   targetDossierId;
    private String tenantId;

    @Enumerated(EnumType.STRING)
    private RelationType type;

    private String  createdBy;
    private Instant createdAt;

    // conversion assurée par DossierRelationJpaAdapter — même principe que DossierJpaAdapter
}
```

#### Workflow supplémentaire sur le même dossier, ou nouveau dossier lié ?

Deux mécanismes distincts permettent à un dossier de « continuer » son
traitement — le choix entre les deux est une décision métier, pas technique :

| Situation | Solution |
|---|---|
| Le dossier doit être repris par une étape métier supplémentaire, sur le même objet (contrôle qualité, réouverture, escalade…) | Nouveau `DossierWorkflowLink` sur le **même** dossier |
| Des éléments du dossier initial s'avèrent erronés ou incomplets et doivent être corrigés dans un nouveau dossier | Nouveau dossier lié en `SUBSEQUENT` |
| L'instruction porte sur un aspect précis du dossier nécessitant un workflow dédié, distinct du dossier principal | Nouveau dossier enfant lié en `PARENT_CHILD` |

---

## Cycle de vie du statut

Le statut est une propriété du dossier, pilotée par le workflow via
un `ServiceTask` dédié. Il est indépendant de l'état interne de Flowable
**et indépendant du statut du workflow en cours** (`DossierWorkflowLink.status`,
voir plus haut) — les deux axes sont pilotés séparément par le concepteur du
processus BPMN, avec chacun leur propre `ServiceTask` (`UpdateDossierStatusTask`
ci-dessous pour le dossier, `UpdateWorkflowLinkStatusTask` pour l'exécution en
cours — voir [05-workflow-engine.md](05-workflow-engine.md)). Rien n'impose de
règle de dérivation automatique entre les deux : le statut du dossier reste
sous le contrôle exclusif du processus, quel que soit le nombre de workflows
qui se sont succédé.

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
            type.fieldDefinitions(), cmd.fields(), type.allowUnknownFields());

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
entrant peut produire N dossiers (ex: import de fichier CSV). **Décision
n° 16 — option B :** chaque dossier est créé (ou résolu) et journalisé dans
l'outbox dans la même transaction producteur ; le démarrage Flowable est
différé au relais (voir [05-workflow-engine.md](05-workflow-engine.md) et
[06-connecteurs.md](06-connecteurs.md)).

```java
// TriggerConnector retourne une liste — 1 événement = 1 dossier
public interface TriggerConnector {
    List<WorkflowTriggerEvent> onEvent(ConnectorInstanceData instance,
                                       Map<String, Object> rawPayload);
}

// ConnectorTriggerRuntime — phase producteur (décision n° 16)
@Transactional
public void onIncomingEvent(ConnectorInstanceData instance,
                            Map<String, Object> rawPayload) {
    connector.onEvent(instance, rawPayload).forEach(event -> {

        // 1. Création ou résolution du dossier — validation métier ici
        UUID dossierId = event.getExistingDossierReference() != null
            ? getDossierUseCase.getByReference(
                  instance.tenantId(), event.getExistingDossierReference(),
                  AccessContext.system("connector-trigger")).id()
            : createDossierUseCase.create(CreateDossierCommand.builder()
                  .tenantId       (instance.tenantId())
                  .dossierTypeCode(event.getDossierTypeCode())
                  .fields         (event.getDossierFields())
                  .createdBy      ("trigger:" + instance.id())
                  .build()).id();

        // 2. Journalisation outbox — atomique avec l'étape 1
        //    (pas de réserve / démarrage Flowable ici)
        outboxPort.append(TriggerOutboxPayload.from(dossierId, instance, event));
    });
    // Commit → ACK. Le relais fera reserveOrResume → start → confirmStarted.
}
```

## Démarrage d'un nouveau workflow sur un dossier existant

Trois origines sont possibles pour démarrer un nouveau workflow sur un
dossier qui en a déjà eu un — les trois passent par le même use case
`AttachWorkflowUseCase`, seul le `triggerRef` et le point d'entrée diffèrent.

```java
// domain/dossier/port/in
public interface AttachWorkflowUseCase {

    // Réserve le lien avant le démarrage Flowable — lève
    // WorkflowAlreadyRunningException si un workflow est déjà actif sur ce dossier
    DossierWorkflowLink reserve(UUID dossierId, String processDefinitionKey,
                                String triggerRef);

    // Variante idempotente pour le relais outbox (décision n° 16) :
    // reprend un lien RESERVED existant pour ce dossier+process au rejeu ;
    // no-op conceptuel si déjà ACTIVE avec processInstanceId ;
    // délègue à reserve(...) sinon.
    DossierWorkflowLink reserveOrResume(UUID dossierId, String processDefinitionKey,
                                        String triggerRef);

    // Complète le lien réservé une fois l'instance Flowable effectivement démarrée
    void confirmStarted(UUID linkId, String processInstanceId);
}

// domain/dossier/port/in
public interface GetDossierWorkflowHistoryUseCase {
    List<DossierWorkflowLink> getHistory(UUID dossierId); // triée par sequence
}
```

### 1. Déclenchement manuel — API / UI

Un utilisateur habilité démarre explicitement un nouveau workflow depuis la
fiche dossier.

```java
@RestController
@RequestMapping("/api/dossiers/{dossierId}/workflows")
@RequiredArgsConstructor
public class DossierWorkflowController {

    private final AttachWorkflowUseCase attachWorkflowUseCase;
    private final RuntimeService        runtimeService;
    private final TenantProperties      tenantProperties; // décision n° 13 — constante de configuration

    @PostMapping
    public ResponseEntity<DossierWorkflowLink> start(
            @PathVariable UUID dossierId,
            @RequestBody StartWorkflowRequest req,
            @AuthenticationPrincipal OidcUser user) {

        DossierWorkflowLink link = attachWorkflowUseCase.reserve(
            dossierId, req.processDefinitionKey(), "manual:" + user.getSubject());

        ProcessInstance pi = runtimeService.createProcessInstanceBuilder()
            .processDefinitionKey(req.processDefinitionKey())
            .tenantId(tenantProperties.id())
            .variable("dossierId", dossierId.toString())
            .variable("dossierWorkflowLinkId", link.id().toString())
            .variables(req.variables())
            .start();

        attachWorkflowUseCase.confirmStarted(link.id(), pi.getId());
        return ResponseEntity.ok(link);
    }
}
```

### 2. Connecteur externe référençant un dossier existant

Un `TriggerConnector` peut cibler un dossier déjà existant (par exemple un
webhook qui referme une boucle sur un dossier identifié par sa référence)
plutôt que d'en créer un nouveau — voir l'extension de `WorkflowTriggerEvent`
dans [06-connecteurs.md](06-connecteurs.md). Le `ConnectorTriggerRuntime`
choisit alors la branche « dossier existant » plutôt que
`createDossierUseCase.create(...)`.

### 3. Chaînage interne — un workflow en démarre un autre

Un `ServiceTask` du workflow N peut démarrer le workflow N+1 sur le même
dossier avant de se terminer — utile pour enchaîner automatiquement deux
processus BPMN distincts sur un même dossier.

```java
@Component("startChainedWorkflowTask")
@RequiredArgsConstructor
public class StartChainedWorkflowTask extends DossierAwareTask {

    private final AttachWorkflowUseCase attachWorkflowUseCase;
    private final RuntimeService        runtimeService;

    @Override
    protected void executeWithDossier(DelegateExecution exec, Dossier dossier) {
        String nextProcessKey = (String) exec.getVariable("nextProcessKey");

        DossierWorkflowLink link = attachWorkflowUseCase.reserve(
            dossier.id(), nextProcessKey,
            "workflow:" + exec.getProcessInstanceId());

        ProcessInstance pi = runtimeService.createProcessInstanceBuilder()
            .processDefinitionKey(nextProcessKey)
            .tenantId(exec.getTenantId())
            .variable("dossierId", dossier.id().toString())
            .variable("dossierWorkflowLinkId", link.id().toString())
            .start();

        attachWorkflowUseCase.confirmStarted(link.id(), pi.getId());
    }
}
```

> **Ordre important :** le workflow N doit atteindre un statut terminal
> (`DossierWorkflowLink.status` avec `outcome != NONE`) **avant** que ce
> `ServiceTask` ne s'exécute, sinon `reserve(...)` échoue — la règle de
> concurrence s'applique aussi au chaînage interne.

---

## Contrôle d'accès au niveau type de dossier (décision n° 20)

L'ACL porte sur le **`DossierType`**, pas sur l'instance. Un grant `VIEW` /
`EDIT` sur un type s'applique à tous les dossiers de ce type. Pour un
appelant `User`, `DossierAccessPolicy` consulte `dossier_type_acl` ;
`TENANT_ADMIN` / `WORKFLOW_ADMIN` contournent. Les appels système
(`DossierAwareTask`, relais outbox, résolution trigger) passent
`AccessContext.system(...)`. Détail dans
[12-autorisation-conformite.md](12-autorisation-conformite.md).

```java
// domain/dossier/port/in
public interface GetDossierUseCase {
    Dossier get(UUID id, AccessContext ctx);
    Dossier getByReference(String tenantId, String reference, AccessContext ctx);
}

public interface GrantDossierTypeAccessUseCase {
    void grant(GrantDossierTypeAccessCommand cmd);
    // dossierTypeCode, subjectUserId, permission, grantedBy — admin tenant
}

public interface RevokeDossierTypeAccessUseCase {
    void revoke(String dossierTypeCode, String subjectUserId, AccessContext.User actor);
}
```

`CreateDossierUseCase` (appelant `User`) exige `EDIT` sur le `dossierTypeCode`
cible. Aucun grant par instance n'est écrit à la création. La liste / recherche
est filtrée aux types pour lesquels le sujet a au moins `VIEW`.

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
        // Décision n° 20 — appel système : pas d'ACL utilisateur
        Dossier dossier = getDossier.get(id, AccessContext.system("flowable"));
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
            .dossierId     (dossier.id())
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
et relations visibles en panneau latéral. Lorsqu'un dossier a plusieurs
`DossierWorkflowLink` dans son historique, la vue propose un sélecteur pour
choisir quelle exécution afficher — par défaut, le workflow actif s'il y en
a un, sinon le plus récent.

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
    │   ├── Dossier                     ← record — décision n° 17
    │   ├── DossierType                 ← record
    │   ├── DossierElement              ← record
    │   ├── DossierRelation             ← record
    │   ├── DossierWorkflowLink         ← record — historique dossier ↔ workflows
    │   ├── DossierStatus
    │   ├── ElementType
    │   ├── RelationType
    │   ├── FieldDefinition
    │   ├── DossierFieldValidator
    │   ├── AccessContext                  ← décision n° 20
    │   ├── DossierTypeAclEntry            ← décision n° 20
    │   └── DossierPermission              ← VIEW | EDIT (sur le type)
    ├── DossierAccessPolicy                ← décision n° 20 — service domaine
    ├── usecase
    │   ├── CreateDossierUseCaseImpl
    │   ├── CreateDossierBatchUseCaseImpl
    │   ├── GetDossierUseCaseImpl
    │   ├── UpdateDossierStatusUseCaseImpl
    │   ├── AddDossierElementUseCaseImpl
    │   ├── LinkDossiersUseCaseImpl
    │   ├── AttachWorkflowUseCaseImpl
    │   ├── UpdateWorkflowLinkStatusUseCaseImpl
    │   ├── GetDossierWorkflowHistoryUseCaseImpl
    │   ├── GrantDossierTypeAccessUseCaseImpl  ← décision n° 20
    │   └── RevokeDossierTypeAccessUseCaseImpl ← décision n° 20
    └── port
        ├── in
        │   ├── CreateDossierUseCase
        │   ├── CreateDossierBatchUseCase
        │   ├── GetDossierUseCase          ← get(id, AccessContext)
        │   ├── UpdateDossierStatusUseCase
        │   ├── AddDossierElementUseCase
        │   ├── LinkDossiersUseCase
        │   ├── AttachWorkflowUseCase
        │   ├── UpdateWorkflowLinkStatusUseCase
        │   ├── GetDossierWorkflowHistoryUseCase
        │   ├── GrantDossierTypeAccessUseCase
        │   └── RevokeDossierTypeAccessUseCase
        └── out
            ├── DossierRepository
            ├── DossierTypeRepository
            ├── DossierElementRepository
            ├── DossierRelationRepository
            ├── DossierWorkflowLinkRepository
            ├── DossierTypeAclRepository   ← décision n° 20
            └── DossierReferenceGenerator

infrastructure
└── persistence
    └── dossier
        ├── DossierJpaEntity
        ├── DossierTypeJpaEntity
        ├── DossierElementJpaEntity
        ├── DossierRelationJpaEntity
        ├── DossierWorkflowLinkJpaEntity
        ├── DossierTypeAclJpaEntity        ← décision n° 20
        ├── DossierJpaAdapter
        ├── DossierTypeJpaAdapter
        ├── DossierElementJpaAdapter
        ├── DossierRelationJpaAdapter
        ├── DossierWorkflowLinkJpaAdapter
        ├── DossierTypeAclJpaAdapter
        └── SequentialReferenceGenerator
```

## Implémentation — correction des champs d'un dossier (`PATCH /api/dossiers/{id}/fields`, TODO §24)

*Synthèse ajoutée lors de la remédiation (TODO §95), d'après les documents de travail
([requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-08-15-feature-patch-dossier-fields.md),
[implementation](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-08-15-feature-patch-dossier-fields.md)).*

- **Fusion :** seuls les champs présents dans le corps changent ; une valeur `null` efface le champ
  (`DossierFieldMerger`). Le jeu **fusionné** est revalidé en entier par le même `DossierFieldValidator` qu'à la
  création (types, `required`, options `SELECT`, `allowUnknownFields`).
- **Garde-fous :** 409 si le dossier est `CLOTURE` ou `ARCHIVE` ; verrou optimiste `expectedVersion` optionnel,
  comme `PATCH …/status`. L'édition reste possible sur un type de dossier inactif (seule la création l'interdit).
- **Champs sensibles :** tout détenteur de l'ACL `EDIT` peut les modifier ; chaque changement est journalisé
  (`SecurityAuditLog.sensitiveFieldChanged`, niveau INFO), jamais bloqué.
- **Tests :** `UpdateDossierFieldsUseCaseImplTest`, tests du contrôleur, Playwright `E2E-22`.

## Implémentation — unicité d'un champ et recherche depuis un workflow (TODO §102, issue #184)

- **Déclaration :** `FieldDefinition.unique` sur un champ `STRING`, `NUMBER` ou `DATE`, non sensible, hors groupe, nom ≤ 100
  caractères ; permis sur un champ calculé (clé normalisée par une formule).
- **Règle :** une valeur canonique (texte tel quel, nombre sans zéro non significatif, date ISO) ne figure que dans un seul
  dossier non `CLOTURE`/`ARCHIVE` du type. Valeur vide : jamais en conflit. Valeur unique ≤ 500 caractères.
- **Garantie :** table `dossier_unique_value` tenue par `DossierRepository.save` (toutes les écritures y passent), sous
  verrou consultatif partagé du type ; reconstruite à l'enregistrement du type sous verrou exclusif. Les clés sont tirées du
  dossier recalculé par les règles (`DossierUniqueKeys`).
- **Erreurs :** doublon → 400, `errors[]` `code: "UNIQUE"` + `reference` du détenteur (pré-contrôle de la création, de la
  correction et de l'essai à blanc MCP ; la base tranche les courses). Activer `unique` sur des doublons ouverts → 409 avec
  `duplicates` (au plus 20 valeurs ; `duplicateCount` est le total, et le `detail` le nomme quand la liste est tronquée).
  Chaque valeur cite au plus 20 références et garde le total dans `referenceCount`. Webhook entrant : 400 avec champs et
  codes seulement.
- **Saisie :** case « Valeur unique » dans l'éditeur de schéma, inactive hors `STRING`, `NUMBER` et `DATE` ou sur un champ sensible
  (raison visible, hors du libellé, reliée par `aria-describedby`). Sous le champ, `UNIQUE` s'affiche
  « Déjà utilisé par le dossier {reference} » ; le message serveur anglais n'est pas recopié. Un champ masqué ou inconnu
  reste dans la bannière du formulaire qui a produit l'erreur. Guide :
  [GUIDE-UTILISATEUR.md](../guides/GUIDE-UTILISATEUR.md) §1.
- **Tests :** `DossierUniqueValueIT`, `DossierUniqueTypeIT`, `UniqueFieldApiTest`, `UniqueFieldWorkflowIT`,
  `RiverainUniquenessExampleTest`.
- **Exemple riverain :** `plaqueNormalisee` est déclarée `unique`. La case « J'ai vérifié qu'aucune autre demande n'existe
  pour ce véhicule » est retirée du questionnaire d'instruction, et la tâche ne demande plus de rechercher le doublon à la
  main. Une seconde demande ouverte dont la plaque se normalise vers la même clé est refusée à la création.
  `exemples/autorisation-riverain/DIFFICULTES.md` §1 est levée.
- **Recherche depuis un workflow (lot B) :** brique `${dossierSearchTask}` — dossiers en cours du même type, dossier courant
  exclu, 1 à 3 égalités sur des champs cherchables ; nombre et référence du plus ancien
  ([GUIDE-CONCEPTION-BPMN.md](../guides/GUIDE-CONCEPTION-BPMN.md) §3octies). Port `DossierSearchPort.findOpenMatches`,
  use case `SearchSimilarDossiersUseCase`. Tests : `DossierOpenMatchesIT`, `DossierSearchTaskIT`.

## Implémentation — antivirus des pièces jointes (lot C, PR #131)

*Synthèse ajoutée lors de la remédiation (TODO §95), d'après les documents de travail
([requirements](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/requirements/2026-09-20-feature-scan-antivirus-clamav.md),
[implementation](https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/implementation/2026-09-20-feature-scan-antivirus-clamav.md)). Exploitation :
[docs/guides/DEPLOIEMENT.md](../guides/DEPLOIEMENT.md) §8.*

- **Port inchangé :** `DossierElementUploadController` appelle `AttachmentScanPort.scan(...)` ; la propriété
  `app.dossier.attachment.scan.type` (`noop` par défaut, ou `clamav`) choisit l'unique adaptateur actif
  (D-AV-CLAMAV-CONFIG-SELECTOR). Un autre moteur ne demanderait qu'un nouvel adaptateur.
- **ClamAV :** protocole `INSTREAM` de `clamd` en TCP brut, sans bibliothèque cliente (D-AV-CLAMAV-PROTO). Un verdict
  infecté bloque le dépôt.
- **Panne de `clamd` :** fail-open (D-AV-CLAMAV-FAILMODE) — la pièce est acceptée, un `WARN` est journalisé et le
  compteur `myrmacta_attachment_scan_fail_open_total` est incrémenté (D-AV-CLAMAV-HEALTH), pour qu'une panne
  prolongée reste visible dans Prometheus.
- **Démarrage :** depuis TODO §95, `noop` est refusé en `production` sans
  `APP_DOSSIER_ATTACHMENT_SCAN_NOOP_ACKNOWLEDGED=true`, et signalé en `staging`.
- **Tests :** `ClamAvAttachmentScanAdapterTest` (faux `clamd` sur socket loopback), `ClamAvAttachmentScanIT` (vrai
  ClamAV sous Docker, ignoré sans démon Docker).

## Implémentation — échéance d'un dossier, rappel et escalade (TODO §115, lot 4a de #217)

Décisions : [§115](../decisions/115-echeances-de-dossier-rappel-escalade-et-minuteurs.md) (D-DEADLINE-FIELD,
D-DEADLINE-ESCALATE).

- **Réglage par type.** Un type désigne l'un de ses champs `DATE` comme échéance, avec un rappel J-n (0 = aucun),
  le décompte de ce rappel (`CALENDAR` par défaut, ou `BUSINESS`, §120) et un groupe d'escalade facultatif.
  - Stockage : table `dossier_type_deadline` (changeset 045).
  - API : `GET`, `PUT` et `DELETE /api/dossier-types/{code}/deadline` (`TENANT_ADMIN` ; 204 sans réglage).
  - Écran : Admin → Types → Modifier les champs → « Échéance ».
  - Cas d'usage : `ManageDeadlineUseCase`, qui refuse un champ qui n'est pas `DATE` et un groupe inconnu.
- **Source de la date.** Saisie, mapping de connecteur, ou brique « Router le courrier » : celle-ci écrit
  `aujourd'hui + routageDelaiJours` dans le champ désigné du type du dossier métier qu'elle ouvre.
- **Job `DeadlineAlertJob`** (`app.deadlines.scan-interval-ms`, 15 min par défaut) → `EvaluateDeadlinesUseCase` :
  - périmètre : dossiers `OUVERT`, `EN_COURS` ou `SUSPENDU` des types réglés, dont le champ vaut une date ISO, lue en base
    par `fields ->> champ` (`DeadlineCandidates`) ;
  - le jour courant est la date civile du fuseau du tenant (UTC si aucun calendrier n'est enregistré, §120) ;
  - en décompte calendaire, le jour du terme est encore dans les temps et l'escalade part le lendemain ;
  - en décompte ouvrable, l'escalade attend le prochain jour ouvrable après le terme ; entre les deux, ni rappel ni escalade ;
  - **rappel** dès J-n : notification « boîte de réception » aux participants des process du dossier ;
  - **escalade** dès le lendemain du terme :
    - les tâches ouvertes du process actif passent au groupe d'escalade (`reassignTaskToGroup`, groupe du lien) ;
    - les participants d'avant et les membres effectifs du groupe (`UserGroupsUseCase.membersOf`) sont prévenus ;
    - une note `DEADLINE_ESCALATED` est versée au dossier au nom de `system` ;
    - un groupe supprimé depuis le réglage réduit l'escalade à la note ;
  - **une seule fois** : chaque palier est inséré dans `dossier_deadline_alert` (clé tenant, dossier, palier, date) avant
    d'agir, dans la transaction du dossier. Une échéance reportée ouvre de nouveaux paliers.
- Tests : `EvaluateDeadlinesUseCaseImplTest`, `DossierTypeDeadlineApiTest`, `MailRoomExampleIT` (rappel, puis
  escalade réelle d'une tâche Flowable).
- Promotion : le réglage voyage dans le bundle (artefact `DEADLINE`, §117 D-PROMO-TYPE-SETTINGS).
- **Minuteur à expression (lot 4b, D-DEADLINE-TIMER).**
  - La brique « Calculer une échéance » (`ComputeDeadlineDelegate`, `computeDeadlineTask`, paramètres littéraux
    seulement) relit un champ date non sensible du dossier, ou applique `delaiJours`. `delaiOuvrable` et `rappelOuvrable`
    valent `CALENDAIRE` (défaut) ou `OUVRABLE` (§120).
  - Elle pose `echeance` et les variables réservées `echeanceRappel` et `echeanceDepassee`.
  - `echeanceDepassee` est le lendemain calendaire à minuit dans le fuseau du tenant, même si le rappel est ouvrable.
  - `BpmnExpressionSurfaceScanner` admet ces deux derniers noms, nus, dans le `timeDate` d'un minuteur, et rien d'autre.
  - Tests :
    - `ComputeDeadlineDelegateTest` ;
    - `FlowableBpmnParserAdapterUelSurfaceTest` (formes admises et refusées) ;
    - `MailRoomExampleIT` (le minuteur `${echeanceDepassee}` du process `traiterReponse` clôture la réponse échue).

## Implémentation — calendrier du tenant : saisies, sources et surcharges (TODO §120, §121, issue #33)

Décisions : [§120](../decisions/120-calendrier-jours-ouvrables.md) (fuseau, week-end, comptage),
[§121](../decisions/121-calendrier-saisie-sources-ics.md) (D-CAL-ENTRY, D-CAL-OVERRIDE, D-CAL-SOURCE, D-CAL-BE-SOURCE,
D-CAL-API, D-CAL-PROMO-2).

- **Modèle.** `TenantCalendar` = fuseau, week-end, saisies manuelles (`CalendarEntry` : début, fin, nature `CLOSED`
  ou `OPEN`, libellé) et sources (`CalendarSource` : jours `CLOSED` seulement). `toCalendar()` déroule les périodes en
  deux ensembles pour `BusinessCalendar`.
  - Priorité (D-CAL-OVERRIDE) : manuel `OPEN`, puis manuel `CLOSED`, puis jour fermé d'une source, puis week-end.
  - Plafonds : 366 jours par période, 2 000 saisies, 10 sources, 10 000 périodes par source, 200 000 jours déroulés.
- **Stockage.** Tables `tenant_calendar` (fuseau, week-end), `tenant_calendar_source` et `tenant_calendar_entry`
  (changeset 047). Une saisie manuelle a `source_id` nul ; supprimer une source supprime ses jours. Les dates de
  l'ancienne colonne `holidays` sont reprises en saisies manuelles `CLOSED`.
- **API** (`TENANT_ADMIN`) : `GET` et `PUT /api/tenant/calendar` (`entries`, ou l'ancien `holidays` déprécié ; la
  réponse porte `entries`, `sources` et `holidays` calculé) ; `POST /api/tenant/calendar/belgian-federal-holidays`
  crée ou recharge la source `BE_FEDERAL` (années courante et suivante, fuseau du tenant).
  - **Sources ICS par fichier** (multipart, D-CAL-ICS, D-CAL-ICS-PREVIEW) : `POST .../sources/preview` (`name`, `file`) et
    `POST .../sources/{id}/preview` (`file`) rendent l'aperçu sans rien écrire (jours ajoutés, retirés, masqués par un jour
    ouvré forcé, événements ignorés par motif, SHA-256, nombre de périodes) ; `POST .../sources` (`name`, `file`,
    `expectedSha256`, 201) et `POST .../sources/{id}/reload` (`file`, `expectedSha256`) appliquent ; `DELETE
    .../sources/{id}` (204) supprime la source et ses jours.
  - Codes : 400 (fichier illisible, de plus de 1 Mio ou de plus de 5 000 événements, nom vide, source `BE_FEDERAL`
    rechargée par fichier), 404 (source d'un autre tenant ou inconnue), 409 (nom déjà pris, ignorant la casse, ou
    contenu différent de celui de l'aperçu), 403 (hors `TENANT_ADMIN` ou sans jeton CSRF).
  - Lecture : `Ical4jIcsCalendarParser` (ical4j 4.4.0, infrastructure seulement ; groovy exclu). Journées entières
    (`VALUE=DATE`, `DTEND` exclusif, `DURATION` admise, un jour sans fin) ; `RRULE`, `RDATE` et `EXDATE` déroulés de
    l'année N-1 à N+2 dans le fuseau du tenant, une fréquence plus fine que quotidienne étant refusée ; événements
    horaires, `CANCELLED` et `TRANSPARENT` écartés et comptés ; 10 000 périodes au plus. Aucun appel réseau
    (`ical4j.properties`).
- **Écran** : Admin → Types → Calendrier. Formulaire Du / Au / nature / libellé (`CalendarEntryForm`), tableau trié
  des saisies et des jours de source (`CalendarEntryList`, jour de source recouvert par un jour ouvré forcé signalé),
  sources (`CalendarSourcesPanel` : ajout d'un fichier `.ics`, rechargement, suppression après confirmation) et boîte
  d'aperçu avant import (`CalendarImportPreview` : modale, focus piégé, Échap annule).
- **Promotion.** L'artefact `CALENDAR` est un instantané complet (saisies et sources déroulées, sans URL) ; un ancien
  bundle à liste plate s'importe en saisies manuelles. Une source de même nom garde son identifiant ; une source absente
  du bundle est supprimée.
- **Tests.** `CalendarEntryTest`, `TenantCalendarTest`, `BusinessCalendarTest`, `ManageTenantCalendarUseCaseImplTest`,
  `TenantCalendarApiTest` (chaîne HTTP et sécurité, isolation du tenant), `TenantCalendarEntriesMigrationIT`,
  `TypeSettingsArtifactsCalendarTest`, `Ical4jIcsCalendarParserTest` ; Vitest (`TenantCalendarPanel`,
  `CalendarEntryForm`, `CalendarEntryList`, `CalendarSourcesPanel`, `CalendarImportPreview`) et Playwright `E2E-CAL-1`
  à `8`.
