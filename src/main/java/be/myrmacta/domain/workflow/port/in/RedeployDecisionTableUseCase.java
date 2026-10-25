package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.DecisionTableDeploymentResult;

public interface RedeployDecisionTableUseCase {

    /**
     * Deploys the XML of an earlier version as a new current version.
     * Refuses when {@code version} is already the current one.
     */
    DecisionTableDeploymentResult redeploy(AccessContext.User actor, String decisionKey, int version);
}
