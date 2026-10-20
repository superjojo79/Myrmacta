package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElementView;
import com.flowforge.domain.dossier.model.DossierRelationView;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierWorkflowHistoryUseCase;
import com.flowforge.domain.dossier.port.in.ListDossierElementsUseCase;
import com.flowforge.domain.dossier.port.in.ListDossierRelationsUseCase;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.port.in.ListDossierIncidentsSystemUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class GetDossierTool {

    private static final int PAGE_SIZE = 100;

    private final GetDossierUseCase getDossierUseCase;
    private final ListDossierElementsUseCase listDossierElementsUseCase;
    private final GetDossierWorkflowHistoryUseCase getDossierWorkflowHistoryUseCase;
    private final ListDossierRelationsUseCase listDossierRelationsUseCase;
    private final ListDossierIncidentsSystemUseCase incidentsUseCase;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;

    public GetDossierTool(
            GetDossierUseCase getDossierUseCase,
            ListDossierElementsUseCase listDossierElementsUseCase,
            GetDossierWorkflowHistoryUseCase getDossierWorkflowHistoryUseCase,
            ListDossierRelationsUseCase listDossierRelationsUseCase,
            ListDossierIncidentsSystemUseCase incidentsUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory) {
        this.getDossierUseCase = Objects.requireNonNull(getDossierUseCase, "getDossierUseCase");
        this.listDossierElementsUseCase =
                Objects.requireNonNull(listDossierElementsUseCase, "listDossierElementsUseCase");
        this.getDossierWorkflowHistoryUseCase =
                Objects.requireNonNull(getDossierWorkflowHistoryUseCase, "getDossierWorkflowHistoryUseCase");
        this.listDossierRelationsUseCase =
                Objects.requireNonNull(listDossierRelationsUseCase, "listDossierRelationsUseCase");
        this.incidentsUseCase = Objects.requireNonNull(incidentsUseCase, "incidentsUseCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "get_dossier",
            description = "Get a dossier (redacted) with its elements, workflow history, relations and incidents "
                    + "(engine jobs that failed: RETRYING while automatic retries remain, INCIDENT once in dead letter "
                    + "— a human administrator retries it from the UI; the exception message is never returned). "
                    + "Exactly one of id or reference is required.")
    public Result getDossier(
            @McpToolParam(required = false, description = "Dossier id (UUID)") String id,
            @McpToolParam(required = false, description = "Dossier reference") String reference) {
        boolean hasId = id != null && !id.isBlank();
        boolean hasReference = reference != null && !reference.isBlank();
        if (hasId == hasReference) {
            throw new IllegalArgumentException("Exactly one of id or reference is required");
        }

        AccessContext.User actor = accessContextFactory.currentActor();
        Dossier dossier = hasId
                ? getDossierUseCase.get(UUID.fromString(id), actor)
                : getDossierUseCase.getByReference(tenantProperties.id(), reference, actor);

        List<DossierElementView> elements = drain(
                query -> listDossierElementsUseCase.list(dossier.id(), actor, query));
        List<DossierWorkflowLink> history = drain(
                query -> getDossierWorkflowHistoryUseCase.getHistory(dossier.id(), query));
        List<DossierRelationView> relations = listDossierRelationsUseCase.list(dossier.id(), actor);

        // TODO §94 : lecture seule, sans message (D-INCIDENT-MESSAGE) ; le dossier est déjà autorisé ci-dessus.
        List<IncidentMcpView> incidents = incidentsUseCase.listForDossierSystem(dossier.id()).stream()
                .map(IncidentMcpView::from)
                .toList();
        return new Result(DossierMcpView.from(dossier), elements, history, relations, incidents);
    }

    private static <T> List<T> drain(java.util.function.Function<PageQuery, PageResult<T>> fetch) {
        List<T> all = new ArrayList<>();
        int page = 0;
        while (true) {
            PageResult<T> result = fetch.apply(PageQuery.of(page, PAGE_SIZE));
            all.addAll(result.content());
            if (page + 1 >= result.totalPages()) {
                return all;
            }
            page++;
        }
    }

    public record Result(
            DossierMcpView dossier,
            List<DossierElementView> elements,
            List<DossierWorkflowLink> history,
            List<DossierRelationView> relations,
            List<IncidentMcpView> incidents) {}

    /** TODO §94 — échec d'un job du dossier, sans message (D-INCIDENT-MESSAGE). */
    public record IncidentMcpView(
            java.util.UUID workflowLinkId,
            String jobId,
            String activityId,
            String activityName,
            com.flowforge.domain.workflow.model.IncidentState state,
            java.time.Instant since,
            int retriesLeft) {

        static IncidentMcpView from(com.flowforge.domain.workflow.model.TenantIncident row) {
            var i = row.incident();
            return new IncidentMcpView(row.linkId(), i.jobId(), i.activityId(), i.activityName(), i.state(), i.since(),
                    i.retriesLeft());
        }
    }
}
