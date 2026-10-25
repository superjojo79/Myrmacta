package be.myrmacta.infrastructure.expression;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.expression.model.ExpressionLimits;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * LC6 (TODO §89) — spike de qualification D-SCRIPT-GO : les cas que l'analyse 45 range sous « algorithme de calcul,
 * transformation complexe » sont-ils exprimables avec LC2 (JSONata borné) ? Chaque test est une preuve versionnée ; si un
 * cas réel futur n'y entrait pas, ce fichier est l'endroit où le consigner avant de rouvrir D-SCRIPT-GO.
 */
class ScriptQualificationSpikeTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);
    private final JsonataExpressionEngine engine = new JsonataExpressionEngine(ExpressionLimits.DEFAULTS, CLOCK);

    private Object eval(String formula, Map<String, Object> ctx) {
        return engine.evaluate(formula, ctx).value();
    }

    @Test
    void progressiveTieredCommission() {
        // 0–1000 : 1 % ; 1000–5000 : 2 % ; au-delà : 3 % (barème progressif par tranches, fonction locale + $reduce).
        String formula = "(\n"
                + "  $tiers := [{\"upTo\": 1000, \"rate\": 0.01}, {\"upTo\": 5000, \"rate\": 0.02}, {\"upTo\": 1000000000, \"rate\": 0.03}];\n"
                + "  $amount := dossier.fields.montant;\n"
                + "  $round($reduce($tiers, function($acc, $t) {\n"
                + "    ($lo := $acc.lo; $hi := $min([$t.upTo, $amount]);\n"
                + "     {\"lo\": $t.upTo, \"sum\": $acc.sum + ($hi > $lo ? ($hi - $lo) * $t.rate : 0)})\n"
                + "  }, {\"lo\": 0, \"sum\": 0}).sum, 2)\n"
                + ")";
        assertThat(((Number) eval(formula, Map.of("dossier", Map.of("fields", Map.of("montant", 6000))))).doubleValue())
                .isEqualTo(10.0 + 80.0 + 30.0);
    }

    @Test
    void ibanChecksumModulo97() {
        // Contrôle IBAN : déplacement, conversion lettres → chiffres, modulo 97 itératif (boucle d'état).
        String formula = "(\n"
                + "  $iban := $uppercase(dossier.fields.iban);\n"
                + "  $r := $substring($iban, 4) & $substring($iban, 0, 4);\n"
                + "  $digits := $join($map($split($r, ''), function($c) {\n"
                + "    $contains('0123456789', $c) ? $c : $string($number('0x' & '0') + $length($substringBefore('ABCDEFGHIJKLMNOPQRSTUVWXYZ', $c)) + 10)\n"
                + "  }), '');\n"
                + "  $reduce($split($digits, ''), function($acc, $d) { ($acc * 10 + $number($d)) % 97 }, 0) = 1\n"
                + ")";
        assertThat(eval(formula, Map.of("dossier", Map.of("fields", Map.of("iban", "GB82WEST12345698765432")))))
                .isEqualTo(Boolean.TRUE);
        assertThat(eval(formula, Map.of("dossier", Map.of("fields", Map.of("iban", "GB82WEST12345698765433")))))
                .isEqualTo(Boolean.FALSE);
    }

    @Test
    void groupingAndAggregationOfAConnectorList() {
        List<Map<String, Object>> lines = List.of(
                Map.of("cat", "A", "qty", 2, "price", 10), Map.of("cat", "B", "qty", 1, "price", 7),
                Map.of("cat", "A", "qty", 1, "price", 5));
        Object r = eval("connectorResult.lines{cat: $sum($.(qty * price))}", Map.of("connectorResult", Map.of("lines", lines)));
        assertThat(r).isEqualTo(Map.of("A", 25L, "B", 7L));
    }

    @Test
    void recursiveAmortizationSchedule() {
        // Fonction récursive : solde restant après n mensualités constantes.
        String formula = "(\n"
                + "  $balance := function($p, $rate, $pay, $n) { $n = 0 ? $p : $balance($p * (1 + $rate) - $pay, $rate, $pay, $n - 1) };\n"
                + "  $round($balance(1000, 0.01, 100, 3), 2)\n"
                + ")";
        assertThat(((Number) eval(formula, Map.of())).doubleValue()).isEqualTo(727.29);
    }

    @Test
    void dateArithmeticOnBusinessDays() {
        // Échéance = date + 10 jours ouvrés (boucle bornée sur un intervalle, calendrier via $toMillis / $fromMillis).
        String formula = "(\n"
                + "  $start := $toMillis('2026-10-01');\n"
                + "  $weekday := function($ms) { ($floor($ms / 86400000) + 4) % 7 };\n"
                + "  $days := [1..20].($start + $ * 86400000);\n"
                + "  $open := $days[$weekday($) >= 1 and $weekday($) <= 5];\n"
                + "  $substring($fromMillis($open[9]), 0, 10)\n"
                + ")";
        assertThat(eval(formula, Map.of())).isEqualTo("2026-10-15");
    }

    @Test
    void objectTransformationWithConditionalsAndStrings() {
        Object r = eval("{\"fullName\": $trim(dossier.fields.first & ' ' & $uppercase(dossier.fields.last)),"
                        + " \"vip\": dossier.fields.score >= 700 ? 'oui' : 'non'}",
                Map.of("dossier", Map.of("fields", Map.of("first", " Ada", "last", "lovelace", "score", 712))));
        assertThat(r).isEqualTo(Map.of("fullName", "Ada LOVELACE", "vip", "oui"));
    }
}
