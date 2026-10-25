package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.workflow.model.DmnDraft;
import java.util.Optional;

public interface GetDmnDraftUseCase {

    Optional<DmnDraft> get(String decisionKey);
}
