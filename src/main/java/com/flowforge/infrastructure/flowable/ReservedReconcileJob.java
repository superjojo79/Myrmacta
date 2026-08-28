package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.dossier.model.AbortReservedReason;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.port.in.AttachWorkflowUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.infrastructure.config.WorkflowProperties;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ReservedReconcileJob {

    private final DossierWorkflowLinkRepository linkRepository;
    private final AttachWorkflowUseCase attachWorkflow;
    private final WorkflowEnginePort workflowEnginePort;
    private final DossierRepository dossierRepository;
    private final WorkflowProperties properties;

    ReservedReconcileJob(
            DossierWorkflowLinkRepository linkRepository,
            AttachWorkflowUseCase attachWorkflow,
            WorkflowEnginePort workflowEnginePort,
            DossierRepository dossierRepository,
            WorkflowProperties properties) {
        this.linkRepository = linkRepository;
        this.attachWorkflow = attachWorkflow;
        this.workflowEnginePort = workflowEnginePort;
        this.dossierRepository = dossierRepository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.workflow.reserve-timeout:5m}")
    void reconcileReserved() {
        Instant olderThan = Instant.now().minus(properties.reserveTimeout());
        for (DossierWorkflowLink link : linkRepository.findOrphanReserved(olderThan)) {
            reconcile(link);
        }
    }

    void reconcile(DossierWorkflowLink link) {
        var existing = workflowEnginePort.findProcessInstanceIdByBusinessKey(link.id().toString());
        if (existing.isPresent()) {
            attachWorkflow.confirmStarted(link.id(), existing.get());
            return;
        }
        try {
            Dossier dossier = dossierRepository.findById(link.dossierId())
                    .orElseThrow(() -> new IllegalStateException("Dossier not found: " + link.dossierId()));
            String processInstanceId = workflowEnginePort.start(
                    link.processDefinitionKey(),
                    link.tenantId(),
                    link.id().toString(),
                    WorkflowVariables.withPlatformVariables(null, link, dossier.fields()));
            attachWorkflow.confirmStarted(link.id(), processInstanceId);
        } catch (RuntimeException ex) {
            attachWorkflow.abortReserved(link.id(), AbortReservedReason.ORPHAN_TIMEOUT);
        }
    }
}
