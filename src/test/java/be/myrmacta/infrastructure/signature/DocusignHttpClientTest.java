package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Client HTTP DocuSign contre un serveur fictif (TODO §109, D-SIGNATURE-HTTP). */
class DocusignHttpClientTest {

    private static final String INTEGRATION_KEY = "integration-key-secret";
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final UUID ENVELOPE_ID = UUID.fromString("0ef334d2-7f36-4643-8a2a-4283b4a3e4a4");
    private static final String ACCOUNT_ID = "22222222-2222-2222-2222-222222222222";
    private static KeyPair keys;
    private static String pem;

    private HttpServer server;
    private final List<String> calls = new ArrayList<>();
    private final AtomicInteger elsewhereHits = new AtomicInteger();
    private HttpServer elsewhere;
    private int mode;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            byte[] raw = exchange.getRequestBody().readAllBytes();
            String body = new String(raw, StandardCharsets.UTF_8);
            calls.add(exchange.getRequestMethod() + " " + path + " " + body);
            byte[] out = response(path, exchange.getRequestMethod(), exchange.getLocalAddress().getPort());
            int status = status(path, exchange.getRequestMethod());
            if (status >= 300 && status < 400) {
                exchange.getResponseHeaders().add("Location",
                        "http://127.0.0.1:" + elsewhere.getAddress().getPort() + "/stolen-" + INTEGRATION_KEY);
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
                return;
            }
            String type = path.contains("/documents/") ? "application/pdf" : "application/json";
            exchange.getResponseHeaders().add("content-type", type);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (elsewhere != null) {
            elsewhere.stop(0);
        }
    }

    @Test
    void onlySandboxAndProductionOAuthHostsAreAccepted() {
        assertThat(DocusignHttpClient.oauthFor("sandbox")).isEqualTo(DocusignHttpClient.SANDBOX_OAUTH);
        assertThat(DocusignHttpClient.oauthFor("production")).isEqualTo(DocusignHttpClient.PRODUCTION_OAUTH);
        assertThatThrownBy(() -> DocusignHttpClient.oauthFor("https://evil.example"))
                .isInstanceOf(DocusignCallException.class);
        assertThat(DocusignHttpClient.accountBase("sandbox", "https://demo.docusign.net", false).getHost())
                .isEqualTo("demo.docusign.net");
        assertThat(DocusignHttpClient.accountBase("production", "https://eu.docusign.net", false).getHost())
                .isEqualTo("eu.docusign.net");
        assertThatThrownBy(() -> DocusignHttpClient.accountBase("production", "https://demo.docusign.net", false))
                .isInstanceOf(DocusignCallException.class);
        assertThatThrownBy(() -> DocusignHttpClient.accountBase("sandbox", "https://evil.example", false))
                .isInstanceOf(DocusignCallException.class);
        assertThatThrownBy(() -> DocusignHttpClient.accountBase("sandbox", "http://127.0.0.1:9", false))
                .isInstanceOf(DocusignCallException.class);
    }

    @Test
    void createSendsTheAnchorTheClientUserAndASignedJwt() throws Exception {
        DocusignHttpClient client = client();

        UUID id = client.createEnvelope("Contrat", true, "req-1", Instant.parse("2026-11-11T00:00:00Z"),
                List.of(new DocusignClient.SignerSpec("Ada Lovelace", "a@example.org", 0)), "%PDF".getBytes(StandardCharsets.UTF_8));

        assertThat(id).isEqualTo(ENVELOPE_ID);
        String envelope = calls.stream().filter(call -> call.startsWith("POST /restapi")).findFirst().orElseThrow();
        assertThat(envelope)
                .contains("\"status\":\"sent\"")
                .contains("\"clientUserId\":\"1\"")
                .contains("\"routingOrder\":\"1\"")
                .contains("{{s1|signature|85|37}}")
                .contains("\"anchorIgnoreIfNotPresent\":\"false\"")
                .contains("\"myrmactaRequestId\"")
                .contains("JVBER")
                .doesNotContain("secret-token");
        String tokenCall = calls.stream().filter(call -> call.startsWith("POST /oauth/token")).findFirst().orElseThrow();
        String assertion = URLDecoder.decode(tokenCall.substring(tokenCall.indexOf("assertion=") + "assertion=".length()), StandardCharsets.UTF_8);
        String[] parts = assertion.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"iss\":\"" + INTEGRATION_KEY + "\"").contains("\"aud\":\"127.0.0.1\"")
                .contains("signature impersonation");
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(keys.getPublic());
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(signature.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    void aSecondCallReusesTheAccessToken() {
        DocusignHttpClient client = client();
        client.createEnvelope("Contrat", false, null, null,
                List.of(new DocusignClient.SignerSpec("Ada", "a@example.org", 0)), new byte[] {'%'});
        client.downloadSignedPdf(ENVELOPE_ID);

        assertThat(calls.stream().filter(call -> call.startsWith("POST /oauth/token"))).hasSize(1);
        assertThat(calls.stream().filter(call -> call.contains("/documents/1"))).hasSize(1);
    }

    @Test
    void missingAnchorDoesNotVoidAndAForeignSigningLinkIsNotEchoed() {
        mode = 1;
        assertThatThrownBy(() -> client().createEnvelope("Contrat", false, null, null,
                List.of(new DocusignClient.SignerSpec("Ada", "a@example.org", 0)), new byte[] {'%'}))
                .isInstanceOf(DocusignAnchorMissingException.class)
                .hasMessageContaining("anchor")
                .hasMessageNotContaining(INTEGRATION_KEY);
        assertThat(calls.stream().noneMatch(call -> call.startsWith("PUT"))).isTrue();

        calls.clear();
        mode = 2;
        assertThatThrownBy(() -> client().recipientView(ENVELOPE_ID, 0, "https://tenant.example/portal/"))
                .isInstanceOf(DocusignCallException.class)
                .hasMessageNotContaining("secret-token")
                .hasMessageNotContaining(INTEGRATION_KEY);
    }

    @Test
    void recipientNotInSequenceReturnsNoLinkAndTheReturnUrlCarriesNoPortalToken() {
        mode = 3;
        assertThat(client().recipientView(ENVELOPE_ID, 0, "https://tenant.example/portal/")).isEmpty();

        calls.clear();
        mode = 0;
        assertThat(client().recipientView(ENVELOPE_ID, 0, "https://tenant.example/portal/"))
                .contains("https://demo.docusign.net/Signing/abc");
        String view = calls.stream().filter(call -> call.contains("/views/recipient")).findFirst().orElseThrow();
        assertThat(view).contains("https://tenant.example/portal/").doesNotContain("secret-token").doesNotContain("#");
    }

    @Test
    void redirectIsNotFollowedAndTheKeyStaysOutOfTheError() throws IOException {
        elsewhere = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        elsewhere.createContext("/", exchange -> {
            elsewhereHits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        elsewhere.start();
        mode = 4;

        assertThatThrownBy(() -> client().createEnvelope("Contrat", false, null, null,
                List.of(new DocusignClient.SignerSpec("Ada", "a@example.org", 0)), new byte[] {'%'}))
                .isInstanceOf(DocusignCallException.class)
                .hasMessageNotContaining(INTEGRATION_KEY)
                .hasMessageNotContaining("stolen");
        assertThat(elsewhereHits).hasValue(0);
    }

    @Test
    void pkcs1PemSignsLikeThePkcs8Key() throws Exception {
        byte[] pkcs1 = pkcs1(keys.getPrivate().getEncoded());
        String rsaPem = "-----BEGIN RSA PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(pkcs1)
                + "\n-----END RSA PRIVATE KEY-----\n";
        byte[] message = "docusign".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(RsaPrivateKeys.parse(rsaPem));
        signer.update(message);
        byte[] signed = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keys.getPublic());
        verifier.update(message);
        assertThat(verifier.verify(signed)).isTrue();
    }

    private DocusignHttpClient client() {
        URI oauth = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new DocusignHttpClient(
                HttpClient(),
                oauth,
                "sandbox",
                INTEGRATION_KEY,
                USER_ID,
                pem,
                ACCOUNT_ID,
                Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC),
                true,
                new DocusignHttpClient.DocusignTokenCache());
    }

    private static java.net.http.HttpClient HttpClient() {
        return java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build();
    }

    private int status(String path, String method) {
        if (mode == 4 && "POST".equals(method) && "/oauth/token".equals(path)) {
            return 307;
        }
        if (mode == 1 && "POST".equals(method) && path.endsWith("/envelopes")) {
            return 400;
        }
        if (mode == 2 && path.endsWith("/views/recipient")) {
            return 201;
        }
        if (mode == 3 && path.endsWith("/views/recipient")) {
            return 400;
        }
        return 200;
    }

    private byte[] response(String path, String method, int port) {
        if ("/oauth/token".equals(path)) {
            return "{\"access_token\":\"access-token-value\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
        }
        if ("/oauth/userinfo".equals(path)) {
            return ("{\"accounts\":[{\"account_id\":\"" + ACCOUNT_ID + "\",\"is_default\":true,\"base_uri\":\"http://127.0.0.1:"
                    + port + "\"}]}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        if (path.endsWith("/views/recipient")) {
            if (mode == 3) {
                return "{\"errorCode\":\"RECIPIENT_NOT_IN_SEQUENCE\",\"message\":\"secret-token\"}".getBytes(StandardCharsets.UTF_8);
            }
            if (mode == 2) {
                return ("{\"url\":\"https://evil.example/sign?token=secret-token\"}").getBytes(StandardCharsets.UTF_8);
            }
            return "{\"url\":\"https://demo.docusign.net/Signing/abc\"}".getBytes(StandardCharsets.UTF_8);
        }
        if ("POST".equals(method) && path.endsWith("/envelopes")) {
            if (mode == 1) {
                return "{\"errorCode\":\"ANCHOR_TAB_STRING_NOT_FOUND\",\"message\":\"secret-token\"}".getBytes(StandardCharsets.UTF_8);
            }
            return ("{\"envelopeId\":\"" + ENVELOPE_ID + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        if (path.endsWith("/documents/1") || path.endsWith("/certificate")) {
            return "%PDF-signed".getBytes(StandardCharsets.UTF_8);
        }
        if (path.endsWith("/recipients")) {
            return "{\"signers\":[{\"recipientId\":\"1\",\"clientUserId\":\"1\",\"name\":\"Ada Lovelace\",\"email\":\"a@example.org\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
        }
        return "{}".getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] pkcs1(byte[] pkcs8) {
        int offset = 1 + lengthBytes(pkcs8, 1);
        offset = skip(pkcs8, offset);
        offset = skip(pkcs8, offset);
        if (pkcs8[offset] != 0x04) {
            throw new IllegalStateException("octet string");
        }
        int header = 1 + lengthBytes(pkcs8, offset + 1);
        int length = lengthValue(pkcs8, offset + 1);
        return java.util.Arrays.copyOfRange(pkcs8, offset + header, offset + header + length);
    }

    private static int skip(byte[] der, int offset) {
        int header = 1 + lengthBytes(der, offset + 1);
        return offset + header + lengthValue(der, offset + 1);
    }

    private static int lengthBytes(byte[] der, int lengthOffset) {
        int first = der[lengthOffset] & 0xff;
        if (first < 0x80) {
            return 1;
        }
        return 1 + (first & 0x7f);
    }

    private static int lengthValue(byte[] der, int lengthOffset) {
        int first = der[lengthOffset] & 0xff;
        if (first < 0x80) {
            return first;
        }
        int count = first & 0x7f;
        int value = 0;
        for (int i = 0; i < count; i++) {
            value = (value << 8) | (der[lengthOffset + 1 + i] & 0xff);
        }
        return value;
    }
}
