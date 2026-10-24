package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.signature.model.SignatureBlockDefinition;
import com.flowforge.domain.signature.model.SignatureMode;
import com.flowforge.domain.signature.model.SignerKind;
import com.flowforge.domain.workflow.AllowedBpmnDelegates;
import com.flowforge.domain.workflow.TaskQuestionFormKeys;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.expression.model.InvalidExpressionException;
import com.flowforge.domain.expression.port.out.ExpressionEnginePort;
import com.flowforge.domain.workflow.palette.BlockAttributeRules;
import com.flowforge.domain.workflow.palette.FieldMappingSpec;
import com.flowforge.domain.workflow.palette.BlockParameterUsage;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.domain.workflow.portal.model.PortalIdentityField;
import com.flowforge.domain.workflow.portal.model.PortalRecipientSource;
import com.flowforge.domain.workflow.portal.model.PortalTaskDefinition;
import java.util.ArrayList;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.BusinessRuleTask;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
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

    /** TODO §75 — la syntaxe d'une formule (brique « Évaluer une expression ») est une règle de forme : refusée au parseur. */
    private static final String EXPRESSION_DELEGATE = "businessExpressionTask";

    private final ExpressionEnginePort expressionEngine;

    @org.springframework.beans.factory.annotation.Autowired
    FlowableBpmnParserAdapter(ExpressionEnginePort expressionEngine) {
        this.expressionEngine = java.util.Objects.requireNonNull(expressionEngine, "expressionEngine");
    }

    /** Pour les tests du parseur seul : moteur par défaut, mêmes bornes que la production. */
    FlowableBpmnParserAdapter() {
        this(new com.flowforge.infrastructure.expression.JsonataExpressionEngine(
                com.flowforge.domain.expression.model.ExpressionLimits.DEFAULTS, java.time.Clock.systemUTC()));
    }

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
        // TODO §99 (issue #203) : le BPMN ne peut pas recopier une valeur dans une variable réservée.
        BpmnReservedVariableWriteScanner.assertNoReservedVariableWrites(bpmnXml);
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

    @Override
    public List<BlockParameterUsage> extractBlockReferences(byte[] bpmnXml) {
        BpmnModel model;
        try {
            model = new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(bpmnXml), true, false);
        } catch (Exception ex) {
            throw new InvalidBpmnException("File is not a valid BPMN 2.0 document: " + ex.getMessage(), ex);
        }
        List<BlockParameterUsage> usages = new ArrayList<>();
        for (Process process : model.getProcesses()) {
            collectBlockReferences(process.getFlowElements(), usages);
        }
        return List.copyOf(usages);
    }

    private static void collectBlockReferences(Collection<FlowElement> elements, List<BlockParameterUsage> usages) {
        if (elements == null) {
            return;
        }
        for (FlowElement element : elements) {
            usages.addAll(BlockAttributeRules.referenceUsages(
                    element.getId(), blockDelegate(element), flowableAttributes(element)));
            if (element instanceof SubProcess subProcess) {
                collectBlockReferences(subProcess.getFlowElements(), usages);
            }
        }
    }

    /** TODO §75 — la formule d'une brique « Évaluer une expression » doit être valide (syntaxe et constructions permises). */
    private void assertBusinessExpression(FlowElement element) {
        if (!EXPRESSION_DELEGATE.equals(blockDelegate(element))) {
            return;
        }
        String formula = flowableAttributes(element).get("formula");
        if (formula == null) {
            return; // déjà refusé par BlockAttributeRules (attribut obligatoire)
        }
        try {
            expressionEngine.validate(formula.strip());
        } catch (InvalidExpressionException e) {
            throw new InvalidBpmnException(
                    "BPMN attribute flowable:formula on " + element.getId() + " is invalid: " + e.getMessage());
        }
    }

    /** TODO §78 — chaque expression de la brique « Enregistrer des valeurs dans le dossier » doit être valide. */
    private void assertFieldMapping(FlowElement element) {
        if (!"fieldMappingTask".equals(blockDelegate(element))) {
            return;
        }
        String raw = flowableAttributes(element).get("fieldMapping");
        if (raw == null) {
            return; // déjà refusé par BlockAttributeRules (attribut obligatoire)
        }
        for (FieldMappingSpec.Entry entry : FieldMappingSpec.parse(
                raw, "BPMN attribute flowable:fieldMapping on " + element.getId())) {
            try {
                expressionEngine.validate(entry.expression());
            } catch (InvalidExpressionException e) {
                throw new InvalidBpmnException("BPMN attribute flowable:fieldMapping on " + element.getId()
                        + ": the expression of '" + entry.field() + "' is invalid: " + e.getMessage());
            }
        }
    }

    /** Nom du bean {@code delegateExpression} d'un {@code serviceTask}, sinon {@code null}. */
    private static String blockDelegate(FlowElement element) {
        if (element instanceof ServiceTask serviceTask
                && ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION.equals(serviceTask.getImplementationType())) {
            return AllowedBpmnDelegates.unwrapBeanName(serviceTask.getImplementation());
        }
        return null;
    }

    /** Attributs {@code flowable:*} de l'élément (nom → première valeur). */
    private static Map<String, String> flowableAttributes(FlowElement element) {
        Map<String, String> attributes = new LinkedHashMap<>();
        element.getAttributes().forEach((name, list) -> {
            for (ExtensionAttribute attribute : list) {
                if (FLOWABLE_NAMESPACE.equals(attribute.getNamespace()) && attribute.getValue() != null) {
                    attributes.putIfAbsent(name, attribute.getValue());
                }
            }
        });
        return attributes;
    }

    @Override
    public List<SignatureBlockDefinition> extractSignatureBlocks(byte[] bpmnXml) {
        BpmnModel model;
        try {
            model = new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(bpmnXml), true, false);
        } catch (Exception ex) {
            throw new InvalidBpmnException("File is not a valid BPMN 2.0 document: " + ex.getMessage(), ex);
        }
        List<SignatureBlockDefinition> blocks = new ArrayList<>();
        for (Process process : model.getProcesses()) {
            collectSignatureBlocks(process.getFlowElements(), blocks);
        }
        return List.copyOf(blocks);
    }

    private static void collectSignatureBlocks(Collection<FlowElement> elements, List<SignatureBlockDefinition> blocks) {
        if (elements == null) {
            return;
        }
        for (FlowElement element : elements) {
            if (element instanceof ServiceTask serviceTask) {
                signatureDefinition(serviceTask).ifPresent(blocks::add);
            }
            if (element instanceof SubProcess subProcess) {
                collectSignatureBlocks(subProcess.getFlowElements(), blocks);
            }
        }
    }

    private static final String SIGNATURE_DELEGATE = "signatureRequestTask";
    private static final String SIGNATURE_DOCUMENT_ATTRIBUTE = "signatureDocument";
    private static final String SIGNATURE_MODE_ATTRIBUTE = "signatureMode";
    private static final String SIGNATURE_DEADLINE_ATTRIBUTE = "signatureDeadlineDays";
    private static final String SIGNER_ELEMENT = "signer";
    private static final Set<String> SIGNER_ATTRIBUTES = Set.of("source", "kind", "required", "name");
    private static final int MAX_SIGNATURE_DEADLINE_DAYS = 365;
    /** Colonne {@code signature_request.activity_id} : varchar(128). */
    private static final int MAX_ACTIVITY_ID_LENGTH = 128;

    /**
     * TODO §72 D-SIGNATURE-BPMN-MARKER — un {@code serviceTask} {@code ${signatureRequestTask}} est un
     * bloc signature : {@code flowable:signatureDocument="variable:<nom>|dossierField:<nom>"}
     * (obligatoire), {@code flowable:signatureMode="PARALLEL|SEQUENTIAL"} (défaut {@code PARALLEL}),
     * {@code flowable:signatureDeadlineDays} (1–{@value #MAX_SIGNATURE_DEADLINE_DAYS}, plafond tenant
     * vérifié au déploiement) et un élément {@code <flowable:signer source= kind= required= name=/>} par
     * signataire, avec au moins un requis et des sources distinctes. Les sources sont des noms
     * <em>littéraux</em>, jamais évalués en UEL (§69) ; les attributs inconnus sont refusés.
     * Tout attribut ou élément de signature hors de ce bloc est refusé.
     */
    static Optional<SignatureBlockDefinition> signatureDefinition(ServiceTask serviceTask) {
        String owner = "BPMN signature block " + serviceTask.getId();
        String documentRaw = serviceTask.getAttributeValue(FLOWABLE_NAMESPACE, SIGNATURE_DOCUMENT_ATTRIBUTE);
        String modeRaw = serviceTask.getAttributeValue(FLOWABLE_NAMESPACE, SIGNATURE_MODE_ATTRIBUTE);
        String deadlineRaw = serviceTask.getAttributeValue(FLOWABLE_NAMESPACE, SIGNATURE_DEADLINE_ATTRIBUTE);
        List<ExtensionElement> signerElements = signerElements(serviceTask);
        boolean isSignatureDelegate = ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION
                        .equals(serviceTask.getImplementationType())
                && SIGNATURE_DELEGATE.equals(
                        AllowedBpmnDelegates.unwrapBeanName(serviceTask.getImplementation()));
        boolean hasSignatureMarkup =
                documentRaw != null || modeRaw != null || deadlineRaw != null || !signerElements.isEmpty();
        if (!isSignatureDelegate) {
            if (hasSignatureMarkup) {
                throw new InvalidBpmnException(
                        owner + ": signature attributes and flowable:signer require delegateExpression ${"
                                + SIGNATURE_DELEGATE + "}");
            }
            return Optional.empty();
        }
        PortalRecipientSource document = PortalRecipientSource.parse(documentRaw)
                .orElseThrow(() -> new InvalidBpmnException(
                        owner + ": signatureDocument must be dossierField:<name> or variable:<name>"));
        SignatureMode mode = modeRaw == null
                ? SignatureMode.PARALLEL
                : SignatureMode.parse(modeRaw)
                        .orElseThrow(() -> new InvalidBpmnException(
                                owner + ": signatureMode must be PARALLEL or SEQUENTIAL"));
        Integer deadlineDays = null;
        if (deadlineRaw != null) {
            try {
                deadlineDays = Integer.valueOf(deadlineRaw.trim());
            } catch (NumberFormatException ex) {
                throw new InvalidBpmnException(owner + ": signatureDeadlineDays must be an integer", ex);
            }
            if (deadlineDays < 1 || deadlineDays > MAX_SIGNATURE_DEADLINE_DAYS) {
                throw new InvalidBpmnException(
                        owner + ": signatureDeadlineDays must be between 1 and " + MAX_SIGNATURE_DEADLINE_DAYS);
            }
        }
        if (serviceTask.getId() != null && serviceTask.getId().length() > MAX_ACTIVITY_ID_LENGTH) {
            throw new InvalidBpmnException(
                    owner + ": the activity id must be at most " + MAX_ACTIVITY_ID_LENGTH + " characters");
        }
        if (signerElements.isEmpty()) {
            throw new InvalidBpmnException(owner + ": at least one flowable:signer is required");
        }
        List<SignatureBlockDefinition.SignerDefinition> signers = new ArrayList<>();
        for (ExtensionElement element : signerElements) {
            signers.add(signerDefinition(owner, element));
        }
        try {
            return Optional.of(new SignatureBlockDefinition(serviceTask.getId(), document, mode, deadlineDays, signers));
        } catch (IllegalArgumentException ex) {
            throw new InvalidBpmnException(owner + ": " + ex.getMessage(), ex);
        }
    }

    private static List<ExtensionElement> signerElements(ServiceTask serviceTask) {
        List<ExtensionElement> all = serviceTask.getExtensionElements().get(SIGNER_ELEMENT);
        if (all == null) {
            return List.of();
        }
        return all.stream().filter(e -> FLOWABLE_NAMESPACE.equals(e.getNamespace())).toList();
    }

    private static SignatureBlockDefinition.SignerDefinition signerDefinition(String owner, ExtensionElement element) {
        for (String attribute : element.getAttributes().keySet()) {
            if (!SIGNER_ATTRIBUTES.contains(attribute)) {
                throw new InvalidBpmnException(owner + ": unexpected attribute '" + attribute + "' on flowable:signer");
            }
        }
        if (element.getChildElements() != null && !element.getChildElements().isEmpty()
                || (element.getElementText() != null && !element.getElementText().isBlank())) {
            throw new InvalidBpmnException(owner + ": flowable:signer must be an empty element");
        }
        PortalRecipientSource source = PortalRecipientSource.parse(signerAttribute(element, "source"))
                .orElseThrow(() -> new InvalidBpmnException(
                        owner + ": flowable:signer source must be dossierField:<name> or variable:<name>"));
        SignerKind kind = SignerKind.parse(signerAttribute(element, "kind"))
                .orElseThrow(() -> new InvalidBpmnException(
                        owner + ": flowable:signer kind must be EXTERNAL or INTERNAL"));
        String requiredRaw = signerAttribute(element, "required");
        boolean required;
        if (requiredRaw == null || "true".equalsIgnoreCase(requiredRaw.trim())) {
            required = true;
        } else if ("false".equalsIgnoreCase(requiredRaw.trim())) {
            required = false;
        } else {
            throw new InvalidBpmnException(owner + ": flowable:signer required must be true or false");
        }
        String name = signerAttribute(element, "name");
        return new SignatureBlockDefinition.SignerDefinition(
                source, kind, required, name == null || name.isBlank() ? source.name() : name.trim());
    }

    private static String signerAttribute(ExtensionElement element, String name) {
        List<ExtensionAttribute> values = element.getAttributes().get(name);
        return values == null || values.isEmpty() ? null : values.get(0).getValue();
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
        String identityRaw = userTask.getAttributeValue(FLOWABLE_NAMESPACE, PORTAL_IDENTITY_ATTRIBUTE);
        if (recipientRaw == null) {
            if (ttlRaw != null) {
                throw new InvalidBpmnException(owner + ": portalLinkTtlDays requires portalRecipient");
            }
            if (identityRaw != null) {
                throw new InvalidBpmnException(owner + ": portalIdentityField requires portalRecipient");
            }
            return Optional.empty();
        }
        // TODO §103 (T2) — dossierField: seulement : le NRN vit dans le dossier, jamais dans une variable (N2).
        PortalIdentityField identityField = identityRaw == null ? null : PortalIdentityField.parse(identityRaw)
                .orElseThrow(() -> new InvalidBpmnException(
                        owner + ": portalIdentityField must be dossierField:<name>"));
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
        return Optional.of(new PortalTaskDefinition(userTask.getId(), schemaKey, source, ttlDays, identityField));
    }

    private static final String FLOWABLE_NAMESPACE = "http://flowable.org/bpmn";
    private static final String PORTAL_RECIPIENT_ATTRIBUTE = "portalRecipient";
    private static final String PORTAL_TTL_ATTRIBUTE = "portalLinkTtlDays";
    private static final String PORTAL_IDENTITY_ATTRIBUTE = "portalIdentityField";
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

    private void assertSafeProcess(Process process) {
        assertSafeListeners("process " + process.getId(), process.getExecutionListeners());
        walk(process.getFlowElements());
    }

    private void walk(Collection<FlowElement> elements) {
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

    private void assertSafeElement(FlowElement element) {
        // TODO §73 : forme des attributs de brique (dérivée du catalogue), sur tout élément — un attribut de brique
        // posé hors de son delegate est refusé.
        BlockAttributeRules.validate(element.getId(), blockDelegate(element), flowableAttributes(element));
        assertBusinessExpression(element);
        assertFieldMapping(element);
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
            signatureDefinition(serviceTask);
        } else if (element.getAttributeValue(FLOWABLE_NAMESPACE, SIGNATURE_DOCUMENT_ATTRIBUTE) != null
                || element.getAttributeValue(FLOWABLE_NAMESPACE, SIGNATURE_MODE_ATTRIBUTE) != null
                || element.getAttributeValue(FLOWABLE_NAMESPACE, SIGNATURE_DEADLINE_ATTRIBUTE) != null) {
            throw new InvalidBpmnException("BPMN signature attributes are only allowed on a serviceTask "
                    + "with delegateExpression ${" + SIGNATURE_DELEGATE + "}: " + element.getId());
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
