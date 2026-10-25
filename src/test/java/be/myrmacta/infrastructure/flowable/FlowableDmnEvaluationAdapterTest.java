package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.workflow.model.DecisionEvaluationException;
import be.myrmacta.domain.workflow.model.DecisionEvaluationResult;
import be.myrmacta.domain.workflow.model.UnknownDecisionVersionException;
import java.util.Map;
import org.flowable.dmn.api.DmnDecision;
import org.flowable.dmn.api.DmnDecisionQuery;
import org.flowable.dmn.api.DmnDecisionService;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.dmn.api.ExecuteDecisionBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableDmnEvaluationAdapterTest {

    private DmnRepositoryService repository;
    private DmnDecisionService decisionService;
    private DmnDecisionQuery decisionQuery;
    private ExecuteDecisionBuilder builder;
    private FlowableDmnEvaluationAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(DmnRepositoryService.class);
        decisionService = mock(DmnDecisionService.class);
        decisionQuery = mock(DmnDecisionQuery.class);
        builder = mock(ExecuteDecisionBuilder.class);
        when(repository.createDecisionQuery()).thenReturn(decisionQuery);
        when(decisionQuery.decisionKey("eligibilite")).thenReturn(decisionQuery);
        when(decisionQuery.decisionVersion(2)).thenReturn(decisionQuery);
        when(decisionQuery.decisionTenantId("tenant")).thenReturn(decisionQuery);
        when(decisionService.createExecuteDecisionBuilder()).thenReturn(builder);
        when(builder.decisionKey("eligibilite")).thenReturn(builder);
        when(builder.tenantId("tenant")).thenReturn(builder);
        when(builder.parentDeploymentId("dep-2")).thenReturn(builder);
        when(builder.disableHistory()).thenReturn(builder);
        adapter = new FlowableDmnEvaluationAdapter(repository, decisionService);
    }

    @Test
    void unknownVersion_isNotFoundBeforeExecution() {
        when(decisionQuery.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> adapter.evaluate("tenant", "eligibilite", 2, Map.of()))
                .isInstanceOf(UnknownDecisionVersionException.class);
    }

    @Test
    void nullRows_becomeAnEmptyList() {
        stubDecision();
        when(builder.variables(Map.of("customerCat", "BRONZE"))).thenReturn(builder);
        when(builder.execute()).thenReturn(null);

        DecisionEvaluationResult result =
                adapter.evaluate("tenant", "eligibilite", 2, Map.of("customerCat", "BRONZE"));

        assertThat(result.rows()).isEmpty();
        assertThat(result.version()).isEqualTo(2);
    }

    @Test
    void engineFailure_exposesTheEngineMessage() {
        stubDecision();
        when(builder.variables(Map.of())).thenReturn(builder);
        when(builder.execute()).thenThrow(new IllegalStateException("Unknown variable customerCat"));

        assertThatThrownBy(() -> adapter.evaluate("tenant", "eligibilite", 2, null))
                .isInstanceOf(DecisionEvaluationException.class)
                .hasMessageContaining("Unknown variable customerCat");
    }

    @Test
    void engineFailure_withoutMessage_keepsTheDecisionContext() {
        stubDecision();
        when(builder.variables(Map.of())).thenReturn(builder);
        when(builder.execute()).thenThrow(new IllegalStateException());

        assertThatThrownBy(() -> adapter.evaluate("tenant", "eligibilite", 2, null))
                .isInstanceOf(DecisionEvaluationException.class)
                .hasMessage("Decision evaluation failed for eligibilite version 2");
    }

    private void stubDecision() {
        DmnDecision decision = mock(DmnDecision.class);
        when(decision.getDeploymentId()).thenReturn("dep-2");
        when(decisionQuery.singleResult()).thenReturn(decision);
    }
}
