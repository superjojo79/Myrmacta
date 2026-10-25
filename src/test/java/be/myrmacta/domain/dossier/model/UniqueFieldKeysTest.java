package be.myrmacta.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** TODO §102 (issue #184) — clés d'unicité d'un dossier : portée U5, forme canonique U6, valeur vide U7. */
class UniqueFieldKeysTest {

    private static FieldDefinition unique(String name, FieldType type) {
        return new FieldDefinition(name, name, type, false, null, null, null, false, null, null, null, null, null, null,
                false, null, null, true);
    }

    private static final List<FieldDefinition> DEFS = List.of(
            unique("plaque", FieldType.STRING),
            unique("numero", FieldType.NUMBER),
            unique("jour", FieldType.DATE),
            new FieldDefinition("libre", "libre", FieldType.STRING, false, null, null, null));

    @Test
    void oneKeyPerNonEmptyUniqueField() {
        var keys = UniqueFieldKeys.of(DEFS, DossierStatus.EN_COURS,
                Map.of("plaque", "BE-1ABC123", "numero", 42, "jour", "2026-10-24", "libre", "x"));
        assertThat(keys).containsExactlyInAnyOrder(
                new UniqueKey("plaque", "BE-1ABC123"), new UniqueKey("numero", "42"), new UniqueKey("jour", "2026-10-24"));
    }

    @Test
    void textIsComparedAsIs_U6() {
        assertThat(UniqueFieldKeys.of(DEFS, DossierStatus.DRAFT, Map.of("plaque", " be-1abc 123 ")))
                .containsExactly(new UniqueKey("plaque", " be-1abc 123 "));
    }

    @Test
    void numbersHaveOneCanonicalForm_U6() {
        assertThat(UniqueFieldKeys.canonical(FieldType.NUMBER, 1.50d)).isEqualTo("1.5");
        assertThat(UniqueFieldKeys.canonical(FieldType.NUMBER, new BigDecimal("1.500"))).isEqualTo("1.5");
        assertThat(UniqueFieldKeys.canonical(FieldType.NUMBER, 100L)).isEqualTo("100");
        assertThat(UniqueFieldKeys.canonical(FieldType.NUMBER, new BigDecimal("1E+2"))).isEqualTo("100");
        assertThat(UniqueFieldKeys.canonical(FieldType.NUMBER, 0.0d)).isEqualTo("0");
        assertThat(UniqueFieldKeys.canonical(FieldType.NUMBER, -0.0d)).isEqualTo("0");
        assertThat(UniqueFieldKeys.canonical(FieldType.NUMBER, 7)).isEqualTo("7");
    }

    @Test
    void datesAreIso_U6() {
        assertThat(UniqueFieldKeys.canonical(FieldType.DATE, LocalDate.of(2026, 1, 5))).isEqualTo("2026-01-05");
        assertThat(UniqueFieldKeys.canonical(FieldType.DATE, "2026-01-05")).isEqualTo("2026-01-05");
    }

    @Test
    void emptyValuesNeverConflict_U7() {
        assertThat(UniqueFieldKeys.of(DEFS, DossierStatus.OUVERT, Map.of("plaque", "  ", "numero", ""))).isEmpty();
        assertThat(UniqueFieldKeys.of(DEFS, DossierStatus.OUVERT, Map.of())).isEmpty();
    }

    @Test
    void aClosedOrArchivedDossierHoldsNoKey_U5() {
        Map<String, Object> fields = Map.of("plaque", "BE-1");
        assertThat(UniqueFieldKeys.of(DEFS, DossierStatus.CLOTURE, fields)).isEmpty();
        assertThat(UniqueFieldKeys.of(DEFS, DossierStatus.ARCHIVE, fields)).isEmpty();
        for (DossierStatus open : List.of(DossierStatus.DRAFT, DossierStatus.OUVERT, DossierStatus.EN_COURS,
                DossierStatus.SUSPENDU)) {
            assertThat(UniqueFieldKeys.of(DEFS, open, fields)).hasSize(1);
        }
    }

    @Test
    void tellsWhetherATypeHasAUniqueField() {
        assertThat(UniqueFieldKeys.hasUniqueField(DEFS)).isTrue();
        assertThat(UniqueFieldKeys.hasUniqueField(DEFS.subList(3, 4))).isFalse();
    }

    @Test
    void fieldsThatAreNotUniqueYieldNoKey() {
        assertThat(UniqueFieldKeys.of(DEFS.subList(3, 4), DossierStatus.EN_COURS, Map.of("libre", "x"))).isEmpty();
    }
}
