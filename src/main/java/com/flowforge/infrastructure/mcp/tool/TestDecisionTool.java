package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.workflow.model.DecisionTestCase;
import com.flowforge.domain.workflow.model.DecisionTestReport;
import com.flowforge.domain.workflow.port.in.TestDecisionUseCase;
import com.flowforge.infrastructure.flowable.DmnCsvTestCaseParser;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.ObjectMapper;

@Component
public class TestDecisionTool {

    private final TestDecisionUseCase testDecisionUseCase;
    private final DmnCsvTestCaseParser csvParser;
    private final ObjectMapper objectMapper;
    private final McpAccessContextFactory accessContextFactory;
    private final DataSize maxPayloadSize;

    public TestDecisionTool(
            TestDecisionUseCase testDecisionUseCase,
            DmnCsvTestCaseParser csvParser,
            ObjectMapper objectMapper,
            McpAccessContextFactory accessContextFactory,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize maxPayloadSize) {
        this.testDecisionUseCase = Objects.requireNonNull(testDecisionUseCase, "testDecisionUseCase");
        this.csvParser = Objects.requireNonNull(csvParser, "csvParser");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
        this.maxPayloadSize = Objects.requireNonNull(maxPayloadSize, "maxPayloadSize");
    }

    @McpTool(
            name = "test_decision",
            description = "Run a JSON or CSV test suite against a deployed DMN decision table. "
                    + "format is json or csv. JSON payload is {\"cases\":[{\"inputs\":{},\"expectedRows\":[{}]}]}. "
                    + "CSV payload uses input.<name> and expected.<name> columns. At most 100 cases. "
                    + "Payload size is limited to the configured upload maximum.")
    public DecisionTestReport testDecision(
            @McpToolParam(required = true, description = "Decision key") String decisionKey,
            @McpToolParam(required = false, description = "Deployed version; omit for the latest") Integer version,
            @McpToolParam(required = true, description = "json or csv") String format,
            @McpToolParam(required = true, description = "JSON object or raw CSV text") String payload) {
        String kind = McpArgs.requireNonBlank(format, "format");
        String body = McpArgs.requireNonBlank(payload, "payload");
        if (body.getBytes(StandardCharsets.UTF_8).length > maxPayloadSize.toBytes()) {
            throw new IllegalArgumentException("payload exceeds max size of " + maxPayloadSize);
        }
        boolean csv = "csv".equalsIgnoreCase(kind);
        if (!csv && !"json".equalsIgnoreCase(kind)) {
            throw new IllegalArgumentException("format must be json or csv");
        }
        List<DecisionTestCase> cases = csv ? csvParser.parse(body) : parseJson(body);
        return testDecisionUseCase.test(
                accessContextFactory.currentActor(), decisionKey, version, cases, csv);
    }

    private List<DecisionTestCase> parseJson(String payload) {
        try {
            JsonSuite suite = objectMapper.readValue(payload, JsonSuite.class);
            if (suite == null || suite.cases() == null) {
                throw new IllegalArgumentException("payload.cases is required");
            }
            return List.copyOf(suite.cases());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("payload is not valid JSON", ex);
        }
    }

    private record JsonSuite(List<DecisionTestCase> cases) {}
}
