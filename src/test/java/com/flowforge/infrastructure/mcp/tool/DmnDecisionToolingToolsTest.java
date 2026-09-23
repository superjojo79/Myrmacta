package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.DecisionEvaluationResult;
import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;
import com.flowforge.domain.workflow.model.DecisionTableVersionInfo;
import com.flowforge.domain.workflow.model.DecisionTestCase;
import com.flowforge.domain.workflow.model.DecisionTestReport;
import com.flowforge.domain.workflow.port.in.EvaluateDecisionUseCase;
import com.flowforge.domain.workflow.port.in.ListDecisionTableVersionsUseCase;
import com.flowforge.domain.workflow.port.in.RedeployDecisionTableUseCase;
import com.flowforge.domain.workflow.port.in.TestDecisionUseCase;
import com.flowforge.infrastructure.flowable.DmnCsvTestCaseParser;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DmnDecisionToolingToolsTest {

    private static final AccessContext.User ACTOR = AccessContext.user("svc", Set.of("API_SERVICE"));

    @Mock
    private EvaluateDecisionUseCase evaluateDecisionUseCase;

    @Mock
    private TestDecisionUseCase testDecisionUseCase;

    @Mock
    private ListDecisionTableVersionsUseCase listDecisionTableVersionsUseCase;

    @Mock
    private RedeployDecisionTableUseCase redeployDecisionTableUseCase;

    @Mock
    private McpAccessContextFactory accessContextFactory;

    @BeforeEach
    void actor() {
        when(accessContextFactory.currentActor()).thenReturn(ACTOR);
    }

    @Test
    void evaluateDecision_mapsArgumentsToTheUseCase() {
        DecisionEvaluationResult expected =
                new DecisionEvaluationResult("eligibilite", 2, List.of(Map.of("result", "ok")));
        when(evaluateDecisionUseCase.evaluate(ACTOR, "eligibilite", 2, Map.of("customerCat", "BRONZE")))
                .thenReturn(expected);
        EvaluateDecisionTool tool = new EvaluateDecisionTool(evaluateDecisionUseCase, accessContextFactory);

        assertThat(tool.evaluateDecision("eligibilite", Map.of("customerCat", "BRONZE"), 2)).isEqualTo(expected);
    }

    @Test
    void testDecision_jsonAndCsv_delegateWithTheCoerceFlag() {
        when(testDecisionUseCase.test(ACTOR, "eligibilite", null, List.of(
                new DecisionTestCase(Map.of("customerCat", "BRONZE"), List.of(Map.of("result", "ok")))), false))
                .thenReturn(new DecisionTestReport("eligibilite", 1, List.of()));
        when(testDecisionUseCase.test(ACTOR, "eligibilite", 1, List.of(
                new DecisionTestCase(Map.of("customerCat", "BRONZE"), List.of(Map.of("result", "ok")))), true))
                .thenReturn(new DecisionTestReport("eligibilite", 1, List.of()));
        TestDecisionTool tool = new TestDecisionTool(
                testDecisionUseCase, new DmnCsvTestCaseParser(), new ObjectMapper(), accessContextFactory);

        tool.testDecision(
                "eligibilite",
                null,
                "json",
                "{\"cases\":[{\"inputs\":{\"customerCat\":\"BRONZE\"},\"expectedRows\":[{\"result\":\"ok\"}]}]}");
        tool.testDecision("eligibilite", 1, "csv", "input.customerCat,expected.result\nBRONZE,ok\n");

        verify(testDecisionUseCase).test(ACTOR, "eligibilite", null, List.of(
                new DecisionTestCase(Map.of("customerCat", "BRONZE"), List.of(Map.of("result", "ok")))), false);
        verify(testDecisionUseCase).test(ACTOR, "eligibilite", 1, List.of(
                new DecisionTestCase(Map.of("customerCat", "BRONZE"), List.of(Map.of("result", "ok")))), true);
    }

    @Test
    void listAndRedeploy_mapArgumentsToTheUseCases() {
        DecisionTableVersionInfo info =
                new DecisionTableVersionInfo("eligibilite", 1, "dep-1", Instant.parse("2026-09-01T00:00:00Z"));
        when(listDecisionTableVersionsUseCase.list(ACTOR, "eligibilite")).thenReturn(List.of(info));
        when(redeployDecisionTableUseCase.redeploy(ACTOR, "eligibilite", 1))
                .thenReturn(new DecisionTableDeploymentResult("eligibilite", 3, "dep-3"));

        assertThat(new ListDecisionTableVersionsTool(listDecisionTableVersionsUseCase, accessContextFactory)
                        .listDecisionTableVersions("eligibilite"))
                .containsExactly(info);
        assertThat(new RedeployDecisionTableTool(redeployDecisionTableUseCase, accessContextFactory)
                        .redeployDecisionTable("eligibilite", 1)
                        .deployedVersion())
                .isEqualTo(3);
    }
}
