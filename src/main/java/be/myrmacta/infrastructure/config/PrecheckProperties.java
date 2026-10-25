package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.precheck.model.PrecheckLimits;
import java.net.URI;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * TODO §106 (issue #211, lot A, P8, C1, C7, D-PRECHECK-CONFIG) — pré-vérification IA. Désactivée par défaut, configuration distincte de
 * l'assistant de conception. Fournisseur {@code openai-compatible} (Mistral, vLLM, Ollama, Azure OpenAI…) ou
 * {@code anthropic}. Démarrage refusé si la configuration activée est incohérente : HTTPS obligatoire hors boucle locale,
 * sauf {@code allow-plain-http=true} (réseau interne, risque documenté : les données du dossier circulent en clair).
 */
@ConfigurationProperties(prefix = "app.precheck")
public record PrecheckProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("openai-compatible") String provider,
        @DefaultValue("") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("") String model,
        @DefaultValue("false") boolean allowPlainHttp,
        @DefaultValue("120") int timeoutSeconds,
        @DefaultValue("1000000") long maxTokensPerDay,
        @DefaultValue("2000") int maxOutputTokens,
        @DefaultValue("10") int maxPiecesRead,
        @DefaultValue("30") int maxPagesPerPiece,
        @DefaultValue("30000") int maxCharsPerPiece,
        @DefaultValue("100000") int maxCharsTotal,
        @DefaultValue("20") int maxQuestions,
        @DefaultValue("2023-06-01") String anthropicVersion) {

    public static final String OPENAI_COMPATIBLE = "openai-compatible";
    public static final String ANTHROPIC = "anthropic";
    private static final Set<String> PROVIDERS = Set.of(OPENAI_COMPATIBLE, ANTHROPIC);

    public PrecheckProperties {
        provider = provider == null ? OPENAI_COMPATIBLE : provider.strip().toLowerCase(java.util.Locale.ROOT);
        baseUrl = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        apiKey = apiKey == null ? "" : apiKey.strip();
        model = model == null ? "" : model.strip();
        if (timeoutSeconds < 5 || timeoutSeconds > 600) {
            throw new IllegalArgumentException("app.precheck.timeout-seconds must be between 5 and 600");
        }
        // Les bornes sont validées même désactivée : une valeur absurde ne doit pas attendre l'activation pour être vue.
        new PrecheckLimits(maxTokensPerDay, maxOutputTokens, maxPiecesRead, maxPagesPerPiece, maxCharsPerPiece,
                maxCharsTotal, maxQuestions);
        if (enabled) {
            if (!PROVIDERS.contains(provider)) {
                throw new IllegalStateException("app.precheck.provider must be one of " + PROVIDERS + ", was: " + provider);
            }
            if (baseUrl.isEmpty() || model.isEmpty()) {
                throw new IllegalStateException(
                        "app.precheck.enabled=true requires app.precheck.base-url and app.precheck.model");
            }
            if (ANTHROPIC.equals(provider) && apiKey.isEmpty()) {
                throw new IllegalStateException("app.precheck.provider=anthropic requires app.precheck.api-key");
            }
            URI uri;
            try {
                uri = URI.create(baseUrl);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("app.precheck.base-url is not a valid URL", ex);
            }
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if (!scheme.equals("https") && !scheme.equals("http") || uri.getHost() == null) {
                throw new IllegalStateException("app.precheck.base-url must be an absolute http(s) URL");
            }
            boolean loopback = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost())
                    || "[::1]".equals(uri.getHost());
            if (scheme.equals("http") && !loopback && !allowPlainHttp) {
                throw new IllegalStateException("app.precheck.base-url must use https outside the loopback interface "
                        + "(dossier data and the API key travel in the request), or set app.precheck.allow-plain-http=true");
            }
        }
    }

    public PrecheckLimits limits() {
        return new PrecheckLimits(maxTokensPerDay, maxOutputTokens, maxPiecesRead, maxPagesPerPiece, maxCharsPerPiece,
                maxCharsTotal, maxQuestions);
    }

    /** Ne jamais journaliser ni sérialiser la clé. */
    @Override
    public String toString() {
        return "PrecheckProperties[enabled=" + enabled + ", provider=" + provider + ", baseUrl=" + baseUrl + ", model="
                + model + "]";
    }
}
