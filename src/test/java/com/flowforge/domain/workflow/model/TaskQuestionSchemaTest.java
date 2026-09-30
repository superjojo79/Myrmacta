package com.flowforge.domain.workflow.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.workflow.WorkflowVariables;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskQuestionSchemaTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String TENANT = "tenant_dev";

    @Test
    void constructor_acceptsValidSchema() {
        List<FieldDefinition> fields = List.of(
                new FieldDefinition("decision", "Decision", FieldType.SELECT, true, null,
                        List.of("approve", "reject"), null));

        TaskQuestionSchema schema =
                new TaskQuestionSchema(ID, TENANT, "orientation-dossier", "Orientation", fields, "u", "u");

        assertThat(schema.fields()).isEqualTo(fields);
    }

    @Test
    void constructor_rejectsBlankFields() {
        assertThatThrownBy(() -> new TaskQuestionSchema(ID, TENANT, "key", "Label", List.of(), "u", "u"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsSchemaKeyWithSpace() {
        List<FieldDefinition> fields = List.of(
                new FieldDefinition("decision", "Decision", FieldType.STRING, false, null, List.of(), null));

        assertThatThrownBy(() -> new TaskQuestionSchema(ID, TENANT, "bad key", "Label", fields, "u", "u"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsDuplicateFieldNames() {
        List<FieldDefinition> duplicated = List.of(
                new FieldDefinition("decision", "Decision", FieldType.STRING, false, null, List.of(), null),
                new FieldDefinition("decision", "Decision again", FieldType.NUMBER, false, null, List.of(), null));

        assertThatThrownBy(() -> new TaskQuestionSchema(ID, TENANT, "key", "Label", duplicated, "u", "u"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decision");
    }

    @Test
    void constructor_rejectsFileFieldType() {
        List<FieldDefinition> fields = List.of(
                new FieldDefinition("attachment", "Attachment", FieldType.FILE, false, null, List.of(), null));

        assertThatThrownBy(() -> new TaskQuestionSchema(ID, TENANT, "key", "Label", fields, "u", "u"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attachment");
    }

    @Test
    void constructor_rejectsTheStructuredTypesOfTheDossierFieldsForNow() {
        // TODO §76 (D-CHAMPS-USERQ) : réservés aux champs de dossier en D1.
        FieldDefinition group = new FieldDefinition("lignes", "Lignes", FieldType.REPEATABLE_GROUP, false, null,
                List.of(), null, false,
                List.of(new FieldDefinition("a", "A", FieldType.STRING, false, null, List.of(), null)), null, null);
        for (FieldDefinition field : List.of(
                new FieldDefinition("m", "Montant", FieldType.MONEY, false, null, List.of(), null),
                new FieldDefinition("r", "Ref", FieldType.EXTERNAL_REF, false, null, List.of(), null),
                group)) {
            assertThatThrownBy(() -> new TaskQuestionSchema(ID, TENANT, "key", "Label", List.of(field), "u", "u"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(field.name())
                    .hasMessageContaining("not supported");
        }
    }

    @Test
    void constructor_rejectsFieldNameCollidingWithReservedPlatformVariable() {
        List<FieldDefinition> fields = List.of(
                new FieldDefinition(WorkflowVariables.DOSSIER_ID, "Dossier", FieldType.STRING, false, null, List.of(), null));

        assertThatThrownBy(() -> new TaskQuestionSchema(ID, TENANT, "key", "Label", fields, "u", "u"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(WorkflowVariables.DOSSIER_ID);
    }

    @Test
    void constructor_rejectsFieldNameCollidingWithDocumentGenerationVariable() {
        List<FieldDefinition> fields = List.of(
                new FieldDefinition(WorkflowVariables.DOCUMENT_TYPE, "Type", FieldType.STRING, false, null, List.of(), null));

        assertThatThrownBy(() -> new TaskQuestionSchema(ID, TENANT, "key", "Label", fields, "u", "u"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(WorkflowVariables.DOCUMENT_TYPE);
    }

    @Test
    void withUpdate_preservesIdentityAndCreatedBy() {
        List<FieldDefinition> fields = List.of(
                new FieldDefinition("decision", "Decision", FieldType.STRING, false, null, List.of(), null));
        TaskQuestionSchema schema =
                new TaskQuestionSchema(ID, TENANT, "key", "Label", fields, "creator", "creator");

        TaskQuestionSchema updated = schema.withUpdate("New label", fields, "updater");

        assertThat(updated.id()).isEqualTo(ID);
        assertThat(updated.createdBy()).isEqualTo("creator");
        assertThat(updated.updatedBy()).isEqualTo("updater");
        assertThat(updated.label()).isEqualTo("New label");
    }
}
