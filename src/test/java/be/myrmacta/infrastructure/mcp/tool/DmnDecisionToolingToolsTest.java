package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.workflow.model.DecisionEvaluationResult;
import be.myrmacta.domain.workflow.model.DecisionTableDeploymentResult;
import be.myrmacta.domain.workflow.model.DecisionTableVersionInfo;
import be.myrmacta.domain.workflow.model.DecisionTestCase;
import be.myrmacta.domain.workflow.model.DecisionTestReport;
import be.myrmacta.domain.workflow.port.in.EvaluateDecisionUseCase;
import be.myrmacta.domain.workflow.port.in.ListDecisionTableVersionsUseCase;
import be.myrmacta.domain.workflow.port.in.RedeployDecisionTableUseCase;
import be.myrmacta.domain.workflow.port.in.TestDecisionUseCase;
import be.myrmacta.infrastructure.flowable.DmnCsvTestCaseParser;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

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
        lenient().when(accessContextFactory.currentActor()).thenReturn(ACTOR);
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
                testDecisionUseCase,
                new DmnCsvTestCaseParser(),
                new ObjectMapper(),
                accessContextFactory,
                DataSize.ofMegabytes(25));

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
    void list_mapsArgumentsToTheUseCase() {
        DecisionTableVersionInfo info =
                new DecisionTableVersionInfo("eligibilite", 1, "dep-1", Instant.parse("2026-09-01T00:00:00Z"));
        when(listDecisionTableVersionsUseCase.list(ACTOR, "eligibilite")).thenReturn(List.of(info));

        assertThat(new ListDecisionTableVersionsTool(listDecisionTableVersionsUseCase, accessContextFactory)
                        .listDecisionTableVersions("eligibilite"))
                .containsExactly(info);
    }

    @Test
    void testDecision_rejectsUnknownFormatAndInvalidJson() {
        TestDecisionTool tool = new TestDecisionTool(
                testDecisionUseCase,
                new DmnCsvTestCaseParser(),
                new ObjectMapper(),
                accessContextFactory,
                DataSize.ofMegabytes(25));

        assertThatThrownBy(() -> tool.testDecision("eligibilite", null, "xml", "<x/>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.testDecision("eligibilite", null, "json", "not-json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.testDecision("eligibilite", null, "json", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cases");
        verifyNoInteractions(testDecisionUseCase);
    }

    @Test
    void testDecision_rejectsPayloadAboveTheUploadLimit() {
        TestDecisionTool tool = new TestDecisionTool(
                testDecisionUseCase,
                new DmnCsvTestCaseParser(),
                new ObjectMapper(),
                accessContextFactory,
                DataSize.ofBytes(4));

        assertThatThrownBy(() -> tool.testDecision("eligibilite", null, "json", "{\"cases\":[]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds max size");
        verifyNoInteractions(testDecisionUseCase);
    }
}
