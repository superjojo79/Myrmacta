package com.flowforge.domain.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DossierFieldRedactorTest {

    private static final List<FieldDefinition> FIELD_DEFINITIONS = List.of(
            new FieldDefinition("priority", "Priority", FieldType.STRING, false, null, List.of(), null, false),
            new FieldDefinition("ssn", "SSN", FieldType.STRING, false, null, List.of(), null, true));

    @Test
    void redact_stripsSensitiveFieldsForNonAdminUser() {
        Dossier dossier = dossier(Map.of("priority", "HIGH", "ssn", "123-45-6789"));

        Dossier result = DossierFieldRedactor.redact(
                dossier, FIELD_DEFINITIONS, AccessContext.user("u1", Set.of("DOSSIER_MANAGER")));

        assertThat(result.fields()).containsEntry("priority", "HIGH");
        assertThat(result.fields()).doesNotContainKey("ssn");
    }

    @Test
    void redact_keepsClearValuesForTenantAdmin() {
        Dossier dossier = dossier(Map.of("priority", "HIGH", "ssn", "123-45-6789"));

        Dossier result = DossierFieldRedactor.redact(
                dossier, FIELD_DEFINITIONS, AccessContext.user("admin", Set.of("TENANT_ADMIN")));

        assertThat(result).isEqualTo(dossier);
    }

    @Test
    void redact_keepsClearValuesForWorkflowAdmin() {
        Dossier dossier = dossier(Map.of("ssn", "123-45-6789"));

        Dossier result = DossierFieldRedactor.redact(
                dossier, FIELD_DEFINITIONS, AccessContext.user("wf-admin", Set.of("WORKFLOW_ADMIN")));

        assertThat(result.fields()).doesNotContainKey("ssn");
    }

    @Test
    void redact_passesThroughSystemContextUnchanged() {
        Dossier dossier = dossier(Map.of("ssn", "123-45-6789"));

        Dossier result = DossierFieldRedactor.redact(
                dossier, FIELD_DEFINITIONS, AccessContext.system("document-generation"));

        assertThat(result).isEqualTo(dossier);
    }

    @Test
    void redact_isNoOpWhenNoFieldIsMarkedSensitive() {
        Dossier dossier = dossier(Map.of("priority", "HIGH"));
        List<FieldDefinition> noSensitiveFields =
                List.of(new FieldDefinition("priority", "Priority", FieldType.STRING, false, null, List.of(), null, false));

        Dossier result = DossierFieldRedactor.redact(
                dossier, noSensitiveFields, AccessContext.user("u1", Set.of("DOSSIER_MANAGER")));

        assertThat(result).isSameAs(dossier);
    }

    private static Dossier dossier(Map<String, Object> fields) {
        return new Dossier(
                UUID.randomUUID(),
                "tenant_dev",
                "DOS-1",
                "SINISTRE",
                "Dossier",
                DossierStatus.OUVERT,
                fields,
                null,
                0,
                "creator",
                Instant.parse("2026-08-02T09:00:00Z"),
                "creator",
                Instant.parse("2026-08-02T09:00:00Z"),
                null,
                null,
                null);
    }
}
