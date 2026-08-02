package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.application.workflow.ManualStartOrchestrator;
import com.flowforge.domain.dossier.port.in.AttachWorkflowUseCase;
import com.flowforge.domain.workflow.port.in.StartWorkflowUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.port.out.WorkflowRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DomainConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void productionWiringUsesFlowableWorkflowEngineInsteadOfLegacyInMemoryStart() {
        assertThat(applicationContext.getBeansOfType(StartWorkflowUseCase.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(WorkflowRepository.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ManualStartOrchestrator.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(WorkflowEnginePort.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(AttachWorkflowUseCase.class)).hasSize(1);
    }
}
