package com.flowforge.domain.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.DossierFieldValidator;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.model.FieldValidationError;
import com.flowforge.domain.expression.model.ExpressionLimits;
import com.flowforge.infrastructure.expression.JsonataExpressionEngine;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** TODO §80 (D1, règles) — champs calculés, visibilité et obligation conditionnelles, avec le vrai moteur LC2. */
class DossierFieldRulesTest {

    private final DossierFieldRules rules =
            new DossierFieldRules(new JsonataExpressionEngine(ExpressionLimits.DEFAULTS, Clock.systemUTC()));
    private final DossierFieldValidator validator = new DossierFieldValidator();

    private static FieldDefinition plain(String name, FieldType type) {
        return new FieldDefinition(name, name, type, false, null, null, null, false);
    }

    private static FieldDefinition sensitive(String name) {
        return new FieldDefinition(name, name, FieldType.STRING, false, null, null, null, true);
    }

    private static FieldDefinition rule(String name, FieldType type, boolean required, String formula, String visibleWhen, String requiredWhen) {
        return new FieldDefinition(name, name, type, required, null, null, null, false, null, null, null, formula, visibleWhen, requiredWhen);
    }

    private static Map<String, Object> fields(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private List<FieldValidationError> allErrors(List<FieldDefinition> defs, Map<String, Object> input, Set<String> explicit) {
        var outcome = rules.apply(defs, input, explicit);
        var errors = new java.util.ArrayList<>(validator.validate(outcome.effectiveDefinitions(), outcome.fields(), true));
        errors.addAll(outcome.errors());
        return errors;
    }

    @Test
    void withoutRulesNothingChanges() {
        List<FieldDefinition> defs = List.of(plain("a", FieldType.NUMBER));
        var in = fields("a", 1);
        var outcome = rules.apply(defs, in, Set.of("a"));
        assertThat(outcome.fields()).isSameAs(in);
        assertThat(outcome.effectiveDefinitions()).isSameAs(defs);
        assertThat(outcome.errors()).isEmpty();
    }

    @Test
    void aComputedFieldIsDerivedFromThePlainFields() {
        List<FieldDefinition> defs = List.of(plain("ht", FieldType.NUMBER), plain("taux", FieldType.NUMBER),
                rule("ttc", FieldType.NUMBER, false, "dossier.fields.ht * (1 + dossier.fields.taux)", null, null));

        var outcome = rules.apply(defs, fields("ht", 100, "taux", 0.2), Set.of("ht", "taux"));

        assertThat(outcome.errors()).isEmpty();
        assertThat(((Number) outcome.fields().get("ttc")).doubleValue()).isEqualTo(120.0);
    }

    @Test
    void aComputedFieldIsRecomputedAndAStoredStaleValueNeverSurvives() {
        List<FieldDefinition> defs = List.of(plain("ht", FieldType.NUMBER), rule("double", FieldType.NUMBER, false, "dossier.fields.ht * 2", null, null));
        // valeur périmée stockée (ht a changé dans le patch) : recalculée, pas conservée
        var outcome = rules.apply(defs, fields("ht", 10, "double", 999), Set.of("ht"));
        assertThat(((Number) outcome.fields().get("double")).intValue()).isEqualTo(20);
    }

    @Test
    void suppliedValueForAComputedFieldIsRefused() {
        List<FieldDefinition> defs = List.of(plain("ht", FieldType.NUMBER), rule("double", FieldType.NUMBER, false, "dossier.fields.ht * 2", null, null));
        var errors = allErrors(defs, fields("ht", 10, "double", 5), Set.of("ht", "double"));
        assertThat(errors).extracting(FieldValidationError::code).contains("COMPUTED_READ_ONLY");
    }

    @Test
    void aFormulaWithoutAValueRemovesTheComputedField() {
        List<FieldDefinition> defs = List.of(plain("ht", FieldType.NUMBER), rule("double", FieldType.NUMBER, false, "dossier.fields.ht * 2", null, null));
        var outcome = rules.apply(defs, fields("double", 5), Set.of());
        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.fields()).doesNotContainKey("double");
    }

    @Test
    void aFormulaInErrorRefusesTheSaveWithoutEchoingValues() {
        List<FieldDefinition> defs = List.of(plain("nom", FieldType.STRING), rule("bad", FieldType.NUMBER, false, "dossier.fields.nom * 2", null, null));
        var outcome = rules.apply(defs, fields("nom", "SECRET-NOM"), Set.of("nom"));
        assertThat(outcome.errors()).extracting(FieldValidationError::code).containsExactly("RULE_FAILED");
        assertThat(outcome.errors().toString()).doesNotContain("SECRET-NOM");
    }

    @Test
    void aComputedValueIsValidatedByItsType() {
        List<FieldDefinition> defs = List.of(plain("ht", FieldType.NUMBER), rule("etiquette", FieldType.NUMBER, false, "'texte'", null, null));
        assertThat(allErrors(defs, fields("ht", 1), Set.of("ht"))).extracting(FieldValidationError::code).contains("TYPE_MISMATCH");
    }

    @Test
    void aSensitiveFieldIsNeverReadableByARule() {
        List<FieldDefinition> defs = List.of(sensitive("iban"), rule("copie", FieldType.STRING, false, "dossier.fields.iban", null, null));
        var outcome = rules.apply(defs, fields("iban", "FR76"), Set.of("iban"));
        assertThat(outcome.fields()).doesNotContainKey("copie");
        assertThat(outcome.fields().toString()).doesNotContain("FR76".concat("X"));
    }

    @Test
    void aFormulaDoesNotChainOnAnotherComputedField() {
        List<FieldDefinition> defs = List.of(plain("a", FieldType.NUMBER), rule("b", FieldType.NUMBER, false, "dossier.fields.a + 1", null, null),
                rule("c", FieldType.NUMBER, false, "dossier.fields.b + 1", null, null));
        var outcome = rules.apply(defs, fields("a", 1), Set.of("a"));
        assertThat(outcome.fields()).containsKey("b").doesNotContainKey("c");
    }

    @Test
    void aHiddenFieldIsNotRequiredAndAStoredValueThatBecomesHiddenIsRemoved() {
        List<FieldDefinition> defs = List.of(plain("pays", FieldType.STRING),
                rule("siret", FieldType.STRING, true, null, "dossier.fields.pays = 'FR'", null));
        // pays = DE : siret sans objet, non obligatoire
        assertThat(allErrors(defs, fields("pays", "DE"), Set.of("pays"))).isEmpty();
        // pays = FR : siret obligatoire
        assertThat(allErrors(defs, fields("pays", "FR"), Set.of("pays"))).extracting(FieldValidationError::code).contains("REQUIRED");
        // valeur stockée devenue sans objet : retirée sans erreur
        var outcome = rules.apply(defs, fields("pays", "DE", "siret", "12345678901234"), Set.of("pays"));
        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.fields()).doesNotContainKey("siret");
        assertThat(outcome.hidden()).containsExactly("siret");
    }

    @Test
    void aSuppliedValueForAHiddenFieldIsRefused() {
        List<FieldDefinition> defs = List.of(plain("pays", FieldType.STRING), rule("siret", FieldType.STRING, false, null, "dossier.fields.pays = 'FR'", null));
        var errors = allErrors(defs, fields("pays", "DE", "siret", "123"), Set.of("pays", "siret"));
        assertThat(errors).extracting(FieldValidationError::code).contains("NOT_APPLICABLE");
    }

    @Test
    void requiredWhenMakesAFieldRequiredOnlyWhenTrue() {
        List<FieldDefinition> defs = List.of(plain("montant", FieldType.NUMBER),
                rule("justificatif", FieldType.STRING, false, null, null, "dossier.fields.montant > 1000"));
        assertThat(allErrors(defs, fields("montant", 500), Set.of("montant"))).isEmpty();
        assertThat(allErrors(defs, fields("montant", 5000), Set.of("montant")))
                .extracting(FieldValidationError::field, FieldValidationError::code).containsExactly(org.assertj.core.groups.Tuple.tuple("justificatif", "REQUIRED"));
        assertThat(rules.apply(defs, fields("montant", 5000), Set.of("montant")).requiredByRule()).containsExactly("justificatif");
    }

    @Test
    void aRuleThatIsNotBooleanOrFailsToEvaluateIsReported() {
        List<FieldDefinition> defs = List.of(plain("a", FieldType.NUMBER), rule("b", FieldType.STRING, false, null, "dossier.fields.a", null));
        assertThat(rules.apply(defs, fields("a", 1), Set.of("a")).errors()).extracting(FieldValidationError::code).containsExactly("RULE_FAILED");
        List<FieldDefinition> broken = List.of(plain("a", FieldType.STRING), rule("b", FieldType.STRING, false, null, null, "dossier.fields.a * 2 > 1"));
        assertThat(rules.apply(broken, fields("a", "x"), Set.of("a")).errors()).extracting(FieldValidationError::code).containsExactly("RULE_FAILED");
    }

    @Test
    void anUndefinedRuleCountsAsFalse() {
        List<FieldDefinition> defs = List.of(plain("a", FieldType.NUMBER), rule("b", FieldType.STRING, false, null, "dossier.fields.a > 5", null));
        // a absent : « a > 5 » n'est pas vrai => b sans objet
        assertThat(rules.apply(defs, fields(), Set.of()).hidden()).containsExactly("b");
    }

    // ---- définitions ----------------------------------------------------------------------------------------------------

    @Test
    void validateDefinitionsAcceptsSoundRulesAndRefusesTheOthers() {
        rules.validateDefinitions(List.of(plain("a", FieldType.NUMBER), rule("b", FieldType.NUMBER, false, "dossier.fields.a + 1", null, null)));

        assertThatThrownBy(() -> rules.validateDefinitions(List.of(plain("a", FieldType.NUMBER), rule("b", FieldType.NUMBER, false, "dossier.fields.a +", null, null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("'b'").hasMessageContaining("formula");
        assertThatThrownBy(() -> rules.validateDefinitions(List.of(rule("b", FieldType.NUMBER, false, "$eval('1')", null, null))))
                .hasMessageContaining("$eval");
        assertThatThrownBy(() -> rules.validateDefinitions(List.of(plain("a", FieldType.NUMBER), rule("b", FieldType.STRING, false, null, "dossier.fields.inconnu = 1", null))))
                .hasMessageContaining("inconnu");
        assertThatThrownBy(() -> rules.validateDefinitions(List.of(sensitive("iban"), rule("b", FieldType.STRING, false, null, "dossier.fields.iban = 'x'", null))))
                .hasMessageContaining("iban");
        assertThatThrownBy(() -> rules.validateDefinitions(List.of(plain("a", FieldType.NUMBER), rule("b", FieldType.NUMBER, false, "dossier.fields.a", null, null),
                rule("c", FieldType.NUMBER, false, "dossier.fields.b", null, null))))
                .hasMessageContaining("'b'");
    }

    @Test
    void validateDefinitionsBoundsTheNumberOfRules() {
        List<FieldDefinition> many = new java.util.ArrayList<>();
        many.add(plain("a", FieldType.NUMBER));
        for (int i = 0; i < DossierFieldRules.MAX_EXPRESSIONS_PER_TYPE + 1; i++) {
            many.add(rule("f" + i, FieldType.NUMBER, false, "1", null, null));
        }
        assertThatThrownBy(() -> rules.validateDefinitions(many)).hasMessageContaining("at most");
    }

    @Test
    void withoutAnEngineRulesAreRefusedAtDefinitionAndNeverSilentlyIgnored() {
        List<FieldDefinition> defs = List.of(plain("a", FieldType.NUMBER), rule("b", FieldType.NUMBER, false, "dossier.fields.a", null, null));
        assertThatThrownBy(() -> DossierFieldRules.disabled().validateDefinitions(defs)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DossierFieldRules.disabled().apply(defs, fields("a", 1), Set.of())).isInstanceOf(IllegalStateException.class);
    }
}
