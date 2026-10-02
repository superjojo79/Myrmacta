package com.flowforge.domain.workflow.palette;

import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import java.util.ArrayList;
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
    private static final Set<String> LITERAL_ONLY_DELEGATES = Set.of("businessExpressionTask", "fieldMappingTask");

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
                if (p.type() == ParameterType.REFERENCE && p.binding() == ParameterBinding.ATTRIBUTE && value != null) {
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
                if ("formulaResult".equals(parameter.name())) {
                    checkFormulaResult(owner, value);
                }
                if ("fieldMapping".equals(parameter.name())) {
                    FieldMappingSpec.parse(value, owner);
                }
            }
            default -> { }
        }
    }

    private static void requireIn(String owner, BlockParameter parameter, String value) {
        if (!parameter.enumValues().contains(value)) {
            throw new InvalidBpmnException(owner + " must be one of " + parameter.enumValues() + ", got: " + value);
        }
    }
}
