package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.infrastructure.web.portal.FixedClockPortalRateLimiterConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * T14.3 — revue adversariale de l'API publique de signature (TODO §72) sur un <strong>vrai
 * Tomcat</strong>, sur le modèle de {@code PortalRealServerTest} (§68, C1–C3) : MockMvc n'exécute ni le
 * dispatch d'erreur vers {@code /error} ni le {@code ForwardedHeaderFilter}. On vérifie qu'aucune
 * réponse ne révèle l'application interne (redirection de connexion, session), que tout refus est
 * indiscernable, que le jeton n'est accepté que dans l'en-tête et que la limite par IP s'applique.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.signature.provider=stub",
        "app.signature.send.poll-delay=1h",
        "app.signature.expiry.poll-delay=1h",
        "app.signature.dispatch.poll-delay=1h",
        "app.portal.enabled=true",
        "app.portal.public-base-url=https://ff.example.org/tenant-a",
        "app.portal.send.poll-delay=1h",
        "app.portal.expiry.poll-delay=1h"
})
@Import(FixedClockPortalRateLimiterConfig.class)
class SignaturePortalRealServerTest extends AbstractSignatureEngineTest {

    private static final String TOKEN = "X-Portal-Token";
    private static final String BASE = "/api/portal/signature";

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private String tokenA;
    private String mailA;

    @BeforeEach
    void setUp() {
        mailA = "client-" + System.nanoTime() + "@example.org";
        String mailB = "garant-" + System.nanoTime() + "@example.org";
        UUID dossierId = dossier(Map.of("signerA", mailA, "signerB", mailB)).id();
        startSignatureSmoke(dossierId, producedDoc(dossierId));
        tokenA = sendAndCaptureToken(mailA);
    }

    private HttpRequest.Builder request(String path, String clientIp) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/tenant_dev" + path))
                .header("X-Forwarded-For", clientIp);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Ni redirection vers la connexion Keycloak, ni session, ni indice sur l'application interne. */
    private static void assertNeutral(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("JSESSIONID"));
        assertThat(response.body()).doesNotContain("oauth2").doesNotContain("jsessionid");
    }

    @Test
    void mappingAndFirewallErrorsAnswerTheirStatusNeverALoginRedirectNorASession() throws Exception {
        String ip = "203.0.113.20";
        assertNeutral(send(request(BASE, ip).header(TOKEN, tokenA).DELETE()), 405);
        assertNeutral(send(request(BASE + "/inexistant", ip).GET()), 404);
        assertNeutral(send(request(BASE + "/decline", ip).header(TOKEN, tokenA)
                .header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString("x"))), 415);
        assertNeutral(send(request(BASE + "/decline", ip).header(TOKEN, tokenA)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{oops"))), 400);
        assertNeutral(send(request(BASE + "/sign;x=/api/portal/signature/decline", ip).header(TOKEN, tokenA)
                .POST(HttpRequest.BodyPublishers.noBody())), 400);
    }

    @Test
    void everyDenialIsIndistinguishableAndTheTokenIsOnlyAcceptedInTheHeader() throws Exception {
        String ip = "203.0.113.21";
        HttpResponse<String> unknown = send(request(BASE, ip).header(TOKEN, "u".repeat(43)).GET());
        HttpResponse<String> missing = send(request(BASE, ip).GET());
        HttpResponse<String> inQuery = send(request(BASE + "?token=" + tokenA, ip).GET());
        HttpResponse<String> malformed = send(request(BASE, ip).header(TOKEN, "not a token").GET());
        assertNeutral(unknown, 404);
        for (HttpResponse<String> other : new HttpResponse[] {missing, inQuery, malformed}) {
            assertNeutral(other, 404);
            assertThat(other.body()).isEqualTo(unknown.body());
        }

        // Après signature, le même jeton est refusé exactement comme un jeton inconnu.
        assertThat(send(request(BASE + "/sign", ip).header(TOKEN, tokenA).POST(HttpRequest.BodyPublishers.noBody()))
                        .statusCode())
                .isEqualTo(200);
        HttpResponse<String> reused = send(request(BASE, ip).header(TOKEN, tokenA).GET());
        assertNeutral(reused, 404);
        assertThat(reused.body()).isEqualTo(unknown.body());
        HttpResponse<String> twice = send(request(BASE + "/sign", ip).header(TOKEN, tokenA)
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertNeutral(twice, 404);
        assertThat(twice.body()).isEqualTo(unknown.body());
    }

    @Test
    void oversizedDeclineBodyIsRefusedWithA4xxAndChangesNothing() throws Exception {
        String reason = "x".repeat(300_000);
        HttpResponse<String> response = send(request(BASE + "/decline", "203.0.113.23").header(TOKEN, tokenA)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"reason\":\"" + reason + "\"}")));
        assertThat(response.statusCode()).isBetween(400, 499);
        assertNeutral(response, response.statusCode());
        // Le jeton reste utilisable : rien n'a été enregistré.
        assertThat(send(request(BASE, "203.0.113.23").header(TOKEN, tokenA).GET()).statusCode()).isEqualTo(200);
    }

    @Test
    void documentIsServedWithHardenedHeadersAndNoSession() throws Exception {
        HttpResponse<byte[]> response = http.send(
                request(BASE + "/document", "203.0.113.22").header(TOKEN, tokenA).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/pdf");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(new String(response.body(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void rateLimitKeyIgnoresAClientSuppliedForwardedHeader() throws Exception {
        int limited = 0;
        for (int i = 0; i < 35; i++) {
            HttpResponse<String> response = send(request(BASE, "198.51.100.30")
                    .header("Forwarded", "for=192.0.2." + (i + 1)).header(TOKEN, "q".repeat(43)).GET());
            if (response.statusCode() == 429) {
                limited++;
            }
        }
        assertThat(limited).isEqualTo(5);
    }
}
