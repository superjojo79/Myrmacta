package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElementView;
import be.myrmacta.domain.dossier.model.DossierRelationView;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.model.RelationDirection;
import be.myrmacta.domain.dossier.model.RelationType;
import be.myrmacta.domain.dossier.model.DossierListItem;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.domain.dossier.port.in.GetDossierWorkflowHistoryUseCase;
import be.myrmacta.domain.dossier.port.in.ListDossierElementsUseCase;
import be.myrmacta.domain.dossier.port.in.ListDossierRelationsUseCase;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class GetDossierToolTest {

    private static final String TENANT = "tenant_dev";

    private GetDossierUseCase getDossierUseCase;
    private ListDossierElementsUseCase listDossierElementsUseCase;
    private GetDossierWorkflowHistoryUseCase getDossierWorkflowHistoryUseCase;
    private ListDossierRelationsUseCase listDossierRelationsUseCase;
    private be.myrmacta.domain.workflow.port.in.ListDossierIncidentsSystemUseCase incidentsUseCase;
    private GetDossierTool tool;

    @BeforeEach
    void setUp() {
        getDossierUseCase = mock(GetDossierUseCase.class);
        listDossierElementsUseCase = mock(ListDossierElementsUseCase.class);
        getDossierWorkflowHistoryUseCase = mock(GetDossierWorkflowHistoryUseCase.class);
        listDossierRelationsUseCase = mock(ListDossierRelationsUseCase.class);
        incidentsUseCase = mock(be.myrmacta.domain.workflow.port.in.ListDossierIncidentsSystemUseCase.class);
        tool = new GetDossierTool(
                getDossierUseCase,
                listDossierElementsUseCase,
                getDossierWorkflowHistoryUseCase,
                listDossierRelationsUseCase,
                incidentsUseCase,
                new TenantProperties(TENANT),
                new McpAccessContextFactory());

        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("sub", "agent-1")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bothIdAndReference_isRejected() {
        assertThatThrownBy(() -> tool.getDossier(UUID.randomUUID().toString(), "REF-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neitherIdNorReference_isRejected() {
        assertThatThrownBy(() -> tool.getDossier(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aggregatesElementsHistoryAndRelations() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossier(dossierId);
        when(getDossierUseCase.get(eq(dossierId), any())).thenReturn(dossier);
        when(listDossierElementsUseCase.list(eq(dossierId), any(), eq(PageQuery.of(0, 100))))
                .thenReturn(PageResult.of(List.of(element()), 0, 100, 1));
        when(getDossierWorkflowHistoryUseCase.getHistory(eq(dossierId), eq(PageQuery.of(0, 100))))
                .thenReturn(PageResult.of(List.of(historyLink(dossierId)), 0, 100, 1));
        when(listDossierRelationsUseCase.list(eq(dossierId), any()))
                .thenReturn(List.of(relation(dossierId)));

        GetDossierTool.Result result = tool.getDossier(dossierId.toString(), null);

        assertThat(result.dossier().id()).isEqualTo(dossierId);
        assertThat(result.elements()).hasSize(1);
        assertThat(result.history()).hasSize(1);
        assertThat(result.relations()).hasSize(1);
        assertThat(result.incidents()).isEmpty();
    }

    /** TODO §94 — les échecs des jobs du dossier sont exposés, jamais le message de l'exception. */
    @Test
    void exposesTheDossierIncidentsWithoutTheMessage() {
        UUID dossierId = UUID.randomUUID();
        when(getDossierUseCase.get(eq(dossierId), any())).thenReturn(dossier(dossierId));
        when(listDossierElementsUseCase.list(eq(dossierId), any(), any())).thenReturn(PageResult.of(List.of(), 0, 100, 0));
        when(getDossierWorkflowHistoryUseCase.getHistory(eq(dossierId), any())).thenReturn(PageResult.of(List.of(), 0, 100, 0));
        when(listDossierRelationsUseCase.list(eq(dossierId), any())).thenReturn(List.of());
        UUID linkId = UUID.randomUUID();
        when(incidentsUseCase.listForDossierSystem(dossierId)).thenReturn(List.of(
                new be.myrmacta.domain.workflow.model.TenantIncident(
                        new be.myrmacta.domain.workflow.model.WorkflowIncident("job-1", "pi-1", "mailRefus",
                                "E-mail : refus", be.myrmacta.domain.workflow.model.IncidentState.INCIDENT,
                                Instant.EPOCH, 0, "secret detail"),
                        linkId, dossierId, null, "autorisationRiverain")));

        GetDossierTool.Result result = tool.getDossier(dossierId.toString(), null);

        assertThat(result.incidents()).singleElement().satisfies(i -> {
            assertThat(i.activityId()).isEqualTo("mailRefus");
            assertThat(i.state()).isEqualTo(be.myrmacta.domain.workflow.model.IncidentState.INCIDENT);
            assertThat(i.workflowLinkId()).isEqualTo(linkId);
            assertThat(i.jobId()).isEqualTo("job-1");
        });
        assertThat(result.incidents().toString()).doesNotContain("secret detail");
    }

    @Test
    void byReference_usesTenantFromProperties() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossier(dossierId);
        when(getDossierUseCase.getByReference(eq(TENANT), eq("REF-1"), any())).thenReturn(dossier);
        when(listDossierElementsUseCase.list(any(), any(), any())).thenReturn(PageResult.empty(0, 100));
        when(getDossierWorkflowHistoryUseCase.getHistory(any(), any())).thenReturn(PageResult.empty(0, 100));
        when(listDossierRelationsUseCase.list(any(), any())).thenReturn(List.of());

        tool.getDossier(null, "REF-1");

        verify(getDossierUseCase).getByReference(eq(TENANT), eq("REF-1"), any());
    }

    @Test
    void paginatesElementsUntilExhausted() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossier(dossierId);
        when(getDossierUseCase.get(eq(dossierId), any())).thenReturn(dossier);
        when(listDossierElementsUseCase.list(eq(dossierId), any(), eq(PageQuery.of(0, 100))))
                .thenReturn(PageResult.of(List.of(element()), 0, 100, 150));
        when(listDossierElementsUseCase.list(eq(dossierId), any(), eq(PageQuery.of(1, 100))))
                .thenReturn(PageResult.of(List.of(element()), 1, 100, 150));
        when(getDossierWorkflowHistoryUseCase.getHistory(any(), any())).thenReturn(PageResult.empty(0, 100));
        when(listDossierRelationsUseCase.list(any(), any())).thenReturn(List.of());

        GetDossierTool.Result result = tool.getDossier(dossierId.toString(), null);

        assertThat(result.elements()).hasSize(2);
    }

    private static Dossier dossier(UUID id) {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new Dossier(
                id, TENANT, "REF-1", "SINISTRE", "Label", DossierStatus.OUVERT,
                Map.of("amount", 10), null, 0L, "creator", now, "creator", now, null, null, null);
    }

    private static DossierElementView element() {
        return new DossierElementView(
                UUID.randomUUID(), ElementType.NOTE, "Note", "user", Instant.now(), null, Map.of(), false);
    }

    private static DossierWorkflowLink historyLink(UUID dossierId) {
        return new DossierWorkflowLink(
                UUID.randomUUID(), dossierId, TENANT, "proc-1", "wf", 1, "STARTED", "manual",
                Instant.now(), null, null, "starter");
    }

    private static DossierRelationView relation(UUID dossierId) {
        return new DossierRelationView(
                UUID.randomUUID(), RelationType.LINKED, RelationDirection.OUTGOING,
                new DossierListItem(dossierId, "REF-2", "Other", "SINISTRE", "Sinistre",
                        DossierStatus.OUVERT, Instant.now(), Instant.now()),
                "creator", Instant.now());
    }
}
