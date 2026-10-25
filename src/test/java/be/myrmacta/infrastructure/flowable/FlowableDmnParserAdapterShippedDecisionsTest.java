package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * TODO §61 (introduction-dmn), M11 : mirrors {@link FlowableBpmnParserAdapterShippedProcessesTest}
 * for decision tables -- every {@code .dmn} shipped with this repository ({@code
 * src/main/resources/decisions}) must keep passing {@link
 * FlowableDmnParserAdapter#extractDecisionKey}, even though {@link TenantDmnDeployment} deploys
 * these fixtures directly at boot without going through the adapter (same bypass already accepted
 * for shipped BPMN processes). This is the non-regression guard: if a future edit to the shipped
 * fixture (or to the allowlist itself) would make it unacceptable through the admin editor / MCP
 * tool, this test catches it.
 */
class FlowableDmnParserAdapterShippedDecisionsTest {

    private final FlowableDmnParserAdapter adapter = new FlowableDmnParserAdapter();

    static List<Path> shippedDmnFiles() throws IOException {
        try (Stream<Path> main = Files.walk(Path.of("src/main/resources/decisions"))) {
            return main.filter(p -> p.toString().endsWith(".dmn")).toList();
        }
    }

    @ParameterizedTest
    @MethodSource("shippedDmnFiles")
    void shippedDecision_passesTheAllowlist(Path dmnFile) throws IOException {
        byte[] bytes = Files.readAllBytes(dmnFile);
        assertThatCode(() -> adapter.extractDecisionKey(bytes)).doesNotThrowAnyException();
    }
}
