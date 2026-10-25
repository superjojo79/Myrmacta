package be.myrmacta.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Identifiant du tenant servi par cette instance (décision n° 13) — jamais résolu par requête.
 */
@Validated
@ConfigurationProperties(prefix = "app.tenant")
public record TenantProperties(@NotBlank String id) {}
