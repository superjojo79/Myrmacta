package be.myrmacta.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DossierTypeTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String TENANT = "tenant_dev";

    @Test
    void constructor_acceptsAlphanumericCode() {
        DossierType type =
                DossierType.inherit(ID, TENANT, "CLAIM-1", "Claim", "wf-claim", true, false, List.of());

        assertThat(type.code()).isEqualTo("CLAIM-1");
    }

    @Test
    void constructor_rejectsCodeWithControlCharacter() {
        assertThatThrownBy(() -> DossierType.inherit(
                        ID, TENANT, "CLAIM\nCODE", "Claim", "wf-claim", true, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsCodeWithSpace() {
        assertThatThrownBy(() -> DossierType.inherit(
                        ID, TENANT, "CLAIM CODE", "Claim", "wf-claim", true, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsDuplicateFieldDefinitionNames() {
        List<FieldDefinition> duplicated = List.of(
                new FieldDefinition("priority", "Priority", FieldType.STRING, false, null, List.of(), null),
                new FieldDefinition("priority", "Priority again", FieldType.NUMBER, false, null, List.of(), null));

        assertThatThrownBy(() -> DossierType.inherit(
                        ID, TENANT, "CLAIM", "Claim", "wf-claim", true, false, duplicated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("priority");
    }

    @Test
    void constructor_acceptsUniqueFieldDefinitionNames() {
        List<FieldDefinition> fields = List.of(
                new FieldDefinition("priority", "Priority", FieldType.STRING, false, null, List.of(), null),
                new FieldDefinition("amount", "Amount", FieldType.NUMBER, false, null, List.of(), null));

        DossierType type = DossierType.inherit(
                ID, TENANT, "CLAIM", "Claim", "wf-claim", true, false, fields);

        assertThat(type.fieldDefinitions()).isEqualTo(fields);
    }

    @Test
    void constructor_acceptsEmptyFieldDefinitions() {
        DossierType type = DossierType.inherit(
                ID, TENANT, "CLAIM", "Claim", "wf-claim", true, false, List.of());

        assertThat(type.fieldDefinitions()).isEmpty();
    }

    @Test
    void constructor_rejectsBlankCode() {
        assertThatThrownBy(
                        () -> DossierType.inherit(ID, TENANT, "", "Claim", "wf-claim", true, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsBlankWorkflowKey() {
        assertThatThrownBy(
                        () -> DossierType.inherit(ID, TENANT, "CLAIM", "Claim", "   ", true, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workflowKey");
    }

    @Test
    void constructor_rejectsNonPositiveSlaTargetHours() {
        assertThatThrownBy(() -> new DossierType(
                        ID, TENANT, "CLAIM", "Claim", "wf-claim", true, false, List.of(), null, false, 0,
                        "system", "system"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slaTargetHours");
        assertThatThrownBy(() -> new DossierType(
                        ID, TENANT, "CLAIM", "Claim", "wf-claim", true, false, List.of(), null, false, -8,
                        "system", "system"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inherit_leavesSlaTargetHoursNull() {
        DossierType type = DossierType.inherit(ID, TENANT, "CLAIM", "Claim", "wf-claim", true, false, List.of());
        assertThat(type.slaTargetHours()).isNull();
    }

    @Test
    void inherit_defaultsDocumentTemplateKeysToEmptyList() {
        DossierType type = DossierType.inherit(ID, TENANT, "CLAIM", "Claim", "wf-claim", true, false, List.of());
        assertThat(type.documentTemplateKeys()).isEmpty();
    }

    @Test
    void constructor_copiesDocumentTemplateKeysDefensively() {
        List<String> mutable = new java.util.ArrayList<>(List.of("invoice"));
        DossierType type = new DossierType(
                ID, TENANT, "CLAIM", "Claim", "wf-claim", true, false, List.of(),
                null, false, null, mutable, "system", "system");

        mutable.add("contract");

        assertThat(type.documentTemplateKeys()).containsExactly("invoice");
    }

    @Test
    void constructor_defaultsNullDocumentTemplateKeysToEmptyList() {
        DossierType type = new DossierType(
                ID, TENANT, "CLAIM", "Claim", "wf-claim", true, false, List.of(),
                null, false, null, null, "system", "system");

        assertThat(type.documentTemplateKeys()).isEmpty();
    }

    @Test
    void withUpdate_propagatesDocumentTemplateKeys() {
        DossierType type = DossierType.inherit(ID, TENANT, "CLAIM", "Claim", "wf-claim", true, false, List.of());

        DossierType updated = type.withUpdate(
                "Claim", "wf-claim", true, false, List.of(), null, false, null,
                List.of("invoice"), "admin");

        assertThat(updated.documentTemplateKeys()).containsExactly("invoice");
    }
}
