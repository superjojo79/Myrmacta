# Visualisation des instances de workflow

> Rendu BPMN.js à partir du XML natif Flowable.  
> Coloration des états (actif / terminé / erreur) par overlays CSS.  
> Notification SSE sur changement d'état — rafraîchissement manuel.

---

### Principe

La visualisation est rendue dans le browser via **BPMN.js** (`bpmn-js` — `NavigatedViewer`) à partir du XML BPMN natif Flowable. Aucune conversion de format — le XML produit par Flowable est consommé directement. Les états (actif, terminé, erreur) sont appliqués par **overlays CSS** sur le rendu.

### Accès

La visualisation est accessible aux utilisateurs impliqués dans l'instance (assignés à une tâche, initiateurs, observateurs configurés). Le contrôle d'accès est géré par le port `WorkflowParticipantPort`.

### Endpoints backend

| Méthode | Endpoint | Description |
|---|---|---|
| `GET` | `/api/workflows/{instanceId}/diagram` | XML BPMN + IDs actifs / terminés / en erreur |
| `GET` | `/api/workflows/{instanceId}/history` | Étapes avec timing, durée et assigné |
| `GET` | `/api/workflows/{instanceId}/tasks` | Tâches actives avec assigné courant |
| `POST` | `/api/workflows/{instanceId}/tasks/{taskId}/reassign` | Réassignation de tâche |
| `GET` | `/api/dossiers/{dossierId}/workflows` | Historique des workflows exécutés sur le dossier (`DossierWorkflowLink[]`, voir [04-dossiers.md](04-dossiers.md)) |

> **Mise à jour — décision n° 17.** `GetDossierWorkflowHistoryUseCase.getHistory(...)` renvoyait une
> entité JPA directement comme corps de cette réponse — le risque concret (proxy Hibernate sérialisé,
> `LazyInitializationException`) disparaît par construction puisque `DossierWorkflowLink` est
> désormais un objet domaine pur (`@Value`, aucune annotation JPA — voir
> [04-dossiers.md](04-dossiers.md)), sans danger technique à être sérialisé tel quel. La question plus
> générale de découpler le contrat d'API du modèle domaine (pour qu'un renommage de champ domaine ne
> change pas silencieusement la réponse HTTP) reste du ressort du point 24 — sur le modèle du `record`
> `WorkflowDiagramView` déjà utilisé ci-dessous comme DTO de réponse dédié.

### Modèles de réponse

```java
// Réponse diagram
public record WorkflowDiagramView(
    String       bpmnXml,        // XML BPMN natif Flowable → consommé par BPMN.js
    List<String> activeIds,      // activités en cours
    List<String> completedIds,   // activités terminées
    List<String> failedIds       // activités en erreur
) {}

// Entrée d'historique
@Value @Builder
public class ActivityHistoryEntry {
    String   activityId;
    String   name;
    String   assignee;
    Instant  startTime;
    Instant  endTime;       // null si encore en cours
    Long     durationMs;
}
```

### Adaptateur Flowable

```java
@Component
@RequiredArgsConstructor
public class FlowableWorkflowVisualizationAdapter
        implements WorkflowVisualizationPort {

    private final RepositoryService repositoryService;
    private final RuntimeService    runtimeService;
    private final HistoryService    historyService;
    private final TaskService       taskService;

    @Override
    public WorkflowDiagramView getDiagram(String instanceId, String tenantId) {

        HistoricProcessInstance hpi = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instanceId)
            .processInstanceTenantId(tenantId)
            .singleResult();

        // XML BPMN natif — transmis directement à BPMN.js sans conversion
        BpmnModel model  = repositoryService
            .getBpmnModel(hpi.getProcessDefinitionId());
        String    bpmnXml = new BpmnXMLConverter()
            .convertToXML(model).toString();

        List<String> activeIds = runtimeService
            .getActiveActivityIds(instanceId);

        List<String> completedIds = historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(instanceId).finished().list()
            .stream().map(HistoricActivityInstance::getActivityId)
            .distinct().toList();

        List<String> failedIds = historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(instanceId).list().stream()
            .filter(a -> a.getDeleteReason() != null
                      && a.getDeleteReason().startsWith("error"))
            .map(HistoricActivityInstance::getActivityId).toList();

        return new WorkflowDiagramView(bpmnXml, activeIds,
                                       completedIds, failedIds);
    }

    @Override
    public List<ActivityHistoryEntry> getHistory(String instanceId,
                                                  String tenantId) {
        return historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(instanceId)
            .orderByHistoricActivityInstanceStartTime().asc()
            .list().stream()
            .map(a -> ActivityHistoryEntry.builder()
                .activityId(a.getActivityId())
                .name      (a.getActivityName())
                .assignee  (a.getAssignee())
                .startTime (a.getStartTime().toInstant())
                .endTime   (a.getEndTime() != null
                              ? a.getEndTime().toInstant() : null)
                .durationMs(a.getDurationInMillis())
                .build())
            .toList();
    }

    @Override
    public void reassignTask(String taskId, String assigneeId,
                             String tenantId) {
        taskService.setAssignee(taskId, assigneeId);
    }
}
```

### Frontend Vue 3 — composant WorkflowViewer

**Dépendance npm :**

```bash
npm install bpmn-js
```

**Rendu et coloration :**

```javascript
import BpmnViewer from 'bpmn-js/lib/NavigatedViewer';

const viewer = new BpmnViewer({ container: '#bpmn-canvas' });

async function loadDiagram(instanceId) {
    const { bpmnXml, activeIds, completedIds, failedIds }
        = await api.get(`/api/workflows/${instanceId}/diagram`);

    // Rendu du XML natif Flowable — aucune conversion
    await viewer.importXML(bpmnXml);

    const canvas   = viewer.get('canvas');
    const overlays = viewer.get('overlays');

    completedIds.forEach(id => canvas.addMarker(id, 'highlight-completed'));
    activeIds.forEach   (id => canvas.addMarker(id, 'highlight-active'));
    failedIds.forEach   (id => canvas.addMarker(id, 'highlight-failed'));

    // Badge visuel sur les étapes actives
    activeIds.forEach(id => overlays.add(id, {
        position: { top: -12, right: -12 },
        html: '<div class="bpmn-badge active">●</div>'
    }));
}
```

**CSS des overlays :**

```css
.highlight-completed .djs-visual > :nth-child(1) {
    fill: #dcfce7 !important; stroke: #16a34a !important;
}
.highlight-active .djs-visual > :nth-child(1) {
    fill: #dbeafe !important; stroke: #2563eb !important;
}
.highlight-failed .djs-visual > :nth-child(1) {
    fill: #fee2e2 !important; stroke: #dc2626 !important;
}
.bpmn-badge.active {
    background: #2563eb; color: white;
    border-radius: 50%; width: 16px; height: 16px;
    font-size: 10px; display: flex;
    align-items: center; justify-content: center;
}
```

### Notification de changement d'état — SSE

Le canal SSE existant est réutilisé. Un `FlowableEventListener` notifie les utilisateurs impliqués à chaque transition significative — la vue affiche une bannière de rafraîchissement sans polling.

```java
@Component
@RequiredArgsConstructor
public class WorkflowStateEventListener implements FlowableEventListener {

    private final SendNotificationUseCase notification;
    private final WorkflowParticipantPort participants;

    private static final Set<FlowableEngineEventType> RELEVANT = Set.of(
        FlowableEngineEventType.TASK_ASSIGNED,
        FlowableEngineEventType.ACTIVITY_COMPLETED,
        FlowableEngineEventType.PROCESS_COMPLETED
    );

    @Override
    public void onEvent(FlowableEvent raw) {
        if (!(raw instanceof FlowableEngineEvent event)) return;
        if (!RELEVANT.contains(event.getType()))        return;

        String instanceId = event.getProcessInstanceId();

        participants.findByInstanceId(instanceId).forEach(userId ->
            notification.send(NotificationEvent.builder()
                .channel (NotificationChannel.BROWSER)
                .userId  (userId)
                .type    ("WORKFLOW_STATE_CHANGED")
                .payload (Map.of("instanceId", instanceId))
                .build())
        );
    }

    @Override public boolean isFailOnException()                   { return false; }
    @Override public boolean isFireOnTransactionLifecycleEvent()   { return false; }
}
```

Côté Vue 3, la notification déclenche une bannière — rafraîchissement au clic :

```javascript
watch(notifications, (newNotifs) => {
    const latest = newNotifs[0];
    if (latest?.type === 'WORKFLOW_STATE_CHANGED'
     && latest.payload.instanceId === props.instanceId) {
        showRefreshBanner.value = true;
    }
});
```

### Ports domaine ajoutés

```java
// domain/workflow/port/out
public interface WorkflowVisualizationPort {
    WorkflowDiagramView      getDiagram(String instanceId, String tenantId);
    List<ActivityHistoryEntry> getHistory(String instanceId, String tenantId);
    void reassignTask(String taskId, String assigneeId, String tenantId);
}

// Résolution des utilisateurs impliqués dans une instance
public interface WorkflowParticipantPort {
    List<String> findByInstanceId(String instanceId); // retourne les userIds
}
```

### Mise à jour de la structure de packages

Dans `infrastructure/flowable` :

```
infrastructure/flowable
├── FlowableWorkflowAdapter
├── FlowableWorkflowVisualizationAdapter  ← nouveau
├── WorkflowStateEventListener            ← nouveau
├── ConnectorServiceTask
└── OutboxRelay                           # décision n° 16 — démarre Flowable depuis outbox

infrastructure/connector
├── ConnectorTriggerRuntime               # décision n° 16 — producteur dossier + outbox
├── registry
└── builtin
```

---

## Affichage du dossier en regard du workflow

Le panneau de visualisation affiche le dossier associé à l'instance en
parallèle du diagramme BPMN — signalétique, statut, éléments et relations.
Le rendu de ce panneau (schéma et contenu) est décrit une seule fois dans
[04-dossiers.md — Impact sur la visualisation](04-dossiers.md#impact-sur-la-visualisation),
pour éviter de dupliquer le même exemple dans les deux documents.

```
GET /api/workflows/{instanceId}/dossier
    → { dossier, elements, relations }
```

### Sélecteur de run — dossier avec plusieurs workflows

Un dossier ayant été manipulé par plusieurs workflows successifs (voir
[04-dossiers.md](04-dossiers.md)) a plusieurs entrées dans
`GET /api/dossiers/{dossierId}/workflows`. Le `WorkflowViewer` affiche cette
liste comme une frise chronologique au-dessus du diagramme ; sélectionner
une entrée recharge le diagramme BPMN correspondant via
`GET /api/workflows/{instanceId}/diagram` avec l'`instanceId` de l'entrée
choisie. Par défaut, l'entrée sans `endedAt` (le workflow actif) est
sélectionnée ; s'il n'y en a pas, c'est la plus récente.

```
Historique des workflows — Dossier DOS-SINISTRE-2024-000042
┌───────────────────────────────────────────────────────────────┐
│  #1 instruction-sinistre   DOSSIER_INSTRUIT     10/01 → 25/01  │
│  #2 reouverture-sinistre   REOUVERTURE_CLOTUREE 03/02 → 04/02  │
│  #3 controle-qualite       EN_INSTRUCTION       01/03 → …   ●  │ ← sélectionné (pas d'endedAt)
└───────────────────────────────────────────────────────────────┘
```

> Les libellés affichés (`DOSSIER_INSTRUIT`, `EN_INSTRUCTION`…) sont les
> codes métier configurés dans `WorkflowTypeConfig.statusDefinitions` pour
> chaque `processDefinitionKey` — voir [05-workflow-engine.md](05-workflow-engine.md).
> Ce ne sont pas les valeurs de l'enum technique `StatusOutcome`, qui ne
> détermine que si l'exécution est terminée et de quelle manière.
