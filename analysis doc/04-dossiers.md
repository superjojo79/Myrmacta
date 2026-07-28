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
    // Un dossier peut être manipulé par plusieurs workflows successifs — voir
    // DossierWorkflowLink ci-dessous, qui porte l'historique complet. Ce champ
    // n'est qu'un pointeur dénormalisé de confort vers le workflow actif ;
    // il vaut null si aucun workflow n'est en cours sur ce dossier.
    private UUID currentWorkflowLinkId;  // nullable — DossierWorkflowLink actif

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

> Un `DossierElement` reste rattaché au **dossier**, pas à un workflow en
> particulier — `workflowStepId` (l'activité BPMN productrice) suffit à
> tracer son origine. Les éléments d'un dossier restent donc visibles et
> consultables quel que soit le nombre de workflows qui se sont succédé.

### `DossierWorkflowLink` — historique des workflows exécutés sur le dossier

Un dossier peut être manipulé par plusieurs workflows au fil de sa vie
(contrôle qualité, réouverture, escalade…), mais **un seul à la fois**.
Chaque exécution — passée ou en cours — est représentée par une ligne
`DossierWorkflowLink`, ce qui constitue l'historique complet des workflows
ayant opéré sur le dossier.

```java
@Entity
public class DossierWorkflowLink {

    @Id @GeneratedValue
    private UUID id;

    private UUID   dossierId;
    private String tenantId;

    private String processInstanceId;     // ID Flowable — renseigné une fois l'instance démarrée
    private String processDefinitionKey;  // clé BPMN — référence un WorkflowTypeConfig (voir 05-workflow-engine.md)
    private int    sequence;              // 1, 2, 3… — ordre des exécutions sur ce dossier

    private String status;   // statut métier courant — code libre, validé contre
                              // WorkflowTypeConfig.statusDefinitions (voir 05-workflow-engine.md)

    private String  triggerRef; // origine du démarrage : "connector:<id>", "manual:<userId>",
                                 // "api", "workflow:<processInstanceId d'origine>" (chaînage)

    private Instant startedAt;
    private Instant endedAt;    // renseigné quand le statut courant atteint un outcome terminal
    private String  endReason;  // optionnel — motif de fin (message d'erreur, annulation…)
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
@Entity
public class DossierRelation {

    @Id @GeneratedValue
    private UUID id;

    private UUID   sourceDossierId;
    private UUID   targetDossierId;
    private String tenantId;

    @Enumerated(EnumType.STRING)
    private RelationType type;
    // PARENT_CHILD  — dossier enfant créé pour traiter un aspect précis nécessitant
    //                 un workflow dédié, distinct du dossier principal
    // SUBSEQUENT    — dossier de suite créé lorsque des éléments du dossier initial
    //                 s'avèrent erronés ou incomplets et doivent être repris à neuf
    // LINKED        — lien logique simple : B est une conséquence de A, sans
    //                 hiérarchie ni notion de correction
    // DUPLICATE     — doublon identifié
    // MERGED_INTO   — dossier fusionné dans un autre

    private String  createdBy;
    private Instant createdAt;
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

    // 2. Réservation du lien dossier ↔ workflow — vérifie la règle de
    //    concurrence (aucun autre workflow actif) et attribue son id avant
    //    même le démarrage Flowable, pour que la variable soit disponible
    //    dès les premiers ServiceTask exécutés de façon synchrone par .start()
    DossierWorkflowLink link = attachWorkflowUseCase.reserve(
        dossier.getId(), event.getProcessKey(), "connector:" + instance.getId());

    // 3. Démarrage de l'instance Flowable
    ProcessInstance pi = runtimeService
        .createProcessInstanceBuilder()
        .processDefinitionKey(event.getProcessKey())
        .tenantId(instance.getTenantId())
        .variable("dossierId", dossier.getId().toString())
        .variable("dossierWorkflowLinkId", link.getId().toString())
        .variables(event.getVariables())
        .start();

    // 4. Confirmation — renseigne processInstanceId + startedAt sur le lien
    attachWorkflowUseCase.confirmStarted(link.getId(), pi.getId());
});
```

---

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
    private final TenantContext         tenantContext;

    @PostMapping
    public ResponseEntity<DossierWorkflowLink> start(
            @PathVariable UUID dossierId,
            @RequestBody StartWorkflowRequest req,
            @AuthenticationPrincipal OidcUser user) {

        DossierWorkflowLink link = attachWorkflowUseCase.reserve(
            dossierId, req.processDefinitionKey(), "manual:" + user.getSubject());

        ProcessInstance pi = runtimeService.createProcessInstanceBuilder()
            .processDefinitionKey(req.processDefinitionKey())
            .tenantId(tenantContext.getTenantId())
            .variable("dossierId", dossierId.toString())
            .variable("dossierWorkflowLinkId", link.getId().toString())
            .variables(req.variables())
            .start();

        attachWorkflowUseCase.confirmStarted(link.getId(), pi.getId());
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
            dossier.getId(), nextProcessKey,
            "workflow:" + exec.getProcessInstanceId());

        ProcessInstance pi = runtimeService.createProcessInstanceBuilder()
            .processDefinitionKey(nextProcessKey)
            .tenantId(exec.getTenantId())
            .variable("dossierId", dossier.getId().toString())
            .variable("dossierWorkflowLinkId", link.getId().toString())
            .start();

        attachWorkflowUseCase.confirmStarted(link.getId(), pi.getId());
    }
}
```

> **Ordre important :** le workflow N doit atteindre un statut terminal
> (`DossierWorkflowLink.status` avec `outcome != NONE`) **avant** que ce
> `ServiceTask` ne s'exécute, sinon `reserve(...)` échoue — la règle de
> concurrence s'applique aussi au chaînage interne.

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

```
domain
└── dossier
    ├── model
    │   ├── Dossier
    │   ├── DossierType
    │   ├── DossierElement
    │   ├── DossierRelation
    │   ├── DossierWorkflowLink        ← nouveau — historique dossier ↔ workflows
    │   ├── DossierStatus
    │   ├── ElementType
    │   ├── RelationType
    │   └── FieldDefinition
    ├── usecase
    │   ├── CreateDossierUseCase
    │   ├── CreateDossierBatchUseCase
    │   ├── GetDossierUseCase
    │   ├── UpdateDossierStatusUseCase
    │   ├── AddDossierElementUseCase
    │   ├── LinkDossiersUseCase
    │   ├── AttachWorkflowUseCase          ← nouveau — remplace LinkDossierToProcessUseCase
    │   ├── UpdateWorkflowLinkStatusUseCase ← nouveau
    │   └── GetDossierWorkflowHistoryUseCase ← nouveau
    └── port
        ├── in
        │   ├── CreateDossierUseCase
        │   ├── CreateDossierBatchUseCase
        │   ├── GetDossierUseCase
        │   ├── UpdateDossierStatusUseCase
        │   ├── AddDossierElementUseCase
        │   ├── LinkDossiersUseCase
        │   ├── AttachWorkflowUseCase
        │   ├── UpdateWorkflowLinkStatusUseCase
        │   └── GetDossierWorkflowHistoryUseCase
        └── out
            ├── DossierRepository
            ├── DossierTypeRepository
            ├── DossierElementRepository
            ├── DossierRelationRepository
            ├── DossierWorkflowLinkRepository  ← nouveau
            └── DossierReferenceGenerator

infrastructure
└── persistence
    └── dossier
        ├── DossierJpaAdapter
        ├── DossierTypeJpaAdapter
        ├── DossierElementJpaAdapter
        ├── DossierRelationJpaAdapter
        ├── DossierWorkflowLinkJpaAdapter  ← nouveau
        └── SequentialReferenceGenerator
```
