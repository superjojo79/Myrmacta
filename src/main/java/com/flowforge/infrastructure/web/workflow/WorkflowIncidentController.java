package com.flowforge.infrastructure.web.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.workflow.model.IncidentState;
import com.flowforge.domain.workflow.model.TenantIncident;
import com.flowforge.domain.workflow.model.WorkflowIncident;
import com.flowforge.domain.workflow.port.in.WorkflowIncidentsUseCase;
import com.flowforge.infrastructure.security.SecurityAuditLog;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §94 — jobs du moteur en échec : liste d'une exécution, rejeu d'un incident (TA/WA, journalisé), supervision du
 * tenant. Les droits et la rétention du message (D-INCIDENT-MESSAGE) sont appliqués par le use case.
 */
@RestController
class WorkflowIncidentController {

    private final WorkflowIncidentsUseCase useCase;
    private final AccessContextFactory accessContextFactory;

    WorkflowIncidentController(WorkflowIncidentsUseCase useCase, AccessContextFactory accessContextFactory) {
        this.useCase = useCase;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping("/api/workflow-links/{linkId}/incidents")
    List<IncidentResponse> incidents(@PathVariable UUID linkId, Authentication authentication) {
        return useCase.listForLink(linkId, accessContextFactory.user(authentication)).stream()
                .map(IncidentResponse::from)
                .toList();
    }

    /** Flowable participe à la transaction ambiante : le rejeu et sa trace d'audit vont ensemble. */
    @PostMapping("/api/workflow-links/{linkId}/incidents/{jobId}/retry")
    @Transactional
    ResponseEntity<Void> retry(
            @PathVariable UUID linkId,
            @PathVariable String jobId,
            Authentication authentication,
            HttpServletRequest request) {
        useCase.retry(linkId, jobId, accessContextFactory.user(authentication));
        SecurityAuditLog.incidentRetried(request, authentication, linkId, jobId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/incidents")
    TenantIncidentsResponse tenantIncidents(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication) {
        PageQuery query = new PageQuery(page == null ? 0 : page, size == null ? PageQuery.DEFAULT_SIZE : size);
        WorkflowIncidentsUseCase.TenantIncidentPage result =
                useCase.listForTenant(accessContextFactory.user(authentication), query);
        return new TenantIncidentsResponse(
                result.page().content().stream().map(TenantIncidentResponse::from).toList(),
                result.page().page(),
                result.page().size(),
                result.page().totalElements(),
                result.page().totalPages(),
                result.truncated());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record IncidentResponse(
            String jobId,
            String activityId,
            String activityName,
            IncidentState state,
            Instant since,
            int retriesLeft,
            String message) {

        static IncidentResponse from(WorkflowIncident incident) {
            return new IncidentResponse(incident.jobId(), incident.activityId(), incident.activityName(),
                    incident.state(), incident.since(), incident.retriesLeft(), incident.message());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TenantIncidentResponse(
            String jobId,
            String activityId,
            String activityName,
            IncidentState state,
            Instant since,
            int retriesLeft,
            String message,
            UUID linkId,
            UUID dossierId,
            String dossierReference,
            String processDefinitionKey) {

        static TenantIncidentResponse from(TenantIncident row) {
            WorkflowIncident i = row.incident();
            return new TenantIncidentResponse(i.jobId(), i.activityId(), i.activityName(), i.state(), i.since(),
                    i.retriesLeft(), i.message(), row.linkId(), row.dossierId(), row.dossierReference(),
                    row.processDefinitionKey());
        }
    }

    /** Enveloppe paginée standard, plus {@code truncated} (D-INCIDENT-SCAN). */
    record TenantIncidentsResponse(
            List<TenantIncidentResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean truncated) {}
}
