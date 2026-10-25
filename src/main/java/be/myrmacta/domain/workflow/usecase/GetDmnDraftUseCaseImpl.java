package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.workflow.model.DmnDraft;
import be.myrmacta.domain.workflow.port.in.GetDmnDraftUseCase;
import be.myrmacta.domain.workflow.port.out.DmnDraftRepository;
import java.util.Objects;
import java.util.Optional;

public final class GetDmnDraftUseCaseImpl implements GetDmnDraftUseCase {

    private final String tenantId;
    private final DmnDraftRepository draftRepository;

    public GetDmnDraftUseCaseImpl(String tenantId, DmnDraftRepository draftRepository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.draftRepository = Objects.requireNonNull(draftRepository, "draftRepository");
    }

    @Override
    public Optional<DmnDraft> get(String decisionKey) {
        return draftRepository.findByTenantIdAndDecisionKey(tenantId, decisionKey);
    }
}
