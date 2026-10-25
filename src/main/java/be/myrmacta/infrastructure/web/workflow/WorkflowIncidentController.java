package be.myrmacta.infrastructure.web.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.workflow.model.IncidentState;
import be.myrmacta.domain.workflow.model.TenantIncident;
import be.myrmacta.domain.workflow.model.WorkflowIncident;
import be.myrmacta.domain.workflow.port.in.WorkflowIncidentsUseCase;
import be.myrmacta.infrastructure.security.SecurityAuditLog;
import be.myrmacta.infrastructure.web.dossier.AccessContextFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    /**
     * Flowable participe à la transaction ambiante. Revue L5 : la trace d'audit n'est écrite qu'après le commit — un rejeu
     * annulé n'est pas journalisé comme effectué.
     */
    @PostMapping("/api/workflow-links/{linkId}/incidents/{jobId}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void retry(
            @PathVariable UUID linkId,
            @PathVariable String jobId,
            Authentication authentication,
            HttpServletRequest request) {
        useCase.retry(linkId, jobId, accessContextFactory.user(authentication));
        String method = request.getMethod();
        String path = request.getRequestURI();
        Runnable audit = () -> SecurityAuditLog.incidentRetried(method, path, authentication, linkId, jobId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    audit.run();
                }
            });
        } else {
            audit.run();
        }
    }

    /** Revue Info : défense en profondeur, en plus de la garde du use case (même règle que les brouillons). */
    @GetMapping("/api/admin/incidents")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
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
