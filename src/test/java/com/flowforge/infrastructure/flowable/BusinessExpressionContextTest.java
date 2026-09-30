package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** LC2 (TODO §75, D-EXPR-CONTEXT) — ce qu'une expression exécutée dans un process peut voir. */
class BusinessExpressionContextTest {

    private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

    private static Dossier dossier(Map<String, Object> fields) {
        return new Dossier(UUID.randomUUID(), "t", "DOS-7", "SINISTRE", "Sinistre Dupont", DossierStatus.OUVERT, fields,
                null, 1L, "u", NOW, null, NOW, null, null, null);
    }

    @Test
    void sensitiveFieldsAreNeverExposedToAnExpression() {
        Dossier d = dossier(Map.of("montant", 6000, "iban", "FR7630001007941234567890185"));
        List<FieldDefinition> defs = List.of(
                new FieldDefinition("montant", "Montant", FieldType.NUMBER, false, null, null, null, false),
                new FieldDefinition("iban", "IBAN", FieldType.STRING, false, null, null, null, true));

        Map<String, Object> ctx = BusinessExpressionContext.build(d, defs, null, Map.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) ((Map<String, Object>) ctx.get("dossier")).get("fields");
        assertThat(fields).containsOnlyKeys("montant");
        assertThat(ctx.toString()).doesNotContain("FR7630001007941234567890185");
    }

    @Test
    void dossierMetadataConnectorResultAndScalarVariablesAreExposed() {
        Map<String, Object> processVariables = new java.util.LinkedHashMap<>();
        processVariables.put("amount", 120);
        processVariables.put("flag", true);
        processVariables.put("text", "x");
        processVariables.put("id", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        processVariables.put("when", NOW);
        processVariables.put("blob", new byte[] {1, 2});
        processVariables.put("structure", Map.of("a", 1));
        processVariables.put("nothing", null);

        Map<String, Object> ctx = BusinessExpressionContext.build(
                dossier(Map.of()), List.of(), Map.of("score", 7), processVariables);

        @SuppressWarnings("unchecked")
        Map<String, Object> d = (Map<String, Object>) ctx.get("dossier");
        assertThat(d).containsEntry("reference", "DOS-7").containsEntry("type", "SINISTRE")
                .containsEntry("status", "OUVERT").containsEntry("label", "Sinistre Dupont");
        assertThat(ctx.get("connectorResult")).isEqualTo(Map.of("score", 7));
        @SuppressWarnings("unchecked")
        Map<String, Object> vars = (Map<String, Object>) ctx.get("variables");
        // Seuls les scalaires passent : ni binaire, ni structure arbitraire, ni null.
        assertThat(vars).containsOnlyKeys("amount", "flag", "text", "id", "when")
                .containsEntry("id", "11111111-1111-1111-1111-111111111111")
                .containsEntry("when", "2026-09-30T10:00:00Z");
    }

    @Test
    void withoutADossierOnlyTheRemainingRootsAreProvided() {
        Map<String, Object> ctx = BusinessExpressionContext.build(null, List.of(), null, Map.of("a", 1));
        assertThat(ctx).containsOnlyKeys("variables");
    }

    @Test
    void onlyDeclaredNonSensitiveFieldsAreExposedNotEverythingThatIsStored() {
        // B3 (seconde passe) : `withoutSensitiveKeys` est une liste d'exclusion — un champ encore stocké mais plus déclaré
        // (renommé, supprimé, ancien champ sensible) était exposé. Liste blanche : déclaré ET non sensible.
        // (Un `Dossier` ne peut pas porter de valeur nulle : `Map.copyOf` dans son constructeur.)
        java.util.Map<String, Object> stored = new java.util.LinkedHashMap<>();
        stored.put("montant", 6000);
        stored.put("ancienIban", "FR7630001007941234567890185");
        stored.put("renomme", "valeur");
        Dossier d = new Dossier(UUID.randomUUID(), "t", "DOS-7", "SINISTRE", "x", DossierStatus.OUVERT, stored, null, 1L, "u",
                NOW, null, NOW, null, null, null);
        List<FieldDefinition> defs = List.of(
                new FieldDefinition("montant", "Montant", FieldType.NUMBER, false, null, null, null, false),
                new FieldDefinition("jamaisSaisi", "Jamais saisi", FieldType.STRING, false, null, null, null, false),
                new FieldDefinition("iban", "IBAN", FieldType.STRING, false, null, null, null, true));

        Map<String, Object> ctx = BusinessExpressionContext.build(d, defs, null, Map.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) ((Map<String, Object>) ctx.get("dossier")).get("fields");
        assertThat(fields).containsOnlyKeys("montant");
        assertThat(ctx.toString()).doesNotContain("FR7630001007941234567890185").doesNotContain("valeur");
    }
}
