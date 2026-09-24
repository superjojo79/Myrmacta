package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.DecisionEvaluationResult;
import com.flowforge.domain.workflow.model.DecisionTestCase;
import com.flowforge.domain.workflow.model.DecisionTestReport;
import com.flowforge.domain.workflow.port.in.EvaluateDecisionUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestDecisionUseCaseImplTest {

    private static final AccessContext.User ADMIN = AccessContext.user("admin", Set.of("WORKFLOW_ADMIN"));

    @Mock
    private EvaluateDecisionUseCase evaluateDecisionUseCase;

    private TestDecisionUseCaseImpl useCase() {
        return new TestDecisionUseCaseImpl(evaluateDecisionUseCase, new TenantAdminGuard());
    }

    @Test
    void allCasesPass_reportIsFullyPassed() {
        when(evaluateDecisionUseCase.evaluate(ADMIN, "eligibilite", null, Map.of("customerCat", "BRONZE")))
                .thenReturn(new DecisionEvaluationResult("eligibilite", 2, List.of(Map.of("result", "ok"))));
        when(evaluateDecisionUseCase.evaluate(ADMIN, "eligibilite", null, Map.of("customerCat", "SILVER")))
                .thenReturn(new DecisionEvaluationResult("eligibilite", 2, List.of(Map.of("result", "review", "extra", 1))));

        DecisionTestReport report = useCase().test(
                ADMIN,
                "eligibilite",
                null,
                List.of(
                        new DecisionTestCase(Map.of("customerCat", "BRONZE"), List.of(Map.of("result", "ok"))),
                        new DecisionTestCase(Map.of("customerCat", "SILVER"), List.of(Map.of("result", "review")))),
                false);

        assertThat(report.version()).isEqualTo(2);
        assertThat(report.results()).allMatch(result -> result.passed() && result.failureMessage() == null);
    }

    @Test
    void failingCase_reportsActualRowsAndMessage() {
        when(evaluateDecisionUseCase.evaluate(ADMIN, "eligibilite", 1, Map.of("customerCat", "BRONZE")))
                .thenReturn(new DecisionEvaluationResult("eligibilite", 1, List.of(Map.of("result", "ko"))));

        DecisionTestReport report = useCase().test(
                ADMIN,
                "eligibilite",
                1,
                List.of(new DecisionTestCase(Map.of("customerCat", "BRONZE"), List.of(Map.of("result", "ok")))),
                false);

        assertThat(report.results()).hasSize(1);
        assertThat(report.results().getFirst().passed()).isFalse();
        assertThat(report.results().getFirst().actualRows()).containsExactly(Map.of("result", "ko"));
        assertThat(report.results().getFirst().failureMessage()).contains("result");
    }

    @Test
    void emptyCases_areRejectedBeforeEvaluation() {
        assertThatThrownBy(() -> useCase().test(ADMIN, "eligibilite", null, List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(evaluateDecisionUseCase);
    }

    @Test
    void eachCase_delegatesToEvaluateDecisionUseCase() {
        when(evaluateDecisionUseCase.evaluate(ADMIN, "eligibilite", 2, Map.of("n", 1)))
                .thenReturn(new DecisionEvaluationResult("eligibilite", 2, List.of(Map.of("result", "ok"))));

        useCase().test(
                ADMIN,
                "eligibilite",
                2,
                List.of(new DecisionTestCase(Map.of("n", 1), List.of(Map.of("result", "ok")))),
                false);

        verify(evaluateDecisionUseCase, times(1)).evaluate(ADMIN, "eligibilite", 2, Map.of("n", 1));
    }

    @Test
    void csvLiteral_matchesNumericActual_jsonStringDoesNot() {
        when(evaluateDecisionUseCase.evaluate(ADMIN, "eligibilite", null, Map.of()))
                .thenReturn(new DecisionEvaluationResult("eligibilite", 1, List.of(Map.of("amount", 1000))));
        DecisionTestCase csvShaped = new DecisionTestCase(Map.of(), List.of(Map.of("amount", "1000")));

        DecisionTestReport coerced = useCase().test(ADMIN, "eligibilite", null, List.of(csvShaped), true);
        DecisionTestReport strict = useCase().test(ADMIN, "eligibilite", null, List.of(csvShaped), false);

        assertThat(coerced.results().getFirst().passed()).isTrue();
        assertThat(strict.results().getFirst().passed()).isFalse();
    }

    @Test
    void rowOrderAndLength_mustMatch() {
        when(evaluateDecisionUseCase.evaluate(ADMIN, "eligibilite", null, Map.of("a", 1)))
                .thenReturn(new DecisionEvaluationResult(
                        "eligibilite", 1, List.of(Map.of("result", "b"), Map.of("result", "a"))));
        when(evaluateDecisionUseCase.evaluate(ADMIN, "eligibilite", null, Map.of("a", 2)))
                .thenReturn(new DecisionEvaluationResult("eligibilite", 1, List.of(Map.of("result", "only"))));

        DecisionTestReport swapped = useCase().test(
                ADMIN,
                "eligibilite",
                null,
                List.of(new DecisionTestCase(
                        Map.of("a", 1), List.of(Map.of("result", "a"), Map.of("result", "b")))),
                false);
        DecisionTestReport extra = useCase().test(
                ADMIN,
                "eligibilite",
                null,
                List.of(new DecisionTestCase(Map.of("a", 2), List.of())),
                false);

        assertThat(swapped.results().getFirst().passed()).isFalse();
        assertThat(extra.results().getFirst().passed()).isFalse();
        assertThat(extra.results().getFirst().failureMessage()).contains("row count");
    }

    @Test
    void nullCaseOrMissingExpectedKey_isRejectedOrFailed() {
        assertThatThrownBy(() -> useCase().test(ADMIN, "eligibilite", null, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase().test(
                        ADMIN, "eligibilite", null, java.util.Arrays.asList((DecisionTestCase) null), false))
                .isInstanceOf(IllegalArgumentException.class);

        when(evaluateDecisionUseCase.evaluate(ADMIN, "eligibilite", null, Map.of()))
                .thenReturn(new DecisionEvaluationResult("eligibilite", 1, List.of(Map.of("result", "ok"))));
        DecisionTestReport missing = useCase().test(
                ADMIN,
                "eligibilite",
                null,
                List.of(new DecisionTestCase(null, List.of(Map.of("absent", true)))),
                true);

        assertThat(missing.results().getFirst().passed()).isFalse();
        assertThat(missing.results().getFirst().failureMessage()).contains("absent");
    }

    @Test
    void booleanLiteralAndDecimal_matchCoercedActuals() {
        when(evaluateDecisionUseCase.evaluate(ADMIN, "eligibilite", null, Map.of("n", 1)))
                .thenReturn(new DecisionEvaluationResult("eligibilite", 1, List.of(Map.of("ok", true, "amount", 1.5d))));

        DecisionTestReport report = useCase().test(
                ADMIN,
                "eligibilite",
                null,
                List.of(new DecisionTestCase(Map.of("n", 1), List.of(Map.of("ok", "true", "amount", "1.5")))),
                true);

        assertThat(report.results().getFirst().passed()).isTrue();
    }

    @Test
    void nullRowsAndFalseLiteral_followThePartialMatchRules() {
        when(evaluateDecisionUseCase.evaluate(ADMIN, "eligibilite", null, Map.of("n", 1)))
                .thenReturn(new DecisionEvaluationResult("eligibilite", 1, null));
        when(evaluateDecisionUseCase.evaluate(ADMIN, "eligibilite", null, Map.of("n", 2)))
                .thenReturn(new DecisionEvaluationResult(
                        "eligibilite", 1, List.of(Map.of("ok", false, "amount", 1.5f))));

        DecisionTestReport empty = useCase().test(
                ADMIN,
                "eligibilite",
                null,
                List.of(new DecisionTestCase(Map.of("n", 1), null)),
                false);
        DecisionTestReport falseLiteral = useCase().test(
                ADMIN,
                "eligibilite",
                null,
                List.of(new DecisionTestCase(Map.of("n", 2), List.of(Map.of("ok", "false", "amount", "1.5")))),
                true);

        assertThat(empty.results().getFirst().passed()).isTrue();
        assertThat(falseLiteral.results().getFirst().passed()).isTrue();
    }

    @Test
    void moreThanOneHundredCases_areRejected() {
        List<DecisionTestCase> cases = new ArrayList<>();
        IntStream.range(0, 101).forEach(i -> cases.add(new DecisionTestCase(Map.of(), List.of())));

        assertThatThrownBy(() -> useCase().test(ADMIN, "eligibilite", null, cases, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
        verifyNoInteractions(evaluateDecisionUseCase);
    }
}
