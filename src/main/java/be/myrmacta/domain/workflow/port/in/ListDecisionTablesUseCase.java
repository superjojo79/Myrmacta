package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.workflow.model.DecisionTableDeploymentInfo;
import java.util.List;

public interface ListDecisionTablesUseCase {

    List<DecisionTableDeploymentInfo> list();
}
