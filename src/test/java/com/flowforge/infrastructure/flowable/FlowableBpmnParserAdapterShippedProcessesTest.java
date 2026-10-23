package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * TODO §57 revue adversariale PR #113 : garde-fou de non-régression -- chaque BPMN de production
 * livré avec ce dépôt ({@code src/main/resources/processes}) doit continuer à passer {@link
 * FlowableBpmnParserAdapter#extractProcessDefinitionKey}, y compris après le durcissement
 * (assignee/candidateUsers/candidateGroups littéraux uniquement, conditionExpression restreinte à
 * une grammaire de comparaison sûre, field extensions et businessRuleTask interdits). Ne couvre
 * pas {@code src/test/resources} : certaines fixtures y sont délibérément déployées en
 * contournant notre parseur (ex. {@code async-lock-dimensioning-test.bpmn20.xml}, qui teste le
 * moteur Flowable lui-même, pas notre allowlist). Couvre aussi les exemples livrés ({@code exemples/},
 * issue #183) : ils se déploient tels quels, ils doivent donc passer les mêmes règles, attributs de brique compris.
 */
class FlowableBpmnParserAdapterShippedProcessesTest {

    private final FlowableBpmnParserAdapter adapter = new FlowableBpmnParserAdapter();

    static List<Path> shippedBpmnFiles() throws IOException {
        try (Stream<Path> main = Files.walk(Path.of("src/main/resources/processes"));
                Stream<Path> examples = Files.walk(Path.of("exemples"))) {
            return Stream.concat(main, examples).filter(p -> p.toString().endsWith(".bpmn20.xml")).toList();
        }
    }

    @ParameterizedTest
    @MethodSource("shippedBpmnFiles")
    void shippedProcess_passesTheAllowlist(Path bpmnFile) throws IOException {
        byte[] bytes = Files.readAllBytes(bpmnFile);
        assertThatCode(() -> adapter.extractProcessDefinitionKey(bytes)).doesNotThrowAnyException();
    }
}
