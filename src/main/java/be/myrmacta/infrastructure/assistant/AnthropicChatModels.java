package be.myrmacta.infrastructure.assistant;

import be.myrmacta.infrastructure.config.AssistantProperties;
import java.time.Duration;
import com.anthropic.backends.AnthropicBackend;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientImpl;
import com.anthropic.core.ClientOptions;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;

/**
 * D-ASSIST-PROVIDER — {@code ChatModel} Anthropic du starter Spring AI, construit à la main et seulement si l'assistant est activé :
 * l'auto-configuration du starter est coupée ({@code spring.ai.model.chat=none}), car sans clé elle empêcherait un démarrage qui
 * n'a pas à l'exiger. Aucune relance automatique (chaque appel compte dans les plafonds de jetons).
 */
public final class AnthropicChatModels {

    private AnthropicChatModels() {}

    public static AnthropicChatModel create(AssistantProperties properties) {
        return create(properties.baseUrl(), properties.apiKey(), properties.model(), properties.anthropicVersion(),
                properties.timeoutSeconds(), properties.maxOutputTokens(), "flowforge-assistant");
    }

    /** TODO §106 (lot A) — même construction pour la pré-vérification IA, avec ses propres réglages. */
    public static AnthropicChatModel create(String baseUrl, String apiKey, String model, String anthropicVersion,
            int timeoutSeconds, int maxOutputTokens, String userAgent) {
        String url = baseUrl.replaceAll("/+$", "");
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        AnthropicBackend backend = AnthropicBackend.builder()
                .baseUrl(url)
                .apiKey(apiKey)
                .build();
        NoRedirectHttpClient http = new NoRedirectHttpClient(timeout);
        ClientOptions.Builder options = ClientOptions.builder()
                .baseUrl(url)
                .timeout(timeout)
                .maxRetries(0)
                .putHeader("User-Agent", userAgent)
                .putHeader("anthropic-version", anthropicVersion);
        backend.applyCredentials(http, options);
        AnthropicClient client = new AnthropicClientImpl(options.httpClient(http).build());
        AnthropicChatOptions defaults = AnthropicChatOptions.builder()
                .model(model)
                .maxTokens(maxOutputTokens)
                .build();
        return AnthropicChatModel.builder().anthropicClient(client).anthropicClientAsync(client.async()).options(defaults).build();
    }
}
