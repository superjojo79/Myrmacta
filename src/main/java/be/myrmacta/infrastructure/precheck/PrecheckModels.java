package be.myrmacta.infrastructure.precheck;

import be.myrmacta.domain.precheck.port.out.PrecheckModelPort;
import be.myrmacta.infrastructure.assistant.AnthropicChatModels;
import be.myrmacta.infrastructure.config.PrecheckProperties;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import java.time.Duration;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * TODO §106 (lot A, C1, HC1, D-PRECHECK-PROVIDER) — modèle de la pré-vérification, construit à la main et seulement si
 * {@code app.precheck.enabled=true} (aucune auto-configuration : {@code spring.ai.model.chat=none}). Aucune relance
 * automatique (chaque appel compte dans le budget de jetons ; l'échec devient un incident rejouable), aucune redirection
 * suivie.
 */
public final class PrecheckModels {

    static final String USER_AGENT = "myrmacta-precheck";

    private PrecheckModels() {}

    public static PrecheckModelPort create(PrecheckProperties properties) {
        return PrecheckProperties.ANTHROPIC.equals(properties.provider())
                ? anthropic(properties)
                : openAiCompatible(properties);
    }

    /**
     * Fournisseurs compatibles OpenAI : réponse contrainte par {@code response_format: json_schema} (strict) ; le
     * domaine valide de toute façon. Sans clé (Ollama, vLLM local), un jeton factice est envoyé. {@code reasoning_effort}
     * n'est envoyé que s'il est configuré : un fournisseur qui ne le connaît pas pourrait refuser la requête.
     */
    static PrecheckModelPort openAiCompatible(PrecheckProperties properties) {
        Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
        OpenAIClient client = new OpenAIClientImpl(ClientOptions.builder()
                .httpClient(new OpenAiNoRedirectHttpClient(timeout))
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey().isEmpty() ? "unused" : properties.apiKey())
                .timeout(timeout)
                .maxRetries(0)
                .putHeader("User-Agent", USER_AGENT)
                .build());
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiClient(client)
                .openAiClientAsync(client.async())
                .options(OpenAiChatOptions.builder().model(properties.model()).build())
                .build();
        return new SpringAiPrecheckModel(chatModel, PrecheckProperties.OPENAI_COMPATIBLE, properties.model(),
                request -> OpenAiChatOptions.builder()
                        .model(properties.model())
                        .maxTokens(request.maxOutputTokens())
                        .temperature(0.0)
                        .reasoningEffort(properties.reasoningEffort().isEmpty() ? null : properties.reasoningEffort())
                        .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                                .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                                .jsonSchema(request.jsonSchema())
                                .strict(true)
                                .build())
                        .build(),
                request -> request.system());
    }

    /** Anthropic : pas de format de réponse imposé par l'API ; le schéma est donné dans les consignes. */
    static PrecheckModelPort anthropic(PrecheckProperties properties) {
        var chatModel = AnthropicChatModels.create(properties.baseUrl(), properties.apiKey(), properties.model(),
                properties.anthropicVersion(), properties.timeoutSeconds(), properties.maxOutputTokens(), USER_AGENT);
        return new SpringAiPrecheckModel(chatModel, PrecheckProperties.ANTHROPIC, properties.model(),
                request -> AnthropicChatOptions.builder()
                        .model(properties.model())
                        .maxTokens(request.maxOutputTokens())
                        .temperature(0.0)
                        .build(),
                request -> request.system() + "\n\nRéponds uniquement par un objet JSON conforme à ce schéma JSON :\n"
                        + request.jsonSchema());
    }
}
