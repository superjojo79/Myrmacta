package com.flowforge.domain.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.model.UniqueKey;
import com.flowforge.domain.expression.port.out.ExpressionEnginePort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TODO §102 (issue #184) — clés d'un dossier selon son type, champs calculés recalculés ; doublons parmi des dossiers. */
class DossierUniqueKeysTest {

    private static final FieldDefinition PLAQUE = new FieldDefinition("plaque", "Plaque", FieldType.STRING, false, null, null,
            null, false, null, null, null, null, null, null, false, null, null, true);

    private static Dossier dossier(String reference, DossierStatus status, Map<String, Object> fields) {
        return new Dossier(UUID.randomUUID(), "t", reference, "T", "l", status, fields, null, 0, "u", Instant.now(), "u",
                Instant.now(), null, null, null);
    }

    @Test
    void aComputedUniqueFieldIsRecomputedEvenWhenAbsentFromTheStoredValues() {
        var cle = new FieldDefinition("cle", "Clé", FieldType.STRING, false, null, null, null, false, null, null, null,
                "$uppercase(dossier.fields.plaque)", null, null, false, null, null, true);
        var brut = new FieldDefinition("plaque", "Plaque", FieldType.STRING, false, null, null, null);
        ExpressionEnginePort engine = mock(ExpressionEnginePort.class);
        when(engine.evaluate(any(), anyMap())).thenReturn(com.flowforge.domain.expression.model.ExpressionResult.of("BE-1"));
        var keys = new DossierUniqueKeys(new DossierFieldRules(engine));

        assertThat(keys.of(List.of(brut, cle), dossier("R1", DossierStatus.EN_COURS, Map.of("plaque", "be-1"))))
                .containsExactly(new UniqueKey("cle", "BE-1"));
    }

    @Test
    void duplicatesAreTheValuesHeldByMoreThanOneOpenDossier() {
        var keys = new DossierUniqueKeys(DossierFieldRules.disabled());
        var duplicates = keys.duplicates(List.of(PLAQUE), List.of(
                dossier("R3", DossierStatus.EN_COURS, Map.of("plaque", "A")),
                dossier("R1", DossierStatus.DRAFT, Map.of("plaque", "A")),
                dossier("R2", DossierStatus.CLOTURE, Map.of("plaque", "B")),
                dossier("R4", DossierStatus.OUVERT, Map.of("plaque", "B")),
                dossier("R5", DossierStatus.CLOTURE, Map.of("plaque", "A"))));

        assertThat(duplicates).containsExactly(
                new com.flowforge.domain.dossier.model.UniqueDuplicate("plaque", "A", List.of("R1", "R3")));
    }

    @Test
    void nothingWithoutAUniqueFieldOrForAClosedDossier() {
        var keys = new DossierUniqueKeys(DossierFieldRules.disabled());
        var libre = new FieldDefinition("plaque", "Plaque", FieldType.STRING, false, null, null, null);
        assertThat(keys.of(List.of(libre), dossier("R1", DossierStatus.EN_COURS, Map.of("plaque", "A")))).isEmpty();
        assertThat(keys.of(List.of(PLAQUE), dossier("R1", DossierStatus.ARCHIVE, Map.of("plaque", "A")))).isEmpty();
        assertThat(keys.duplicates(List.of(libre), List.of(
                dossier("R1", DossierStatus.EN_COURS, Map.of("plaque", "A")),
                dossier("R2", DossierStatus.EN_COURS, Map.of("plaque", "A"))))).isEmpty();
    }
}
