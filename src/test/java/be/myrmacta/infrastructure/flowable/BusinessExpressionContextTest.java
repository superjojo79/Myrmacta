package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
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

    // ---- D3b (TODO §78) : connectorResult.json ----------------------------------------------------------------------

    private static final tools.jackson.databind.ObjectMapper MAPPER = new tools.jackson.databind.ObjectMapper();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> connector(String body) {
        Map<String, Object> ctx = BusinessExpressionContext.build(
                null, List.of(), Map.of("status", 200, "body", body), Map.of(), MAPPER);
        return (Map<String, Object>) ctx.get("connectorResult");
    }

    @Test
    void aJsonBodyIsExposedParsedUnderJsonAndTheRawTextStays() {
        Map<String, Object> result = connector("{\"score\": 87, \"ok\": true, \"tags\": [\"a\"]}");
        assertThat(result).containsKeys("status", "body", "json");
        assertThat(result.get("body")).isEqualTo("{\"score\": 87, \"ok\": true, \"tags\": [\"a\"]}");
        assertThat(result.get("json")).isEqualTo(Map.of("score", 87, "ok", true, "tags", List.of("a")));
    }

    @Test
    void aTrappedResponseNeverBlocksTheExpressionsThatDoNotTouchIt() {
        // M1 : profondeur > 24 et nombre hors plage — `json` est omis, jamais d'échec du contexte entier.
        String deep = "{\"a\":".repeat(40) + "1" + "}".repeat(40);
        assertThat(connector(deep)).doesNotContainKey("json").containsKey("status");
        assertThat(connector("{\"v\": 1e400}")).containsKey("json");
        Object huge = ((Map<?, ?>) connector("{\"v\": 1e400}").get("json")).get("v");
        assertThat(huge).isInstanceOf(String.class);
        // Le contexte complet reste évaluable par le moteur (aucune valeur non finie ni trop profonde).
        be.myrmacta.domain.expression.port.out.ExpressionEnginePort engine =
                new be.myrmacta.infrastructure.expression.JsonataExpressionEngine(
                        be.myrmacta.domain.expression.model.ExpressionLimits.DEFAULTS, java.time.Clock.systemUTC());
        Map<String, Object> ctx = BusinessExpressionContext.build(
                null, List.of(), Map.of("status", 200, "body", deep), Map.of(), MAPPER);
        assertThat(engine.evaluate("connectorResult.status", ctx).value()).isEqualTo(200L);
        Map<String, Object> ctx2 = BusinessExpressionContext.build(
                null, List.of(), Map.of("status", 200, "body", "{\"v\": 1e400}"), Map.of(), MAPPER);
        assertThat(engine.evaluate("connectorResult.status", ctx2).value()).isEqualTo(200L);
    }

    @Test
    void numbersThatWouldLosePrecisionAsADoubleAreExposedAsExactText() {
        // B2 : 12345678901234.567 → double 1.2345678901234566E13 ; un montant ne s'arrondit pas en silence.
        Map<?, ?> json = (Map<?, ?>) connector("{\"total\": 12345678901234.567, \"id\": 12345678901234567890, \"small\": 1234.5, \"n\": 42}").get("json");
        assertThat(json.get("total")).isEqualTo("12345678901234.567");
        assertThat(json.get("id")).isEqualTo("12345678901234567890");
        assertThat(json.get("small")).isEqualTo(1234.5);
        assertThat(json.get("n")).isEqualTo(42);
    }

    @Test
    void nonJsonScalarOrOversizedBodiesAreLeftAlone() {
        assertThat(connector("<html/>")).doesNotContainKey("json");
        assertThat(connector("42")).doesNotContainKey("json");
        assertThat(connector("\"text\"")).doesNotContainKey("json");
        assertThat(connector("{\"a\":\"" + "x".repeat(BusinessExpressionContext.MAX_PARSED_BODY_CHARS) + "\"}")).doesNotContainKey("json");
    }

    @Test
    void theJsonKeyIsTheParsedBodyEvenWhenTheResultAlreadyHadOne() {
        Map<String, Object> ctx = BusinessExpressionContext.build(null, List.of(),
                Map.of("status", 200, "body", "{\"a\":1}", "json", "forged"), Map.of(), MAPPER);
        assertThat(((Map<?, ?>) ctx.get("connectorResult")).get("json")).isEqualTo(Map.of("a", 1));
    }
}
