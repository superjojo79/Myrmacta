package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** TODO §106 (lot A, P8) — démarrage refusé sur une configuration activée incohérente. */
class PrecheckPropertiesTest {

    private static PrecheckProperties props(boolean enabled, String provider, String baseUrl, String key, String model,
            boolean plainHttp) {
        return new PrecheckProperties(enabled, provider, baseUrl, key, model, plainHttp, 120, 1_000_000, 2_000, 10, 30,
                30_000, 100_000, 20, "2023-06-01", "");
    }

    @Test
    void disabledNeedsNothing() {
        assertThatCode(() -> props(false, "openai-compatible", "", "", "", false)).doesNotThrowAnyException();
    }

    @Test
    void enabledNeedsAnUrlAModelAndAKnownProvider() {
        assertThatThrownBy(() -> props(true, "openai-compatible", "", "", "m", false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> props(true, "openai-compatible", "https://api.mistral.ai/v1", "", "", false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> props(true, "gemini", "https://x.example/v1", "k", "m", false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> props(true, "anthropic", "https://api.anthropic.com", "", "m", false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void httpsIsRequiredOutsideLoopbackUnlessExplicitlyAllowed() {
        assertThatThrownBy(() -> props(true, "openai-compatible", "http://vllm.interne:8000/v1", "", "m", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https");
        assertThatCode(() -> props(true, "openai-compatible", "http://vllm.interne:8000/v1", "", "m", true))
                .doesNotThrowAnyException();
        assertThatCode(() -> props(true, "openai-compatible", "http://localhost:11434/v1/", "", "llama", false))
                .doesNotThrowAnyException();
        assertThat(props(true, " OpenAI-Compatible ", "https://api.mistral.ai/v1/", "k", "m", false).baseUrl())
                .isEqualTo("https://api.mistral.ai/v1");
    }

    @Test
    void absurdLimitsAreRefusedEvenWhenDisabled() {
        assertThatThrownBy(() -> new PrecheckProperties(false, "openai-compatible", "", "", "", false, 120, 0, 2_000, 10,
                30, 30_000, 100_000, 20, "2023-06-01", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PrecheckProperties(false, "openai-compatible", "", "", "", false, 2, 1, 2_000, 10,
                30, 30_000, 100_000, 20, "2023-06-01", "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reasoningEffortIsOptionalNormalisedAndBounded() {
        assertThat(props(true, "openai-compatible", "http://localhost:11434/v1", "", "qwen3", false).reasoningEffort())
                .isEmpty();
        assertThat(withReasoning(true, "openai-compatible", " None ").reasoningEffort()).isEqualTo("none");
        // Valeur absurde refusée même désactivée, comme les bornes.
        assertThatThrownBy(() -> withReasoning(false, "openai-compatible", "off"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reasoning-effort");
    }

    @Test
    void reasoningEffortIsRefusedForAnthropic() {
        assertThatThrownBy(() -> withReasoning(true, "anthropic", "none"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openai-compatible");
    }

    private static PrecheckProperties withReasoning(boolean enabled, String provider, String reasoningEffort) {
        return new PrecheckProperties(enabled, provider, "https://api.example/v1", "k", "m", false, 120, 1_000_000, 2_000,
                10, 30, 30_000, 100_000, 20, "2023-06-01", reasoningEffort);
    }

    @Test
    void theKeyIsNeverPrinted() {
        assertThat(props(true, "anthropic", "https://api.anthropic.com", "sk-secret", "m", false).toString())
                .doesNotContain("sk-secret");
    }
}
