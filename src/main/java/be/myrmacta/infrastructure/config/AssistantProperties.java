package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.assistant.model.AssistantLimits;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * TODO §86 (LC3, D-ASSIST-PROVIDER / D-ASSIST-COST / D-ASSIST-HISTORY) — assistant de conception. Désactivé par défaut : il ne
 * s'active que si {@code enabled=true} <b>et</b> une clé est fournie (la clé n'existe que côté serveur ; démarrage refusé si
 * l'un sans l'autre). {@code baseUrl} permet une passerelle ou une région d'hébergement ; HTTPS obligatoire hors boucle locale.
 */
@ConfigurationProperties(prefix = "app.assistant")
public record AssistantProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://api.anthropic.com") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("claude-sonnet-5-5") String model,
        @DefaultValue("2023-06-01") String anthropicVersion,
        @DefaultValue("120") int timeoutSeconds,
        @DefaultValue("30") int retentionDays,
        @DefaultValue("12") int maxToolRoundsPerTurn,
        @DefaultValue("60") int maxTurnsPerConversation,
        @DefaultValue("400000") long maxTokensPerConversation,
        @DefaultValue("3000000") long maxTokensPerTenantPerDay,
        @DefaultValue("4096") int maxOutputTokens) {

    public AssistantProperties {
        if (timeoutSeconds < 5 || timeoutSeconds > 600) {
            throw new IllegalArgumentException("app.assistant.timeout-seconds must be between 5 and 600");
        }
        if (retentionDays < 1 || retentionDays > 3650) {
            throw new IllegalArgumentException("app.assistant.retention-days must be between 1 and 3650");
        }
        if (enabled) {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("app.assistant.enabled=true requires app.assistant.api-key (APP_ASSISTANT_API_KEY)");
            }
            URI uri = URI.create(baseUrl);
            boolean loopback = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !loopback) {
                throw new IllegalStateException("app.assistant.base-url must use https (the API key travels in a header)");
            }
        }
    }

    public AssistantLimits limits() {
        return new AssistantLimits(maxToolRoundsPerTurn, maxTurnsPerConversation, maxTokensPerConversation,
                maxTokensPerTenantPerDay, maxOutputTokens, 8000, 12_000, 4000);
    }

    /** Ne jamais journaliser ni sérialiser la clé. */
    @Override
    public String toString() {
        return "AssistantProperties[enabled=" + enabled + ", baseUrl=" + baseUrl + ", model=" + model + "]";
    }
}
