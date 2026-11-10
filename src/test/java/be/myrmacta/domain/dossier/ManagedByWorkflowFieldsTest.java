package be.myrmacta.domain.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.model.FieldValidationError;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** TODO §126 — refus des clés touchées hors fieldMappingTask (D-MANAGED-WRITE). */
class ManagedByWorkflowFieldsTest {

    private static FieldDefinition plain(String name) {
        return new FieldDefinition(name, name, FieldType.STRING, false, null, null, null, false);
    }

    private static FieldDefinition managed(String name) {
        return new FieldDefinition(
                name, name, FieldType.STRING, false, null, null, null, false, null, null, null, null, null, null, false,
                null, null, false, true);
    }

    @Test
    void noErrorWhenManagedFieldsAreNotTouched() {
        assertThat(ManagedByWorkflowFields.rejectTouched(List.of(managed("id"), plain("nom")), Set.of("nom")))
                .isEmpty();
        assertThat(ManagedByWorkflowFields.rejectTouched(List.of(managed("id")), Set.of())).isEmpty();
        assertThat(ManagedByWorkflowFields.rejectTouched(List.of(plain("nom")), Set.of("nom"))).isEmpty();
    }

    @Test
    void rejectsEachTouchedManagedFieldEvenWhenValueWouldBeUnchanged() {
        List<FieldValidationError> errors =
                ManagedByWorkflowFields.rejectTouched(List.of(managed("id"), managed("login"), plain("nom")), Set.of("id", "nom", "login"));
        assertThat(errors).extracting(FieldValidationError::field).containsExactly("id", "login");
        assertThat(errors).extracting(FieldValidationError::code).containsOnly("MANAGED_BY_WORKFLOW");
    }
}
