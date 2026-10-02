package com.flowforge.infrastructure.assistant;

import com.flowforge.infrastructure.config.AssistantProperties;
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
        Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
        AnthropicBackend backend = AnthropicBackend.builder()
                .baseUrl(properties.baseUrl().replaceAll("/+$", ""))
                .apiKey(properties.apiKey())
                .build();
        NoRedirectHttpClient http = new NoRedirectHttpClient(timeout);
        ClientOptions.Builder options = ClientOptions.builder()
                .baseUrl(properties.baseUrl().replaceAll("/+$", ""))
                .timeout(timeout)
                .maxRetries(0)
                .putHeader("User-Agent", "flowforge-assistant")
                .putHeader("anthropic-version", properties.anthropicVersion());
        backend.applyCredentials(http, options);
        AnthropicClient client = new AnthropicClientImpl(options.httpClient(http).build());
        AnthropicChatOptions defaults = AnthropicChatOptions.builder()
                .model(properties.model())
                .maxTokens(properties.maxOutputTokens())
                .build();
        return AnthropicChatModel.builder().anthropicClient(client).anthropicClientAsync(client.async()).options(defaults).build();
    }
}
