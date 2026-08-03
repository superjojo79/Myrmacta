package com.flowforge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration de la chaîne Security M2M ({@code /api/m2m/**}, DD-M2M).
 *
 * <ul>
 *   <li>{@code issuer-uri} : émetteur JWT resource-server (production) ; vide en test.</li>
 *   <li>{@code jwt-secret} : secret HMAC HS256 optionnel — décodeur local pour tests/local
 *       sans IdP. Ignoré si {@code issuer-uri} est renseigné.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.security.m2m")
public record M2mSecurityProperties(String issuerUri, String jwtSecret) {}
