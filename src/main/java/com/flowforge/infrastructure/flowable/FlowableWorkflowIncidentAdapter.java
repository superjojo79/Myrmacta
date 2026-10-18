package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.model.IncidentState;
import com.flowforge.domain.workflow.model.WorkflowIncident;
import com.flowforge.domain.workflow.model.WorkflowIncidentNotFoundException;
import com.flowforge.domain.workflow.port.out.WorkflowIncidentPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.flowable.engine.ManagementService;
import org.flowable.job.api.Job;
import org.springframework.stereotype.Component;

/**
 * TODO §94 — jobs du moteur en échec, toujours filtrés par tenant ({@code jobTenantId}) : job exécutable ou minuteur
 * avec exception = {@link IncidentState#RETRYING} (le moteur retentera) ; dead letter = {@link IncidentState#INCIDENT}.
 * {@code since} est la date de création du job dans son état courant : Flowable ne garde pas d'horodatage d'échec.
 */
@Component
class FlowableWorkflowIncidentAdapter implements WorkflowIncidentPort {

    private final ManagementService managementService;

    FlowableWorkflowIncidentAdapter(ManagementService managementService) {
        this.managementService = Objects.requireNonNull(managementService, "managementService");
    }

    @Override
    public List<WorkflowIncident> listByProcessInstance(String processInstanceId, String tenantId) {
        Objects.requireNonNull(processInstanceId, "processInstanceId");
        Stream<WorkflowIncident> retrying = Stream.concat(
                managementService.createJobQuery().processInstanceId(processInstanceId).jobTenantId(tenantId)
                        .withException().list().stream(),
                managementService.createTimerJobQuery().processInstanceId(processInstanceId).jobTenantId(tenantId)
                        .withException().list().stream())
                .map(job -> toIncident(job, IncidentState.RETRYING));
        Stream<WorkflowIncident> dead = managementService.createDeadLetterJobQuery()
                .processInstanceId(processInstanceId).jobTenantId(tenantId).list().stream()
                .map(job -> toIncident(job, IncidentState.INCIDENT));
        return Stream.concat(dead, retrying).sorted(MOST_RECENT_FIRST).toList();
    }

    @Override
    public List<WorkflowIncident> listByTenant(String tenantId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<WorkflowIncident> all = new ArrayList<>();
        managementService.createDeadLetterJobQuery().jobTenantId(tenantId).orderByJobCreateTime().desc()
                .listPage(0, limit).forEach(job -> all.add(toIncident(job, IncidentState.INCIDENT)));
        managementService.createJobQuery().jobTenantId(tenantId).withException().orderByJobCreateTime().desc()
                .listPage(0, limit).forEach(job -> all.add(toIncident(job, IncidentState.RETRYING)));
        managementService.createTimerJobQuery().jobTenantId(tenantId).withException().orderByJobCreateTime().desc()
                .listPage(0, limit).forEach(job -> all.add(toIncident(job, IncidentState.RETRYING)));
        return all.stream().sorted(MOST_RECENT_FIRST).limit(limit).toList();
    }

    @Override
    public Optional<WorkflowIncident> find(String jobId, String tenantId) {
        if (jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        Job dead = managementService.createDeadLetterJobQuery().jobId(jobId).jobTenantId(tenantId).singleResult();
        if (dead != null) {
            return Optional.of(toIncident(dead, IncidentState.INCIDENT));
        }
        Job executable = managementService.createJobQuery().jobId(jobId).jobTenantId(tenantId).withException()
                .singleResult();
        if (executable != null) {
            return Optional.of(toIncident(executable, IncidentState.RETRYING));
        }
        Job timer = managementService.createTimerJobQuery().jobId(jobId).jobTenantId(tenantId).withException()
                .singleResult();
        return Optional.ofNullable(timer).map(job -> toIncident(job, IncidentState.RETRYING));
    }

    @Override
    public void retry(String jobId, String tenantId, int retries) {
        // Revérifie le tenant ici aussi : l'adaptateur ne rejoue jamais un dead letter d'un autre tenant.
        if (managementService.createDeadLetterJobQuery().jobId(jobId).jobTenantId(tenantId).count() != 1) {
            throw new WorkflowIncidentNotFoundException("No dead-letter job " + jobId);
        }
        managementService.moveDeadLetterJobToExecutableJob(jobId, retries);
    }

    private static final Comparator<WorkflowIncident> MOST_RECENT_FIRST = Comparator.comparing(
            WorkflowIncident::since, Comparator.nullsLast(Comparator.reverseOrder()));

    private static WorkflowIncident toIncident(Job job, IncidentState state) {
        String activityId = job.getElementId();
        String activityName = job.getElementName() == null || job.getElementName().isBlank()
                ? activityId : job.getElementName();
        Instant since = job.getCreateTime() == null ? null : job.getCreateTime().toInstant();
        int retries = state == IncidentState.INCIDENT ? 0 : job.getRetries();
        return new WorkflowIncident(job.getId(), job.getProcessInstanceId(), activityId, activityName, state, since,
                retries, job.getExceptionMessage());
    }
}
