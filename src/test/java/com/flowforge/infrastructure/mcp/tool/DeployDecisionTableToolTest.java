package com.flowforge.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;
import com.flowforge.domain.workflow.model.InvalidDmnException;
import com.flowforge.domain.workflow.port.in.DeployDecisionTableUseCase;
import com.flowforge.domain.workflow.port.out.DmnParserPort;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class DeployDecisionTableToolTest {

    private static final String KEY = "testDecision";
    private static final String XML = "<dmn/>";

    private DmnParserPort dmnParserPort;
    private DeployDecisionTableUseCase deployDecisionTableUseCase;
    private DeployDecisionTableTool tool;

    @BeforeEach
    void setUp() {
        dmnParserPort = mock(DmnParserPort.class);
        deployDecisionTableUseCase = mock(DeployDecisionTableUseCase.class);
        tool = new DeployDecisionTableTool(dmnParserPort, deployDecisionTableUseCase, DataSize.ofMegabytes(25));
    }

    @Test
    void validXmlWithMatchingKey_deploysAndReturnsResult() {
        when(dmnParserPort.extractDecisionKey(XML.getBytes(StandardCharsets.UTF_8))).thenReturn(KEY);
        DecisionTableDeploymentResult deployment = new DecisionTableDeploymentResult(KEY, 1, "dep-1");
        when(deployDecisionTableUseCase.deploy(KEY + ".dmn", XML.getBytes(StandardCharsets.UTF_8)))
                .thenReturn(deployment);

        Map<String, Object> result = tool.deployDecisionTable(KEY, XML, false);

        assertThat(result.get("decisionKey")).isEqualTo(KEY);
        assertThat(result.get("dryRun")).isEqualTo(false);
        assertThat(result.get("deployment")).isEqualTo(deployment);
    }

    @Test
    void dryRun_parsesButDoesNotDeploy() {
        when(dmnParserPort.extractDecisionKey(XML.getBytes(StandardCharsets.UTF_8))).thenReturn(KEY);

        Map<String, Object> result = tool.deployDecisionTable(KEY, XML, true);

        assertThat(result.get("dryRun")).isEqualTo(true);
        assertThat(result.get("deployment")).isNull();
        verifyNoInteractions(deployDecisionTableUseCase);
    }

    @Test
    void mismatchedDecisionKey_isRejectedBeforeDeploying() {
        when(dmnParserPort.extractDecisionKey(any())).thenReturn("otherDecision");

        assertThatThrownBy(() -> tool.deployDecisionTable(KEY, XML, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("otherDecision");
        verifyNoInteractions(deployDecisionTableUseCase);
    }

    @Test
    void invalidDmn_propagatesInvalidDmnException() {
        when(dmnParserPort.extractDecisionKey(any())).thenThrow(new InvalidDmnException("bad dmn"));

        assertThatThrownBy(() -> tool.deployDecisionTable(KEY, XML, false)).isInstanceOf(InvalidDmnException.class);
        verifyNoInteractions(deployDecisionTableUseCase);
    }

    @Test
    void xmlExceedingMaxSize_isRejectedBeforeParsing() {
        DeployDecisionTableTool tinyLimitTool =
                new DeployDecisionTableTool(dmnParserPort, deployDecisionTableUseCase, DataSize.ofBytes(4));

        assertThatThrownBy(() -> tinyLimitTool.deployDecisionTable(KEY, XML, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds max size");
        verifyNoInteractions(dmnParserPort, deployDecisionTableUseCase);
    }
}
