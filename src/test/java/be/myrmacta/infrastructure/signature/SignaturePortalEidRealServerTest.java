package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import be.myrmacta.infrastructure.web.portal.FixedClockPortalRateLimiterConfig;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * TODO §138 — le téléversement d'un PDF signé sur un <strong>vrai Tomcat</strong> (modèle de
 * {@link SignaturePortalRealServerTest}) : le multipart n'est pas lu sans jeton vivant, la limite globale
 * {@code spring.servlet.multipart} répond 413 par le même filet du portail, et aucune réponse ne révèle
 * l'application interne (pas de redirection de connexion, pas de session).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.signature.provider=eid",
        "app.signature.eid.upload-enabled=true",
        "app.signature.eid.trust-anchors-file=src/test/resources/eid/test-anchors.pem",
        "app.signature.send.poll-delay=1h",
        "app.signature.expiry.poll-delay=1h",
        "app.signature.dispatch.poll-delay=1h",
        "app.portal.enabled=true",
        "app.portal.public-base-url=https://ff.example.org/tenant-a",
        "app.portal.send.poll-delay=1h",
        "app.portal.expiry.poll-delay=1h",
        "spring.servlet.multipart.max-file-size=100KB",
        "spring.servlet.multipart.max-request-size=100KB"
})
@ActiveProfiles("test")
@Import({OidcClientTestConfiguration.class, AbstractEidSignatureEngineTest.FakeVerifierConfig.class,
        FixedClockPortalRateLimiterConfig.class})
class SignaturePortalEidRealServerTest extends AbstractSignatureEngineTest {

    private static final String TOKEN = "X-Portal-Token";
    private static final String UPLOAD = "/api/portal/signature/signed-document";
    private static final String BOUNDARY = "----myrmacta-test-boundary";

    @LocalServerPort
    private int port;

    @Autowired
    private SignedUploadRepository uploads;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private String tokenA;
    private SignatureRequest request;
    private byte[] source;

    @BeforeEach
    void setUp() {
        FakeSignedPdfVerifier.reset();
        String mailA = "client-" + System.nanoTime() + "@example.org";
        UUID dossierId = dossier(Map.of("signerA", mailA, "signerB", "garant-" + System.nanoTime() + "@example.org")).id();
        startSignatureSmoke(dossierId, producedDoc(dossierId));
        tokenA = sendAndCaptureToken(mailA);
        request = signatureRepository.findByDossierId(dossierId).getFirst();
        source = storage.retrieve(TENANT, request.sourceStorageRef());
    }

    private HttpResponse<String> upload(String token, byte[] pdf, String clientIp) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"signe.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(pdf);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/tenant_dev" + UPLOAD))
                .header("X-Forwarded-For", clientIp)
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (token != null) {
            builder.header(TOKEN, token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertNeutral(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(response.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("JSESSIONID"));
        assertThat(response.body()).doesNotContain("oauth2").doesNotContain("jsessionid");
    }

    @Test
    void aValidUploadIsAcceptedOnTheRealServer() throws Exception {
        HttpResponse<String> response = upload(tokenA, FakeSignedPdfVerifier.signed(source, "Client Martin"), "203.0.113.40");

        assertNeutral(response, 200);
        assertThat(response.body()).contains("\"status\":\"SIGNED\"");
        assertThat(uploads.findByRequestId(request.id())).hasSize(1);
    }

    @Test
    void aPdfBiggerThanThePortalJsonBodyLimitIsNotCutByTheBodySizeFilter() throws Exception {
        // la limite de corps du portail (64 Kio) ne vise que le JSON : le PDF signé n'est borné que par le multipart
        byte[] big = FakeSignedPdfVerifier.signed(source, "Client " + "x".repeat(80_000));

        HttpResponse<String> response = upload(tokenA, big, "203.0.113.44");

        assertNeutral(response, 200);
        assertThat(uploads.findByRequestId(request.id())).hasSize(1);
    }

    @Test
    void anOversizedBodyAnswers413ThroughThePortalSafetyNetOnlyWithALiveToken() throws Exception {
        byte[] huge = new byte[300_000];

        HttpResponse<String> withToken = upload(tokenA, huge, "203.0.113.41");
        assertNeutral(withToken, 413);
        assertThat(withToken.body()).doesNotContain("Exception");

        // sans jeton vivant, le corps n'est pas lu : refus uniforme, pas 413
        HttpResponse<String> unknown = upload("u".repeat(43), huge, "203.0.113.42");
        HttpResponse<String> missing = upload(null, huge, "203.0.113.42");
        assertNeutral(unknown, 404);
        assertNeutral(missing, 404);
        assertThat(missing.body()).isEqualTo(unknown.body());
        assertThat(signatureRepository.findById(request.id()).orElseThrow().status()).isEqualTo(SignatureStatus.PENDING);
    }

    @Test
    void aRefusedFileAnswers422WithItsCodeAndNoSessionOnTheRealServer() throws Exception {
        HttpResponse<String> response = upload(tokenA, "%PDF-not-an-extension".getBytes(), "203.0.113.43");

        assertNeutral(response, 422);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(response.body()).contains("\"code\":\"DOCUMENT_MISMATCH\"");
    }
}
