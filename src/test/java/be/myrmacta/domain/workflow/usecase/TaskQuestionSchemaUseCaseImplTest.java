package be.myrmacta.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Focused on the domain RBAC guard added by extension-mcp-flux-metier (D5) — full behavioral
 * coverage of save/get/list/delete already exists end-to-end via {@code TaskQuestionSchemaApiTest}.
 */
class TaskQuestionSchemaUseCaseImplTest {

    private static final AccessContext.User TENANT_ADMIN = AccessContext.user("admin", Set.of("TENANT_ADMIN"));
    private static final AccessContext.User WORKFLOW_ADMIN =
            AccessContext.user("wf-admin", Set.of("WORKFLOW_ADMIN"));
    private static final AccessContext.User API_SERVICE = AccessContext.user("svc", Set.of("API_SERVICE"));
    private static final AccessContext.User NO_ROLE = AccessContext.user("nobody", Set.of("DOSSIER_MANAGER"));

    private TaskQuestionSchemaRepository repository;
    private TaskQuestionSchemaUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        repository = mock(TaskQuestionSchemaRepository.class);
        useCase = new TaskQuestionSchemaUseCaseImpl("tenant_dev", repository, new TenantAdminGuard());
    }

    private static TaskQuestionSchema schema() {
        return new TaskQuestionSchema(
                UUID.randomUUID(),
                "tenant_dev",
                "orientation",
                "Orientation",
                List.of(new FieldDefinition("decision", "Decision", FieldType.STRING, true, null, List.of(), null, false)),
                "admin",
                "admin");
    }

    @Test
    void save_tenantAdmin_isAllowed() {
        TaskQuestionSchema schema = schema();
        when(repository.save(schema)).thenReturn(schema);
        assertThat(useCase.save(schema, TENANT_ADMIN)).isEqualTo(schema);
    }

    @Test
    void save_workflowAdmin_isAllowed() {
        TaskQuestionSchema schema = schema();
        when(repository.save(schema)).thenReturn(schema);
        assertThat(useCase.save(schema, WORKFLOW_ADMIN)).isEqualTo(schema);
    }

    @Test
    void save_apiService_isAllowed() {
        TaskQuestionSchema schema = schema();
        when(repository.save(schema)).thenReturn(schema);
        assertThat(useCase.save(schema, API_SERVICE)).isEqualTo(schema);
    }

    @Test
    void save_withoutRequiredRole_isDenied() {
        TaskQuestionSchema schema = schema();
        assertThatThrownBy(() -> useCase.save(schema, NO_ROLE)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getByKey_withoutRequiredRole_isDenied() {
        assertThatThrownBy(() -> useCase.getByKey("orientation", NO_ROLE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getByKey_apiService_isAllowed() {
        when(repository.findByKey("tenant_dev", "orientation")).thenReturn(Optional.of(schema()));
        assertThat(useCase.getByKey("orientation", API_SERVICE)).isPresent();
    }

    @Test
    void list_withoutRequiredRole_isDenied() {
        assertThatThrownBy(() -> useCase.list(PageQuery.defaults(), NO_ROLE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void list_apiService_isAllowed() {
        when(repository.page("tenant_dev", PageQuery.defaults()))
                .thenReturn(PageResult.of(List.of(), 0, 20, 0));
        assertThat(useCase.list(PageQuery.defaults(), API_SERVICE).content()).isEmpty();
    }

    @Test
    void delete_withoutRequiredRole_isDenied() {
        assertThatThrownBy(() -> useCase.delete("orientation", NO_ROLE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void delete_apiService_isAllowed() {
        useCase.delete("orientation", API_SERVICE);
    }
}
