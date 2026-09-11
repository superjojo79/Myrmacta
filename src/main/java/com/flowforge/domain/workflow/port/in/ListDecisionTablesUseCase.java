package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.workflow.model.DecisionTableDeploymentInfo;
import java.util.List;

public interface ListDecisionTablesUseCase {

    List<DecisionTableDeploymentInfo> list();
}
