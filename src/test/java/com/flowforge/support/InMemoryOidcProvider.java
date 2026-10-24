package com.flowforge.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
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
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO §103 — fournisseur OIDC en mémoire (FAS ou itsme simulé) pour les tests d'intégration du portail : découverte,
 * JWKS, endpoint de jeton qui exige l'assertion {@code private_key_jwt} signée par la clé de l'instance et le
 * vérificateur PKCE, puis émet un ID token signé portant les claims enregistrés pour le code.
 */
public final class InMemoryOidcProvider implements AutoCloseable {

    public static final String CLIENT_ID = "portail-test";

    private final HttpServer server;
    private final String issuer;
    private final RSAKey issuerKey;
    private final RSAKey instanceKey;
    private final Map<String, Grant> grants = new ConcurrentHashMap<>();

    /** Ce que le fournisseur renverra pour un code : claims de l'ID token, et la requête d'autorisation reçue. */
    private record Grant(Map<String, Object> claims, String nonce, String challenge) {}

    public InMemoryOidcProvider() {
        try {
            issuerKey = new RSAKeyGenerator(2048).keyID("issuer").generate();
            instanceKey = new RSAKeyGenerator(2048).keyID("instance").generate();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/fas";
        server.createContext("/realms/fas/.well-known/openid-configuration", ex -> json(ex, 200, """
                {"issuer":"%1$s","authorization_endpoint":"%1$s/auth","token_endpoint":"%1$s/token","jwks_uri":"%1$s/certs"}
                """.formatted(issuer)));
        server.createContext("/realms/fas/certs", ex -> json(ex, 200, new JWKSet(issuerKey.toPublicJWK()).toString()));
        server.createContext("/realms/fas/token", this::token);
        server.start();
    }

    public String issuer() {
        return issuer;
    }

    /** Clé privée de l'instance (PEM PKCS#8), à donner à {@code app.portal.strong-auth.keys.signing-key-file}. */
    public Path writeInstanceKey(Path dir) {
        try {
            byte[] der = instanceKey.toRSAPrivateKey().getEncoded();
            Path file = Files.createTempFile(dir, "portal-signing", ".pem");
            Files.writeString(file, "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der) + "\n-----END PRIVATE KEY-----\n");
            return file;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Simule l'authentification du tiers sur la page du fournisseur : lit la requête d'autorisation, enregistre ce que
     * l'ID token portera, et renvoie {@code state} et {@code code} comme le ferait la redirection vers l'instance.
     */
    public Map<String, String> authorize(URI authorizationRequest, String code, Map<String, Object> identityClaims) {
        Map<String, String> query = form(authorizationRequest.getRawQuery());
        grants.put(code, new Grant(identityClaims, query.get("nonce"), query.get("code_challenge")));
        return Map.of("state", query.get("state"), "code", code);
    }

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> form = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Grant grant = grants.remove(form.getOrDefault("code", ""));
        try {
            SignedJWT assertion = SignedJWT.parse(form.get("client_assertion"));
            boolean assertionOk = assertion.verify(new RSASSAVerifier(instanceKey.toPublicJWK()))
                    && CLIENT_ID.equals(assertion.getJWTClaimsSet().getIssuer());
            if (grant == null || !assertionOk || !s256(form.get("code_verifier")).equals(grant.challenge())) {
                json(exchange, 400, "{\"error\":\"invalid_grant\"}");
                return;
            }
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer(issuer).audience(CLIENT_ID).subject("sub")
                    .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 60_000))
                    .claim("nonce", grant.nonce());
            grant.claims().forEach(claims::claim);
            SignedJWT idToken = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("issuer").build(),
                    claims.build());
            idToken.sign(new RSASSASigner(issuerKey));
            json(exchange, 200, "{\"access_token\":\"at\",\"token_type\":\"Bearer\",\"id_token\":\"" + idToken.serialize()
                    + "\"}");
        } catch (Exception ex) {
            json(exchange, 500, "{\"error\":\"server_error\"}");
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static Map<String, String> form(String encoded) {
        Map<String, String> values = new HashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return values;
        }
        for (String pair : encoded.split("&")) {
            int eq = pair.indexOf('=');
            values.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return values;
    }

    private static String s256(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest((verifier == null ? "" : verifier).getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void json(HttpExchange exchange, int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
