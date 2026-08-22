package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.workflow.model.BpmnDeploymentResult;
import com.flowforge.domain.workflow.model.ProcessDeploymentInfo;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class FlowableWorkflowDeploymentAdapterTest {

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         targetNamespace="http://flowforge/test">
              <process id="%s" isExecutable="true">
                <startEvent id="start"/>
                <userTask id="wait" name="Wait"/>
                <endEvent id="end"/>
                <sequenceFlow id="flow1" sourceRef="start" targetRef="wait"/>
                <sequenceFlow id="flow2" sourceRef="wait" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TenantProperties tenantProperties;

    @Autowired
    private FlowableWorkflowDeploymentAdapter adapter;

    @Test
    void deploy_newProcess_createsFirstVersionUsableImmediately() {
        String key = "adapterDeployNew" + System.nanoTime();

        BpmnDeploymentResult result = adapter.deploy(
                tenantProperties.id(), "diagram.bpmn20.xml", bpmn(key));

        assertThat(result.processDefinitionKey()).isEqualTo(key);
        assertThat(result.deployedVersion()).isEqualTo(1);
        assertThat(result.deploymentId()).isNotBlank();

        runtimeService.startProcessInstanceByKeyAndTenantId(key, tenantProperties.id());
        assertThat(runtimeService
                        .createProcessInstanceQuery()
                        .processDefinitionKey(key)
                        .processInstanceTenantId(tenantProperties.id())
                        .count())
                .isEqualTo(1);
    }

    @Test
    void deploy_sameKeyTwice_incrementsVersionWithoutAffectingExistingInstances() {
        String key = "adapterRedeploy" + System.nanoTime();

        adapter.deploy(tenantProperties.id(), "diagram.bpmn20.xml", bpmn(key));
        String firstInstanceId = runtimeService
                .startProcessInstanceByKeyAndTenantId(key, tenantProperties.id())
                .getId();

        BpmnDeploymentResult second = adapter.deploy(tenantProperties.id(), "diagram.bpmn20.xml", bpmn(key));

        assertThat(second.deployedVersion()).isEqualTo(2);
        var firstInstanceDefinition = runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(firstInstanceId)
                .singleResult()
                .getProcessDefinitionVersion();
        assertThat(firstInstanceDefinition).isEqualTo(1);
    }

    @Test
    void findDeploymentInfo_afterDeployAndStart_returnsLatestVersionAndActiveCount() {
        String key = "adapterInfo" + System.nanoTime();
        adapter.deploy(tenantProperties.id(), "diagram.bpmn20.xml", bpmn(key));
        runtimeService.startProcessInstanceByKeyAndTenantId(key, tenantProperties.id());
        runtimeService.startProcessInstanceByKeyAndTenantId(key, tenantProperties.id());

        Optional<ProcessDeploymentInfo> info = adapter.findDeploymentInfo(tenantProperties.id(), key);

        assertThat(info).isPresent();
        assertThat(info.get().processDefinitionKey()).isEqualTo(key);
        assertThat(info.get().latestVersion()).isEqualTo(1);
        assertThat(info.get().activeInstanceCount()).isEqualTo(2);
    }

    @Test
    void findDeploymentInfo_keyNeverDeployed_returnsEmpty() {
        Optional<ProcessDeploymentInfo> info =
                adapter.findDeploymentInfo(tenantProperties.id(), "neverDeployedKey" + System.nanoTime());

        assertThat(info).isEmpty();
    }

    private static byte[] bpmn(String key) {
        return BPMN.formatted(key).getBytes(StandardCharsets.UTF_8);
    }
}
