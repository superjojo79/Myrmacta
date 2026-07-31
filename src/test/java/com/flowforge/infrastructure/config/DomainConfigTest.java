package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.workflow.model.StartWorkflowCommand;
import com.flowforge.domain.workflow.model.WorkflowInstance;
import com.flowforge.domain.workflow.model.WorkflowStatus;
import com.flowforge.domain.workflow.port.in.StartWorkflowUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DomainConfigTest {

    @Autowired
    private StartWorkflowUseCase startWorkflowUseCase;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Test
    void start_viaSpringBeans_persistsInstance() {
        WorkflowInstance instance = startWorkflowUseCase.start(
                new StartWorkflowCommand("demo-process", "tenant_a"));

        assertThat(instance.status()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(workflowRepository.findById(instance.id())).contains(instance);
    }
}
