package com.flowforge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * TODO §82 (D6, D-SOCLE-RETENTION) — durée de vie des brouillons d'origine automatisée (MCP, assistant) non modifiés.
 * {@code 0} : jamais purgés. Les brouillons de l'éditeur ne sont <b>jamais</b> purgés.
 */
@ConfigurationProperties(prefix = "app.drafts")
public record DraftRetentionProperties(@DefaultValue("30") int retentionDays) {

    public DraftRetentionProperties {
        if (retentionDays < 0 || retentionDays > 3650) {
            throw new IllegalArgumentException("app.drafts.retention-days must be between 0 and 3650");
        }
    }
}
