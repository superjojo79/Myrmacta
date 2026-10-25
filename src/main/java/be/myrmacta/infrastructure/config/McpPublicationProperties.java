package be.myrmacta.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * TODO §82 (D6, D-MCP-LC-PUBLISH = C) — politique de publication des configurations venant du MCP. Lue au démarrage, jamais
 * modifiable à l'exécution.
 *
 * <ul>
 *   <li>{@code draft} (défaut) : un agent écrit un brouillon ; un humain le publie depuis « Brouillons à publier » ;</li>
 *   <li>{@code direct} : l'outil {@code publish_draft} publie sans relecture — refusé au démarrage si
 *       {@code app.environment=production}.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.mcp.publication")
public record McpPublicationProperties(@DefaultValue("draft") Mode mode) {

    public enum Mode {
        DRAFT,
        DIRECT
    }

    public boolean directAllowed() {
        return mode == Mode.DIRECT;
    }
}
