package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.workflow.model.DecisionTableDeploymentInfo;
import be.myrmacta.domain.workflow.model.UnknownDecisionKeyException;
import be.myrmacta.domain.workflow.port.in.GetDecisionTableDeploymentInfoUseCase;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetDecisionTableToolTest {

    @Test
    void knownKey_returnsDeploymentInfo() {
        GetDecisionTableDeploymentInfoUseCase useCase = mock(GetDecisionTableDeploymentInfoUseCase.class);
        DecisionTableDeploymentInfo info = new DecisionTableDeploymentInfo("testDecision", 2);
        when(useCase.getDeploymentInfo("testDecision")).thenReturn(Optional.of(info));
        GetDecisionTableTool tool = new GetDecisionTableTool(useCase);

        assertThat(tool.getDecisionTable("testDecision")).isEqualTo(info);
    }

    @Test
    void unknownKey_throwsUnknownDecisionKeyException() {
        GetDecisionTableDeploymentInfoUseCase useCase = mock(GetDecisionTableDeploymentInfoUseCase.class);
        when(useCase.getDeploymentInfo("missingDecision")).thenReturn(Optional.empty());
        GetDecisionTableTool tool = new GetDecisionTableTool(useCase);

        assertThatThrownBy(() -> tool.getDecisionTable("missingDecision"))
                .isInstanceOf(UnknownDecisionKeyException.class)
                .hasMessageContaining("missingDecision");
    }
}
