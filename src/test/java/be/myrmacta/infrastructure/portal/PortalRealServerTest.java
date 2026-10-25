package be.myrmacta.infrastructure.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.port.out.AttachmentScanPort;
import be.myrmacta.domain.dossier.port.out.AttachmentScanResult;
import be.myrmacta.infrastructure.web.portal.FixedClockPortalRateLimiterConfig;
import be.myrmacta.support.OidcClientTestConfiguration;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Revue adversariale du portail (TODO §68, constats C1–C3) — sur un <strong>vrai Tomcat</strong> :
 * MockMvc n'exécute ni le dispatch d'erreur vers {@code /error}, ni le {@code ForwardedHeaderFilter}
 * tel qu'en production, ni le suivi de session par URL. Les trois constats n'y étaient pas visibles.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.portal.enabled=true",
        "app.portal.public-base-url=https://ff.example.org/tenant-a",
        "app.portal.send.poll-delay=1h",
        "app.portal.expiry.poll-delay=1h"
})
@Import({OidcClientTestConfiguration.class, FixedClockPortalRateLimiterConfig.class})
class PortalRealServerTest extends AbstractPortalEngineTest {

    private static final String TOKEN = "X-Portal-Token";

    @LocalServerPort
    private int port;

    @MockitoBean
    private AttachmentScanPort attachmentScanPort;

    @MockitoBean
    private DocumentStoragePort documentStoragePort;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private String token;

    @BeforeEach
    void setUp() {
        ensureSchema();
        when(attachmentScanPort.scan(any(), anyString())).thenReturn(new AttachmentScanResult(true, null));
        when(documentStoragePort.store(anyString(), anyString(), any())).thenReturn("tenant_dev/x.pdf");
        String recipient = "tiers-" + System.nanoTime() + "@example.org";
        UUID dossierId = dossier(Map.of("contactEmail", recipient)).id();
        startPortalSmoke(dossierId);
        token = sendAndCaptureToken(recipient);
    }

    private HttpRequest.Builder request(String path, String clientIp) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/tenant_dev" + path))
                .header("X-Forwarded-For", clientIp);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** C1 : ni redirection vers la connexion Keycloak (qui révèle l'application interne), ni session. */
    private static void assertNeutralError(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("JSESSIONID"));
        assertThat(response.body()).doesNotContain("oauth2").doesNotContain("jsessionid");
    }

    @Test
    void mappingErrorsUnderThePortal_answerTheirStatus_neverALoginRedirectNorASession() throws Exception {
        String ip = "203.0.113.10";
        assertNeutralError(send(request("/api/portal/task", ip).header(TOKEN, token).DELETE()), 405);
        assertNeutralError(send(request("/api/portal/inexistant", ip).GET()), 404);
        assertNeutralError(send(request("/api/portal/task/attachments", ip)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}"))), 415);
        // Rejet du StrictHttpFirewall (« ; » dans le chemin) : même traitement.
        assertNeutralError(send(request("/api/portal/task/submit;x=/api/portal/task/attachments", ip)
                .header(TOKEN, token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))), 400);
        // Même mécanisme hors portail : la chaîne publique des webhooks.
        assertNeutralError(send(request("/api/webhooks/inexistant", ip).GET()), 405);
    }

    @Test
    void malformedRequestsWithAResolvedHandler_answer4xx_notA500() throws Exception {
        String ip = "203.0.113.11";
        assertNeutralError(send(request("/api/portal/task/submit", ip).header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("x"))), 415);
        assertNeutralError(send(request("/api/portal/task/submit", ip).header(TOKEN, token)
                .header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString("x"))), 415);
        assertNeutralError(send(request("/api/portal/task/attachments", ip).header(TOKEN, token)
                .header("Content-Type", "multipart/form-data; boundary=XYZ")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "--XYZ\r\nContent-Disposition: form-data; name=\"file\"\r\n\r\ngarbage-without-end"))), 400);
    }

    @Test
    void rateLimitKey_ignoresAClientSuppliedForwardedHeader() throws Exception {
        // C2 : le proxy de confiance (ici le pair loopback) écrase X-Forwarded-For ; un en-tête
        // RFC 7239 « Forwarded » ajouté par le client ne doit pas changer la clé du limiteur.
        int limited = 0;
        for (int i = 0; i < 35; i++) {
            HttpResponse<String> response = send(request("/api/portal/task", "198.51.100.8")
                    .header("Forwarded", "for=192.0.2." + (i + 1)).header(TOKEN, "q".repeat(43)).GET());
            if (response.statusCode() == 429) {
                limited++;
            }
        }
        assertThat(limited).isEqualTo(5);
    }
}
