package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Client HTTP Yousign contre un serveur fictif (TODO §109, D-SIGNATURE-HTTP). */
class YousignHttpClientTest {

    private static final String API_KEY = "ys-secret-key-not-for-logs";
    private static final UUID REQUEST_ID = UUID.fromString("0ef334d2-7f36-4643-8a2a-4283b4a3e4a4");

    private HttpServer server;
    private final List<String> calls = new ArrayList<>();
    private final List<String> authorizations = new ArrayList<>();
    private final AtomicInteger elsewhereHits = new AtomicInteger();
    private HttpServer elsewhere;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            String query = exchange.getRequestURI().getRawQuery();
            byte[] raw = exchange.getRequestBody().readAllBytes();
            String body = new String(raw, StandardCharsets.UTF_8);
            authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            calls.add(exchange.getRequestMethod() + " " + path + (query == null ? "" : "?" + query) + " " + body);
            byte[] out = route(path, exchange.getRequestMethod()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(201, out.length);
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
    void createSendsDeliveryModeNoneAndTheBearerKey() {
        YousignHttpClient client = client();

        UUID id = client.createSignatureRequest("Contrat", true, "req-1", "2026-11-11");

        assertThat(id).isEqualTo(REQUEST_ID);
        assertThat(calls).singleElement().asString()
                .contains("POST /v3/signature_requests")
                .contains("\"delivery_mode\":\"none\"")
                .contains("\"ordered_signers\":true")
                .contains("\"external_id\":\"req-1\"")
                .contains("\"expiration_date\":\"2026-11-11\"");
        assertThat(authorizations).containsExactly("Bearer " + API_KEY);
    }

    /** #132 — la page et les messages Yousign du signataire suivent la langue du tenant. */
    @Test
    void addSignerInFrench() {
        client().addSigner(REQUEST_ID, "Ada", "Lovelace", "ada@example.org", Locale.FRENCH);

        assertThat(calls).singleElement().asString()
                .contains("POST /v3/signature_requests/" + REQUEST_ID + "/signers")
                .contains("\"locale\":\"fr\"");
    }

    @Test
    void addSignerInEnglish() {
        client().addSigner(REQUEST_ID, "Ada", "Lovelace", "ada@example.org", Locale.ENGLISH);

        assertThat(calls).singleElement().asString().contains("\"locale\":\"en\"");
    }

    /** Une variante régionale garde sa langue. */
    @Test
    void addSignerInBritishEnglish() {
        client().addSigner(REQUEST_ID, "Ada", "Lovelace", "ada@example.org", Locale.UK);

        assertThat(calls).singleElement().asString().contains("\"locale\":\"en\"");
    }

    /** Langue absente ou que Myrmacta ne propose pas : repli sur le français, la langue par défaut d'un tenant. */
    @Test
    void addSignerFallsBackToFrench() {
        client().addSigner(REQUEST_ID, "Ada", "Lovelace", "ada@example.org", Locale.JAPANESE);
        client().addSigner(REQUEST_ID, "Ada", "Lovelace", "ada@example.org", null);

        assertThat(calls).hasSize(2).allSatisfy(call -> assertThat(call).contains("\"locale\":\"fr\""));
    }

    @Test
    void uploadAsksForAnchorParsing() {
        client().uploadSignablePdf(REQUEST_ID, "contrat.pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));

        assertThat(calls).singleElement().asString()
                .contains("POST /v3/signature_requests/" + REQUEST_ID + "/documents")
                .contains("name=\"parse_anchors\"")
                .contains("name=\"nature\"")
                .contains("signable_document")
                .contains("%PDF-1.4");
    }

    /**
     * TODO §148 (lot 4, premier essai sur le bac à sable) — Yousign refuse un fichier sans extension autorisée (400,
     * « The extension of the file is invalid ») et un nom qui n'est pas de l'ASCII simple : le nom d'un document généré
     * (« Document généré à Joindre la convention au dossier ») n'en a pas.
     */
    @Test
    void uploadSendsAnAsciiPdfFileName_evenForAGeneratedDocumentLabel() {
        client().uploadSignablePdf(REQUEST_ID, "Document généré à Joindre la convention au dossier",
                "%PDF-1.4".getBytes(StandardCharsets.UTF_8));

        assertThat(calls).singleElement().asString()
                .contains("filename=\"Document genere a Joindre la convention au dossier.pdf\"");
    }

    @Test
    void uploadKeepsAnExistingPdfExtension_andReplacesCharactersYousignWouldReject() {
        client().uploadSignablePdf(REQUEST_ID, "Contrat n°12 / “final”.PDF", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));

        assertThat(calls).singleElement().asString().contains("filename=\"Contrat n-12 - -final-.PDF\"");
    }

    /** Revue du lot 4 : l'extension survit à la coupe d'un nom long, et un nom sans base devient « document ». */
    @Test
    void pdfFileName_keepsItsExtensionWhenClipped_andNeverHasAnEmptyBase() {
        String clipped = YousignHttpClient.pdfFileName("a".repeat(300) + ".pdf");
        assertThat(clipped).hasSize(255).endsWith("a.pdf");
        assertThat(YousignHttpClient.pdfFileName(".pdf")).isEqualTo("document.pdf");
        assertThat(YousignHttpClient.pdfFileName("  .PDF ")).isEqualTo("document.PDF");
        assertThat(YousignHttpClient.pdfFileName(null)).isEqualTo("document.pdf");
        assertThat(YousignHttpClient.pdfFileName("   ")).isEqualTo("document.pdf");
    }

    /**
     * #133 — l'API v3 n'annule (POST /cancel) qu'une demande en approbation ou en cours ; un brouillon se supprime
     * (DELETE, 204 sans corps).
     */
    @Test
    void deleteDraftSendsADeleteOnTheRequest_andAcceptsAnEmptyNoContentAnswer() {
        server.removeContext("/");
        server.createContext("/", exchange -> {
            calls.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        client().deleteDraft(REQUEST_ID);

        assertThat(calls).containsExactly("DELETE /v3/signature_requests/" + REQUEST_ID);
    }

    @Test
    void deleteDraftRefusedByYousignFailsWithoutTheKey() {
        server.removeContext("/");
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> client().deleteDraft(REQUEST_ID))
                .isInstanceOf(YousignCallException.class)
                .hasMessageContaining("405")
                .hasMessageNotContaining(API_KEY);
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
        server.stop(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + elsewhere.getAddress().getPort() + "/stolen");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.start();

        assertThatThrownBy(() -> client().createSignatureRequest("Contrat", false, "req-1", null))
                .isInstanceOf(YousignCallException.class)
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining("stolen");
        assertThat(elsewhereHits).hasValue(0);
    }

    @Test
    void signingLinkOnAForeignHostIsRejectedWithoutEchoingTheLink() {
        server.removeContext("/");
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            String json = path.endsWith("/signers")
                    ? "[{\"id\":\"11111111-1111-1111-1111-111111111111\"}]"
                    : "{\"signature_link\":\"https://evil.example/sign?token=" + API_KEY + "\"}";
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });

        assertThatThrownBy(() -> client().signatureLink(REQUEST_ID, 0))
                .isInstanceOf(YousignCallException.class)
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining("evil.example");
    }

    @Test
    void signingLinkOnAYousignHostIsReturned() {
        server.removeContext("/");
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            String json = path.endsWith("/signers")
                    ? "[{\"id\":\"11111111-1111-1111-1111-111111111111\"}]"
                    : "{\"signature_link\":\"https://yousign.app/signatures/abc\"}";
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });

        assertThat(client().signatureLink(REQUEST_ID, 0)).contains("https://yousign.app/signatures/abc");
    }

    private YousignHttpClient client() {
        return new YousignHttpClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3"), API_KEY);
    }

    private static String route(String path, String method) {
        if (path.endsWith("/signature_requests") && "POST".equals(method)) {
            return "{\"id\":\"" + REQUEST_ID + "\"}";
        }
        if (path.endsWith("/documents") && "POST".equals(method)) {
            return "{\"id\":\"22222222-2222-2222-2222-222222222222\",\"total_anchors\":1}";
        }
        if (path.endsWith("/signers") && "POST".equals(method)) {
            return "{\"id\":\"33333333-3333-3333-3333-333333333333\"}";
        }
        return "{}";
    }
}
