package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.AllowedBpmnDelegates;
import com.flowforge.domain.workflow.TaskQuestionFormKeys;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.domain.workflow.portal.model.PortalRecipientSource;
import com.flowforge.domain.workflow.portal.model.PortalTaskDefinition;
import java.util.ArrayList;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.BusinessRuleTask;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.HasExecutionListeners;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ScriptTask;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.TaskWithFieldExtensions;
import org.flowable.bpmn.model.UserTask;
import org.springframework.stereotype.Component;

@Component
class FlowableBpmnParserAdapter implements BpmnParserPort {

    @Override
    public String extractProcessDefinitionKey(byte[] bpmnXml) {
        BpmnModel model;
        try {
            model = new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(bpmnXml), true, false);
        } catch (Exception ex) {
            throw new InvalidBpmnException("File is not a valid BPMN 2.0 document: " + ex.getMessage(), ex);
        }
        Process mainProcess = model.getMainProcess();
        if (mainProcess == null || mainProcess.getId() == null || mainProcess.getId().isBlank()) {
            throw new InvalidBpmnException("BPMN document does not define a process with an id");
        }
        for (Process process : model.getProcesses()) {
            assertSafeProcess(process);
        }
        // TODO §69 : après les règles ciblées ci-dessus (qui gardent leurs messages précis), toute
        // autre surface UEL du document est refusée — voir BpmnExpressionSurfaceScanner.
        BpmnExpressionSurfaceScanner.assertNoUnvettedExpressions(bpmnXml);
        return mainProcess.getId();
    }

    @Override
    public Set<String> extractReferencedDecisionKeys(byte[] bpmnXml) {
        BpmnModel model;
        try {
            model = new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(bpmnXml), true, false);
        } catch (Exception ex) {
            throw new InvalidBpmnException("File is not a valid BPMN 2.0 document: " + ex.getMessage(), ex);
        }
        Set<String> keys = new HashSet<>();
        for (Process process : model.getProcesses()) {
            collectDecisionKeys(process.getFlowElements(), keys);
        }
        return keys;
    }

    @Override
    public List<PortalTaskDefinition> extractPortalTasks(byte[] bpmnXml) {
        BpmnModel model;
        try {
            model = new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(bpmnXml), true, false);
        } catch (Exception ex) {
            throw new InvalidBpmnException("File is not a valid BPMN 2.0 document: " + ex.getMessage(), ex);
        }
        List<PortalTaskDefinition> tasks = new ArrayList<>();
        for (Process process : model.getProcesses()) {
            collectPortalTasks(process.getFlowElements(), tasks);
        }
        return List.copyOf(tasks);
    }

    private static void collectPortalTasks(Collection<FlowElement> elements, List<PortalTaskDefinition> tasks) {
        if (elements == null) {
            return;
        }
        for (FlowElement element : elements) {
            if (element instanceof UserTask userTask) {
                portalDefinition(userTask).ifPresent(tasks::add);
            }
            if (element instanceof SubProcess subProcess) {
                collectPortalTasks(subProcess.getFlowElements(), tasks);
            }
        }
    }

    /**
     * TODO §68 D-PORTAL-BPMN-MARKER — la présence de {@code flowable:portalRecipient} fait d'un
     * {@code userTask} une tâche externe. Flowable conserve ces attributs inconnus sans jamais les
     * évaluer ({@code BpmnXMLUtil.addCustomAttributes}, sources 8.0.0) ; leur caractère littéral est
     * de toute façon garanti pour tout le document par {@link BpmnExpressionSurfaceScanner} (§69).
     * Règles : {@code formKey} = {@code user-question:<schemaKey>} (D-PORTAL-FORM), source
     * {@code dossierField:<nom>} ou {@code variable:<nom>}, durée entière 1–{@value #MAX_PORTAL_TTL_DAYS}
     * jours (le plafond tenant est vérifié au déploiement), et aucune assignation interne
     * (D-PORTAL-NO-ASSIGNEE).
     */
    static Optional<PortalTaskDefinition> portalDefinition(UserTask userTask) {
        String owner = "BPMN external portal task " + userTask.getId();
        String recipientRaw = userTask.getAttributeValue(FLOWABLE_NAMESPACE, PORTAL_RECIPIENT_ATTRIBUTE);
        String ttlRaw = userTask.getAttributeValue(FLOWABLE_NAMESPACE, PORTAL_TTL_ATTRIBUTE);
        if (recipientRaw == null) {
            if (ttlRaw != null) {
                throw new InvalidBpmnException(owner + ": portalLinkTtlDays requires portalRecipient");
            }
            return Optional.empty();
        }
        PortalRecipientSource source = PortalRecipientSource.parse(recipientRaw)
                .orElseThrow(() -> new InvalidBpmnException(
                        owner + ": portalRecipient must be dossierField:<name> or variable:<name>"));
        String schemaKey = TaskQuestionFormKeys.schemaKeyOf(userTask.getFormKey())
                .filter(key -> SCHEMA_KEY.matcher(key).matches())
                .orElseThrow(() -> new InvalidBpmnException(
                        owner + ": formKey must be user-question:<schemaKey>"));
        Integer ttlDays = null;
        if (ttlRaw != null) {
            try {
                ttlDays = Integer.valueOf(ttlRaw.trim());
            } catch (NumberFormatException ex) {
                throw new InvalidBpmnException(owner + ": portalLinkTtlDays must be an integer", ex);
            }
            if (ttlDays < 1 || ttlDays > MAX_PORTAL_TTL_DAYS) {
                throw new InvalidBpmnException(
                        owner + ": portalLinkTtlDays must be between 1 and " + MAX_PORTAL_TTL_DAYS);
            }
        }
        if (userTask.getAssignee() != null
                || !safeList(userTask.getCandidateUsers()).isEmpty()
                || !safeList(userTask.getCandidateGroups()).isEmpty()) {
            throw new InvalidBpmnException(owner + ": an external task must not be assigned internally");
        }
        return Optional.of(new PortalTaskDefinition(userTask.getId(), schemaKey, source, ttlDays));
    }

    private static final String FLOWABLE_NAMESPACE = "http://flowable.org/bpmn";
    private static final String PORTAL_RECIPIENT_ATTRIBUTE = "portalRecipient";
    private static final String PORTAL_TTL_ATTRIBUTE = "portalLinkTtlDays";
    private static final int MAX_PORTAL_TTL_DAYS = 365;
    /** Même format que {@code TaskQuestionSchema} (clé de schéma {@code user-question}). */
    private static final Pattern SCHEMA_KEY = Pattern.compile("[A-Za-z0-9_-]+");

    private static void collectDecisionKeys(Collection<FlowElement> elements, Set<String> keys) {
        if (elements == null) {
            return;
        }
        for (FlowElement element : elements) {
            if (element instanceof ServiceTask serviceTask
                    && DMN_SERVICE_TASK_TYPE.equals(serviceTask.getType())
                    && serviceTask.getFieldExtensions() != null
                    && serviceTask.getFieldExtensions().size() == 1) {
                FieldExtension field = serviceTask.getFieldExtensions().get(0);
                if (DMN_DECISION_TABLE_REFERENCE_KEY_FIELD.equals(field.getFieldName())
                        && field.getStringValue() != null) {
                    keys.add(field.getStringValue());
                }
            }
            if (element instanceof SubProcess subProcess) {
                collectDecisionKeys(subProcess.getFlowElements(), keys);
            }
        }
    }

    private static void assertSafeProcess(Process process) {
        assertSafeListeners("process " + process.getId(), process.getExecutionListeners());
        walk(process.getFlowElements());
    }

    private static void walk(Collection<FlowElement> elements) {
        if (elements == null) {
            return;
        }
        for (FlowElement element : elements) {
            assertSafeElement(element);
            if (element instanceof SubProcess subProcess) {
                walk(subProcess.getFlowElements());
            }
        }
    }

    private static void assertSafeElement(FlowElement element) {
        if (element instanceof HasExecutionListeners hasListeners) {
            assertSafeListeners(element.getId(), hasListeners.getExecutionListeners());
        }
        if (element instanceof UserTask userTask) {
            assertSafeListeners(element.getId() + " taskListeners", userTask.getTaskListeners());
            assertSafeUserTask(userTask);
        }
        if (element instanceof ScriptTask) {
            throw new InvalidBpmnException("BPMN scriptTask is not allowed: " + element.getId());
        }
        if (element instanceof BusinessRuleTask) {
            throw new InvalidBpmnException("BPMN businessRuleTask is not allowed: " + element.getId());
        }
        if (element instanceof ServiceTask serviceTask) {
            assertSafeServiceTask(serviceTask);
        }
        if (element instanceof TaskWithFieldExtensions taskWithFields && !isDmnServiceTask(element)) {
            assertNoFieldExtensions(element.getId(), taskWithFields.getFieldExtensions());
        }
        if (element instanceof SequenceFlow sequenceFlow) {
            assertSafeConditionExpression(element.getId(), sequenceFlow.getConditionExpression());
        }
    }

    private static boolean isDmnServiceTask(FlowElement element) {
        return element instanceof ServiceTask serviceTask && DMN_SERVICE_TASK_TYPE.equals(serviceTask.getType());
    }

    /**
     * TODO §61 (introduction-dmn), D1 -- the real Flowable DMN invocation mechanism is not
     * {@code businessRuleTask} (rejected above, câblé sur Drools, sans rapport) but
     * {@code <serviceTask flowable:type="dmn">} with a single {@code decisionTableReferenceKey}
     * field extension. This is otherwise indistinguishable from any other dangerous built-in
     * service task type (mail/camel/http/shell/...), all rejected below -- so the exception is
     * intentionally narrow: {@code type} must be exactly {@code "dmn"} (literal), exactly one field
     * extension named {@code decisionTableReferenceKey}, {@code flowable:string} only (never
     * {@code flowable:expression} -- same rule as {@link #assertLiteralAssigneeValue}, a literal
     * key value must not be able to hide a UEL expression). Whether the key actually references a
     * <em>deployed</em> decision table is verified at deploy time
     * ({@code DeployBpmnProcessUseCaseImpl}), not here -- the parser alone cannot reach the DMN
     * deployment catalog.
     */
    private static final String DMN_SERVICE_TASK_TYPE = "dmn";

    private static final String DMN_DECISION_TABLE_REFERENCE_KEY_FIELD = "decisionTableReferenceKey";

    private static void assertSafeDmnServiceTask(ServiceTask serviceTask) {
        List<FieldExtension> fields = serviceTask.getFieldExtensions();
        if (fields == null || fields.size() != 1) {
            throw new InvalidBpmnException(
                    "BPMN serviceTask type=\"dmn\" must declare exactly one field extension: " + serviceTask.getId());
        }
        FieldExtension field = fields.get(0);
        if (!DMN_DECISION_TABLE_REFERENCE_KEY_FIELD.equals(field.getFieldName())) {
            throw new InvalidBpmnException(
                    "BPMN serviceTask type=\"dmn\" field must be named decisionTableReferenceKey: "
                            + serviceTask.getId());
        }
        if (field.getExpression() != null) {
            throw new InvalidBpmnException(
                    "BPMN serviceTask type=\"dmn\" decisionTableReferenceKey must be a literal, not an expression: "
                            + serviceTask.getId());
        }
        String key = field.getStringValue();
        if (key == null || key.isBlank() || key.contains("${") || key.contains("#{")) {
            throw new InvalidBpmnException(
                    "BPMN serviceTask type=\"dmn\" decisionTableReferenceKey is not allowed: " + serviceTask.getId());
        }
    }

    private static void assertSafeServiceTask(ServiceTask serviceTask) {
        if (serviceTask.getType() != null && !serviceTask.getType().isBlank()) {
            if (DMN_SERVICE_TASK_TYPE.equals(serviceTask.getType())) {
                assertSafeDmnServiceTask(serviceTask);
                return;
            }
            throw new InvalidBpmnException(
                    "BPMN built-in serviceTask type is not allowed: " + serviceTask.getType());
        }
        String implementationType = serviceTask.getImplementationType();
        if (implementationType == null || implementationType.isBlank()) {
            throw new InvalidBpmnException(
                    "BPMN serviceTask must use flowable:delegateExpression: " + serviceTask.getId());
        }
        if (!ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION.equals(implementationType)) {
            throw new InvalidBpmnException(
                    "BPMN serviceTask implementation type is not allowed: " + implementationType);
        }
        if (!AllowedBpmnDelegates.isAllowedDelegateExpression(serviceTask.getImplementation())) {
            throw new InvalidBpmnException(
                    "BPMN delegateExpression is not in the allowlist: " + serviceTask.getImplementation());
        }
    }

    /**
     * TODO §57 revue adversariale PR #113 (H1 gap, confirmé par test réel --
     * FlowableBpmnAllowlistGapTest) : {@code assignee}/{@code candidateUsers}/{@code
     * candidateGroups} ne sont pas des sorties texte, ce sont des expressions UEL évaluées par le
     * moteur. Une première version de ce correctif autorisait {@code ${nomSimple}} (un seul
     * identifiant, ni point ni parenthèse) en pensant limiter la surface à une référence de
     * variable de process -- mais {@code ${connectorServiceTask}} EST déjà un seul identifiant
     * syntaxiquement "simple", et résout malgré tout au bean Spring du même nom : UEL retombe sur
     * la résolution de bean par nom dès qu'aucune variable de process ne porte ce nom, ce qui rend
     * indiscernables, par la seule syntaxe, une variable métier légitime et un nom de bean choisi
     * par l'attaquant. Aucun BPMN livré avec ce dépôt n'utilise d'expression ici (grep vérifié) --
     * donc littéral uniquement, sans exception.
     */
    private static void assertSafeUserTask(UserTask userTask) {
        assertLiteralAssigneeValue(userTask.getId() + " assignee", userTask.getAssignee());
        for (String candidate : safeList(userTask.getCandidateUsers())) {
            assertLiteralAssigneeValue(userTask.getId() + " candidateUsers", candidate);
        }
        for (String candidate : safeList(userTask.getCandidateGroups())) {
            assertLiteralAssigneeValue(userTask.getId() + " candidateGroups", candidate);
        }
        portalDefinition(userTask);
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static void assertLiteralAssigneeValue(String owner, String raw) {
        if (raw != null && (raw.contains("${") || raw.contains("#{"))) {
            throw new InvalidBpmnException("BPMN assignee/candidate expression is not allowed: " + owner);
        }
    }

    /**
     * TODO §57 revue adversariale PR #113 : {@code conditionExpression} reste une expression
     * booléenne légitime et courante ({@code ${decision == 'approve'}}, seul usage réel du dépôt,
     * grep vérifié) -- donc pas interdite comme {@link #assertLiteralAssigneeValue}. Allowlist
     * plutôt que blocklist, pour la même raison que ci-dessus (un simple identifiant bare peut
     * résoudre un bean) : le contenu doit être composé uniquement d'identifiants, littéraux
     * numériques/chaînes/booléens et opérateurs de comparaison/logique -- jamais de point (accès
     * propriété/méthode implicite via getter), parenthèse (appel), crochet, {@code @} (référence
     * de bean) ni {@code #{} } (EL différé) -- et doit contenir au moins un opérateur : un
     * identifiant seul serait la même ambiguïté variable-ou-bean que pour assignee.
     */
    private static final Pattern SAFE_CONDITION_TOKEN = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*|\\d+(\\.\\d+)?|'[^']*'|\"[^\"]*\"|>=|<=|==|!=|&&|\\|\\||[><!]|\\s+");

    static void assertSafeConditionExpression(String owner, String raw) {
        if (raw == null) {
            return;
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("${") && !trimmed.startsWith("#{")) {
            return;
        }
        if (!trimmed.endsWith("}")) {
            throw new InvalidBpmnException("BPMN conditionExpression is not allowed: " + owner);
        }
        String inner = trimmed.substring(2, trimmed.length() - 1);
        if (inner.contains(".")
                || inner.contains("(")
                || inner.contains(")")
                || inner.contains("[")
                || inner.contains("]")
                || inner.contains("@")
                || inner.contains("#")) {
            throw new InvalidBpmnException("BPMN conditionExpression is not allowed: " + owner);
        }
        Matcher matcher = SAFE_CONDITION_TOKEN.matcher(inner);
        int end = 0;
        boolean hasOperator = false;
        while (matcher.find(end)) {
            if (matcher.start() != end) {
                throw new InvalidBpmnException("BPMN conditionExpression is not allowed: " + owner);
            }
            String token = matcher.group();
            if (token.matches(">=|<=|==|!=|&&|\\|\\||[><!]")) {
                hasOperator = true;
            }
            end = matcher.end();
        }
        if (end != inner.length() || !hasOperator) {
            throw new InvalidBpmnException("BPMN conditionExpression is not allowed: " + owner);
        }
    }

    private static void assertNoFieldExtensions(String owner, List<FieldExtension> fields) {
        if (fields != null && !fields.isEmpty()) {
            throw new InvalidBpmnException("BPMN field extensions are not allowed: " + owner);
        }
    }

    private static void assertSafeListeners(String owner, List<FlowableListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (FlowableListener listener : listeners) {
            String type = listener.getImplementationType();
            if (ImplementationType.IMPLEMENTATION_TYPE_SCRIPT.equals(type)
                    || listener.getScriptInfo() != null) {
                throw new InvalidBpmnException("BPMN script listener is not allowed: " + owner);
            }
            if (ImplementationType.IMPLEMENTATION_TYPE_CLASS.equals(type)
                    || ImplementationType.IMPLEMENTATION_TYPE_EXPRESSION.equals(type)) {
                throw new InvalidBpmnException("BPMN listener implementation type is not allowed: " + type);
            }
            if (ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION.equals(type)
                    && !AllowedBpmnDelegates.isAllowedDelegateExpression(listener.getImplementation())) {
                throw new InvalidBpmnException(
                        "BPMN listener delegateExpression is not in the allowlist: "
                                + listener.getImplementation());
            }
            assertNoFieldExtensions(owner + " listener", listener.getFieldExtensions());
        }
    }
}
