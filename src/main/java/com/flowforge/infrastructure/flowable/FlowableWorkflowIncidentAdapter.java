package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.model.IncidentNotRetryableException;
import com.flowforge.domain.workflow.model.IncidentState;
import com.flowforge.domain.workflow.model.WorkflowIncident;
import com.flowforge.domain.workflow.model.WorkflowIncidentNotFoundException;
import com.flowforge.domain.workflow.port.out.WorkflowIncidentPort;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.job.api.Job;
import org.springframework.stereotype.Component;

/**
 * TODO §94 — jobs du moteur en échec, toujours filtrés par tenant ({@code jobTenantId}) : job exécutable ou minuteur
 * avec exception = {@link IncidentState#RETRYING} (le moteur retentera) ; dead letter = {@link IncidentState#INCIDENT}.
 * {@code since} est la date d'insertion du job dans sa table courante, soit à peu près celle du dernier échec.
 *
 * <p>Revue M1 : un job sans instance de process (minuteur de démarrage) n'est rattaché à aucun dossier : ignoré.
 * Revue M2 : un job d'un sous-process appelé ({@code callActivity}) est rapporté à l'instance <b>racine</b> (celle du lien
 * de workflow) et à l'activité {@code callActivity} du process racine — celle que montre le diagramme du dossier —, le
 * libellé gardant l'étape du sous-process.
 */
@Component
class FlowableWorkflowIncidentAdapter implements WorkflowIncidentPort {

    private static final Comparator<WorkflowIncident> MOST_RECENT_FIRST = Comparator
            .comparing(WorkflowIncident::since, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(WorkflowIncident::jobId);

    private final ManagementService managementService;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;

    FlowableWorkflowIncidentAdapter(
            ManagementService managementService, RuntimeService runtimeService, RepositoryService repositoryService) {
        this.managementService = Objects.requireNonNull(managementService, "managementService");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.repositoryService = Objects.requireNonNull(repositoryService, "repositoryService");
    }

    @Override
    public List<WorkflowIncident> listByProcessInstance(String processInstanceId, String tenantId) {
        Objects.requireNonNull(processInstanceId, "processInstanceId");
        Locator locator = new Locator();
        List<WorkflowIncident> found = new ArrayList<>();
        for (String instance : withDescendants(processInstanceId)) {
            managementService.createDeadLetterJobQuery().processInstanceId(instance).jobTenantId(tenantId).list()
                    .forEach(job -> locator.toIncident(job, IncidentState.INCIDENT).ifPresent(found::add));
            managementService.createJobQuery().processInstanceId(instance).jobTenantId(tenantId).withException().list()
                    .forEach(job -> locator.toIncident(job, IncidentState.RETRYING).ifPresent(found::add));
            managementService.createTimerJobQuery().processInstanceId(instance).jobTenantId(tenantId).withException()
                    .list().forEach(job -> locator.toIncident(job, IncidentState.RETRYING).ifPresent(found::add));
        }
        return found.stream().sorted(MOST_RECENT_FIRST).toList();
    }

    @Override
    public List<WorkflowIncident> listByTenant(String tenantId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Locator locator = new Locator();
        List<WorkflowIncident> all = new ArrayList<>();
        managementService.createDeadLetterJobQuery().jobTenantId(tenantId).orderByJobCreateTime().desc()
                .listPage(0, limit).forEach(job -> locator.toIncident(job, IncidentState.INCIDENT).ifPresent(all::add));
        managementService.createJobQuery().jobTenantId(tenantId).withException().orderByJobCreateTime().desc()
                .listPage(0, limit).forEach(job -> locator.toIncident(job, IncidentState.RETRYING).ifPresent(all::add));
        managementService.createTimerJobQuery().jobTenantId(tenantId).withException().orderByJobCreateTime().desc()
                .listPage(0, limit).forEach(job -> locator.toIncident(job, IncidentState.RETRYING).ifPresent(all::add));
        return all.stream().sorted(MOST_RECENT_FIRST).limit(limit).toList();
    }

    @Override
    public Optional<WorkflowIncident> find(String jobId, String tenantId) {
        if (jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        Locator locator = new Locator();
        Job dead = managementService.createDeadLetterJobQuery().jobId(jobId).jobTenantId(tenantId).singleResult();
        if (dead != null) {
            return locator.toIncident(dead, IncidentState.INCIDENT);
        }
        Job executable = managementService.createJobQuery().jobId(jobId).jobTenantId(tenantId).withException()
                .singleResult();
        if (executable != null) {
            return locator.toIncident(executable, IncidentState.RETRYING);
        }
        Job timer = managementService.createTimerJobQuery().jobId(jobId).jobTenantId(tenantId).withException()
                .singleResult();
        return timer == null ? Optional.empty() : locator.toIncident(timer, IncidentState.RETRYING);
    }

    @Override
    public void retry(String jobId, String tenantId, int retries) {
        // Revérifie le tenant ici aussi : l'adaptateur ne rejoue jamais un dead letter d'un autre tenant.
        if (managementService.createDeadLetterJobQuery().jobId(jobId).jobTenantId(tenantId).count() != 1) {
            throw new WorkflowIncidentNotFoundException("No dead-letter job " + jobId);
        }
        Job moved;
        try {
            moved = managementService.moveDeadLetterJobToExecutableJob(jobId, retries);
        } catch (FlowableOptimisticLockingException | FlowableObjectNotFoundException concurrent) {
            // Revue L2 : deux rejeux simultanés — le second perd la course.
            throw new IncidentNotRetryableException("Incident " + jobId + " has already been retried");
        }
        if (moved == null) {
            // Revue L1 : Flowable ne déplace rien (exécution du job introuvable) et ne lève pas d'erreur.
            throw new IncidentNotRetryableException("Incident " + jobId + " cannot be retried: its execution no longer exists");
        }
    }

    /** L'instance et ses sous-process appelés, récursivement. */
    private List<String> withDescendants(String processInstanceId) {
        List<String> all = new ArrayList<>();
        Deque<String> pending = new ArrayDeque<>(List.of(processInstanceId));
        while (!pending.isEmpty()) {
            String current = pending.pop();
            all.add(current);
            runtimeService.createProcessInstanceQuery().superProcessInstanceId(current).list()
                    .forEach(child -> pending.push(child.getId()));
        }
        return all;
    }

    /** Rattache un job à l'instance racine et à l'activité visible dans son diagramme (cache par appel). */
    private final class Locator {

        private final Map<String, Optional<ProcessInstance>> instances = new HashMap<>();

        Optional<WorkflowIncident> toIncident(Job job, IncidentState state) {
            String instanceId = job.getProcessInstanceId();
            if (instanceId == null) {
                return Optional.empty(); // revue M1 : job de définition (minuteur de démarrage), sans dossier
            }
            String activityId = job.getElementId();
            String activityName = blankToNull(job.getElementName()) == null ? activityId : job.getElementName();
            String rootId = instanceId;
            Optional<ProcessInstance> current = instance(instanceId);
            // Remonte la chaîne des callActivity jusqu'à la racine ; l'activité retenue est celle du niveau racine.
            while (current.isPresent() && current.get().getSuperExecutionId() != null) {
                Execution superExecution = runtimeService.createExecutionQuery()
                        .executionId(current.get().getSuperExecutionId()).singleResult();
                if (superExecution == null) {
                    break;
                }
                rootId = superExecution.getProcessInstanceId();
                String callActivityId = superExecution.getActivityId();
                activityName = callActivityName(rootId, callActivityId) + " › " + activityName;
                activityId = callActivityId;
                current = instance(rootId);
            }
            Instant since = job.getCreateTime() == null ? null : job.getCreateTime().toInstant();
            int retries = state == IncidentState.INCIDENT ? 0 : job.getRetries();
            return Optional.of(new WorkflowIncident(job.getId(), rootId, activityId, activityName, state, since, retries,
                    job.getExceptionMessage()));
        }

        private Optional<ProcessInstance> instance(String id) {
            return instances.computeIfAbsent(id, key -> Optional.ofNullable(
                    runtimeService.createProcessInstanceQuery().processInstanceId(key).singleResult()));
        }

        private String callActivityName(String processInstanceId, String activityId) {
            return instance(processInstanceId)
                    .map(ProcessInstance::getProcessDefinitionId)
                    .map(repositoryService::getBpmnModel)
                    .map((BpmnModel model) -> model.getFlowElement(activityId))
                    .map(FlowElement::getName)
                    .map(FlowableWorkflowIncidentAdapter::blankToNull)
                    .orElse(activityId);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
