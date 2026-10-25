package be.myrmacta.infrastructure.portal.identity;

import be.myrmacta.domain.workflow.portal.model.NationalRegisterNumber;
import be.myrmacta.domain.workflow.portal.model.VerifiedIdentity;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityProviderPort;
import be.myrmacta.infrastructure.config.PortalStrongAuthProperties;
import be.myrmacta.infrastructure.config.PortalStrongAuthProperties.Provider;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * TODO §104 (issue #210, D-BEAUTH-PORTAL-CLIENT) — l'instance, cliente OIDC directe de FAS et d'itsme pour le portail :
 * code d'autorisation avec PKCE (S256), authentification du client par {@code private_key_jwt} (clé de l'instance,
 * T11) ou, si le fournisseur a émis un secret, par {@code client_secret_post}, ID token éventuellement chiffré (itsme) puis vérifié sur la JWKS du fournisseur : signature, émetteur de la
 * découverte, audience, expiration, {@code nonce}. Sans session ni {@code oauth2Login} : le portail reste sans état
 * (D-PORTAL-CSRF-SESSION). Aucune donnée d'identité n'est journalisée.
 *
 * <p>Écrit avec Nimbus (déjà présent par Spring Security) plutôt qu'avec les {@code ClientRegistration} de Spring
 * Security OAuth2 Client, pensés pour une session de connexion.
 */
public final class OidcPortalIdentityProviderAdapter implements PortalIdentityProviderPort {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration ASSERTION_LIFETIME = Duration.ofSeconds(60);
    private static final int CLOCK_SKEW_SECONDS = 60;
    private static final Set<JWSAlgorithm> ID_TOKEN_ALGORITHMS = Set.of(JWSAlgorithm.RS256, JWSAlgorithm.PS256);

    private final Map<String, Provider> providers;
    private final String redirectUri;
    private final PortalIdentityKeys keys;
    private final Clock clock;
    private final HttpClient http;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Metadata> metadata = new ConcurrentHashMap<>();

    public OidcPortalIdentityProviderAdapter(
            PortalStrongAuthProperties properties, String redirectUri, PortalIdentityKeys keys, Clock clock) {
        this.providers = Objects.requireNonNull(properties, "properties").orderedProviders();
        this.redirectUri = Objects.requireNonNull(redirectUri, "redirectUri");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.http = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).proxy(ProxySelector.getDefault())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public List<ProviderOption> providers() {
        return providers.entrySet().stream()
                .map(entry -> new ProviderOption(entry.getKey(), entry.getValue().displayLabel(entry.getKey())))
                .toList();
    }

    @Override
    public AuthorizationRequest begin(String provider, String state, String nonce) {
        Provider configuration = provider(provider);
        Metadata endpoints = metadata(provider, configuration);
        String verifier = randomUrlSafe(32);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("response_type", "code");
        query.put("client_id", configuration.clientId());
        query.put("redirect_uri", redirectUri);
        query.put("scope", configuration.scopesOrDefault());
        query.put("state", state);
        query.put("nonce", nonce);
        query.put("code_challenge", s256(verifier));
        query.put("code_challenge_method", "S256");
        if (configuration.acrValues() != null && !configuration.acrValues().isBlank()) {
            query.put("acr_values", configuration.acrValues());
        }
        String separator = endpoints.authorizationEndpoint().contains("?") ? "&" : "?";
        return new AuthorizationRequest(URI.create(endpoints.authorizationEndpoint() + separator + encode(query)), verifier);
    }

    @Override
    public VerifiedIdentity complete(String provider, String code, String pkceVerifier, String nonce) {
        Provider configuration = provider(provider);
        Metadata endpoints = metadata(provider, configuration);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("code_verifier", pkceVerifier);
        form.put("client_id", configuration.clientId());
        if (configuration.usesClientSecret()) {
            form.put("client_secret", configuration.clientSecret());
        } else {
            form.put("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
            form.put("client_assertion", clientAssertion(configuration.clientId(), endpoints.tokenEndpoint()));
        }
        String idToken = idToken(post(endpoints.tokenEndpoint(), form));
        JWTClaimsSet claims = verify(signedIdToken(idToken), endpoints, configuration, nonce);
        var names = configuration.claims();
        return new VerifiedIdentity(provider, configuration.levelOf(string(claims, names.level())),
                string(claims, names.givenName()), string(claims, names.familyName()),
                NationalRegisterNumber.parse(string(claims, names.nrn())).orElse(null));
    }

    // --- découverte -------------------------------------------------------------------------------------------------

    private record Metadata(String issuer, String authorizationEndpoint, String tokenEndpoint,
                            JWKSource<SecurityContext> jwks) {}

    private Metadata metadata(String id, Provider configuration) {
        return metadata.computeIfAbsent(id, key -> discover(configuration));
    }

    private Metadata discover(Provider configuration) {
        String issuer = trimSlash(configuration.issuerUri());
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                    URI.create(issuer + "/.well-known/openid-configuration")).timeout(HTTP_TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IdentityProviderException("discovery failed with HTTP " + response.statusCode());
            }
            Map<String, Object> document = JSONObjectUtils.parse(response.body());
            String declaredIssuer = trimSlash(JSONObjectUtils.getString(document, "issuer"));
            if (!issuer.equals(declaredIssuer)) {
                throw new IdentityProviderException("discovery issuer does not match the configured issuer");
            }
            return new Metadata(declaredIssuer, required(document, "authorization_endpoint"),
                    required(document, "token_endpoint"),
                    JWKSourceBuilder.create(new URI(required(document, "jwks_uri")).toURL()).build());
        } catch (IOException | ParseException | URISyntaxException ex) {
            throw new IdentityProviderException("discovery failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IdentityProviderException("discovery interrupted", ex);
        }
    }

    // --- échange du code ------------------------------------------------------------------------------------------

    private String clientAssertion(String clientId, String tokenEndpoint) {
        Date now = Date.from(clock.instant());
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(clientId).subject(clientId).audience(tokenEndpoint)
                .jwtID(UUID.randomUUID().toString()).issueTime(now)
                .expirationTime(Date.from(clock.instant().plus(ASSERTION_LIFETIME))).build();
        SignedJWT assertion = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keys.signing().getKeyID()).build(), claims);
        try {
            assertion.sign(new RSASSASigner(keys.signing()));
        } catch (JOSEException ex) {
            throw new IdentityProviderException("client assertion signature failed", ex);
        }
        return assertion.serialize();
    }

    private String post(String tokenEndpoint, Map<String, String> form) {
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(tokenEndpoint))
                            .timeout(HTTP_TIMEOUT)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(encode(form))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IdentityProviderException("token endpoint answered HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException ex) {
            throw new IdentityProviderException("token endpoint unreachable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IdentityProviderException("token request interrupted", ex);
        }
    }

    private static String idToken(String tokenResponse) {
        try {
            String idToken = JSONObjectUtils.getString(JSONObjectUtils.parse(tokenResponse), "id_token");
            if (idToken == null || idToken.isBlank()) {
                throw new IdentityProviderException("token response without id_token");
            }
            return idToken;
        } catch (ParseException ex) {
            throw new IdentityProviderException("unreadable token response", ex);
        }
    }

    private SignedJWT signedIdToken(String idToken) {
        try {
            if (idToken.chars().filter(c -> c == '.').count() == 4) {
                JWEObject jwe = JWEObject.parse(idToken);
                jwe.decrypt(new RSADecrypter(keys.encryption().orElseThrow(
                        () -> new IdentityProviderException("encrypted id_token but no encryption key configured"))));
                SignedJWT nested = jwe.getPayload().toSignedJWT();
                if (nested == null) {
                    throw new IdentityProviderException("encrypted id_token does not hold a signed JWT");
                }
                return nested;
            }
            return SignedJWT.parse(idToken);
        } catch (ParseException | JOSEException ex) {
            throw new IdentityProviderException("unreadable id_token", ex);
        }
    }

    private JWTClaimsSet verify(SignedJWT idToken, Metadata endpoints, Provider configuration, String nonce) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(ID_TOKEN_ALGORITHMS, endpoints.jwks()));
        DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier = new DefaultJWTClaimsVerifier<>(
                configuration.clientId(), new JWTClaimsSet.Builder().issuer(endpoints.issuer()).build(),
                Set.of("sub", "exp", "iat"));
        claimsVerifier.setMaxClockSkew(CLOCK_SKEW_SECONDS);
        processor.setJWTClaimsSetVerifier(claimsVerifier);
        try {
            JWTClaimsSet claims = processor.process(idToken, null);
            if (!Objects.equals(nonce, claims.getStringClaim("nonce"))) {
                throw new IdentityProviderException("id_token nonce mismatch");
            }
            return claims;
        } catch (BadJOSEException | JOSEException | ParseException ex) {
            throw new IdentityProviderException("id_token rejected: " + ex.getClass().getSimpleName(), ex);
        }
    }

    // --- claims -----------------------------------------------------------------------------------------------------

    /** Valeur textuelle d'un claim ; une liste donne son premier élément (certains fournisseurs émettent un tableau). */
    private static String string(JWTClaimsSet claims, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Object value = claims.getClaim(name);
        if (value instanceof List<?> list) {
            value = list.isEmpty() ? null : list.getFirst();
        }
        return value instanceof String text ? text : null;
    }

    // --- utilitaires ------------------------------------------------------------------------------------------------

    private Provider provider(String id) {
        Provider configuration = id == null ? null : providers.get(id);
        if (configuration == null) {
            throw new IdentityProviderException("unknown provider");
        }
        return configuration;
    }

    private String randomUrlSafe(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private static String s256(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String encode(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String required(Map<String, Object> document, String name) throws ParseException {
        String value = JSONObjectUtils.getString(document, name);
        if (value == null || value.isBlank()) {
            throw new IdentityProviderException("discovery document without " + name);
        }
        return value;
    }

    private static String trimSlash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
