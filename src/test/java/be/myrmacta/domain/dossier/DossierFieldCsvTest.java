package be.myrmacta.domain.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** D1 (TODO §76, D-CHAMPS-EXPORT) — colonnes et cellules CSV des champs structurés. */
class DossierFieldCsvTest {

    private static FieldDefinition def(String name, FieldType type) {
        return new FieldDefinition(name, name, type, false, null, List.of(), null);
    }

    @Test
    void scalarFieldsKeepASingleColumnAsBefore() {
        assertThat(DossierFieldCsv.headers(def("a", FieldType.STRING))).containsExactly("a");
        assertThat(DossierFieldCsv.cells(def("a", FieldType.NUMBER), 3)).containsExactly("3");
        assertThat(DossierFieldCsv.cells(def("a", FieldType.STRING), null)).containsExactly("");
    }

    @Test
    void moneyFlattensToAmountAndCurrencyColumns() {
        FieldDefinition m = def("m", FieldType.MONEY);
        assertThat(DossierFieldCsv.headers(m)).containsExactly("m.amount", "m.currency");
        assertThat(DossierFieldCsv.cells(m, Map.of("amount", "12.50", "currency", "EUR"))).containsExactly("12.50", "EUR");
        assertThat(DossierFieldCsv.cells(m, null)).containsExactly("", "");
        assertThat(DossierFieldCsv.cells(m, "n'importe quoi")).containsExactly("", "");
    }

    @Test
    void externalReferenceFlattensToSystemIdAndLabelColumns() {
        FieldDefinition r = def("r", FieldType.EXTERNAL_REF);
        assertThat(DossierFieldCsv.headers(r)).containsExactly("r.system", "r.id", "r.label");
        assertThat(DossierFieldCsv.cells(r, Map.of("system", "d365fo", "id", "V-1"))).containsExactly("d365fo", "V-1", "");
        assertThat(DossierFieldCsv.cells(r, null)).containsExactly("", "", "");
    }

    @Test
    void aGroupIsOneColumnWithCompactJsonSoTheShapeNeverDependsOnTheRowCount() {
        FieldDefinition g = new FieldDefinition("g", "g", FieldType.REPEATABLE_GROUP, false, null, List.of(), null, false,
                List.of(def("a", FieldType.STRING)), null, null);
        assertThat(DossierFieldCsv.headers(g)).containsExactly("g");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("poste", "Toit \"neuf\"\n");
        Map<String, Object> price = new LinkedHashMap<>();
        price.put("amount", "1.00");
        price.put("currency", "EUR");
        row.put("prix", price);
        row.put("ok", true);
        row.put("n", 2);
        assertThat(DossierFieldCsv.cells(g, List.of(row)).getFirst())
                .isEqualTo("[{\"poste\":\"Toit \\\"neuf\\\"\\n\",\"prix\":{\"amount\":\"1.00\",\"currency\":\"EUR\"},\"ok\":true,\"n\":2}]");
        assertThat(DossierFieldCsv.cells(g, List.of())).containsExactly("[]");
    }
}
