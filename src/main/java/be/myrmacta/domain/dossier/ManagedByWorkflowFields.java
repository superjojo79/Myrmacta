package be.myrmacta.domain.dossier;

import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * TODO §126 (D-MANAGED-WRITE) — refuse toute clé de champ {@code managedByWorkflow} touchée par un appelant autre que
 * {@code fieldMappingTask}.
 */
public final class ManagedByWorkflowFields {

    private ManagedByWorkflowFields() {}

    /**
     * @param explicitKeys clés présentes dans la payload (création ou patch), indépendamment de la valeur
     */
    public static List<FieldValidationError> rejectTouched(List<FieldDefinition> definitions, Set<String> explicitKeys) {
        Objects.requireNonNull(definitions, "definitions");
        if (explicitKeys == null || explicitKeys.isEmpty()) {
            return List.of();
        }
        List<FieldValidationError> errors = new ArrayList<>();
        for (FieldDefinition definition : definitions) {
            if (definition.isManagedByWorkflow() && explicitKeys.contains(definition.name())) {
                errors.add(FieldValidationError.managedByWorkflow(definition.name()));
            }
        }
        return List.copyOf(errors);
    }
}
