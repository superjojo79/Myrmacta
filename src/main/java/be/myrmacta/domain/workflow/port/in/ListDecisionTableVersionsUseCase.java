package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.DecisionTableVersionInfo;
import java.util.List;

public interface ListDecisionTableVersionsUseCase {

    /** Every deployed version of {@code decisionKey}, ascending by version number. */
    List<DecisionTableVersionInfo> list(AccessContext.User actor, String decisionKey);
}
