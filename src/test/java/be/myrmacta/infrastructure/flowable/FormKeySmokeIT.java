package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.UUID;
import org.flowable.engine.TaskService;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Smoke BPMN ({@code form-key-smoke.bpmn20.xml}) : proves {@code Task.getFormKey()} is readable
 * on the real Flowable 8.0.0 engine shipped by this repository -- without the optional
 * {@code flowable-form} module on the classpath (verified absent from {@code pom.xml}, TODO §52).
 * M0 spike (https://github.com/superjojo79/FlowForge/blob/e91aa7b0f1d781e02e8418a72da40cf6b027d9ce/docs/ai/planning/2026-09-03-feature-formulaires-dynamiques-taches.md) : if this test
 * fails, the design's central technical assumption is wrong and the feature must return to
 * Phase 3 before any of the {@code formKey} plumbing (M2) is built on top of it.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class FormKeySmokeIT {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TenantProperties tenantProperties;

    @Test
    void activeUserTask_exposesFlowableFormKey_withoutFlowableFormModule() {
        String tenantId = tenantProperties.id();
        runtimeService.startProcessInstanceByKeyAndTenantId(
                "formKeySmoke", "form-key-smoke-" + UUID.randomUUID(), tenantId);

        Task task = taskService.createTaskQuery()
                .processDefinitionKey("formKeySmoke")
                .active()
                .singleResult();

        assertThat(task).isNotNull();
        assertThat(task.getFormKey()).isEqualTo("dossier-fields");
    }
}
