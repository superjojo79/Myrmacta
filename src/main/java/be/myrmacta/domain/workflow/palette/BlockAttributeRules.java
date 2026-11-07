package be.myrmacta.domain.workflow.palette;

import be.myrmacta.domain.precheck.model.PrecheckSpec;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import be.myrmacta.domain.workflow.portal.model.PortalRecipientSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TODO §73 — règles de <b>forme</b> des attributs {@code flowable:<nom>} posés par les briques sur les six delegates
 * qui les lisent (D-PALETTE-PARAMS), dérivées du catalogue : le parseur n'a aucune liste à maintenir de son côté
 * (D-PALETTE-CATALOG-SOURCE). L'existence des ressources référencées est vérifiée séparément au déploiement
 * ({@link #referenceUsages}). Un attribut manquant n'est pas une erreur : la variable de process peut le fournir.
 */
public final class BlockAttributeRules {

    private static final String SIGNATURE_DELEGATE = "signatureRequestTask";
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.:-]+");
    private static final Set<String> KEY_TEXT = Set.of("documentVariant", "documentLocale");
    /** Paramètres pouvant s'étendre sur plusieurs lignes (texte libre ou expression). */
    private static final Set<String> MULTILINE = BlockParameter.MULTILINE_NAMES;

    /**
     * TODO §75 — delegates qui ne lisent <b>que</b> l'attribut littéral, jamais une variable de même nom : une variable de
     * process peut être posée par un utilisateur à l'exécution (complétion de tâche) ; elle ne doit jamais devenir du code
     * exécutable. Tous leurs paramètres obligatoires doivent donc figurer dans le BPMN.
     */
    private static final Set<String> LITERAL_ONLY_DELEGATES =
            Set.of("businessExpressionTask", "fieldMappingTask", "dossierSearchTask", "aiPrecheckTask", "computeDeadlineTask");

    /** TODO §102 (lot B, R4) — noms de variables écrites par la brique « Rechercher des dossiers ». */
    private static final Set<String> RESULT_VARIABLE_PARAMETERS =
            Set.of("formulaResult", "searchCountResult", "searchFirstReferenceResult", "precheckResult");

    /**
     * Le nom d'attribut est {@code formulaResult} et non {@code resultVariable} : ce dernier est un attribut natif de
     * Flowable (alias de {@code resultVariableName}) absorbé par le convertisseur BPMN, donc invisible ici.
     */
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,59}");

    /**
     * Variables de process lues ou écrites par le moteur et les delegates : une expression ne doit pas les écraser (elles
     * pilotent le dossier courant, les connecteurs, les documents, la boucle multi-instance…). S'y ajoutent les variables
     * de {@link WorkflowVariables} (plateforme et clés métier connues) et tous les noms de paramètres de brique, qu'un
     * delegate relit en repli depuis une variable de même nom.
     */
    private static final Set<String> RESERVED_VARIABLES = Set.of(
            "dossierId", "dossierWorkflowLinkId", "connectorInput", "connectorResult", "childDossierFields",
            "childWorkflowVariables", "generatedDocumentId", "notificationPayload", "now", "variables", "dossier",
            // Variables de Flowable : multi-instance, contexte d'exécution et d'identité.
            "nrOfInstances", "nrOfActiveInstances", "nrOfCompletedInstances", "loopCounter", "execution", "task",
            "initiator", "authenticatedUserId", "processInstanceId", "processDefinitionId", "executionId", "taskId");

    /** D-NOTIF-ATTACH-NAME — nom de fichier vu par le destinataire : ni chemin, ni guillemet, ni point initial. */
    private static final Pattern FILE_NAME = Pattern.compile("[\\p{L}\\p{N}_()'-][\\p{L}\\p{N} ._()'-]*");

    /** Issue #19 (TODO §123) — sources dont l'existence n'est pas vérifiée au déploiement. */
    private static final Set<String> UNVERIFIED_AT_DEPLOY = Set.of("notificationTemplates");
    private static final Pattern VARIABLE_SOURCE = Pattern.compile("variable:[A-Za-z][A-Za-z0-9_]{0,59}");

    private static final Set<String> UUID_SOURCES = Set.of("connectorInstances", "notificationWebhooks");
    private static final Pattern UUID_FORM =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Noms d'attributs gérés : paramètres {@code ATTRIBUTE} des briques dont le delegate lit ces attributs. */
    public static final Set<String> MANAGED_ATTRIBUTES;

    static {
        Set<String> names = new LinkedHashSet<>();
        for (BuildingBlock block : BuildingBlockCatalog.all()) {
            if (isManaged(block)) {
                for (BlockParameter p : block.parameters()) {
                    if (p.binding() == ParameterBinding.ATTRIBUTE) {
                        names.add(p.name());
                    }
                }
            }
        }
        MANAGED_ATTRIBUTES = Set.copyOf(names);
    }

    private BlockAttributeRules() {}

    /**
     * @param delegate nom du bean {@code delegateExpression} de l'activité ({@code null} s'il n'y en a pas)
     * @param flowableAttributes attributs {@code flowable:*} présents sur l'activité (nom → valeur)
     */
    public static void validate(String activityId, String delegate, Map<String, String> flowableAttributes) {
        Optional<BuildingBlock> block = BuildingBlockCatalog.forDelegate(delegate).filter(BlockAttributeRules::isManaged);
        for (Map.Entry<String, String> entry : flowableAttributes.entrySet()) {
            String name = entry.getKey();
            if (!MANAGED_ATTRIBUTES.contains(name)) {
                continue; // hors catalogue : traitement actuel (préservé)
            }
            BlockParameter parameter = block.flatMap(b -> b.parameters().stream()
                            .filter(p -> p.binding() == ParameterBinding.ATTRIBUTE && p.name().equals(name))
                            .findFirst())
                    .orElseThrow(() -> new InvalidBpmnException("BPMN attribute flowable:" + name + " is not allowed on "
                            + activityId + (delegate == null ? " (not a building-block serviceTask)"
                                    : " (delegate " + delegate + ")")));
            check(activityId, parameter, entry.getValue());
        }
        String count = flowableAttributes.get("searchCountResult");
        if (count != null && count.strip().equals(
                String.valueOf(flowableAttributes.get("searchFirstReferenceResult")).strip())) {
            throw new InvalidBpmnException("BPMN attributes flowable:searchCountResult and flowable:searchFirstReferenceResult on "
                    + activityId + " must name two distinct variables");
        }
        // TODO §112 (revue M3) : la requête construite par le BPMN part vers l'instance écrite dans le BPMN, jamais vers une
        // instance choisie par une variable de process (posable à une complétion de tâche ou au démarrage).
        if (flowableAttributes.containsKey("connectorRequest") && !flowableAttributes.containsKey("connectorInstanceId")) {
            throw new InvalidBpmnException("BPMN attribute flowable:connectorInstanceId is required on " + activityId
                    + " when flowable:connectorRequest is set");
        }
        checkTemplateCompanions(activityId, flowableAttributes);
        if (flowableAttributes.containsKey("notificationAttachment") || flowableAttributes.containsKey("notificationAttachmentName")) {
            checkAttachmentCompanions(activityId, flowableAttributes);
        }
        block.filter(b -> LITERAL_ONLY_DELEGATES.contains(delegate)).ifPresent(b -> {
            for (BlockParameter p : b.parameters()) {
                if (p.required() && p.binding() == ParameterBinding.ATTRIBUTE && !flowableAttributes.containsKey(p.name())) {
                    throw new InvalidBpmnException("BPMN attribute flowable:" + p.name() + " is required on " + activityId
                            + " (delegate " + delegate + " reads only literal attributes)");
                }
            }
        });
    }

    /** Ressources du tenant référencées par les attributs de cette activité (à vérifier par le use case de déploiement). */
    public static List<BlockParameterUsage> referenceUsages(
            String activityId, String delegate, Map<String, String> flowableAttributes) {
        List<BlockParameterUsage> usages = new ArrayList<>();
        BuildingBlockCatalog.forDelegate(delegate).filter(BlockAttributeRules::isManaged).ifPresent(block -> {
            for (BlockParameter p : block.parameters()) {
                String value = flowableAttributes.get(p.name());
                // Issue #19 : un gabarit de notification peut arriver après le BPMN (promotion) ; son absence échoue à l'exécution.
                if (p.type() == ParameterType.REFERENCE && p.binding() == ParameterBinding.ATTRIBUTE && value != null
                        && !UNVERIFIED_AT_DEPLOY.contains(p.valueSource())) {
                    usages.add(new BlockParameterUsage(activityId, block.id(), p.name(), p.valueSource(), value.strip()));
                }
            }
        });
        return List.copyOf(usages);
    }

    private static void checkFormulaResult(String owner, String value) {
        if (!VARIABLE_NAME.matcher(value).matches()) {
            throw new InvalidBpmnException(owner + " must be a variable name (letters, digits, _; starts with a letter)");
        }
        if (RESERVED_VARIABLES.contains(value) || WorkflowVariables.isReservedOrKnownKey(value)
                || MANAGED_ATTRIBUTES.contains(value)) {
            throw new InvalidBpmnException(owner + " names a reserved variable (" + value + "); choose another name");
        }
    }

    /**
     * TODO §93 — {@code notificationTo} : adresse littérale, ou source {@code dossierField:<nom>} / {@code variable:<nom>}
     * (même grammaire que {@code flowable:portalRecipient}). Une source mal formée est refusée ici plutôt que de partir comme
     * adresse littérale à l'exécution.
     */
    private static void checkRecipient(String owner, String value) {
        if (PortalRecipientSource.hasSourcePrefix(value) && PortalRecipientSource.parse(value).isEmpty()) {
            throw new InvalidBpmnException(owner + " must be dossierField:<name>, variable:<name> or a literal address");
        }
    }

    /**
     * Revue adversariale #183 (M1, L5) — une pièce jointe ne part que vers un destinataire écrit dans le BPMN, par le canal
     * EMAIL écrit dans le BPMN : sinon la variable {@code notificationTo}, posée par un utilisateur à la complétion d'une
     * tâche ou au démarrage, enverrait le PDF du dossier à l'adresse de son choix. Un nom de fichier sans pièce jointe est
     * une erreur de conception, pas une option ignorée.
     */
    private static void checkAttachmentCompanions(String activityId, Map<String, String> attributes) {
        String owner = "BPMN attribute flowable:notificationAttachment on " + activityId;
        if (!attributes.containsKey("notificationAttachment")) {
            throw new InvalidBpmnException("BPMN attribute flowable:notificationAttachmentName on " + activityId
                    + " requires flowable:notificationAttachment");
        }
        if (!attributes.containsKey("notificationTo")) {
            throw new InvalidBpmnException(owner + " requires flowable:notificationTo in the BPMN");
        }
        String channels = attributes.get("notificationChannels");
        boolean email = channels != null && Arrays.stream(channels.split(","))
                .anyMatch(c -> "EMAIL".equals(c.strip()));
        if (!email) {
            throw new InvalidBpmnException(owner + " requires EMAIL in flowable:notificationChannels");
        }
    }

    /**
     * Issue #19 (TODO §123) — un gabarit exclut l'objet et le corps littéraux, exige le canal EMAIL seul et un
     * destinataire écrit dans le BPMN ; variante et langue n'ont de sens qu'avec un gabarit.
     */
    private static void checkTemplateCompanions(String activityId, Map<String, String> attributes) {
        boolean template = attributes.containsKey("notificationTemplate");
        if (!template) {
            for (String companion : List.of("notificationVariant", "notificationLocale")) {
                if (attributes.containsKey(companion)) {
                    throw new InvalidBpmnException("BPMN attribute flowable:" + companion + " on " + activityId
                            + " requires flowable:notificationTemplate");
                }
            }
            return;
        }
        String owner = "BPMN attribute flowable:notificationTemplate on " + activityId;
        for (String literal : List.of("notificationSubject", "notificationBody")) {
            if (attributes.containsKey(literal)) {
                throw new InvalidBpmnException(owner + " excludes flowable:" + literal);
            }
        }
        String channels = attributes.get("notificationChannels");
        List<String> listed = channels == null ? List.of()
                : Arrays.stream(channels.split(",")).map(String::strip).filter(c -> !c.isEmpty()).toList();
        if (listed.size() != 1 || !"EMAIL".equals(listed.get(0))) {
            throw new InvalidBpmnException(owner + " requires flowable:notificationChannels=\"EMAIL\" (EMAIL only)");
        }
        if (!attributes.containsKey("notificationTo")) {
            throw new InvalidBpmnException(owner + " requires flowable:notificationTo in the BPMN");
        }
    }

    private static void checkFileName(String owner, String value) {
        if (!FILE_NAME.matcher(value).matches()) {
            throw new InvalidBpmnException(owner
                    + " must be a file name (letters, digits, space, . _ - ( ) '; not starting with a dot)");
        }
    }

    private static boolean isManaged(BuildingBlock block) {
        String delegate = block.marker().delegate();
        return delegate != null && !SIGNATURE_DELEGATE.equals(delegate);
    }

    private static void check(String activityId, BlockParameter parameter, String raw) {
        String owner = "BPMN attribute flowable:" + parameter.name() + " on " + activityId;
        if (raw == null || raw.isBlank()) {
            throw new InvalidBpmnException(owner + " must not be blank");
        }
        if (raw.contains("${") || raw.contains("#{")) {
            throw new InvalidBpmnException(owner + " must be a literal value, not an expression");
        }
        if (raw.length() > parameter.maxLength()) {
            throw new InvalidBpmnException(owner + " exceeds " + parameter.maxLength() + " characters");
        }
        if (raw.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            throw new InvalidBpmnException(owner + " contains control characters");
        }
        // Revue adversariale I2 : l'exécution lit la valeur telle quelle (après strip) — espaces de bord et retours à la
        // ligne sont donc refusés plutôt que « tolérés » à la validation puis fatals à l'exécution.
        boolean multiline = MULTILINE.contains(parameter.name());
        if (!raw.equals(raw.strip()) && !multiline) {
            throw new InvalidBpmnException(owner + " must not start or end with whitespace");
        }
        if (!multiline && raw.chars().anyMatch(c -> c == '\n' || c == '\r' || c == '\t')) {
            throw new InvalidBpmnException(owner + " must be on a single line");
        }
        String value = raw.strip();
        switch (parameter.type()) {
            case ENUM -> requireIn(owner, parameter, value);
            case ENUM_MULTI -> {
                String[] parts = value.split(",");
                boolean any = false;
                for (String part : parts) {
                    if (!part.isBlank()) {
                        requireIn(owner, parameter, part.trim());
                        any = true;
                    }
                }
                if (!any) {
                    throw new InvalidBpmnException(owner + " needs at least one value");
                }
            }
            case REFERENCE -> {
                boolean uuidForm = UUID_SOURCES.contains(parameter.valueSource());
                if (uuidForm ? !UUID_FORM.matcher(value).matches() : !KEY.matcher(value).matches()) {
                    throw new InvalidBpmnException(owner + (uuidForm ? " must be a UUID" : " has an invalid format"));
                }
            }
            case TEXT -> {
                if (KEY_TEXT.contains(parameter.name()) && !KEY.matcher(value).matches()) {
                    throw new InvalidBpmnException(owner + " has an invalid format");
                }
                if (RESULT_VARIABLE_PARAMETERS.contains(parameter.name())) {
                    checkFormulaResult(owner, value);
                }
                if ("searchCriteria".equals(parameter.name())) {
                    DossierSearchSpec.parse(value, owner);
                }
                if ("fieldMapping".equals(parameter.name())) {
                    FieldMappingSpec.parse(value, owner);
                }
                checkPrecheck(owner, parameter.name(), value);
                if ("notificationTo".equals(parameter.name())) {
                    checkRecipient(owner, value);
                }
                if ("notificationVariant".equals(parameter.name()) || "notificationLocale".equals(parameter.name())) {
                    boolean variable = value.startsWith("variable:");
                    if (variable ? !VARIABLE_SOURCE.matcher(value).matches() : !KEY.matcher(value).matches()) {
                        throw new InvalidBpmnException(owner + " must be a literal key or variable:<name>");
                    }
                }
                if ("notificationAttachmentName".equals(parameter.name())) {
                    checkFileName(owner, value);
                }
            }
            case SOURCE_REF -> {
                if (PortalRecipientSource.parse(value).isEmpty()) {
                    throw new InvalidBpmnException(owner + " must be dossierField:<name> or variable:<name>");
                }
            }
            default -> { }
        }
    }

    /** TODO §106 (D-PRECHECK-SPEC) — grammaire de chaque attribut de la brique « Pré-vérification IA ». */
    private static void checkPrecheck(String owner, String name, String value) {
        try {
            switch (name) {
                case PrecheckSpec.PIECES -> PrecheckSpec.parsePieces(value, owner);
                case PrecheckSpec.FIELDS -> PrecheckSpec.parseFields(value, owner);
                case PrecheckSpec.QUESTIONS -> PrecheckSpec.parseQuestions(value, owner);
                case PrecheckSpec.RULES -> {
                    for (PrecheckSpec.Rule rule : PrecheckSpec.parseRules(value, owner)) {
                        if (RESERVED_VARIABLES.contains(rule.variable())) {
                            throw new InvalidBpmnException(owner + ": variable " + rule.variable() + " is reserved");
                        }
                    }
                }
                default -> { }
            }
        } catch (IllegalArgumentException ex) {
            throw new InvalidBpmnException(ex.getMessage());
        }
    }

    private static void requireIn(String owner, BlockParameter parameter, String value) {
        if (!parameter.enumValues().contains(value)) {
            throw new InvalidBpmnException(owner + " must be one of " + parameter.enumValues() + ", got: " + value);
        }
    }
}
