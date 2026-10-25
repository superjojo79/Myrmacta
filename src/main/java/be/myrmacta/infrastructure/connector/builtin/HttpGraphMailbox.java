package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.InboundAttachment;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §87 (D3c-bis) — {@link GraphMailbox} par l'API REST Microsoft Graph ({@code java.net.http}, aucune dépendance de plus).
 * Sans état : un message pris en charge quitte le dossier lu (déplacé vers « traités » ou « refusés »), il n'y a donc ni delta ni
 * curseur à persister. Redirections jamais suivies ; chaque réponse est bornée ; le jeton n'apparaît dans aucune erreur ; le corps
 * d'une erreur du service n'est jamais relayé.
 */
@Component
class HttpGraphMailbox implements GraphMailbox {

    /** Réponses JSON (liste de messages avec corps) : bornées pour qu'une passerelle défaillante ne sature pas la mémoire. */
    static final int MAX_JSON_BYTES = 16 * 1024 * 1024;
    /** Catégorie posée sur un message remis dont le rangement a échoué : il ne sera pas remis une seconde fois. */
    static final String DELIVERED = "FlowForge-delivered";
    /** Un message (corps et en-têtes compris) plus gros que cela est refusé : il ne doit pas pouvoir saturer ni bloquer la boîte. */
    static final int MAX_MESSAGE_JSON_BYTES = 4 * 1024 * 1024;
    /** Durée maximale d'un appel, corps compris. */
    private static final Logger log = LoggerFactory.getLogger(HttpGraphMailbox.class);

    private static final String LIST_SELECT = "id,categories";
    private static final String MESSAGE_SELECT = "id,internetMessageId,subject,from,sentDateTime,receivedDateTime,body,hasAttachments,"
            + "internetMessageHeaders,categories";

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ObjectMapper mapper;

    HttpGraphMailbox(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Panne du service ou de l'accès (réseau, jeton, droits, limitation) : toute l'interrogation est abandonnée, rien n'est rangé. */
    static final class GraphTransportException extends IllegalStateException {
        GraphTransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Refus propre à une ressource (message disparu, requête refusée) : le message concerné est écarté, la boîte continue. */
    static final class GraphResourceException extends RuntimeException {
        final int status;

        GraphResourceException(int status) {
            super("graph answered HTTP " + status);
            this.status = status;
        }
    }

    @Override
    public void process(Settings settings, int maxMessages, ImapMailbox.Limits limits, ImapMailbox.Handler handler) {
        Api api = new Api(settings);
        String inbox = folderId(api, settings.folder(), false);
        String processed = folderId(api, settings.processedFolder(), true);
        String rejected = folderId(api, settings.rejectedFolder(), true);
        String query = "$top=" + Math.min(maxMessages, 50) + "&$orderby=" + enc("receivedDateTime asc") + "&$select=" + enc(LIST_SELECT);
        JsonNode page = api.getJson(api.user("/mailFolders/" + seg(inbox) + "/messages?" + query));
        int count = 0;
        for (JsonNode node : page.path("value")) {
            if (count++ >= maxMessages) {
                break;
            }
            String id = node.path("id").asString("");
            if (id.isBlank()) {
                continue;
            }
            boolean alreadyDelivered = hasCategory(node, DELIVERED);
            ImapMailbox.Outcome outcome;
            JsonNode full = null;
            if (alreadyDelivered) {
                // Remis lors d'un tour dont le rangement a échoué : on réessaie seulement le rangement (jamais de 2e dossier).
                outcome = ImapMailbox.Outcome.PROCESSED;
            } else {
                try {
                    // Le message est lu seul : un message énorme est refusé sans empêcher les autres d'être listés.
                    full = api.getMessage(api.user("/messages/" + seg(id) + "?$select=" + enc(MESSAGE_SELECT)));
                    outcome = handler.handle(parse(api, full, limits));
                } catch (GraphTransportException transport) {
                    throw transport;
                } catch (RuntimeException | StackOverflowError unreadable) {
                    // Un message empoisonné est écarté : il ne bloque jamais la boîte.
                    handler.unreadable(unreadable.getClass().getSimpleName());
                    outcome = ImapMailbox.Outcome.REJECTED;
                }
            }
            if (outcome == ImapMailbox.Outcome.PROCESSED && !alreadyDelivered) {
                markDelivered(api, id, full == null ? node : full);
            }
            try {
                api.postJson(api.user("/messages/" + seg(id) + "/move"),
                        mapper.writeValueAsString(Map.of("destinationId", outcome == ImapMailbox.Outcome.PROCESSED ? processed : rejected)));
            } catch (GraphResourceException refused) {
                if (refused.status != 404) {
                    // Message qu'on ne peut pas déplacer (verrou, conflit, identifiant refusé) : il est signalé et laissé, sans bloquer les suivants.
                    log.warn("graph-mail-inbound: a message could not be moved (HTTP {}) and was left in place", refused.status);
                }
                // 404 : déjà déplacé ou supprimé par quelqu'un d'autre, rien à ranger.
            }
        }
    }

    /**
     * Posé AVANT le rangement : si celui-ci échoue, le message ne sera pas remis deux fois. Si le marquage lui-même échoue, le rangement
     * suit quand même (un déplacement réussi suffit à empêcher la remise suivante) ; la seule fenêtre restante est l'échec des deux.
     */
    private void markDelivered(Api api, String id, JsonNode node) {
        List<String> categories = new ArrayList<>();
        for (JsonNode category : node.path("categories")) {
            categories.add(category.asString(""));
        }
        categories.add(DELIVERED);
        try {
            api.send("PATCH", api.user("/messages/" + seg(id)), mapper.writeValueAsString(Map.of("categories", categories)));
        } catch (RuntimeException failure) {
            log.warn("graph-mail-inbound: a delivered message could not be marked ({})", failure.getClass().getSimpleName());
        }
    }

    // ----- dossiers -----

    private String folderId(Api api, String name, boolean create) {
        if ("inbox".equalsIgnoreCase(name.strip())) {
            return "inbox";
        }
        String escaped = name.replace("'", "''");
        JsonNode found = api.getJson(api.user("/mailFolders?$filter=" + enc("displayName eq '" + escaped + "'") + "&$select=id&$top=1"));
        for (JsonNode folder : found.path("value")) {
            String id = folder.path("id").asString("");
            if (!id.isBlank()) {
                return id;
            }
        }
        if (!create) {
            throw new IllegalStateException("mail folder not found: " + name);
        }
        JsonNode created = api.postJson(api.user("/mailFolders"), mapper.writeValueAsString(Map.of("displayName", name)));
        String id = created.path("id").asString("");
        if (id.isBlank()) {
            throw new IllegalStateException("mail folder could not be created: " + name);
        }
        return id;
    }

    // ----- message -----

    private ImapMailbox.Mail parse(Api api, JsonNode node, ImapMailbox.Limits limits) {
        JsonNode from = node.path("from").path("emailAddress");
        String fromAddress = from.path("address").asString("").strip();
        String fromDisplay = from.path("name").asString("").strip();
        Map<String, String> headers = new LinkedHashMap<>();
        for (JsonNode header : node.path("internetMessageHeaders")) {
            // Premier en-tête de ce nom = celui que le serveur de réception a ajouté en tête (même convention que l'IMAP).
            headers.putIfAbsent(header.path("name").asString("").toLowerCase(Locale.ROOT), header.path("value").asString("").strip());
        }
        String autoSubmitted = headers.getOrDefault("auto-submitted", "").toLowerCase(Locale.ROOT);
        String precedence = headers.getOrDefault("precedence", "").toLowerCase(Locale.ROOT);
        boolean automatic = (!autoSubmitted.isBlank() && !"no".equals(autoSubmitted))
                || precedence.contains("bulk") || precedence.contains("junk") || precedence.contains("list")
                || !headers.getOrDefault("x-auto-response-suppress", "").isBlank();
        String content = node.path("body").path("content").asString("");
        if (content.length() > limits.maxBodyChars() * 4L) {
            content = content.substring(0, limits.maxBodyChars() * 4);
        }
        String body = "html".equalsIgnoreCase(node.path("body").path("contentType").asString("")) ? AngusImapMailbox.htmlToText(content) : content;
        if (body.length() > limits.maxBodyChars()) {
            body = body.substring(0, limits.maxBodyChars());
        }
        List<InboundAttachment> attachments = new ArrayList<>();
        int skipped = 0;
        if (node.path("hasAttachments").asBoolean(false)) {
            skipped = attachments(api, node.path("id").asString(""), limits, attachments);
        }
        return new ImapMailbox.Mail(node.path("internetMessageId").asString(""), fromAddress, fromDisplay,
                node.path("subject").asString(""), instant(node), body.strip(), automatic,
                headers.getOrDefault("authentication-results", ""), List.copyOf(attachments), skipped);
    }

    private int attachments(Api api, String messageId, ImapMailbox.Limits limits, List<InboundAttachment> out) {
        JsonNode list = api.getJson(api.user("/messages/" + seg(messageId) + "/attachments?$select=" + enc("id,name,contentType,size,isInline")));
        int skipped = 0;
        long total = 0;
        for (JsonNode attachment : list.path("value")) {
            String type = attachment.path("contentType").asString("").strip().toLowerCase(Locale.ROOT);
            if (attachment.path("isInline").asBoolean(false) && type.startsWith("image/")) {
                continue; // image incrustée (logo de signature) : pas un document du dossier
            }
            if (!"#microsoft.graph.fileAttachment".equals(attachment.path("@odata.type").asString(""))) {
                skipped++; // message joint, lien : non repris, mais le processus le sait
                continue;
            }
            long declared = attachment.path("size").asLong(0);
            if (out.size() >= limits.maxAttachments() || declared > limits.maxAttachmentBytes() || total + declared > limits.maxMessageBytes()) {
                skipped++;
                continue;
            }
            byte[] bytes = api.getBytes(api.user("/messages/" + seg(messageId) + "/attachments/" + seg(attachment.path("id").asString("")) + "/$value"),
                    limits.maxAttachmentBytes());
            if (bytes == null || total + bytes.length > limits.maxMessageBytes()) {
                skipped++; // taille annoncée fausse ou total hors limite : jamais retenu
                continue;
            }
            total += bytes.length;
            out.add(new InboundAttachment(AngusImapMailbox.safeFilename(attachment.path("name").asString("")),
                    type.isBlank() ? "application/octet-stream" : type, bytes));
        }
        return skipped;
    }

    private static Instant instant(JsonNode node) {
        for (String field : List.of("sentDateTime", "receivedDateTime")) {
            String value = node.path(field).asString("");
            if (!value.isBlank()) {
                try {
                    return Instant.parse(value);
                } catch (DateTimeParseException ignored) {
                    // essai suivant
                }
            }
        }
        return null;
    }

    private static boolean hasCategory(JsonNode node, String category) {
        for (JsonNode c : node.path("categories")) {
            if (category.equals(c.asString(""))) {
                return true;
            }
        }
        return false;
    }

    // ----- HTTP -----

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String seg(String value) {
        return enc(value);
    }

    private final class Api {
        private final Settings settings;

        Api(Settings settings) {
            this.settings = settings;
        }

        String user(String path) {
            return settings.baseUrl().replaceAll("/+$", "") + "/users/" + seg(settings.mailbox()) + path;
        }

        JsonNode getJson(String url) {
            return send("GET", url, null);
        }

        /** Un message seul : trop gros = refusé (ressource), pas une panne du service. */
        JsonNode getMessage(String url) {
            try {
                return json(exchangeRaw("GET", url, null, MAX_MESSAGE_JSON_BYTES, "application/json"));
            } catch (TooLarge tooLarge) {
                throw new GraphResourceException(413);
            }
        }

        JsonNode postJson(String url, String body) {
            return send("POST", url, body);
        }

        JsonNode send(String method, String url, String body) {
            try {
                return json(exchangeRaw(method, url, body, MAX_JSON_BYTES, "application/json"));
            } catch (TooLarge tooLarge) {
                throw new GraphTransportException("graph answer is too large", null);
            }
        }

        private JsonNode json(byte[] bytes) {
            try {
                return bytes.length == 0 ? mapper.createObjectNode() : mapper.readTree(bytes);
            } catch (RuntimeException ex) {
                throw new GraphResourceException(502);
            }
        }

        /** @return le contenu, ou {@code null} s'il dépasse {@code max} */
        byte[] getBytes(String url, long max) {
            try {
                return exchangeRaw("GET", url, null, max, "application/octet-stream");
            } catch (TooLarge tooLarge) {
                return null;
            }
        }

        private byte[] exchangeRaw(String method, String url, String body, long max, String accept) throws TooLarge {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(settings.timeoutMillis()))
                    .header("Authorization", "Bearer " + settings.accessToken())
                    .header("Accept", accept)
                    .header("Prefer", "outlook.body-content-type=\"text\"");
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
            }
            // Taille bornée (lecture arrêtée à max + 1 octet) ET durée bornée, corps compris : sur dépassement le flux est fermé.
            java.util.concurrent.CompletableFuture<HttpResponse<java.io.InputStream>> sent =
                    client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            Answer answer;
            try {
                answer = sent.thenApplyAsync(readBounded0(max))
                        .orTimeout(settings.timeoutMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                        .join();
            } catch (java.util.concurrent.CompletionException ex) {
                sent.thenAccept(r -> {
                    try {
                        r.body().close();
                    } catch (java.io.IOException ignored) {
                        // abandonné
                    }
                });
                throw new GraphTransportException("graph is unreachable or too slow", ex.getCause());
            }
            int status = answer.status();
            if (status / 100 != 2) {
                // corps jamais relayé : il peut citer la requête
                if (status == 401 || status == 403 || status == 408 || status == 429 || status >= 500 || status / 100 == 3) {
                    throw new GraphTransportException("graph answered HTTP " + status, null);
                }
                throw new GraphResourceException(status);
            }
            if (answer.bytes().length > max) {
                throw new TooLarge();
            }
            return answer.bytes();
        }
    }

    private record Answer(int status, byte[] bytes) {}

    private static java.util.function.Function<HttpResponse<java.io.InputStream>, Answer> readBounded0(long max) {
        return response -> {
            try (java.io.InputStream in = response.body()) {
                return new Answer(response.statusCode(), in.readNBytes((int) Math.min(max + 1, Integer.MAX_VALUE - 8)));
            } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        };
    }

    private static final class TooLarge extends Exception {
        TooLarge() {
            super(null, null, false, false);
        }
    }
}
