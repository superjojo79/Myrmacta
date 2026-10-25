package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.model.InvalidDmnException;
import be.myrmacta.domain.workflow.port.out.DmnParserPort;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.flowable.dmn.model.Decision;
import org.flowable.dmn.model.DecisionRule;
import org.flowable.dmn.model.DecisionService;
import org.flowable.dmn.model.DecisionTable;
import org.flowable.dmn.model.DmnDefinition;
import org.flowable.dmn.model.HitPolicy;
import org.flowable.dmn.model.InputClause;
import org.flowable.dmn.model.OutputClause;
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

    /**
     * TODO §61 adversarial review follow-up (2026-09-11) — {@code <input><inputExpression>} was
     * not validated at all: {@code ELInputEntryExpressionPreParser} injects its text <em>verbatim</em>
     * as the left operand of the wrapped expression ({@code #{ <inputExpressionText> <op> <value> }}),
     * so it is just as much a JUEL sink as a rule cell. Proven by an independent adversarial
     * subagent review, not assumed: a table whose rule cells were all allowlist-safe but whose
     * {@code inputExpression} text was {@code connectorServiceTask.getClass().getName()} passed
     * {@code extractDecisionKey} unrejected and, on the real engine, actually invoked
     * {@code getClass().getName()} on the live {@code connectorServiceTask} Spring bean during
     * rule evaluation — arbitrary method invocation, not just bean resolution. A legitimate
     * inputExpression is always a bare reference to a process variable name (every fixture in this
     * repository uses one, e.g. {@code customerCat}), never a full expression, so it is restricted
     * to a bare identifier here — same restriction already applied to identifiers inside
     * {@code FlowableBpmnParserAdapter.assertSafeConditionExpression} (no {@code .}/{@code (}/
     * {@code )}/{@code [}/{@code ]}/{@code @}/{@code #}/{@code $}/whitespace). This does not
     * eliminate the residual, already-accepted H1-bis-class risk that a bare identifier with no
     * matching process variable resolves against a same-named Spring bean instead (same accepted
     * risk as BPMN's {@code conditionExpression} identifiers) — it closes the proven, strictly
     * worse escalation to arbitrary method invocation via property/method-chain navigation.
     */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

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
        // TODO §100 (issue #204) : un service de décision se déploie comme une décision appelable par le BPMN.
        for (DecisionService service : safeList(model.getDecisionServices())) {
            assertNotReserved(service.getId(), "decisionService @id");
        }
        return decision.getId();
    }

    private static void assertSafeDecision(Decision decision) {
        if (!(decision.getExpression() instanceof DecisionTable table)) {
            throw new InvalidDmnException("DMN decision must be a decision table: " + decision.getId());
        }
        if (!ALLOWED_HIT_POLICIES.contains(table.getHitPolicy())) {
            throw new InvalidDmnException("DMN hit policy is not allowed: " + table.getHitPolicy());
        }
        // TODO §100 (issue #204) : la table ne peut pas écrire une variable réservée.
        assertNotReserved(decision.getId(), "decision @id");
        for (OutputClause output : safeList(table.getOutputs())) {
            assertNotReserved(output.getName(), "output " + output.getId() + " @name");
        }
        for (InputClause input : safeList(table.getInputs())) {
            assertSafeInputExpression(
                    decision.getId(), input.getInputExpression() == null ? null : input.getInputExpression().getText());
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

    /**
     * Issue #204 (TODO §100, D-DMN-RESERVED-OUTPUT) — {@code DmnActivityBehavior} pose chaque sortie d'un résultat unique
     * sous le nom de sa colonne, et la liste d'un résultat multiple sous l'id de la décision ou du service de décision
     * appelé (vérifié sur le bytecode Flowable 8.0.0). Une table qui nommerait ainsi {@code generatedDocumentId} ou
     * {@code dossierId} écraserait une valeur que les délégués lisent comme sûre. Même comparaison que
     * {@code BpmnReservedVariableWriteScanner} (TODO §99) : valeur sans espaces de bord, casse respectée.
     */
    private static void assertNotReserved(String variable, String owner) {
        if (variable != null && WorkflowVariables.reservedKeys().contains(variable.strip())) {
            throw new InvalidDmnException(
                    "DMN cannot write the reserved variable '" + variable.strip() + "': " + owner);
        }
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static void assertSafeInputExpression(String decisionId, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new InvalidDmnException("DMN input clause has no expression: " + decisionId);
        }
        String trimmed = rawText.trim();
        if (!SAFE_IDENTIFIER.matcher(trimmed).matches()) {
            throw new InvalidDmnException(
                    "DMN input expression must be a bare variable name: " + decisionId);
        }
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
