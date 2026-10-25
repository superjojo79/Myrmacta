package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.workflow.model.DecisionTableDeploymentInfo;
import be.myrmacta.domain.workflow.port.in.ListDecisionTablesUseCase;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListDecisionTablesToolTest {

    @Test
    void listDecisionTables_delegatesToUseCase() {
        ListDecisionTablesUseCase useCase = mock(ListDecisionTablesUseCase.class);
        List<DecisionTableDeploymentInfo> infos = List.of(new DecisionTableDeploymentInfo("decisionA", 1));
        when(useCase.list()).thenReturn(infos);
        ListDecisionTablesTool tool = new ListDecisionTablesTool(useCase);

        assertThat(tool.listDecisionTables()).isEqualTo(infos);
    }

    @Test
    void listDecisionTables_noDeployments_returnsEmptyList() {
        ListDecisionTablesUseCase useCase = mock(ListDecisionTablesUseCase.class);
        when(useCase.list()).thenReturn(List.of());
        ListDecisionTablesTool tool = new ListDecisionTablesTool(useCase);

        assertThat(tool.listDecisionTables()).isEmpty();
    }
}
