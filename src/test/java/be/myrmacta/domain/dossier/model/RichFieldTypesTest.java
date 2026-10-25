package be.myrmacta.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** D1 (TODO §76) — types MONEY, REPEATABLE_GROUP et EXTERNAL_REF : définition, validation, normalisation. */
class RichFieldTypesTest {

    private final DossierFieldValidator validator = new DossierFieldValidator();

    private static FieldDefinition money(String name, boolean required, String... currencies) {
        return new FieldDefinition(name, name, FieldType.MONEY, required, null, List.of(currencies), null);
    }

    private static FieldDefinition ref(String name, String... systems) {
        return new FieldDefinition(name, name, FieldType.EXTERNAL_REF, false, null, List.of(systems), null);
    }

    private static FieldDefinition group(String name, boolean required, Integer min, Integer max, FieldDefinition... subs) {
        return new FieldDefinition(name, name, FieldType.REPEATABLE_GROUP, required, null, List.of(), null, false,
                List.of(subs), min, max);
    }

    private static FieldDefinition text(String name, boolean required) {
        return new FieldDefinition(name, name, FieldType.STRING, required, null, List.of(), null);
    }

    private static Map<String, Object> amount(Object amount, String currency) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount", amount);
        m.put("currency", currency);
        return m;
    }

    private List<String> codes(List<FieldDefinition> defs, Map<String, Object> values) {
        return validator.validate(defs, values, false).stream().map(e -> e.field() + ":" + e.code()).toList();
    }

    // ---- MONEY ---------------------------------------------------------------------------------------------------

    @Test
    void moneyAcceptsAnAmountAndAnIsoCurrencyInSeveralJsonForms() {
        List<FieldDefinition> defs = List.of(money("m", false));
        for (Object a : List.of("1234.50", "0", "-12.5", 12, 12L, new BigDecimal("12.34"), 12.5d)) {
            assertThat(codes(defs, Map.of("m", amount(a, "EUR")))).as(String.valueOf(a)).isEmpty();
        }
    }

    @Test
    void moneyRefusesMoreDecimalsThanTheCurrencyAllowsInsteadOfRoundingSilently() {
        List<FieldDefinition> defs = List.of(money("m", false));
        assertThat(codes(defs, Map.of("m", amount("1.234", "EUR")))).containsExactly("m:INVALID_VALUE");
        assertThat(codes(defs, Map.of("m", amount("12.5", "JPY")))).containsExactly("m:INVALID_VALUE");
        assertThat(codes(defs, Map.of("m", amount("12", "JPY")))).isEmpty();
        assertThat(codes(defs, Map.of("m", amount("1.234", "KWD")))).isEmpty(); // 3 décimales légitimes
    }

    @Test
    void moneyRefusesMalformedAmountsAndUnknownCurrencies() {
        List<FieldDefinition> defs = List.of(money("m", false));
        for (Object bad : List.of("abc", "1,5", "", " ", "1e3", "NaN", "--1", "1.", ".5", "1".repeat(40))) {
            assertThat(codes(defs, Map.of("m", amount(bad, "EUR")))).as(String.valueOf(bad)).isNotEmpty();
        }
        for (String cur : List.of("eur", "EU", "EURO", "XXX", "ZZZ", "", "E1R")) {
            assertThat(codes(defs, Map.of("m", amount("1", cur)))).as(cur).isNotEmpty();
        }
        assertThat(codes(defs, Map.of("m", amount(Double.NaN, "EUR")))).isNotEmpty();
        assertThat(codes(defs, Map.of("m", amount(Double.POSITIVE_INFINITY, "EUR")))).isNotEmpty();
    }

    @Test
    void moneyShapeIsStrict() {
        List<FieldDefinition> defs = List.of(money("m", false));
        assertThat(codes(defs, Map.of("m", "12 EUR"))).containsExactly("m:TYPE_MISMATCH");
        assertThat(codes(defs, Map.of("m", 12))).containsExactly("m:TYPE_MISMATCH");
        assertThat(codes(defs, Map.of("m", Map.of("amount", "1")))).isNotEmpty();
        assertThat(codes(defs, Map.of("m", Map.of("currency", "EUR")))).isNotEmpty();
        Map<String, Object> extra = amount("1", "EUR");
        extra.put("note", "x");
        assertThat(codes(defs, Map.of("m", extra))).isNotEmpty();
    }

    @Test
    void moneyCurrenciesCanBeRestrictedByTheDefinition() {
        List<FieldDefinition> defs = List.of(money("m", false, "EUR", "USD"));
        assertThat(codes(defs, Map.of("m", amount("1", "EUR")))).isEmpty();
        assertThat(codes(defs, Map.of("m", amount("1", "GBP")))).containsExactly("m:INVALID_OPTION");
    }

    @Test
    void moneyRequiredAndAbsurdMagnitudes() {
        assertThat(codes(List.of(money("m", true)), Map.of())).containsExactly("m:REQUIRED");
        assertThat(codes(List.of(money("m", false)), Map.of("m", amount("9999999999999999", "EUR")))).isNotEmpty();
    }

    @Test
    void moneyIsNormalizedToACanonicalStringAtTheCurrencyScale() {
        List<FieldDefinition> defs = List.of(money("m", false));
        for (Object[] c : new Object[][] {{"12.5", "EUR", "12.50"}, {12, "EUR", "12.00"}, {"7", "JPY", "7"},
                {new BigDecimal("3.1"), "USD", "3.10"}, {12.5d, "EUR", "12.50"}, {"-0.5", "EUR", "-0.50"},
                {"1.250", "KWD", "1.250"}}) {
            Map<String, Object> out = validator.normalize(defs, Map.of("m", amount(c[0], (String) c[1])));
            assertThat(out.get("m")).as(String.valueOf(c[0])).isEqualTo(Map.of("amount", c[2], "currency", c[1]));
        }
    }

    // ---- EXTERNAL_REF --------------------------------------------------------------------------------------------

    @Test
    void externalRefNeedsASystemAndAnIdAndAcceptsALabel() {
        List<FieldDefinition> defs = List.of(ref("r"));
        assertThat(codes(defs, Map.of("r", Map.of("system", "d365fo", "id", "V-0042")))).isEmpty();
        assertThat(codes(defs, Map.of("r", Map.of("system", "d365fo", "id", "V-0042", "label", "Contoso")))).isEmpty();
        assertThat(codes(defs, Map.of("r", Map.of("system", "d365fo")))).isNotEmpty();
        assertThat(codes(defs, Map.of("r", Map.of("id", "x")))).isNotEmpty();
        assertThat(codes(defs, Map.of("r", Map.of("system", "d365 fo", "id", "x")))).isNotEmpty();
        assertThat(codes(defs, Map.of("r", Map.of("system", "s", "id", "  ")))).isNotEmpty();
        assertThat(codes(defs, Map.of("r", Map.of("system", "s", "id", "x".repeat(201))))).isNotEmpty();
        assertThat(codes(defs, Map.of("r", Map.of("system", "s", "id", "a\nb")))).isNotEmpty();
        assertThat(codes(defs, Map.of("r", Map.of("system", "s", "id", "x", "other", "y")))).isNotEmpty();
        assertThat(codes(defs, Map.of("r", "d365fo:V-1"))).containsExactly("r:TYPE_MISMATCH");
    }

    @Test
    void externalRefSystemsCanBeRestricted() {
        List<FieldDefinition> defs = List.of(ref("r", "d365fo", "sap"));
        assertThat(codes(defs, Map.of("r", Map.of("system", "sap", "id", "1")))).isEmpty();
        assertThat(codes(defs, Map.of("r", Map.of("system", "oracle", "id", "1")))).containsExactly("r:INVALID_OPTION");
    }

    @Test
    void externalRefIsTrimmedWhenNormalized() {
        Map<String, Object> out = validator.normalize(List.of(ref("r")),
                Map.of("r", Map.of("system", "d365fo", "id", "V-1", "label", "  Contoso ")));
        assertThat(out.get("r")).isEqualTo(Map.of("system", "d365fo", "id", "V-1", "label", "Contoso"));
    }

    // ---- REPEATABLE_GROUP ----------------------------------------------------------------------------------------

    private static FieldDefinition lines() {
        return group("lignes", false, 0, 3, text("poste", true),
                new FieldDefinition("prix", "Prix", FieldType.MONEY, false, null, List.of(), null));
    }

    @Test
    void aGroupValidatesEachRowAgainstItsSubFieldsAndLocatesTheError() {
        List<FieldDefinition> defs = List.of(lines());
        Map<String, Object> ok = Map.of("lignes", List.of(
                Map.of("poste", "Toiture", "prix", amount("1200", "EUR")), Map.of("poste", "Cuisine")));
        assertThat(codes(defs, ok)).isEmpty();

        Map<String, Object> bad = Map.of("lignes", List.of(
                Map.of("poste", "Toiture"), Map.of("prix", amount("1.234", "EUR"))));
        assertThat(codes(defs, bad)).containsExactly("lignes[1].poste:REQUIRED", "lignes[1].prix:INVALID_VALUE");
    }

    @Test
    void aGroupBoundsItsNumberOfRowsAndRefusesUnknownSubFields() {
        List<FieldDefinition> defs = List.of(group("g", false, 1, 2, text("a", false)));
        assertThat(codes(defs, Map.of("g", List.of()))).containsExactly("g:INVALID_VALUE");
        assertThat(codes(defs, Map.of("g", List.of(Map.of("a", "1"), Map.of("a", "2"), Map.of("a", "3")))))
                .containsExactly("g:INVALID_VALUE");
        assertThat(codes(defs, Map.of("g", List.of(Map.of("zzz", "1"))))).containsExactly("g[0].zzz:UNKNOWN_FIELD");
        assertThat(codes(defs, Map.of("g", Map.of("a", "1")))).containsExactly("g:TYPE_MISMATCH");
        assertThat(codes(defs, Map.of("g", List.of("pas une ligne")))).containsExactly("g[0]:TYPE_MISMATCH");
    }

    @Test
    void aRequiredGroupNeedsAtLeastOneRow() {
        List<FieldDefinition> defs = List.of(group("g", true, null, null, text("a", false)));
        assertThat(codes(defs, Map.of())).containsExactly("g:REQUIRED");
        assertThat(codes(defs, Map.of("g", List.of()))).containsExactly("g:REQUIRED");
        assertThat(codes(defs, Map.of("g", List.of(Map.of())))).isEmpty();
    }

    @Test
    void groupRowsAreNormalizedRecursively() {
        Map<String, Object> out = validator.normalize(List.of(lines()), Map.of("lignes", List.of(
                Map.of("poste", "Toiture", "prix", amount("12.5", "EUR")))));
        assertThat(out.get("lignes")).isEqualTo(List.of(Map.of("poste", "Toiture", "prix", Map.of("amount", "12.50", "currency", "EUR"))));
    }

    @Test
    void normalizeLeavesEverythingElseUntouched() {
        Map<String, Object> in = Map.of("a", "x", "n", 3, "inconnu", List.of(1, 2));
        Map<String, Object> out = validator.normalize(List.of(text("a", false)), in);
        assertThat(out).isEqualTo(in);
    }

    // ---- définitions ---------------------------------------------------------------------------------------------

    @Test
    void groupDefinitionsAreBounded() {
        assertThatThrownBy(() -> group("g", false, 0, 10, new FieldDefinition[0])).hasMessageContaining("sub-field");
        assertThatThrownBy(() -> group("g", false, 5, 2, text("a", false))).hasMessageContaining("minRows");
        assertThatThrownBy(() -> group("g", false, 0, 501, text("a", false))).hasMessageContaining("maxRows");
        assertThatThrownBy(() -> group("g", false, -1, 5, text("a", false))).hasMessageContaining("minRows");
        assertThatThrownBy(() -> group("g", false, 0, 5, text("a", false), text("a", false)))
                .hasMessageContaining("duplicate");
        List<FieldDefinition> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            many.add(text("f" + i, false));
        }
        assertThatThrownBy(() -> group("g", false, 0, 5, many.toArray(new FieldDefinition[0])))
                .hasMessageContaining("at most 20");
        FieldDefinition defaults = group("g", false, null, null, text("a", false));
        assertThat(defaults.minRows()).isZero();
        assertThat(defaults.maxRows()).isEqualTo(100);
    }

    @Test
    void groupSubFieldsAreFlatAndNeverSensitive() {
        FieldDefinition nested = group("inner", false, 0, 2, text("a", false));
        assertThatThrownBy(() -> group("g", false, 0, 5, nested)).hasMessageContaining("not allowed in a group");
        FieldDefinition file = new FieldDefinition("f", "f", FieldType.FILE, false, null, List.of(), null);
        assertThatThrownBy(() -> group("g", false, 0, 5, file)).hasMessageContaining("not allowed in a group");
        FieldDefinition sensitive = new FieldDefinition("s", "s", FieldType.STRING, false, null, List.of(), null, true);
        assertThatThrownBy(() -> group("g", false, 0, 5, sensitive)).hasMessageContaining("sensitive");
        // La sensibilité se pose sur le groupe entier.
        FieldDefinition whole = new FieldDefinition("g", "g", FieldType.REPEATABLE_GROUP, false, null, List.of(), null, true,
                List.of(text("a", false)), 0, 5);
        assertThat(whole.isSensitive()).isTrue();
    }

    @Test
    void onlyGroupsCarryRowBoundsAndSubFields() {
        assertThatThrownBy(() -> new FieldDefinition("s", "s", FieldType.STRING, false, null, List.of(), null, false,
                List.of(text("a", false)), null, null)).hasMessageContaining("only a REPEATABLE_GROUP");
        assertThatThrownBy(() -> new FieldDefinition("s", "s", FieldType.STRING, false, null, List.of(), null, false,
                List.of(), 1, null)).hasMessageContaining("only a REPEATABLE_GROUP");
    }

    @Test
    void moneyAndReferenceDefinitionsValidateTheirOptionsAndRefuseDefaults() {
        assertThatThrownBy(() -> money("m", false, "EUR", "ZZZ")).hasMessageContaining("ISO 4217");
        assertThatThrownBy(() -> money("m", false, "eur")).hasMessageContaining("ISO 4217");
        assertThatThrownBy(() -> ref("r", "bad system")).hasMessageContaining("system");
        for (FieldType t : List.of(FieldType.MONEY, FieldType.EXTERNAL_REF, FieldType.REPEATABLE_GROUP)) {
            assertThatThrownBy(() -> new FieldDefinition("x", "x", t, false, "1", List.of(), null, false,
                    t == FieldType.REPEATABLE_GROUP ? List.of(text("a", false)) : List.of(), null, null))
                    .as(t.name()).hasMessageContaining("default");
        }
    }

    @Test
    void legacyTypesAreUnchangedAndJsonWithoutTheNewComponentsStillLoads() {
        FieldDefinition legacy = new FieldDefinition("a", "A", FieldType.STRING, false, null, List.of(), null);
        assertThat(legacy.fields()).isEmpty();
        assertThat(legacy.minRows()).isNull();
        assertThat(legacy.maxRows()).isNull();
        assertThat(codes(List.of(legacy), Map.of("a", "x"))).isEmpty();
    }

    // ---- revue adversariale D1 -------------------------------------------------------------------------------------

    @Test
    void mediumAJsonNumberThatADoubleCannotHoldExactlyIsRefusedNotRoundedInSilence() {
        // M1 : Jackson lit 99999999999999.99 comme le Double 9.999999999999998E13 → stocké « 99999999999999.98 ».
        List<FieldDefinition> defs = List.of(money("m", false));
        for (Object risky : List.of(99999999999999.99d, 999999999999999.9d, 16777217f, 0.1f, 1234567890123.456d)) {
            assertThat(codes(defs, Map.of("m", amount(risky, "EUR")))).as(String.valueOf(risky)).isNotEmpty();
        }
        // Ce qu'un double représente exactement (≤ 15 chiffres significatifs) reste accepté ; les textes et grands
        // entiers exacts aussi.
        for (Object exact : List.of(12.5d, 0.05d, 1234567890123.45d, 100.0d, 999999999999999L, new BigDecimal("99999999999999.99"),
                "99999999999999.99")) {
            assertThat(codes(defs, Map.of("m", amount(exact, "EUR")))).as(String.valueOf(exact)).isEmpty();
        }
        assertThat(validator.normalize(defs, Map.of("m", amount("99999999999999.99", "EUR"))).get("m"))
                .isEqualTo(Map.of("amount", "99999999999999.99", "currency", "EUR"));
    }

    @Test
    void mediumANullCellInAGroupRowIsAbsenceNotACrash() {
        // M2 : [{"nom": null}] passait la validation puis `Map.copyOf` levait une NullPointerException (HTTP 500).
        List<FieldDefinition> defs = List.of(lines());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("poste", "Toiture");
        row.put("prix", null);
        Map<String, Object> values = Map.of("lignes", List.of(row));
        assertThat(codes(defs, values)).isEmpty();
        Map<String, Object> out = validator.normalize(defs, values);
        assertThat(out.get("lignes")).isEqualTo(List.of(Map.of("poste", "Toiture")));

        Map<String, Object> onlyNull = new LinkedHashMap<>();
        onlyNull.put("poste", null);
        assertThat(codes(defs, Map.of("lignes", List.of(onlyNull)))).containsExactly("lignes[0].poste:REQUIRED");
    }

    @Test
    void mediumTextSizeIsBoundedSoAGroupCannotCarryHundredsOfMegabytes() {
        // M4 : 500 lignes × 20 cellules de 10 Mo passaient ; bornes par cellule, par groupe et par dossier.
        List<FieldDefinition> defs = List.of(group("g", false, 0, 500, text("a", false)));
        assertThat(codes(defs, Map.of("g", List.of(Map.of("a", "x".repeat(10_000)))))).isEmpty();
        assertThat(codes(defs, Map.of("g", List.of(Map.of("a", "x".repeat(10_001)))))).containsExactly("g[0].a:INVALID_VALUE");
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            rows.add(Map.of("a", "x".repeat(10_000)));
        }
        assertThat(codes(defs, Map.of("g", rows))).containsExactly("g:INVALID_VALUE"); // 1 010 000 > 1 000 000 par groupe
        for (int i = 0; i < 99; i++) {
            rows.add(Map.of("a", "x".repeat(10_000)));
        }
        assertThat(codes(defs, Map.of("g", rows))).containsExactly("fields:INVALID_VALUE"); // 2 000 000 > 1 Mio par dossier
        // Borne globale d'un dossier, tous champs confondus (le champ STRING historique n'était borné nulle part).
        List<FieldDefinition> plain = List.of(text("a", false), text("b", false));
        assertThat(codes(plain, Map.of("a", "x".repeat(600_000), "b", "y".repeat(600_000)))).containsExactly("fields:INVALID_VALUE");
        assertThat(codes(plain, Map.of("a", "x".repeat(600_000)))).isEmpty();
    }

    @Test
    void lowMinRowsAppliesWhenTheGroupIsAbsentToo() {
        // B3 : avec minRows=2 non requis, un groupe absent était accepté mais [] refusé.
        List<FieldDefinition> defs = List.of(group("g", false, 2, 5, text("a", false)));
        assertThat(codes(defs, Map.of())).containsExactly("g:INVALID_VALUE");
        assertThat(codes(defs, Map.of("g", List.of(Map.of("a", "1"), Map.of("a", "2"))))).isEmpty();
        assertThat(codes(List.of(group("g", false, 0, 5, text("a", false))), Map.of())).isEmpty();
    }

    @Test
    void lowDefinitionsAreTighter() {
        // B4
        FieldDefinition blankName = new FieldDefinition("", "x", FieldType.STRING, false, null, List.of(), null);
        assertThatThrownBy(() -> group("g", false, 0, 5, blankName)).hasMessageContaining("name");
        FieldDefinition withDefault = new FieldDefinition("d", "d", FieldType.STRING, false, "x", List.of(), null);
        assertThatThrownBy(() -> group("g", false, 0, 5, withDefault)).hasMessageContaining("default");
        assertThatThrownBy(() -> new FieldDefinition("g", "g", FieldType.REPEATABLE_GROUP, false, null, List.of("a"), null,
                false, List.of(text("a", false)), 0, 5)).hasMessageContaining("options");
        assertThatThrownBy(() -> group("g", false, 0, 0, text("a", false))).hasMessageContaining("maxRows");
    }

    @Test
    void lowAnUnknownRowKeyIsNotEchoedIntoTheErrorPathUnlessItIsAPlainIdentifier() {
        List<FieldDefinition> defs = List.of(group("g", false, 0, 5, text("a", false)));
        assertThat(codes(defs, Map.of("g", List.of(Map.of("<script>alert(1)</script>", "x")))))
                .containsExactly("g[0].?:UNKNOWN_FIELD");
        assertThat(codes(defs, Map.of("g", List.of(Map.of("zzz", "x"))))).containsExactly("g[0].zzz:UNKNOWN_FIELD");
    }

    @Test
    void lowCsvColumnNamesOfATypeCannotCollide() {
        // B4 : un STRING nommé « m.amount » à côté d'un MONEY « m » donnait deux colonnes « m.amount ».
        List<FieldDefinition> colliding = List.of(text("m.amount", false), money("m", false));
        assertThatThrownBy(() -> new DossierType(java.util.UUID.randomUUID(), "t", "CODE", "L", "wf", true, false, colliding,
                null, false, null, "u", "u")).hasMessageContaining("column");
    }
}
