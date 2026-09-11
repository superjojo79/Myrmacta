package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.workflow.model.DmnDraft;
import java.util.Optional;

public interface GetDmnDraftUseCase {

    Optional<DmnDraft> get(String decisionKey);
}
