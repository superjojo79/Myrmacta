package be.myrmacta.domain.workflow;

import be.myrmacta.domain.workflow.model.StartVariableDeclaration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TODO §111 (issue #187, D-START-DECLARED) — forme des variables déclarées dans le BPMN, et filtre appliqué
 * aux valeurs fournies au démarrage. Sans déclaration, la saisie libre reste inchangée. Avec déclaration,
 * seules ces variables passent, en texte, et une obligatoire absente ou blanche est refusée. Le message
 * nomme la variable, jamais la valeur.
 */
public final class StartVariableDeclarations {

    public static final int MAX_DECLARATIONS = 30;
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_LABEL_LENGTH = 200;
    public static final int MAX_VALUE_LENGTH = 4_000;

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private StartVariableDeclarations() {}

    public record Raw(String name, String label, String required) {}

    public static List<StartVariableDeclaration> parse(List<Raw> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_DECLARATIONS) {
            throw new IllegalArgumentException(
                    "flowable:startVariable allows at most " + MAX_DECLARATIONS + " declarations");
        }
        List<StartVariableDeclaration> declarations = new ArrayList<>();
        Set<String> names = new java.util.HashSet<>();
        for (Raw item : raw) {
            String name = item.name() == null ? "" : item.name().strip();
            if (name.length() > MAX_NAME_LENGTH || !NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("flowable:startVariable name is invalid");
            }
            if (WorkflowVariables.reservedKeys().contains(name)) {
                throw new IllegalArgumentException("flowable:startVariable name is reserved: " + name);
            }
            if (!names.add(name)) {
                throw new IllegalArgumentException("flowable:startVariable name is duplicated: " + name);
            }
            String label = item.label() == null ? "" : item.label().strip();
            if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH || hasControl(label)) {
                throw new IllegalArgumentException("flowable:startVariable label is invalid for " + name);
            }
            declarations.add(new StartVariableDeclaration(name, label, required(item.required(), name)));
        }
        return List.copyOf(declarations);
    }

    /**
     * @return la map d'entrée telle quelle s'il n'y a aucune déclaration (y compris {@code null} → vide) ;
     *     sinon uniquement les valeurs déclarées, rognées, sans les facultatives blanches
     */
    public static Map<String, Object> bind(List<StartVariableDeclaration> declarations, Map<String, Object> input) {
        if (declarations == null || declarations.isEmpty()) {
            return input == null ? Map.of() : input;
        }
        Map<String, Object> source = input == null ? Map.of() : input;
        Map<String, StartVariableDeclaration> byName = new LinkedHashMap<>();
        for (StartVariableDeclaration declaration : declarations) {
            byName.put(declaration.name(), declaration);
        }
        for (String key : source.keySet()) {
            if (!byName.containsKey(key)) {
                throw new IllegalArgumentException("Workflow start variable is not declared: " + key);
            }
        }
        Map<String, Object> bound = new LinkedHashMap<>();
        for (StartVariableDeclaration declaration : declarations) {
            if (!source.containsKey(declaration.name())) {
                if (declaration.required()) {
                    throw new IllegalArgumentException("Workflow start variable is required: " + declaration.name());
                }
                continue;
            }
            Object value = source.get(declaration.name());
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException("Workflow start variable is not text: " + declaration.name());
            }
            String stripped = text.strip();
            if (stripped.isEmpty()) {
                if (declaration.required()) {
                    throw new IllegalArgumentException("Workflow start variable is required: " + declaration.name());
                }
                continue;
            }
            if (hasControlExceptNewlines(stripped)) {
                throw new IllegalArgumentException("Workflow start variable is not text: " + declaration.name());
            }
            if (stripped.length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("Workflow start variable is too long: " + declaration.name());
            }
            bound.put(declaration.name(), stripped);
        }
        return Map.copyOf(bound);
    }

    private static boolean required(String raw, String name) {
        if (raw == null) {
            throw new IllegalArgumentException("flowable:startVariable required is missing for " + name);
        }
        if ("true".equalsIgnoreCase(raw.strip())) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw.strip())) {
            return false;
        }
        throw new IllegalArgumentException("flowable:startVariable required must be true or false for " + name);
    }

    private static boolean hasControl(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    /** Une note peut contenir des sauts de ligne ; les autres caractères de contrôle restent refusés. */
    private static boolean hasControlExceptNewlines(String value) {
        return value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t');
    }
}
