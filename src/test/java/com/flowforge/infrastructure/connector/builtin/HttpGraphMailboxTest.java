package com.flowforge.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** TODO §87 (D3c-bis) — accès Graph : serveur local qui rejoue l'API, aucun service réel. */
class HttpGraphMailboxTest {

    private static final String TOKEN = "tok-SECRET";
    private static final ImapMailbox.Limits LIMITS = new ImapMailbox.Limits(1_000_000, 1000, 100, 2);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    private final List<String> auth = Collections.synchronizedList(new ArrayList<>());
    private HttpServer server;
    private HttpServer elsewhere;
    private String messages = "{\"value\":[]}";
    private String attachmentList = "{\"value\":[]}";
    private int attachmentStatus = 200;
    private int moveStatus = 200;
    private int patchStatus = 200;
    private final List<String> rawPaths = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean stall;
    private int listStatus = 200;
    private String redirectTo;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (elsewhere != null) {
            elsewhere.stop(0);
        }
    }

    private void reply(HttpExchange ex, int status, String body) throws IOException {
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("content-type", "application/json");
        ex.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
        if (out.length > 0) {
            ex.getResponseBody().write(out);
        }
        ex.close();
    }

    private GraphMailbox.Settings start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String path = URLDecoder.decode(ex.getRequestURI().getRawPath(), StandardCharsets.UTF_8);
            String query = ex.getRequestURI().getRawQuery() == null ? "" : URLDecoder.decode(ex.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            auth.add(String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
            rawPaths.add(ex.getRequestURI().getRawPath());
            calls.add(ex.getRequestMethod() + " " + path + (query.isEmpty() ? "" : "?" + query) + (body.isEmpty() ? "" : " " + body));
            if (stall) {
                ex.sendResponseHeaders(200, 100); // en-têtes envoyés, corps jamais terminé
                ex.getResponseBody().flush();
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                ex.close();
            } else if (redirectTo != null) {
                ex.getResponseHeaders().add("Location", redirectTo);
                ex.sendResponseHeaders(307, -1);
                ex.close();
            } else if (path.endsWith("/mailFolders") && ex.getRequestMethod().equals("GET")) {
                boolean known = query.contains("FlowForge-processed");
                reply(ex, 200, known ? "{\"value\":[{\"id\":\"F-PROCESSED\"}]}" : "{\"value\":[]}");
            } else if (path.endsWith("/mailFolders") && ex.getRequestMethod().equals("POST")) {
                reply(ex, 201, "{\"id\":\"F-REJECTED\"}");
            } else if (path.endsWith("/messages") && ex.getRequestMethod().equals("GET")) {
                reply(ex, listStatus, messages);
            } else if (ex.getRequestMethod().equals("GET") && path.matches(".*/messages/[^/]+")) {
                String wanted = path.substring(path.lastIndexOf('/') + 1);
                String found = "{}";
                for (JsonNode m : mapper.readTree(messages).path("value")) {
                    if (wanted.equals(m.path("id").asString(""))) {
                        found = m.toString();
                    }
                }
                reply(ex, 200, found);
            } else if (ex.getRequestMethod().equals("PATCH")) {
                reply(ex, patchStatus, "{}");
            } else if (path.endsWith("/attachments") && ex.getRequestMethod().equals("GET")) {
                reply(ex, attachmentStatus, attachmentList);
            } else if (path.endsWith("/$value")) {
                reply(ex, 200, "PDFDATA");
            } else if (path.endsWith("/move")) {
                reply(ex, moveStatus, "{}");
            } else {
                reply(ex, 200, "{}");
            }
        });
        server.start();
        return new GraphMailbox.Settings("http://127.0.0.1:" + server.getAddress().getPort() + "/v1.0", "dossiers@example.com", TOKEN,
                "Inbox", "FlowForge-processed", "FlowForge-rejected", 5000);
    }

    private static class Collector implements ImapMailbox.Handler {
        final List<ImapMailbox.Mail> mails = new ArrayList<>();
        final List<String> unreadable = new ArrayList<>();
        ImapMailbox.Outcome outcome = ImapMailbox.Outcome.PROCESSED;

        @Override
        public ImapMailbox.Outcome handle(ImapMailbox.Mail mail) {
            mails.add(mail);
            return mail.automatic() ? ImapMailbox.Outcome.REJECTED : outcome;
        }

        @Override
        public void oversize(long bytes) {}

        @Override
        public void unreadable(String reason) {
            unreadable.add(reason);
        }
    }

    private static String message(String id, String subject, boolean attachments, String extra) {
        return "{\"id\":\"" + id + "\",\"internetMessageId\":\"<" + id + "@x>\",\"subject\":\"" + subject + "\","
                + "\"from\":{\"emailAddress\":{\"address\":\"client@example.org\",\"name\":\"Client\"}},"
                + "\"sentDateTime\":\"2026-10-01T10:00:00Z\",\"body\":{\"contentType\":\"text\",\"content\":\"Bonjour\"},"
                + "\"hasAttachments\":" + attachments + ",\"categories\":[]," + (extra.isEmpty() ? "\"internetMessageHeaders\":[]" : extra) + "}";
    }

    private static final String AUTH_HEADERS = "\"internetMessageHeaders\":[{\"name\":\"Authentication-Results\",\"value\":\"spf.protection.outlook.com; dmarc=pass\"},"
            + "{\"name\":\"Authentication-Results\",\"value\":\"attacker; dmarc=pass\"}]";

    @Test
    void aMessageIsHandedOverThenMovedToProcessed_withTheBearerTokenAndAnUncreatedExistingFolderReused() throws Exception {
        messages = "{\"value\":[" + message("M1", "Panne", false, AUTH_HEADERS) + "]}";
        var settings = start();
        var collector = new Collector();

        new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, collector);

        assertThat(collector.mails).hasSize(1);
        var mail = collector.mails.getFirst();
        assertThat(mail.subject()).isEqualTo("Panne");
        assertThat(mail.fromAddress()).isEqualTo("client@example.org");
        assertThat(mail.body()).isEqualTo("Bonjour");
        assertThat(mail.messageId()).isEqualTo("<M1@x>");
        assertThat(mail.authenticationResults()).startsWith("spf.protection.outlook.com"); // le PREMIER en-tête, jamais le suivant
        assertThat(auth).containsOnly("Bearer " + TOKEN);
        assertThat(calls).anyMatch(c -> c.startsWith("GET /v1.0/users/dossiers@example.com/mailFolders/inbox/messages?"));
        assertThat(calls).anyMatch(c -> c.startsWith("POST /v1.0/users/dossiers@example.com/mailFolders {\"displayName\":\"FlowForge-rejected\"}"));
        assertThat(calls).anyMatch(c -> c.contains("PATCH") && c.contains(HttpGraphMailbox.DELIVERED)); // marqué AVANT le rangement
        assertThat(calls.getLast()).isEqualTo("POST /v1.0/users/dossiers@example.com/messages/M1/move {\"destinationId\":\"F-PROCESSED\"}");
        assertThat(calls.indexOf(calls.stream().filter(c -> c.startsWith("PATCH")).findFirst().orElseThrow()))
                .isLessThan(calls.size() - 1);
    }

    @Test
    void anAutomaticReplyIsMovedToRejected_neverDeleted() throws Exception {
        messages = "{\"value\":[" + message("M2", "Absence", false,
                "\"internetMessageHeaders\":[{\"name\":\"Auto-Submitted\",\"value\":\"auto-replied\"}]") + "]}";
        var settings = start();
        var collector = new Collector();

        new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, collector);

        assertThat(collector.mails.getFirst().automatic()).isTrue();
        assertThat(calls.getLast()).endsWith("/messages/M2/move {\"destinationId\":\"F-REJECTED\"}");
        assertThat(calls).noneMatch(c -> c.startsWith("DELETE"));
    }

    @Test
    void attachmentsAreBoundedAndInlineOrNonFileOnesIgnored() throws Exception {
        messages = "{\"value\":[" + message("M3", "PJ", true, "\"internetMessageHeaders\":[]") + "]}";
        attachmentList = "{\"value\":["
                + "{\"@odata.type\":\"#microsoft.graph.fileAttachment\",\"id\":\"A1\",\"name\":\"..\\\\devis.pdf\",\"contentType\":\"application/pdf\",\"size\":7,\"isInline\":false},"
                + "{\"@odata.type\":\"#microsoft.graph.fileAttachment\",\"id\":\"A2\",\"name\":\"logo.png\",\"contentType\":\"image/png\",\"size\":7,\"isInline\":true},"
                + "{\"@odata.type\":\"#microsoft.graph.itemAttachment\",\"id\":\"A3\",\"name\":\"mail\",\"size\":7,\"isInline\":false},"
                + "{\"@odata.type\":\"#microsoft.graph.fileAttachment\",\"id\":\"A4\",\"name\":\"gros.bin\",\"contentType\":\"application/octet-stream\",\"size\":5000,\"isInline\":false}"
                + "]}";
        var settings = start();
        var collector = new Collector();

        new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, collector);

        var mail = collector.mails.getFirst();
        assertThat(mail.attachments()).hasSize(1);
        assertThat(mail.attachments().getFirst().filename()).isEqualTo("devis.pdf");
        assertThat(mail.attachments().getFirst().content()).isEqualTo("PDFDATA".getBytes(StandardCharsets.UTF_8));
        assertThat(mail.attachmentsSkipped()).isEqualTo(2); // « gros.bin » au-delà de maxAttachmentBytes (100) + la pièce « élément »
        assertThat(calls).noneMatch(c -> c.contains("/attachments/A4/"));
    }

    @Test
    void aMessageAlreadyDeliveredButNotMovedIsNotHandedOverTwice() throws Exception {
        messages = "{\"value\":[{\"id\":\"M4\",\"subject\":\"déjà remis\",\"categories\":[\"" + HttpGraphMailbox.DELIVERED + "\"]}]}";
        var settings = start();
        var collector = new Collector();

        new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, collector);

        assertThat(collector.mails).isEmpty();
        assertThat(calls.getLast()).endsWith("/messages/M4/move {\"destinationId\":\"F-PROCESSED\"}");
    }

    @Test
    void aPoisonedMessageIsMovedToRejectedAndDoesNotBlockTheNext() throws Exception {
        messages = "{\"value\":[" + message("M5", "pj cassée", true, "\"internetMessageHeaders\":[]") + "," + message("M6", "sain", false, "\"internetMessageHeaders\":[]") + "]}";
        attachmentStatus = 400;
        var settings = start();
        var collector = new Collector();

        new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, collector);

        assertThat(collector.unreadable).hasSize(1);
        assertThat(collector.mails).extracting(ImapMailbox.Mail::subject).containsExactly("sain");
        assertThat(calls).anyMatch(c -> c.endsWith("/messages/M5/move {\"destinationId\":\"F-REJECTED\"}"));
        assertThat(calls).anyMatch(c -> c.endsWith("/messages/M6/move {\"destinationId\":\"F-PROCESSED\"}"));
    }

    @Test
    void aMessageAlreadyMovedByAnotherActorIsSkippedNotFatal() throws Exception {
        messages = "{\"value\":[" + message("M7", "parti", false, "\"internetMessageHeaders\":[]") + "]}";
        moveStatus = 404;
        var settings = start();

        new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, new Collector());
    }

    @Test
    void anAuthenticationOrThrottlingFailureAbortsThePollWithoutMovingAnythingAndWithoutLeakingTheToken() throws Exception {
        for (int status : new int[] {401, 403, 429, 503}) {
            calls.clear();
            listStatus = status;
            messages = "{\"error\":{\"message\":\"bad token " + TOKEN + "\"}}";
            var settings = start();
            assertThatThrownBy(() -> new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, new Collector()))
                    .as("HTTP " + status)
                    .isInstanceOf(HttpGraphMailbox.GraphTransportException.class)
                    .hasMessageContaining(String.valueOf(status))
                    .hasMessageNotContaining(TOKEN)
                    .hasMessageNotContaining("bad token");
            assertThat(calls).noneMatch(c -> c.contains("/move"));
            server.stop(0);
        }
    }

    @Test
    void aRedirectIsNeverFollowed_theBearerTokenNeverTravelsElsewhere() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        elsewhere = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        elsewhere.createContext("/", ex -> {
            hits.incrementAndGet();
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        elsewhere.start();
        redirectTo = "http://127.0.0.1:" + elsewhere.getAddress().getPort() + "/steal";
        var settings = start();

        assertThatThrownBy(() -> new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, new Collector()))
                .isInstanceOf(HttpGraphMailbox.GraphTransportException.class);
        assertThat(hits.get()).isZero();
    }

    @Test
    void anOversizedAnswerIsRefusedRatherThanBuffered() throws Exception {
        messages = "{\"value\":[],\"pad\":\"" + "x".repeat(HttpGraphMailbox.MAX_JSON_BYTES + 10) + "\"}";
        var settings = start();
        assertThatThrownBy(() -> new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, new Collector()))
                .isInstanceOf(HttpGraphMailbox.GraphTransportException.class).hasMessageContaining("too large");
    }

    @Test
    void aMissingFolderToReadIsAnErrorNotACreation() throws Exception {
        var started = start();
        var settings = new GraphMailbox.Settings(started.baseUrl(), started.mailbox(), TOKEN, "Absent", "FlowForge-processed", "FlowForge-rejected", 5000);
        assertThatThrownBy(() -> new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, new Collector()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not found");
        assertThat(calls).noneMatch(c -> c.startsWith("POST"));
    }

    @Test
    void folderNamesWithQuotesAreEscapedInTheFilter() throws Exception {
        var started = start();
        var settings = new GraphMailbox.Settings(started.baseUrl(), started.mailbox(), TOKEN, "Inbox", "L'archive", "FlowForge-rejected", 5000);
        new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, new Collector());
        assertThat(calls).anyMatch(c -> c.contains("displayName eq 'L''archive'"));
    }

    @Test
    void theSettingsNeverShowTheToken() {
        assertThat(new GraphMailbox.Settings("https://graph.microsoft.com/v1.0", "a@b.c", TOKEN, "Inbox", "p", "r", 1).toString()).doesNotContain(TOKEN);
        assertThat(Base64.getEncoder()).isNotNull();
        JsonNode unused = mapper.createObjectNode();
        assertThat(unused).isNotNull();
    }

    @Test
    void aFailedMarkingStillMovesTheMessage_soItIsNotDeliveredTwice() throws Exception {
        for (int status : new int[] {429, 503, 400}) {
            calls.clear();
            patchStatus = status;
            messages = "{\"value\":[" + message("M8", "marquage", false, "\"internetMessageHeaders\":[]") + "]}";
            var settings = start();
            new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, new Collector());
            assertThat(calls.getLast()).as("PATCH " + status).endsWith("/messages/M8/move {\"destinationId\":\"F-PROCESSED\"}");
            server.stop(0);
        }
    }

    @Test
    void aMessageThatCannotBeMovedIsLeftInPlaceWithoutBlockingTheNextOnes() throws Exception {
        messages = "{\"value\":[" + message("M9", "verrouillé", false, "\"internetMessageHeaders\":[]") + ","
                + message("M10", "suivant", false, "\"internetMessageHeaders\":[]") + "]}";
        moveStatus = 409;
        var settings = start();
        var collector = new Collector();

        new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, collector);

        assertThat(collector.mails).extracting(ImapMailbox.Mail::subject).containsExactly("verrouillé", "suivant");
    }

    @Test
    void anEnormousMessageIsRefusedAndMovedToRejectedWhileTheOthersAreStillProcessed() throws Exception {
        String huge = message("M11", "énorme", false, "\"internetMessageHeaders\":[]")
                .replace("\"content\":\"Bonjour\"", "\"content\":\"" + "x".repeat(HttpGraphMailbox.MAX_MESSAGE_JSON_BYTES) + "\"");
        messages = "{\"value\":[" + huge + "," + message("M12", "normal", false, "\"internetMessageHeaders\":[]") + "]}";
        var settings = start();
        var collector = new Collector();

        new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, collector);

        assertThat(collector.unreadable).hasSize(1);
        assertThat(collector.mails).extracting(ImapMailbox.Mail::subject).containsExactly("normal");
        assertThat(calls).anyMatch(c -> c.endsWith("/messages/M11/move {\"destinationId\":\"F-REJECTED\"}"));
    }

    @Test
    void aLyingDeclaredSizeCannotExceedTheMessageTotal() throws Exception {
        messages = "{\"value\":[" + message("M13", "menteur", true, "\"internetMessageHeaders\":[]") + "]}";
        attachmentList = "{\"value\":[{\"@odata.type\":\"#microsoft.graph.fileAttachment\",\"id\":\"A1\",\"name\":\"a.pdf\","
                + "\"contentType\":\"application/pdf\",\"size\":1,\"isInline\":false}]}"; // annonce 1 octet, en livre 7
        var settings = start();
        var collector = new Collector();

        new HttpGraphMailbox(mapper).process(settings, 10, new ImapMailbox.Limits(5, 1000, 100, 2), collector);

        assertThat(collector.mails.getFirst().attachments()).isEmpty();
        assertThat(collector.mails.getFirst().attachmentsSkipped()).isEqualTo(1);
    }

    @Test
    void aNonImageInlineAttachmentIsKeptLikeAnImapPartWithAFilename() throws Exception {
        messages = "{\"value\":[" + message("M14", "inline", true, "\"internetMessageHeaders\":[]") + "]}";
        attachmentList = "{\"value\":[{\"@odata.type\":\"#microsoft.graph.fileAttachment\",\"id\":\"A1\",\"name\":\"contrat.pdf\","
                + "\"contentType\":\"application/pdf\",\"size\":7,\"isInline\":true}]}";
        var settings = start();
        var collector = new Collector();

        new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, collector);

        assertThat(collector.mails.getFirst().attachments()).extracting(a -> a.filename()).containsExactly("contrat.pdf");
    }

    @Test
    void identifiersAreEncodedInPaths() throws Exception {
        messages = "{\"value\":[" + message("A/B+C=", "ids", false, "\"internetMessageHeaders\":[]") + "]}";
        var settings = start();

        new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, new Collector());

        assertThat(rawPaths).anyMatch(p -> p.endsWith("/messages/A%2FB%2BC%3D/move"));
        assertThat(rawPaths).noneMatch(p -> p.contains("A/B"));
    }

    @Test
    void aBodyThatNeverEndsIsCutOffByTheOverallTimeout() throws Exception {
        stall = true;
        var started = start();
        var settings = new GraphMailbox.Settings(started.baseUrl(), started.mailbox(), TOKEN, "Inbox", "FlowForge-processed", "FlowForge-rejected", 500);
        long begin = System.nanoTime();
        assertThatThrownBy(() -> new HttpGraphMailbox(mapper).process(settings, 10, LIMITS, new Collector()))
                .isInstanceOf(HttpGraphMailbox.GraphTransportException.class);
        assertThat((System.nanoTime() - begin) / 1_000_000).isLessThan(2500);
    }
}
