package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.workflow.model.BpmnDraft;
import com.flowforge.domain.workflow.port.in.GetBpmnDraftUseCase;
import com.flowforge.domain.workflow.port.out.BpmnDraftRepository;
import java.util.Objects;
import java.util.Optional;

public final class GetBpmnDraftUseCaseImpl implements GetBpmnDraftUseCase {

    private final String tenantId;
    private final BpmnDraftRepository draftRepository;

    public GetBpmnDraftUseCaseImpl(String tenantId, BpmnDraftRepository draftRepository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.draftRepository = Objects.requireNonNull(draftRepository, "draftRepository");
    }

    @Override
    public Optional<BpmnDraft> get(String processDefinitionKey) {
        return draftRepository.findByTenantIdAndProcessDefinitionKey(tenantId, processDefinitionKey);
    }
}
