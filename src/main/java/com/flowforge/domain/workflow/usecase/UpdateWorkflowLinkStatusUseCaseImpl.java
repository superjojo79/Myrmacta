package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.DossierWorkflowLinkNotFoundException;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.model.WorkflowTypeConfigNotFoundException;
import com.flowforge.domain.workflow.port.in.UpdateWorkflowLinkStatusUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class UpdateWorkflowLinkStatusUseCaseImpl implements UpdateWorkflowLinkStatusUseCase {

    private final DossierWorkflowLinkRepository linkRepository;
    private final DossierRepository dossierRepository;
    private final WorkflowTypeConfigRepository configRepository;

    public UpdateWorkflowLinkStatusUseCaseImpl(
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            WorkflowTypeConfigRepository configRepository) {
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
    }

    @Override
    public void update(UUID linkId, String status, String activityId) {
        DossierWorkflowLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new DossierWorkflowLinkNotFoundException("Workflow link not found: " + linkId));
        WorkflowTypeConfig config = configRepository.findByWorkflowKey(link.tenantId(), link.processDefinitionKey())
                .orElseThrow(() -> new WorkflowTypeConfigNotFoundException(
                        "Workflow type config not found: " + link.processDefinitionKey()));
        StatusOutcome outcome = config.outcomeOf(status);
        if (outcome == null) {
            throw new IllegalArgumentException("Unknown workflow status " + status);
        }

        Instant now = Instant.now();
        DossierWorkflowLink updated = outcome == StatusOutcome.NONE
                ? link.withStatus(status, null, null)
                : link.withStatus(status, now, outcome.name());
        linkRepository.save(updated);
        if (outcome != StatusOutcome.NONE) {
            Dossier dossier = dossierRepository.findById(link.dossierId())
                    .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + link.dossierId()));
            if (link.id().equals(dossier.currentWorkflowLinkId())) {
                dossierRepository.save(dossier.withCurrentWorkflowLinkId(null, "workflow", now));
            }
        }
    }
}
