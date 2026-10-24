package com.flowforge.infrastructure.portal.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.workflow.portal.model.IdentityAssuranceLevel;
import com.flowforge.domain.workflow.portal.model.VerifiedIdentity;
import com.flowforge.domain.workflow.portal.port.out.PortalIdentityProviderPort.AuthorizationRequest;
import com.flowforge.domain.workflow.portal.port.out.PortalIdentityProviderPort.IdentityProviderException;
import com.flowforge.domain.workflow.portal.port.out.PortalIdentityProviderPort.ProviderOption;
import com.flowforge.infrastructure.config.PortalStrongAuthProperties;
import com.flowforge.infrastructure.config.PortalStrongAuthProperties.Claims;
import com.flowforge.infrastructure.config.PortalStrongAuthProperties.Keys;
import com.flowforge.infrastructure.config.PortalStrongAuthProperties.Provider;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TODO §103 (issue #210, D-BEAUTH-PORTAL-CLIENT, T4, T11) — client OIDC direct du portail contre un fournisseur en
 * mémoire : PKCE, assertion {@code private_key_jwt} signée par la clé de l'instance, ID token signé par le fournisseur
 * (et chiffré pour l'instance, comme itsme), contrôles de l'ID token, correspondance des claims.
 */
class OidcPortalIdentityProviderAdapterTest {

    private static final String CLIENT_ID = "portail-tenant_b";
    private static final String NRN = "85073003328";
    private static final String REDIRECT = "https://flowforge.example.be/tenant_b/api/portal/identity/callback";

    @TempDir
    Path dir;

    private HttpServer server;
    private String issuer;
    private RSAKey issuerKey;
    private RSAKey instanceSigningKey;
    private RSAKey instanceEncryptionKey;
    private final Map<String, String> challenges = new HashMap<>();
    private UnaryOperator<JWTClaimsSet.Builder> tamper = b -> b;
    private boolean encryptIdToken;
    private RSAKey idTokenSigner;
    private String lastAssertionAudience;
    private OidcPortalIdentityProviderAdapter adapter;

    @BeforeEach
    void startProvider() throws Exception {
        issuerKey = new RSAKeyGenerator(2048).keyID("issuer").generate();
        idTokenSigner = issuerKey;
        instanceSigningKey = new RSAKeyGenerator(2048).generate();
        instanceEncryptionKey = new RSAKeyGenerator(2048).generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/fas";
        server.createContext("/realms/fas/.well-known/openid-configuration", ex -> json(ex, 200, """
                {"issuer":"%1$s","authorization_endpoint":"%1$s/auth","token_endpoint":"%1$s/token","jwks_uri":"%1$s/certs"}
                """.formatted(issuer)));
        server.createContext("/realms/fas/certs", ex -> json(ex, 200, new JWKSet(issuerKey.toPublicJWK()).toString()));
        server.createContext("/realms/fas/token", this::token);
        server.start();
        adapter = adapter(false);
    }

    @AfterEach
    void stopProvider() {
        server.stop(0);
    }

    private OidcPortalIdentityProviderAdapter adapter(boolean withEncryptionKey) throws IOException {
        Path signing = dir.resolve("signing-" + UUID.randomUUID() + ".pem");
        Files.writeString(signing, PemKeys.toPkcs8Pem(instanceSigningKey));
        String encryption = null;
        if (withEncryptionKey) {
            Path enc = dir.resolve("enc-" + UUID.randomUUID() + ".pem");
            Files.writeString(enc, PemKeys.toPkcs8Pem(instanceEncryptionKey));
            encryption = enc.toString();
        }
        Provider fas = new Provider(null, issuer, CLIENT_ID, "openid profile", "eidas-substantial",
                new Claims("egovNRN", "givenName", "surname", "loa"),
                Map.of(IdentityAssuranceLevel.HIGH, List.of("high"), IdentityAssuranceLevel.SUBSTANTIAL, List.of("substantial")));
        var properties = new PortalStrongAuthProperties(true, null,
                new Keys(signing.toString(), "portal-1", null, null, encryption, encryption == null ? null : "portal-enc-1"),
                null, Map.of("fas", fas));
        return new OidcPortalIdentityProviderAdapter(properties, REDIRECT, PortalIdentityKeys.load(properties.keys()),
                Clock.systemUTC());
    }

    // --- fournisseur en mémoire -------------------------------------------------------------------------------------

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> form = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        try {
            SignedJWT assertion = SignedJWT.parse(form.get("client_assertion"));
            boolean signedByInstance = assertion.verify(new RSASSAVerifier(instanceSigningKey.toPublicJWK()));
            JWTClaimsSet claims = assertion.getJWTClaimsSet();
            lastAssertionAudience = claims.getAudience().getFirst();
            boolean assertionOk = signedByInstance
                    && "urn:ietf:params:oauth:client-assertion-type:jwt-bearer".equals(form.get("client_assertion_type"))
                    && CLIENT_ID.equals(claims.getIssuer()) && CLIENT_ID.equals(claims.getSubject())
                    && claims.getExpirationTime().after(new Date()) && claims.getJWTID() != null;
            String verifier = form.get("code_verifier");
            boolean pkceOk = verifier != null && s256(verifier).equals(challenges.get(form.get("code")));
            if (!assertionOk || !pkceOk || !REDIRECT.equals(form.get("redirect_uri"))
                    || !"authorization_code".equals(form.get("grant_type"))) {
                json(exchange, 400, "{\"error\":\"invalid_client\"}");
                return;
            }
            JWTClaimsSet.Builder idClaims = new JWTClaimsSet.Builder().issuer(issuer).audience(CLIENT_ID)
                    .subject("fas-sub").expirationTime(new Date(System.currentTimeMillis() + 60_000))
                    .issueTime(new Date()).claim("nonce", "nonce-1").claim("egovNRN", NRN)
                    .claim("givenName", "Alice").claim("surname", "Martin").claim("loa", "high");
            SignedJWT idToken = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("issuer").build(),
                    tamper.apply(idClaims).build());
            idToken.sign(new RSASSASigner(idTokenSigner));
            String serialized = idToken.serialize();
            if (encryptIdToken) {
                JWEObject jwe = new JWEObject(new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM)
                        .contentType("JWT").build(), new Payload(idToken));
                jwe.encrypt(new RSAEncrypter(instanceEncryptionKey.toPublicJWK()));
                serialized = jwe.serialize();
            }
            json(exchange, 200, "{\"access_token\":\"at\",\"token_type\":\"Bearer\",\"id_token\":\"" + serialized + "\"}");
        } catch (Exception ex) {
            json(exchange, 500, "{\"error\":\"server_error\"}");
        }
    }

    private AuthorizationRequest beginAndAuthorize(String code) {
        AuthorizationRequest request = adapter.begin("fas", "state-1", "nonce-1");
        challenges.put(code, query(request.authorizationUri()).get("code_challenge"));
        return request;
    }

    // --- tests ----------------------------------------------------------------------------------------------------

    @Test
    void theAuthorizationRequestUsesPkceTheStateTheNonceAndTheRegisteredRedirect() {
        AuthorizationRequest request = adapter.begin("fas", "state-1", "nonce-1");

        assertThat(request.authorizationUri().toString()).startsWith(issuer + "/auth?");
        Map<String, String> query = query(request.authorizationUri());
        assertThat(query).containsEntry("response_type", "code").containsEntry("client_id", CLIENT_ID)
                .containsEntry("redirect_uri", REDIRECT).containsEntry("scope", "openid profile")
                .containsEntry("state", "state-1").containsEntry("nonce", "nonce-1")
                .containsEntry("code_challenge_method", "S256").containsEntry("acr_values", "eidas-substantial");
        assertThat(query.get("code_challenge")).isEqualTo(s256(request.pkceVerifier()));
        assertThat(request.pkceVerifier()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(request.toString()).doesNotContain(request.pkceVerifier());
    }

    @Test
    void theCodeIsExchangedWithAPrivateKeyJwtAndTheIdTokenIsMapped() {
        AuthorizationRequest request = beginAndAuthorize("code-1");

        VerifiedIdentity identity = adapter.complete("fas", "code-1", request.pkceVerifier(), "nonce-1");

        assertThat(identity.provider()).isEqualTo("fas");
        assertThat(identity.level()).isEqualTo(IdentityAssuranceLevel.HIGH);
        assertThat(identity.givenName()).isEqualTo("Alice");
        assertThat(identity.familyName()).isEqualTo("Martin");
        assertThat(identity.nrn()).hasValueSatisfying(n -> assertThat(n.digits()).isEqualTo(NRN));
        assertThat(lastAssertionAudience).isEqualTo(issuer + "/token");
    }

    @Test
    void aWrongPkceVerifierIsRefusedByTheProvider() {
        beginAndAuthorize("code-1");
        assertThatThrownBy(() -> adapter.complete("fas", "code-1", "x".repeat(43), "nonce-1"))
                .isInstanceOf(IdentityProviderException.class);
    }

    @Test
    void theIdTokenIsCheckedForNonceIssuerAudienceExpiryAndSignature() throws Exception {
        record Case(String label, UnaryOperator<JWTClaimsSet.Builder> tamper) {}
        for (Case c : List.of(
                new Case("nonce", b -> b.claim("nonce", "autre")),
                new Case("émetteur", b -> b.issuer("https://autre.example")),
                new Case("audience", b -> b.audience("autre-client")),
                new Case("expiration", b -> b.expirationTime(new Date(System.currentTimeMillis() - 120_000))))) {
            tamper = c.tamper();
            AuthorizationRequest request = beginAndAuthorize("code-" + c.label());
            assertThatThrownBy(() -> adapter.complete("fas", "code-" + c.label(), request.pkceVerifier(), "nonce-1"))
                    .as(c.label()).isInstanceOf(IdentityProviderException.class);
        }
        tamper = b -> b;
        idTokenSigner = new RSAKeyGenerator(2048).keyID("issuer").generate();
        AuthorizationRequest request = beginAndAuthorize("code-signature");
        assertThatThrownBy(() -> adapter.complete("fas", "code-signature", request.pkceVerifier(), "nonce-1"))
                .as("signature d'une autre clé").isInstanceOf(IdentityProviderException.class);
    }

    @Test
    void missingOrUnknownClaimsGiveNoNumberAndALowLevel() {
        tamper = b -> b.claim("egovNRN", null).claim("loa", "inconnu");
        AuthorizationRequest request = beginAndAuthorize("code-1");

        VerifiedIdentity identity = adapter.complete("fas", "code-1", request.pkceVerifier(), "nonce-1");

        assertThat(identity.nrn()).isEmpty();
        assertThat(identity.level()).isEqualTo(IdentityAssuranceLevel.LOW);
    }

    /** itsme : ID token signé puis chiffré pour la clé de chiffrement de l'instance. */
    @Test
    void anEncryptedIdTokenIsDecryptedWithTheInstanceEncryptionKey() throws Exception {
        adapter = adapter(true);
        encryptIdToken = true;
        AuthorizationRequest request = beginAndAuthorize("code-1");

        assertThat(adapter.complete("fas", "code-1", request.pkceVerifier(), "nonce-1").nrn()).isPresent();

        OidcPortalIdentityProviderAdapter withoutKey = adapter(false);
        AuthorizationRequest again = withoutKey.begin("fas", "state-2", "nonce-1");
        challenges.put("code-2", query(again.authorizationUri()).get("code_challenge"));
        assertThatThrownBy(() -> withoutKey.complete("fas", "code-2", again.pkceVerifier(), "nonce-1"))
                .isInstanceOf(IdentityProviderException.class);
    }

    @Test
    void anUnknownProviderIsRefused() {
        assertThatThrownBy(() -> adapter.begin("google", "s", "n")).isInstanceOf(IdentityProviderException.class);
        assertThatThrownBy(() -> adapter.complete("google", "c", "v", "n")).isInstanceOf(IdentityProviderException.class);
    }

    @Test
    void providersAreListedWithTheirLabel() {
        assertThat(adapter.providers()).containsExactly(new ProviderOption("fas", "eID, itsme et autres (FAS)"));
    }

    /** T11 — la JWKS publiée ne contient que des clés publiques, signature et chiffrement. */
    @Test
    void thePublishedKeySetHoldsOnlyPublicKeys() throws Exception {
        PortalIdentityKeys keys = PortalIdentityKeys.load(new Keys(
                write("s.pem", instanceSigningKey), "portal-1", null, null, write("e.pem", instanceEncryptionKey), "enc-1"));

        JWKSet published = JWKSet.parse(keys.publicJwkSetJson());

        assertThat(published.getKeys()).hasSize(2).allSatisfy(k -> assertThat(k.isPrivate()).isFalse());
        assertThat(published.getKeyByKeyId("portal-1").getKeyUse().identifier()).isEqualTo("sig");
        assertThat(published.getKeyByKeyId("enc-1").getKeyUse().identifier()).isEqualTo("enc");
        assertThat(keys.publicJwkSetJson()).doesNotContain("\"d\"").doesNotContain("\"p\"");
    }

    @Test
    void aMissingOrInvalidKeyFileFailsAtStartup() throws Exception {
        assertThatThrownBy(() -> PortalIdentityKeys.load(new Keys(dir.resolve("absent.pem").toString(), null, null, null,
                null, null))).isInstanceOf(IllegalStateException.class);
        Path garbage = dir.resolve("garbage.pem");
        Files.writeString(garbage, "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n");
        assertThatThrownBy(() -> PortalIdentityKeys.load(new Keys(garbage.toString(), null, null, null, null, null)))
                .isInstanceOf(IllegalStateException.class);
        RSAKey small = new RSAKeyGenerator(1024, true).generate();
        assertThatThrownBy(() -> PortalIdentityKeys.load(new Keys(write("small.pem", small), null, null, null, null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("2048");
    }

    // --- utilitaires ----------------------------------------------------------------------------------------------

    private String write(String name, RSAKey key) throws IOException {
        Path path = dir.resolve(name);
        Files.writeString(path, PemKeys.toPkcs8Pem(key));
        return path.toString();
    }

    private static Map<String, String> query(URI uri) {
        return form(uri.getRawQuery());
    }

    private static Map<String, String> form(String encoded) {
        Map<String, String> values = new HashMap<>();
        for (String pair : encoded.split("&")) {
            int eq = pair.indexOf('=');
            values.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return values;
    }

    private static String s256(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
