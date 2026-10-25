package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.in.GetWorkflowTypeConfigUseCase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListWorkflowTypesToolTest {

    private GetWorkflowTypeConfigUseCase getWorkflowTypeConfigUseCase;
    private ListWorkflowTypesTool tool;

    @BeforeEach
    void setUp() {
        getWorkflowTypeConfigUseCase = mock(GetWorkflowTypeConfigUseCase.class);
        tool = new ListWorkflowTypesTool(getWorkflowTypeConfigUseCase);
    }

    @Test
    void pageAndSizeAbsent_defaultsToPage0Size20() {
        PageResult<WorkflowTypeConfig> page = PageResult.empty(0, PageQuery.DEFAULT_SIZE);
        when(getWorkflowTypeConfigUseCase.list(eq(PageQuery.of(0, 20)))).thenReturn(page);

        PageResult<WorkflowTypeConfig> result = tool.listWorkflowTypes(null, null);

        assertThat(result).isSameAs(page);
    }

    @Test
    void pageAndSizeProvided_relayedAsGiven() {
        PageResult<WorkflowTypeConfig> page = PageResult.empty(2, 5);
        when(getWorkflowTypeConfigUseCase.list(eq(PageQuery.of(2, 5)))).thenReturn(page);

        PageResult<WorkflowTypeConfig> result = tool.listWorkflowTypes(2, 5);

        assertThat(result).isSameAs(page);
    }

    @Test
    void onlyPageProvided_sizeDefaultsTo20() {
        when(getWorkflowTypeConfigUseCase.list(eq(PageQuery.of(3, 20))))
                .thenReturn(PageResult.of(List.of(), 3, 20, 0));

        tool.listWorkflowTypes(3, null);

        org.mockito.Mockito.verify(getWorkflowTypeConfigUseCase).list(PageQuery.of(3, 20));
    }

    @Test
    void onlySizeProvided_pageDefaultsTo0() {
        when(getWorkflowTypeConfigUseCase.list(eq(PageQuery.of(0, 50))))
                .thenReturn(PageResult.of(List.of(), 0, 50, 0));

        tool.listWorkflowTypes(null, 50);

        org.mockito.Mockito.verify(getWorkflowTypeConfigUseCase).list(PageQuery.of(0, 50));
    }
}
