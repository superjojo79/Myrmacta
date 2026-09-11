package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.model.InvalidDmnException;
import com.flowforge.domain.workflow.port.out.DmnParserPort;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.flowable.dmn.model.Decision;
import org.flowable.dmn.model.DecisionRule;
import org.flowable.dmn.model.DecisionTable;
import org.flowable.dmn.model.DmnDefinition;
import org.flowable.dmn.model.HitPolicy;
import org.flowable.dmn.model.RuleInputClauseContainer;
import org.flowable.dmn.model.RuleOutputClauseContainer;
import org.flowable.dmn.xml.converter.DmnXMLConverter;
import org.springframework.stereotype.Component;

/**
 * TODO §61 (introduction-dmn) — Flowable does not implement FEEL despite the DMN standard defining
 * it: its DMN engine evaluates input/output cells via <b>JUEL</b>, the same expression language
 * already hardened for BPMN gateways ({@code FlowableBpmnParserAdapter.assertSafeConditionExpression},
 * TODO §57 H1). Verified by decompiling the shipped {@code flowable-dmn-engine} 8.0.0 classes, not
 * assumed: {@code ELInputEntryExpressionPreParser.parse} treats a raw cell as a short <i>fragment</i>
 * (an optional comparison operator followed by a value, or a bare value meaning implicit equality)
 * and wraps it as {@code #{inputVar <op> <value>}} before evaluation — <b>except</b> when the raw
 * cell already contains {@code #{} or ({${} and }}}, in which case Flowable passes it through
 * <em>completely unchanged</em> as a full EL expression, and except when the cell starts with
 * {@code .}, which is appended directly as a property-navigation suffix. Both of those bypasses are
 * rejected outright below — no legitimate business rule needs raw EL or property navigation in a
 * decision table cell. For the remaining "fragment" form, the value half must be a quoted literal
 * or a number/boolean — never a bare unquoted word, which is exactly the H1-bis vulnerability: UEL
 * resolves an unmatched identifier against a Spring bean of the same name.
 */
@Component
class FlowableDmnParserAdapter implements DmnParserPort {

    private static final Set<HitPolicy> ALLOWED_HIT_POLICIES = Set.of(HitPolicy.UNIQUE, HitPolicy.FIRST, HitPolicy.COLLECT);

    /** Longest-prefix-first so {@code >=}/{@code <=} are never mistaken for {@code >}/{@code <}. */
    private static final List<String> COMPARISON_OPERATORS = List.of(">=", "<=", "==", "!=", "<", ">");

    private static final Pattern SAFE_VALUE =
            Pattern.compile("true|false|-?\\d+(\\.\\d+)?|'[^']*'|\"[^\"]*\"");

    @Override
    public String extractDecisionKey(byte[] dmnXml) {
        DmnDefinition model;
        try {
            model = new DmnXMLConverter().convertToDmnModel(() -> new ByteArrayInputStream(dmnXml), true, false);
        } catch (Exception ex) {
            throw new InvalidDmnException("File is not a valid DMN 1.3 document: " + ex.getMessage(), ex);
        }
        List<Decision> decisions = model.getDecisions();
        if (decisions == null || decisions.size() != 1) {
            throw new InvalidDmnException(
                    "DMN document must define exactly one decision, found: " + (decisions == null ? 0 : decisions.size()));
        }
        Decision decision = decisions.get(0);
        if (decision.getId() == null || decision.getId().isBlank()) {
            throw new InvalidDmnException("DMN decision does not define an id");
        }
        assertSafeDecision(decision);
        return decision.getId();
    }

    private static void assertSafeDecision(Decision decision) {
        if (!(decision.getExpression() instanceof DecisionTable table)) {
            throw new InvalidDmnException("DMN decision must be a decision table: " + decision.getId());
        }
        if (!ALLOWED_HIT_POLICIES.contains(table.getHitPolicy())) {
            throw new InvalidDmnException("DMN hit policy is not allowed: " + table.getHitPolicy());
        }
        List<DecisionRule> rules = table.getRules();
        if (rules == null || rules.isEmpty()) {
            throw new InvalidDmnException("DMN decision table has no rules: " + decision.getId());
        }
        for (DecisionRule rule : rules) {
            for (RuleInputClauseContainer input : safeList(rule.getInputEntries())) {
                assertSafeCellText(
                        "rule " + rule.getRuleNumber() + " inputEntry",
                        input.getInputEntry() == null ? null : input.getInputEntry().getText());
            }
            for (RuleOutputClauseContainer output : safeList(rule.getOutputEntries())) {
                assertSafeCellText(
                        "rule " + rule.getRuleNumber() + " outputEntry",
                        output.getOutputEntry() == null ? null : output.getOutputEntry().getText());
            }
        }
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static void assertSafeCellText(String owner, String rawText) {
        if (rawText == null) {
            return;
        }
        String trimmed = rawText.trim();
        if (trimmed.isEmpty() || "-".equals(trimmed)) {
            // Mirrors the engine's own shortcut (RuleEngineExecutorImpl): empty / "-" evaluates to
            // true without ever reaching the EL evaluator.
            return;
        }
        if (trimmed.contains("#{") || (trimmed.contains("${") && trimmed.contains("}"))) {
            throw new InvalidDmnException("DMN cell expression is not allowed (raw EL): " + owner);
        }
        if (trimmed.startsWith(".")) {
            throw new InvalidDmnException("DMN cell expression is not allowed (property navigation): " + owner);
        }
        String value = trimmed;
        for (String operator : COMPARISON_OPERATORS) {
            if (trimmed.startsWith(operator)) {
                value = trimmed.substring(operator.length()).trim();
                break;
            }
        }
        if (!SAFE_VALUE.matcher(value).matches()) {
            throw new InvalidDmnException("DMN cell expression is not allowed: " + owner);
        }
    }
}
