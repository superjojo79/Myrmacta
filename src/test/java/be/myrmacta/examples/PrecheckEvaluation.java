package be.myrmacta.examples;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.infrastructure.config.PrecheckProperties;
import be.myrmacta.infrastructure.precheck.PrecheckInfrastructure;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * TODO §106 (issue #211, lot D, D3) — jeu d'évaluation contre un <b>vrai</b> modèle. Hors {@code verify} et hors CI
 * (le nom ne suit pas les motifs de Surefire) ; coûte des jetons. Lancement :
 * <pre>
 * MYRMACTA_EVAL_BASE_URL=https://api.mistral.ai/v1 MYRMACTA_EVAL_API_KEY=… MYRMACTA_EVAL_MODEL=mistral-small-latest \
 *   ./mvnw test -Dtest=PrecheckEvaluation -Dsurefire.failIfNoSpecifiedTests=false -Dskip.frontend=true
 * </pre>
 * {@code MYRMACTA_EVAL_PROVIDER=anthropic} pour Anthropic ; {@code MYRMACTA_EVAL_ALLOW_PLAIN_HTTP=true} pour un serveur
 * local en HTTP hors boucle locale. Rapport : {@code target/precheck-evaluation.md}.
 */
@EnabledIfEnvironmentVariable(named = "MYRMACTA_EVAL_BASE_URL", matches = ".+")
class PrecheckEvaluation {

    @Test
    void evaluateAgainstARealModel() throws Exception {
        PrecheckProperties properties = new PrecheckProperties(true,
                env("MYRMACTA_EVAL_PROVIDER", PrecheckProperties.OPENAI_COMPATIBLE), env("MYRMACTA_EVAL_BASE_URL", ""),
                env("MYRMACTA_EVAL_API_KEY", ""), env("MYRMACTA_EVAL_MODEL", ""),
                Boolean.parseBoolean(env("MYRMACTA_EVAL_ALLOW_PLAIN_HTTP", "false")), 120, 1_000_000, 2_000, 10, 30,
                30_000, 100_000, 20, "2023-06-01");
        PrecheckEvaluationHarness.Report report = PrecheckEvaluationHarness.run(properties.model(),
                PrecheckInfrastructure.model(properties), properties.limits());
        Path written = PrecheckEvaluationHarness.write(report);
        System.out.println(report.markdown());
        System.out.println("Rapport : " + written.toAbsolutePath());
        assertThat(report.predictions()).isNotEmpty();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
