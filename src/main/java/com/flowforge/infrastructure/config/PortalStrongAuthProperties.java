package com.flowforge.infrastructure.config;

import com.flowforge.domain.workflow.portal.model.IdentityAssuranceLevel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TODO §104 (issue #210, E1, D-BEAUTH-ACTIVATION) — authentification forte des tiers du portail par FAS ou itsme.
 * Désactivée par défaut ; activée, la configuration est vérifiée au démarrage (fail-fast, comme le portail) : au moins un
 * fournisseur complet et une clé de signature.
 *
 * <p>Les noms des claims et les valeurs de niveau ne sont pas confirmés publiquement pour FAS ni pour itsme (K5) : ils
 * n'ont pas de valeur par défaut et se règlent par fournisseur ({@code levels.HIGH}, {@code levels.SUBSTANTIAL},
 * {@code levels.LOW} : listes des valeurs du claim de niveau). Une valeur absente de la correspondance vaut
 * {@code LOW}, donc un refus (T4).
 */
@ConfigurationProperties(prefix = "app.portal.strong-auth")
public record PortalStrongAuthProperties(
        boolean enabled, Duration identityTtl, Keys keys, List<String> providerOrder, Map<String, Provider> providers) {

    /** D-BEAUTH-TTL — plafond de la durée de vie d'une identité vérifiée. */
    public static final Duration MAX_IDENTITY_TTL = Duration.ofHours(4);

    static final Set<String> KNOWN_PROVIDERS = Set.of("fas", "itsme");

    public PortalStrongAuthProperties {
        if (identityTtl == null) {
            identityTtl = Duration.ofMinutes(60);
        }
        if (keys == null) {
            keys = new Keys(null, null, null, null, null, null);
        }
        if (providerOrder == null || providerOrder.isEmpty()) {
            providerOrder = List.of("fas", "itsme");
        }
        providers = providers == null ? Map.of() : Map.copyOf(providers);
        if (identityTtl.isNegative() || identityTtl.isZero() || identityTtl.compareTo(MAX_IDENTITY_TTL) > 0) {
            throw new IllegalStateException("app.portal.strong-auth.identity-ttl must be between 1 second and 4 hours");
        }
        if (enabled) {
            if (providers.isEmpty()) {
                throw new IllegalStateException("app.portal.strong-auth.enabled requires at least one provider");
            }
            for (Map.Entry<String, Provider> entry : providers.entrySet()) {
                if (!KNOWN_PROVIDERS.contains(entry.getKey())) {
                    throw new IllegalStateException("app.portal.strong-auth.providers: unknown provider '"
                            + entry.getKey() + "' (fas or itsme)");
                }
                entry.getValue().requireComplete(entry.getKey());
            }
            if (isBlank(keys.signingKeyFile())) {
                throw new IllegalStateException(
                        "app.portal.strong-auth.keys.signing-key-file is required (private_key_jwt, T11)");
            }
        }
    }

    /** Fournisseurs configurés, dans l'ordre d'affichage (D-BEAUTH-PROVIDER-CHOICE). */
    public Map<String, Provider> orderedProviders() {
        Map<String, Provider> ordered = new LinkedHashMap<>();
        for (String id : providerOrder) {
            if (providers.containsKey(id)) {
                ordered.put(id, providers.get(id));
            }
        }
        providers.keySet().stream().sorted().forEach(id -> ordered.putIfAbsent(id, providers.get(id)));
        return ordered;
    }

    /** Clés de l'instance (fichiers PEM PKCS#8, RSA ≥ 2048) : signature de l'assertion, déchiffrement d'un jeton chiffré. */
    public record Keys(
            String signingKeyFile, String signingKeyId, String previousSigningKeyFile, String previousSigningKeyId,
            String encryptionKeyFile, String encryptionKeyId) {}

    /**
     * {@code clientSecret} : si le fournisseur a émis un secret (FAS selon l'enregistrement BOSA), le client s'authentifie
     * par {@code client_secret_post} ; sinon par {@code private_key_jwt} avec la clé de l'instance (itsme l'impose).
     */
    public record Provider(
            String label, String issuerUri, String clientId, String scopes, String acrValues, Claims claims,
            Map<IdentityAssuranceLevel, List<String>> levels, String clientSecret) {

        // Un seul constructeur : un second rendrait la liaison Spring du record ambiguë (fournisseurs ignorés).
        public Provider {
            levels = levels == null || levels.isEmpty() ? Map.of() : new EnumMap<>(levels);
        }

        public boolean usesClientSecret() {
            return !isBlank(clientSecret);
        }

        @Override
        public String toString() {
            return "Provider[issuerUri=" + issuerUri + ", clientId=" + clientId + ", clientSecret="
                    + (usesClientSecret() ? "***" : "none") + "]";
        }

        void requireComplete(String id) {
            String prefix = "app.portal.strong-auth.providers." + id + ".";
            List<String> missing = new ArrayList<>();
            if (isBlank(issuerUri)) {
                missing.add(prefix + "issuer-uri");
            }
            if (isBlank(clientId)) {
                missing.add(prefix + "client-id");
            }
            if (claims == null || isBlank(claims.nrn()) || isBlank(claims.level())) {
                missing.add(prefix + "claims.nrn / claims.level");
            }
            if (levels.getOrDefault(IdentityAssuranceLevel.HIGH, List.of()).isEmpty()
                    && levels.getOrDefault(IdentityAssuranceLevel.SUBSTANTIAL, List.of()).isEmpty()) {
                missing.add(prefix + "levels.HIGH or levels.SUBSTANTIAL");
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException("incomplete strong-auth provider: " + String.join(", ", missing));
            }
        }

        public String displayLabel(String id) {
            if (!isBlank(label)) {
                return label;
            }
            return "fas".equals(id) ? "eID, itsme et autres (FAS)" : "itsme";
        }

        /** Niveau correspondant à la valeur du claim ; inconnu → {@code LOW} (refusé). */
        public IdentityAssuranceLevel levelOf(String claimValue) {
            if (claimValue == null) {
                return IdentityAssuranceLevel.LOW;
            }
            for (IdentityAssuranceLevel level : List.of(IdentityAssuranceLevel.HIGH, IdentityAssuranceLevel.SUBSTANTIAL)) {
                if (levels.getOrDefault(level, List.of()).contains(claimValue)) {
                    return level;
                }
            }
            return IdentityAssuranceLevel.LOW;
        }

        public String scopesOrDefault() {
            return isBlank(scopes) ? "openid" : scopes;
        }
    }

    public record Claims(String nrn, String givenName, String familyName, String level) {}

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
