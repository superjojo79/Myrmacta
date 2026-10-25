package be.myrmacta.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FieldDefinitionTest {

    @Test
    void sensitiveTrue_isPreserved() {
        var def = new FieldDefinition(
                "ssn", "N° sécu", FieldType.STRING, false, null, List.of(), null, true);
        assertThat(def.sensitive()).isTrue();
        assertThat(def.isSensitive()).isTrue();
    }

    @Test
    void convenienceConstructor_defaultsSensitiveToFalse() {
        var def = new FieldDefinition(
                "name", "Name", FieldType.STRING, true, null, List.of(), "identity");
        assertThat(def.sensitive()).isFalse();
        assertThat(def.isSensitive()).isFalse();
    }

    @Test
    void nullSensitive_coercedToFalse() {
        var def = new FieldDefinition(
                "name", "Name", FieldType.STRING, false, null, List.of(), null, null);
        assertThat(def.sensitive()).isFalse();
        assertThat(def.isSensitive()).isFalse();
    }

    @Test
    void toString_neverPrintsDefaultValue() {
        // D-LOG-GUARD: defaultValue can itself carry a business-prefilled value on a
        // sensitive field — never print it, unconditionally (same "always safe" policy
        // as Dossier.toString()).
        var def = new FieldDefinition(
                "ssn", "N° sécu", FieldType.STRING, false, "SECRET-DEFAULT", List.of(), null, true);

        String text = def.toString();

        assertThat(text).doesNotContain("SECRET-DEFAULT");
        assertThat(text).contains("ssn");
        assertThat(text).contains("sensitive=true");
    }

    @Test
    void dossierType_hasSensitiveFields_usesIsSensitive() {
        var plain = new FieldDefinition(
                "name", "Name", FieldType.STRING, true, null, List.of(), null);
        var secret = new FieldDefinition(
                "ssn", "SSN", FieldType.STRING, false, null, List.of(), null, true);
        var type = DossierType.inherit(
                java.util.UUID.randomUUID(),
                "tenant_dev",
                "T1",
                "Type",
                "wf",
                true,
                false,
                List.of(plain, secret));
        assertThat(type.hasSensitiveFields()).isTrue();
        assertThat(DossierType.inherit(
                        type.id(),
                        type.tenantId(),
                        type.code(),
                        type.label(),
                        type.workflowKey(),
                        true,
                        false,
                        List.of(plain))
                .hasSensitiveFields())
                .isFalse();
    }
}
