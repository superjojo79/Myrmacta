package be.myrmacta.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration de la chaîne Security M2M ({@code /api/m2m/**}, DD-M2M).
 *
 * <ul>
 *   <li>{@code issuer-uri} : émetteur JWT resource-server (production) ; vide en test.</li>
 *   <li>{@code jwt-secret} : secret HMAC HS256 optionnel — décodeur local pour tests/local
 *       sans IdP. Ignoré si {@code issuer-uri} est renseigné.</li>
 *   <li>{@code trusted-client-ids} : allowlist optionnelle des {@code client_id}/{@code sub} JWT
 *       M2M autorisés à recevoir le rôle {@code API_SERVICE} par {@code McpAccessContextFactory}
 *       (revue adversariale A3-M2). Vide (défaut) = comportement historique inchangé, tout JWT
 *       M2M authentifié reçoit {@code API_SERVICE} — le modèle « un seul client M2M de confiance »
 *       assumé par le lot A3. Renseignée, elle referme le risque qu'un futur second client M2M à
 *       portée réduite hérite silencieusement d'un accès admin de facto : seuls les
 *       {@code client_id}/{@code sub} listés ici obtiennent {@code API_SERVICE}, les autres sont
 *       rejetés (403).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.security.m2m")
public record M2mSecurityProperties(String issuerUri, String jwtSecret, List<String> trustedClientIds) {

    public M2mSecurityProperties {
        trustedClientIds = trustedClientIds == null ? List.of() : List.copyOf(trustedClientIds);
    }
}
