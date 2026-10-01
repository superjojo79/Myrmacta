package com.flowforge.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.FieldCondition.Op;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** TODO §81 (D2) — modèle de recherche sur champs dynamiques : drapeau « cherchable », critères, lecture des paramètres f.*. */
class FieldSearchModelTest {

    private static FieldDefinition field(FieldType type, boolean sensitive, boolean searchable) {
        return new FieldDefinition("f", "f", type, false, null, null, null, sensitive, null, null, null, null, null, null, searchable);
    }

    @Test
    void onlyScalarNonSensitiveFieldsCanBeSearchable() {
        for (FieldType ok : List.of(FieldType.STRING, FieldType.NUMBER, FieldType.DATE, FieldType.BOOLEAN)) {
            assertThat(field(ok, false, true).isSearchable()).isTrue();
        }
        assertThatThrownBy(() -> field(FieldType.STRING, true, true)).hasMessageContaining("sensitive");
        assertThatThrownBy(() -> field(FieldType.MONEY, false, true)).hasMessageContaining("cannot be searchable");
        assertThatThrownBy(() -> field(FieldType.FILE, false, true)).hasMessageContaining("cannot be searchable");
        assertThatThrownBy(() -> field(FieldType.EXTERNAL_REF, false, true)).hasMessageContaining("cannot be searchable");
    }

    @Test
    void aRepeatableGroupAndItsSubFieldsAreNeverSearchable() {
        FieldDefinition sub = field(FieldType.STRING, false, true);
        assertThatThrownBy(() -> new FieldDefinition("g", "g", FieldType.REPEATABLE_GROUP, false, null, null, null, false,
                List.of(sub), null, null)).hasMessageContaining("sub-field");
        assertThatThrownBy(() -> new FieldDefinition("g", "g", FieldType.REPEATABLE_GROUP, false, null, null, null, false,
                List.of(field(FieldType.STRING, false, false)), null, null, null, null, null, true))
                .hasMessageContaining("cannot be searchable");
    }

    @Test
    void searchableDefaultsToFalseAndSurvivesCopies() {
        assertThat(new FieldDefinition("a", "a", FieldType.STRING, false, null, null, null, false).isSearchable()).isFalse();
        FieldDefinition s = field(FieldType.STRING, false, true);
        assertThat(s.withRequired(true).isSearchable()).isTrue();
        assertThat(s.withHidden().isSearchable()).isTrue();
    }

    @Test
    void conditionsAreBounded() {
        assertThatThrownBy(() -> new FieldCondition("f", Op.EQ, List.of())).hasMessageContaining("needs a value");
        assertThatThrownBy(() -> new FieldCondition("f", Op.EQ, List.of("a", "b"))).hasMessageContaining("exactly one");
        assertThatThrownBy(() -> new FieldCondition("f", Op.EQ, List.of("x".repeat(201)))).hasMessageContaining("at most");
        assertThatThrownBy(() -> new FieldCondition("f", Op.IN, java.util.Collections.nCopies(21, "a"))).hasMessageContaining("at most");
        assertThat(new FieldCondition("f", Op.IN, List.of("a", "b")).values()).hasSize(2);
    }

    @Test
    void aSearchTakesAtMostFiveFilters() {
        List<FieldCondition> six = java.util.Collections.nCopies(6, new FieldCondition("f", Op.EQ, List.of("a")));
        assertThatThrownBy(() -> SearchDossiersQuery.of(null, "T", null, 0, 20, six)).hasMessageContaining("at most 5");
        assertThat(SearchDossiersQuery.of(null, "T", null, 0, 20, six.subList(0, 5)).fieldConditions()).hasSize(5);
    }

    @Test
    void parserReadsOperatorsAndRefusesUnknownOnes() {
        var conditions = FieldConditionParser.parse(Map.of(
                "f.contrat", "C-42", "f.montant.gte", "5000", "f.categorie.in", "A,B", "f.nom.prefix", "du", "page", "2"));
        assertThat(conditions).extracting(FieldCondition::field).containsExactly("categorie", "contrat", "montant", "nom");
        assertThat(conditions).extracting(FieldCondition::op).containsExactly(Op.IN, Op.EQ, Op.GTE, Op.PREFIX);
        assertThat(conditions.get(0).values()).containsExactly("A", "B");
        assertThatThrownBy(() -> FieldConditionParser.parse(Map.of("f.montant.between", "1"))).isInstanceOf(InvalidDossierSearchException.class);
        assertThatThrownBy(() -> FieldConditionParser.parse(Map.of("f.montant.gte", " "))).isInstanceOf(InvalidDossierSearchException.class);
        assertThat(FieldConditionParser.parse(Map.of("status", "OUVERT"))).isEmpty();
    }
}
