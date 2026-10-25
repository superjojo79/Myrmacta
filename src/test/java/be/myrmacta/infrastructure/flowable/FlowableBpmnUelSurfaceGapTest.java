package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import be.myrmacta.domain.workflow.port.out.BpmnParserPort;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * TODO §69 (suite de H1-bis, TODO §57/§58) : preuve d'exécution réelle que l'allowlist BPMN ne
 * couvrait que {@code assignee}/{@code candidateUsers}/{@code candidateGroups}, alors que Flowable
 * 8 ({@code UserTaskActivityBehavior}) évalue aussi en UEL {@code name}, {@code formKey},
 * {@code documentation}, {@code dueDate}, {@code priority}, {@code category}, {@code owner}... --
 * et, au-delà du {@code userTask}, les timers, {@code callActivity}, le multi-instance,
 * {@code skipExpression}, etc.
 *
 * <p>Le premier test caractérise le moteur (il déploie directement via {@link RepositoryService},
 * sans passer par notre parseur) : une méthode d'un bean Spring vivant est effectivement invoquée
 * depuis {@code name} et {@code formKey}. Il reste vert après correctif -- il documente pourquoi
 * la règle du parseur existe. Le second fige le correctif : le même document est rejeté par
 * {@link BpmnParserPort} avant d'atteindre le moteur (il échouait avant correctif : document
 * accepté).
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class FlowableBpmnUelSurfaceGapTest {

    private static final String BEAN_METHOD_CALL = "${connectorServiceTask.getClass().getName()}";

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://myrmacta/test">
              <process id="%s" isExecutable="true">
                <startEvent id="start"/>
                <userTask id="wait" name="%s" flowable:formKey="%s"/>
                <endEvent id="end"/>
                <sequenceFlow id="flow1" sourceRef="start" targetRef="wait"/>
                <sequenceFlow id="flow2" sourceRef="wait" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Autowired
    private BpmnParserPort bpmnParserPort;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Test
    void engine_evaluatesUserTaskNameAndFormKey_invokingASpringBeanMethod() {
        String key = "uelSurfaceEngine" + System.nanoTime();
        repositoryService.createDeployment()
                .addString(key + ".bpmn20.xml", bpmn(key))
                .deploy();

        String processInstanceId = runtimeService.startProcessInstanceByKey(key).getId();
        Task task = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();

        assertThat(task.getName()).isEqualTo(ConnectorServiceTask.class.getName());
        assertThat(task.getFormKey()).isEqualTo(ConnectorServiceTask.class.getName());
    }

    @Test
    void parser_rejectsUserTaskNameAndFormKeyExpressions_beforeReachingTheEngine() {
        byte[] bpmnXml = bpmn("uelSurfaceParser" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> bpmnParserPort.extractProcessDefinitionKey(bpmnXml))
                .isInstanceOf(InvalidBpmnException.class)
                .hasMessageContaining("expression is not allowed");
    }

    private static String bpmn(String key) {
        return BPMN.formatted(key, BEAN_METHOD_CALL, BEAN_METHOD_CALL);
    }
}
