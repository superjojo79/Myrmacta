package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.DecisionTableVersionInfo;
import java.util.List;

public interface ListDecisionTableVersionsUseCase {

    /** Every deployed version of {@code decisionKey}, ascending by version number. */
    List<DecisionTableVersionInfo> list(AccessContext.User actor, String decisionKey);
}
