package be.myrmacta.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.StatusOutcome;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Correctif extension-mcp-flux-metier (D5) : {@code create}/{@code update} gagnent le garde-fou
 * domaine (écart pré-existant depuis {@code deploy_workflow}, PR #82) ; {@code getByKey}/
 * {@code list} doivent rester ouverts (non-régression explicite sur {@code list_workflow_types}).
 */
class WorkflowTypeConfigUseCaseImplTest {

    private static final AccessContext.User TENANT_ADMIN = AccessContext.user("admin", Set.of("TENANT_ADMIN"));
    private static final AccessContext.User WORKFLOW_ADMIN =
            AccessContext.user("wf-admin", Set.of("WORKFLOW_ADMIN"));
    private static final AccessContext.User API_SERVICE = AccessContext.user("svc", Set.of("API_SERVICE"));
    private static final AccessContext.User NO_ROLE = AccessContext.user("nobody", Set.of("DOSSIER_MANAGER"));

    private WorkflowTypeConfigRepository repository;
    private WorkflowTypeConfigUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        repository = mock(WorkflowTypeConfigRepository.class);
        useCase = new WorkflowTypeConfigUseCaseImpl("tenant_dev", repository, new TenantAdminGuard());
    }

    private static WorkflowTypeConfig config() {
        return new WorkflowTypeConfig(
                UUID.randomUUID(),
                "tenant_dev",
                "smoke-process",
                "Smoke",
                List.of(new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE)),
                List.of(DossierStatus.OUVERT),
                null,
                List.of(),
                "admin",
                "admin");
    }

    @Test
    void create_tenantAdmin_isAllowed() {
        WorkflowTypeConfig config = config();
        when(repository.save(config)).thenReturn(config);
        assertThat(useCase.create(config, TENANT_ADMIN)).isEqualTo(config);
    }

    @Test
    void create_workflowAdmin_isAllowed() {
        WorkflowTypeConfig config = config();
        when(repository.save(config)).thenReturn(config);
        assertThat(useCase.create(config, WORKFLOW_ADMIN)).isEqualTo(config);
    }

    @Test
    void create_apiService_isAllowed() {
        WorkflowTypeConfig config = config();
        when(repository.save(config)).thenReturn(config);
        assertThat(useCase.create(config, API_SERVICE)).isEqualTo(config);
    }

    @Test
    void create_withoutRequiredRole_isDenied() {
        WorkflowTypeConfig config = config();
        assertThatThrownBy(() -> useCase.create(config, NO_ROLE)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_withoutRequiredRole_isDenied() {
        WorkflowTypeConfig config = config();
        assertThatThrownBy(() -> useCase.update(config, NO_ROLE)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getByKey_withoutAnyRole_isStillAllowed_noRegression() {
        when(repository.findByWorkflowKey("tenant_dev", "smoke-process")).thenReturn(Optional.of(config()));
        assertThat(useCase.getByKey("smoke-process")).isPresent();
    }

    @Test
    void list_withoutAnyRole_isStillAllowed_noRegression() {
        when(repository.page("tenant_dev", PageQuery.defaults()))
                .thenReturn(PageResult.of(List.of(config()), 0, 20, 1));
        assertThat(useCase.list(PageQuery.defaults()).content()).hasSize(1);
    }
}
