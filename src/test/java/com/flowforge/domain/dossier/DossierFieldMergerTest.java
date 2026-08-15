package com.flowforge.domain.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DossierFieldMergerTest {

    @Test
    void merge_overwritesExistingKeyWithPatchValue() {
        Map<String, Object> merged =
                DossierFieldMerger.merge(Map.of("montant", 100, "label", "A"), Map.of("montant", 200));

        assertThat(merged).containsEntry("montant", 200).containsEntry("label", "A");
    }

    @Test
    void merge_removesKeyWhenPatchValueIsNull() {
        Map<String, Object> patch = new java.util.HashMap<>();
        patch.put("commentaire", null);

        Map<String, Object> merged = DossierFieldMerger.merge(Map.of("commentaire", "old", "label", "A"), patch);

        assertThat(merged).doesNotContainKey("commentaire");
        assertThat(merged).containsEntry("label", "A");
    }

    @Test
    void merge_keepsExistingKeyAbsentFromPatch() {
        Map<String, Object> merged = DossierFieldMerger.merge(Map.of("label", "A"), Map.of("montant", 200));

        assertThat(merged).containsEntry("label", "A").containsEntry("montant", 200);
    }

    @Test
    void merge_emptyPatchReturnsEquivalentOfExisting() {
        Map<String, Object> existing = Map.of("label", "A", "montant", 100);

        Map<String, Object> merged = DossierFieldMerger.merge(existing, Map.of());

        assertThat(merged).isEqualTo(existing);
    }

    @Test
    void merge_addsNewKeyAbsentFromExisting() {
        Map<String, Object> merged = DossierFieldMerger.merge(Map.of("label", "A"), Map.of("montant", 200));

        assertThat(merged).containsEntry("montant", 200);
    }

    @Test
    void merge_neverProducesANullValuedEntry() {
        Map<String, Object> patch = new java.util.HashMap<>();
        patch.put("commentaire", null);

        Map<String, Object> merged = DossierFieldMerger.merge(Map.of(), patch);

        // Dossier's compact constructor does Map.copyOf(fields), which throws NPE on a
        // null-valued entry — the merged map must never contain one.
        assertThat(merged.values()).doesNotContainNull();
    }

    @Test
    void changedSensitiveFieldNames_reportsValueChangeOnSensitiveField() {
        List<FieldDefinition> definitions = List.of(sensitive("ssn"), nonSensitive("label"));

        List<String> changed = DossierFieldMerger.changedSensitiveFieldNames(
                definitions, Map.of("ssn", "111", "label", "A"), Map.of("ssn", "222", "label", "A"));

        assertThat(changed).containsExactly("ssn");
    }

    @Test
    void changedSensitiveFieldNames_omitsSensitiveFieldRewrittenWithSameValue() {
        List<FieldDefinition> definitions = List.of(sensitive("ssn"));

        List<String> changed = DossierFieldMerger.changedSensitiveFieldNames(
                definitions, Map.of("ssn", "111"), Map.of("ssn", "111"));

        assertThat(changed).isEmpty();
    }

    @Test
    void changedSensitiveFieldNames_reportsNewlyAddedSensitiveField() {
        List<FieldDefinition> definitions = List.of(sensitive("ssn"));

        List<String> changed = DossierFieldMerger.changedSensitiveFieldNames(definitions, Map.of(), Map.of("ssn", "111"));

        assertThat(changed).containsExactly("ssn");
    }

    @Test
    void changedSensitiveFieldNames_reportsClearedSensitiveField() {
        List<FieldDefinition> definitions = List.of(sensitive("ssn"));

        List<String> changed = DossierFieldMerger.changedSensitiveFieldNames(definitions, Map.of("ssn", "111"), Map.of());

        assertThat(changed).containsExactly("ssn");
    }

    @Test
    void changedSensitiveFieldNames_neverReportsNonSensitiveField() {
        List<FieldDefinition> definitions = List.of(nonSensitive("label"));

        List<String> changed = DossierFieldMerger.changedSensitiveFieldNames(
                definitions, Map.of("label", "A"), Map.of("label", "B"));

        assertThat(changed).isEmpty();
    }

    private static FieldDefinition sensitive(String name) {
        return new FieldDefinition(name, name, FieldType.STRING, false, null, List.of(), null, true);
    }

    private static FieldDefinition nonSensitive(String name) {
        return new FieldDefinition(name, name, FieldType.STRING, false, null, List.of(), null, false);
    }
}
