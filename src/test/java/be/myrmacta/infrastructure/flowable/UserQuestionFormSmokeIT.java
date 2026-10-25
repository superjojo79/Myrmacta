package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.workflow.TaskQuestionFormKeys;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import be.myrmacta.domain.workflow.port.in.CompleteTaskUseCase;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Regression fixture ({@code user-question-smoke.bpmn20.xml}) proving the {@code user-question}
 * kind (TODO §55) end to end against the real Flowable 8.0.0 engine: {@code formKey} is read
 * server-side (D-USERQ-FORMKEY-PARSING), the submitted answer is validated against the persisted
 * {@link TaskQuestionSchema}, and the resulting process variable is actually read by the
 * downstream {@code exclusiveGateway} -- not just emitted (TODO §52 Phase 9 found a real bug from
 * testing each side of a similar boundary in isolation).
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class UserQuestionFormSmokeIT {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired
    private TaskQuestionSchemaRepository taskQuestionSchemaRepository;

    @Autowired
    private CompleteTaskUseCase completeTaskUseCase;

    @Test
    void completingUserQuestionTaskForm_routesTheExclusiveGatewayByTheSubmittedDecision() {
        String tenantId = tenantProperties.id();
        taskQuestionSchemaRepository.save(new TaskQuestionSchema(
                UUID.randomUUID(),
                tenantId,
                "orientation-dossier",
                "Orientation",
                List.of(new FieldDefinition(
                        "decision", "Decision", FieldType.SELECT, true, null, List.of("approve", "reject"), null)),
                "system",
                "system"));

        String dossierId = UUID.randomUUID().toString();
        runtimeService.startProcessInstanceByKeyAndTenantId(
                "userQuestionSmoke", "user-question-smoke-" + UUID.randomUUID(), tenantId);

        Task task = taskService.createTaskQuery()
                .processDefinitionKey("userQuestionSmoke")
                .active()
                .singleResult();
        assertThat(task).isNotNull();
        assertThat(task.getFormKey()).isEqualTo(TaskQuestionFormKeys.formKeyFor("orientation-dossier"));

        completeTaskUseCase.complete(
                task.getId(),
                dossierId,
                Map.of("decision", "approve"),
                AccessContext.user("actor-1", Set.of("DOSSIER_MANAGER")));

        assertThat(taskService.createTaskQuery()
                        .processDefinitionKey("userQuestionSmoke")
                        .active()
                        .list())
                .isEmpty();
        assertThat(historyService.createHistoricActivityInstanceQuery()
                        .processInstanceId(task.getProcessInstanceId())
                        .activityId("userQuestionSmokeApproved")
                        .list())
                .hasSize(1);
        assertThat(historyService.createHistoricActivityInstanceQuery()
                        .processInstanceId(task.getProcessInstanceId())
                        .activityId("userQuestionSmokeRejected")
                        .list())
                .isEmpty();
    }
}
