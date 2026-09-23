package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.workflow.model.DecisionEvaluationException;
import com.flowforge.domain.workflow.model.DecisionEvaluationResult;
import com.flowforge.domain.workflow.model.DecisionTableDeploymentInfo;
import com.flowforge.domain.workflow.model.UnknownDecisionKeyException;
import com.flowforge.domain.workflow.model.UnknownDecisionVersionException;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import com.flowforge.domain.workflow.port.out.DmnEvaluationPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluateDecisionUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final AccessContext.User ADMIN = AccessContext.user("admin", Set.of("TENANT_ADMIN"));
    private static final AccessContext.User NO_ROLE = AccessContext.user("viewer", Set.of("DOSSIER_VIEWER"));

    @Mock
    private DmnDeploymentPort deploymentPort;

    @Mock
    private DmnEvaluationPort evaluationPort;

    private EvaluateDecisionUseCaseImpl useCase() {
        return new EvaluateDecisionUseCaseImpl(TENANT, deploymentPort, evaluationPort, new TenantAdminGuard());
    }

    @Test
    void omittedVersion_evaluatesTheLatestDeployedVersion() {
        when(deploymentPort.findDeploymentInfo(TENANT, "eligibilite"))
                .thenReturn(Optional.of(new DecisionTableDeploymentInfo("eligibilite", 3)));
        DecisionEvaluationResult expected =
                new DecisionEvaluationResult("eligibilite", 3, List.of(Map.of("result", "ok")));
        when(evaluationPort.evaluate(TENANT, "eligibilite", 3, Map.of("customerCat", "BRONZE")))
                .thenReturn(expected);

        DecisionEvaluationResult result =
                useCase().evaluate(ADMIN, "eligibilite", null, Map.of("customerCat", "BRONZE"));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void explicitVersion_evaluatesThatVersion() {
        when(deploymentPort.findDeploymentInfo(TENANT, "eligibilite"))
                .thenReturn(Optional.of(new DecisionTableDeploymentInfo("eligibilite", 2)));
        DecisionEvaluationResult versionOne =
                new DecisionEvaluationResult("eligibilite", 1, List.of(Map.of("result", "v1")));
        when(evaluationPort.evaluate(TENANT, "eligibilite", 1, Map.of())).thenReturn(versionOne);

        DecisionEvaluationResult result = useCase().evaluate(ADMIN, "eligibilite", 1, Map.of());

        assertThat(result.rows()).containsExactly(Map.of("result", "v1"));
        verify(evaluationPort, never()).evaluate(TENANT, "eligibilite", 2, Map.of());
    }

    @Test
    void unknownKey_isRejectedBeforeEvaluation() {
        when(deploymentPort.findDeploymentInfo(TENANT, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().evaluate(ADMIN, "missing", null, Map.of()))
                .isInstanceOf(UnknownDecisionKeyException.class);
        verifyNoInteractions(evaluationPort);
    }

    @Test
    void explicitVersionOfUnknownKey_isUnknownKey() {
        when(deploymentPort.findDeploymentInfo(TENANT, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().evaluate(ADMIN, "missing", 4, Map.of()))
                .isInstanceOf(UnknownDecisionKeyException.class);
        verifyNoInteractions(evaluationPort);
    }

    @Test
    void missingVersionOfKnownKey_propagatesUnknownVersion() {
        when(deploymentPort.findDeploymentInfo(TENANT, "eligibilite"))
                .thenReturn(Optional.of(new DecisionTableDeploymentInfo("eligibilite", 2)));
        when(evaluationPort.evaluate(TENANT, "eligibilite", 9, Map.of()))
                .thenThrow(new UnknownDecisionVersionException("absent"));

        assertThatThrownBy(() -> useCase().evaluate(ADMIN, "eligibilite", 9, Map.of()))
                .isInstanceOf(UnknownDecisionVersionException.class);
    }

    @Test
    void noMatchingRule_returnsAnEmptyRowList() {
        when(deploymentPort.findDeploymentInfo(TENANT, "eligibilite"))
                .thenReturn(Optional.of(new DecisionTableDeploymentInfo("eligibilite", 1)));
        when(evaluationPort.evaluate(TENANT, "eligibilite", 1, Map.of("customerCat", "GOLD")))
                .thenReturn(new DecisionEvaluationResult("eligibilite", 1, List.of()));

        DecisionEvaluationResult result =
                useCase().evaluate(ADMIN, "eligibilite", null, Map.of("customerCat", "GOLD"));

        assertThat(result.rows()).isEmpty();
    }

    @Test
    void engineFailure_propagatesDecisionEvaluationException() {
        when(deploymentPort.findDeploymentInfo(TENANT, "eligibilite"))
                .thenReturn(Optional.of(new DecisionTableDeploymentInfo("eligibilite", 1)));
        when(evaluationPort.evaluate(TENANT, "eligibilite", 1, Map.of()))
                .thenThrow(new DecisionEvaluationException("engine down"));

        assertThatThrownBy(() -> useCase().evaluate(ADMIN, "eligibilite", null, Map.of()))
                .isInstanceOf(DecisionEvaluationException.class)
                .hasMessageContaining("engine down");
    }

    @Test
    void actorWithoutAdminRole_isRejectedBeforeAnyPort() {
        assertThatThrownBy(() -> useCase().evaluate(NO_ROLE, "eligibilite", null, Map.of()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(deploymentPort, evaluationPort);
    }
}
