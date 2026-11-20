package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.ebox.model.EboxConsent;
import be.myrmacta.domain.ebox.model.EboxDocument;
import be.myrmacta.domain.ebox.model.EboxOutgoingMessage;
import be.myrmacta.domain.ebox.model.EboxProofOfDispatch;
import be.myrmacta.domain.ebox.model.EboxPublication;
import be.myrmacta.domain.ebox.model.EboxPublicationRejectedException;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxRoute;
import be.myrmacta.domain.ebox.model.EboxSenderAccount;
import be.myrmacta.domain.ebox.port.out.EboxChannel;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §155 (D-EBOX-SEND-CHANNELS) — voie entreprise : publication dans l'eBox Enterprise d'une entreprise (numéro BCE), en
 * <em>Document Sender</em>.
 *
 * <ul>
 *   <li>préférence eBox : {@code POST /eboxPreferences/search} (e-Box Federation 1.4) ; entreprise absente : pas d'eBox ;</li>
 *   <li>message ordinaire : {@code POST /ebox/messages} (e-Box REST 3.8), multipart {@code messageToPublish} + {@code upfileN},
 *       en-tête {@code Idempotency-Key} ; 201 publié, 409 doublon (D-EBOX-SEND-IDEMPOTENCE) ;</li>
 *   <li>recommandé : {@code POST /registeredMails} (Registered Mail 1.1), {@code customerReference} = clé d'idempotence ; la preuve
 *       de dépôt revient dans la réponse ; une preuve déjà émise pour cette référence ({@code GET /proofsOfDispatch}) fait un
 *       doublon (D-EBOX-SEND-REGISTERED) ;</li>
 *   <li>OAuth v5 : {@code client_assertion} signée par la clé du certificat X.509 (D-EBOX-AUTH), un jeton par scope ; un 401
 *       renouvelle le jeton une fois.</li>
 * </ul>
 *
 * Un refus (4xx) ou un appel qui n'est certainement pas parti lève {@link EboxPublicationRejectedException} ; un 5xx ou une réponse
 * qui n'arrive pas rend {@link EboxPublication.Status#UNCERTAIN} (ATREQ04). Le corps d'erreur de l'eBox n'est jamais relayé, le
 * jeton jamais écrit : seule son empreinte SHA-256 part au journal d'audit.
 */
@Component
class HttpEboxEnterpriseChannel implements EboxChannel {

    static final long MAX_JSON_BYTES = 1024L * 1024;
    static final String ON_BEHALF_OF = "On-Behalf-Of-Enterprise-Number";
    static final String CONSENT_SOURCE = "federation-1.4";
    static final int MAX_MESSAGE_ID = 200;
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

    private final ConnectorInstanceRepository instances;
    private final OutboundUrlGuard guard;
    private final ObjectMapper mapper;
    private final OAuth2TokenProvider tokens;
    private final Clock clock;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration totalTimeout;

    @Autowired
    HttpEboxEnterpriseChannel(ConnectorInstanceRepository instances, OutboundUrlGuard guard, ObjectMapper mapper,
            ConnectorProperties properties) {
        this(instances, guard, mapper, new OAuth2TokenProvider(guard, mapper, properties, PinnedHttpClient.sender(), Clock.systemUTC()),
                Clock.systemUTC(), properties.http().connectTimeout(), properties.http().requestTimeout(), Duration.ofMinutes(5));
    }

    HttpEboxEnterpriseChannel(ConnectorInstanceRepository instances, OutboundUrlGuard guard, ObjectMapper mapper,
            OAuth2TokenProvider tokens, Clock clock, Duration connectTimeout, Duration readTimeout, Duration totalTimeout) {
        this.instances = Objects.requireNonNull(instances, "instances");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.totalTimeout = totalTimeout;
    }

    @Override
    public EboxRoute route() {
        return EboxRoute.ENTERPRISE;
    }

    @Override
    public EboxConsent consent(String tenantId, EboxSenderAccount sender, EboxRecipient recipient) {
        String bce = enterprise(recipient).enterpriseNumber();
        EboxOutboundSettings settings = settings(tenantId, sender);
        byte[] body = json(ordered("eboxType", "ENTERPRISE", "eboxIdType", "EnterpriseNumber", "eboxIdValueList", List.of(bce)));
        Response response = authorized(settings.publicationOauth(), token -> {
            Map<String, String> headers = headers(token, settings.onBehalfOf(), "application/json");
            headers.put("Content-Type", "application/json");
            return call("POST", settings.federationUrl() + "/eboxPreferences/search", headers, body);
        });
        // Seul un 200 qui ne cite pas l'entreprise vaut « pas d'eBox » ; un 404 (point d'accès absent, mauvaise URL) est une panne.
        if (response.status() != 200) {
            throw new IllegalStateException("ebox federation answered HTTP " + response.status());
        }
        JsonNode list = tree(response.body());
        if (!list.isArray()) {
            throw new IllegalStateException("ebox federation answered a non-array list of preferences");
        }
        for (JsonNode preference : list) {
            if (bce.equals(preference.path("eboxId").path("eboxIdValue").asString(""))) {
                boolean exclusive = preference.path("exclusivelyEbox").asBoolean(false);
                return new EboxConsent(exclusive ? EboxConsent.Level.EXCLUSIVE_EBOX : EboxConsent.Level.EBOX_NOT_EXCLUSIVE,
                        clock.instant(), CONSENT_SOURCE);
            }
        }
        return new EboxConsent(EboxConsent.Level.NO_EBOX, clock.instant(), CONSENT_SOURCE);
    }

    @Override
    public EboxPublication publish(String tenantId, EboxSenderAccount sender, EboxOutgoingMessage message, String idempotencyKey) {
        EboxRecipient.Enterprise recipient = enterprise(message.recipient());
        EboxOutboundSettings settings = settings(tenantId, sender);
        return message.registered()
                ? publishRegistered(settings, recipient, message, idempotencyKey)
                : publishMessage(settings, recipient, message, idempotencyKey);
    }

    /**
     * TODO §155 (D-EBOX-SEND-REGISTERED) — {@code GET /proofsOfDispatch/{messageId}} si l'identifiant est connu, sinon
     * {@code GET /proofsOfDispatch?customerReference=} ; un 404 ou une liste vide : aucune preuve.
     */
    @Override
    public Optional<EboxPublication> registeredProof(String tenantId, EboxSenderAccount sender, String idempotencyKey,
            String providerMessageId) {
        EboxOutboundSettings settings = settings(tenantId, sender);
        if (settings.registeredMailUrl() == null) {
            throw new IllegalStateException("registered mail is not configured for this eBox sender (registeredMailUrl)");
        }
        if (providerMessageId == null) {
            return registeredProof(settings, idempotencyKey);
        }
        String base = settings.registeredMailUrl() + "/proofsOfDispatch";
        String url = base + "/" + URLEncoder.encode(providerMessageId, StandardCharsets.UTF_8).replace("+", "%20");
        Response response = authorized(settings.registeredMailOauth(),
                token -> call("GET", url, headers(token, null, "application/json"), null));
        if (response.status() == 404) {
            return Optional.empty();
        }
        if (response.status() != 200) {
            throw new IllegalStateException("registered mail answered HTTP " + response.status() + " to the proof lookup");
        }
        EboxProofOfDispatch proof = proof(tree(response.body()), idempotencyKey);
        if (proof == null || !providerMessageId.equals(proof.providerMessageId())) {
            throw new IllegalStateException("registered mail answered an unusable proof of dispatch");
        }
        EboxPublication.Trace trace = new EboxPublication.Trace("GET", base, settings.clientId(), null, null);
        return Optional.of(new EboxPublication(EboxPublication.Status.DUPLICATE, proof.providerMessageId(), 200, trace, proof));
    }

    private EboxPublication publishMessage(EboxOutboundSettings settings, EboxRecipient.Enterprise recipient,
            EboxOutgoingMessage message, String idempotencyKey) {
        String lang = message.language().code();
        // Cartes ordonnées : Map.of itère dans un ordre propre à chaque JVM, le JSON envoyé doit être stable.
        Map<String, Object> toPublish = new LinkedHashMap<>();
        toPublish.put("recipient", ordered("eboxType", "enterprise", "enterpriseNumber", recipient.enterpriseNumber()));
        toPublish.put("senderApplicationId", message.senderApplicationId());
        toPublish.put("messageTypeId", message.messageTypeId());
        toPublish.put("subject", ordered(lang, message.subject()));
        toPublish.put("originalMessageId", idempotencyKey);
        if (message.expirationDate() != null) {
            toPublish.put("expirationDate", message.expirationDate().toString());
        }
        Multipart multipart = new Multipart();
        List<Map<String, Object>> attachments = new ArrayList<>();
        int index = 0;
        for (EboxDocument document : message.documents()) {
            String part = "upfile" + (++index);
            attachments.add(ordered("httpPartName", part, "isMainContent", document.mainContent(), "digest",
                    ordered("digestMethod", "sha_256", "digestValue", base64Url(document.sha256()))));
            multipart.file(part, document);
        }
        toPublish.put("attachments", attachments);
        if (message.body() != null) {
            toPublish.put("bodyContent", ordered(lang, message.body()));
            toPublish.put("isBodyMainContent", false);
        }
        if (message.replyAuthorized()) {
            toPublish.put("isReplyAuthorized", true);
            if (message.replyDueDate() != null) {
                toPublish.put("replyDueDate", message.replyDueDate().toString());
            }
        }
        multipart.jsonFirst("messageToPublish", json(toPublish));
        byte[] body = multipart.finish();
        String url = settings.publicationUrl() + "/ebox/messages";
        Holder<String> tokenUsed = new Holder<>();
        Response response = authorized(settings.publicationOauth(), token -> {
            tokenUsed.value = token;
            Map<String, String> headers = headers(token, settings.onBehalfOf(), "application/json");
            headers.put("Idempotency-Key", idempotencyKey);
            headers.put("Content-Type", multipart.contentType());
            return call("POST", url, headers, body);
        });
        EboxPublication.Trace trace = new EboxPublication.Trace("POST", url, settings.clientId(), settings.onBehalfOf(),
                sha256Hex(tokenUsed.value));
        return switch (response.status()) {
            case 201, 200 -> {
                // Accepté mais inexploitable (JSON illisible, identifiant absent ou trop long) : issue inconnue, journalisée.
                String id = messageId(readOrNull(response.body()));
                yield id == null
                        ? new EboxPublication(EboxPublication.Status.UNCERTAIN, null, response.status(), trace, null)
                        : new EboxPublication(EboxPublication.Status.PUBLISHED, id, response.status(), trace, null);
            }
            case 409 -> new EboxPublication(EboxPublication.Status.DUPLICATE, null, 409, trace, null);
            default -> outcomeOfFailure(response.status(), trace);
        };
    }

    private EboxPublication publishRegistered(EboxOutboundSettings settings, EboxRecipient.Enterprise recipient,
            EboxOutgoingMessage message, String idempotencyKey) {
        if (settings.registeredMailUrl() == null) {
            throw new EboxPublicationRejectedException("registered mail is not configured for this eBox sender (registeredMailUrl)");
        }
        Optional<EboxPublication> existing;
        try {
            existing = registeredProof(settings, idempotencyKey);
        } catch (IllegalStateException lookupFailed) {
            // Rien n'est parti : la recherche précède l'envoi.
            throw new EboxPublicationRejectedException("registered mail proof lookup failed before sending");
        }
        if (existing.isPresent()) {
            return existing.get();
        }
        String lang = message.language().code();
        Multipart multipart = new Multipart();
        List<Map<String, Object>> metadata = new ArrayList<>();
        int annex = 0;
        for (EboxDocument document : message.documents()) {
            String part = document.mainContent() ? "mainContent" : "annex" + (++annex);
            metadata.add(ordered("httpPartName", part, "digest",
                    ordered("method", "sha256", "value", base64Url(document.sha256()))));
            multipart.file(part, document);
        }
        Map<String, Object> toPublish = new LinkedHashMap<>();
        toPublish.put("recipient", ordered("eboxType", "enterprise", "enterpriseNumber", recipient.enterpriseNumber()));
        toPublish.put("senderApplicationId", message.senderApplicationId());
        toPublish.put("messageTypeId", message.messageTypeId());
        toPublish.put("subject", ordered(lang, message.subject()));
        toPublish.put("customerReference", idempotencyKey);
        toPublish.put("documentsMetaData", metadata);
        multipart.jsonFirst("registeredMailToPublish", json(toPublish));
        byte[] body = multipart.finish();
        String url = settings.registeredMailUrl() + "/registeredMails";
        Holder<String> tokenUsed = new Holder<>();
        Response response = authorized(settings.registeredMailOauth(), token -> {
            tokenUsed.value = token;
            Map<String, String> headers = headers(token, null, "application/json");
            headers.put("Content-Type", multipart.contentType());
            return call("POST", url, headers, body);
        });
        EboxPublication.Trace trace = new EboxPublication.Trace("POST", url, settings.clientId(), null, sha256Hex(tokenUsed.value));
        if (response.status() == 200 || response.status() == 201) {
            EboxProofOfDispatch proof = proof(readOrNull(response.body()), idempotencyKey);
            return proof == null
                    ? new EboxPublication(EboxPublication.Status.UNCERTAIN, null, response.status(), trace, null)
                    : new EboxPublication(EboxPublication.Status.PUBLISHED, proof.providerMessageId(), response.status(), trace,
                            proof);
        }
        return outcomeOfFailure(response.status(), trace);
    }

    private Optional<EboxPublication> registeredProof(EboxOutboundSettings settings, String idempotencyKey) {
        String base = settings.registeredMailUrl() + "/proofsOfDispatch";
        String url = base + "?customerReference=" + URLEncoder.encode(idempotencyKey, StandardCharsets.UTF_8);
        Response response = authorized(settings.registeredMailOauth(),
                token -> call("GET", url, headers(token, null, "application/json"), null));
        if (response.status() == 404) {
            return Optional.empty();
        }
        if (response.status() != 200) {
            throw new IllegalStateException("registered mail answered HTTP " + response.status() + " to the proof lookup");
        }
        JsonNode items = tree(response.body()).path("items");
        if (!items.isArray() || items.isEmpty()) {
            return Optional.empty();
        }
        EboxProofOfDispatch proof = proof(items.get(0), idempotencyKey);
        if (proof == null) {
            throw new IllegalStateException("registered mail answered an unusable proof of dispatch");
        }
        EboxPublication.Trace trace = new EboxPublication.Trace("GET", base, settings.clientId(), null, null);
        return Optional.of(new EboxPublication(EboxPublication.Status.DUPLICATE, proof.providerMessageId(), 200, trace, proof));
    }

    /** Preuve exploitable, ou {@code null} : identifiant absent ou hors bornes, signature absente, autre référence, trop grosse. */
    private static EboxProofOfDispatch proof(JsonNode node, String idempotencyKey) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String messageId = messageId(node);
        String signature = node.path("signature").asString("");
        String reference = node.path("customerReference").asString("");
        String json = node.toString();
        if (messageId == null || signature.isBlank() || !idempotencyKey.equals(reference)
                || json.length() > EboxProofOfDispatch.MAX_JSON) {
            return null;
        }
        return new EboxProofOfDispatch(reference, messageId, sha256Hex(signature), json);
    }

    /** {@code messageId} utilisable : présent, au plus 200 caractères, sans caractère de contrôle ; sinon {@code null}. */
    private static String messageId(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String id = node.path("messageId").asString("").strip();
        return id.isEmpty() || id.length() > MAX_MESSAGE_ID || CONTROL.matcher(id).find() ? null : id;
    }

    private JsonNode readOrNull(byte[] body) {
        try {
            return mapper.readTree(body);
        } catch (JacksonException unreadable) {
            return null;
        }
    }

    private static Map<String, Object> ordered(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static EboxPublication outcomeOfFailure(int status, EboxPublication.Trace trace) {
        if (status == EboxPublication.TIMEOUT || status >= 500) {
            return new EboxPublication(EboxPublication.Status.UNCERTAIN, null, status, trace, null);
        }
        throw new EboxPublicationRejectedException(status);
    }

    private EboxOutboundSettings settings(String tenantId, EboxSenderAccount sender) {
        ConnectorInstanceData instance = instances.findByIdAndTenantId(sender.connectorInstanceId(), tenantId)
                .orElseThrow(() -> new IllegalStateException("eBox sender connector instance not found"));
        return EboxOutboundSettings.of(instance, guard);
    }

    private static EboxRecipient.Enterprise enterprise(EboxRecipient recipient) {
        if (recipient instanceof EboxRecipient.Enterprise enterprise) {
            return enterprise;
        }
        throw new IllegalArgumentException("the enterprise eBox channel only serves enterprise recipients");
    }

    @FunctionalInterface
    private interface Call {
        Response with(String token);
    }

    private static final class Holder<T> {
        T value;
    }

    /** Un 401 retire le jeton et rejoue une fois (même clé d'idempotence : sans risque de doublon). */
    private Response authorized(ConnectorInstanceData oauth, Call call) {
        Response response = call.with(tokens.accessToken(oauth));
        if (response.status() == 401 && tokens.invalidate(oauth)) {
            response = call.with(tokens.accessToken(oauth));
        }
        if (response.status() == 401) {
            throw new IllegalStateException("ebox refused the access token (HTTP 401)");
        }
        return response;
    }

    private static Map<String, String> headers(String token, String onBehalfOf, String accept) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Accept", accept);
        if (onBehalfOf != null) {
            headers.put(ON_BEHALF_OF, onBehalfOf);
        }
        return headers;
    }

    private record Response(int status, byte[] body) {}

    /**
     * Appel borné par {@link PinnedHttpClient} (adresse figée, pas de redirection). Une adresse refusée ou une connexion impossible
     * : rien n'est parti, refus certain. Toute autre panne après l'envoi : {@link EboxPublication#TIMEOUT}.
     */
    private Response call(String method, String url, Map<String, String> headers, byte[] body) {
        OutboundUrlGuard.PinnedEndpoint endpoint;
        try {
            endpoint = guard.resolveSafe(url);
        } catch (IllegalArgumentException refused) {
            throw new EboxPublicationRejectedException("ebox endpoint refused by the outbound guard");
        }
        try {
            PinnedHttpClient.Fetched fetched = PinnedHttpClient.exchange(method, url, endpoint.address(), headers, body,
                    connectTimeout, readTimeout, totalTimeout, MAX_JSON_BYTES);
            return new Response(fetched.status(), fetched.body());
        } catch (ConnectException | UnknownHostException notSent) {
            throw new EboxPublicationRejectedException("ebox endpoint unreachable: " + notSent.getClass().getSimpleName());
        } catch (IOException unknown) {
            return new Response(EboxPublication.TIMEOUT, new byte[0]);
        }
    }

    private byte[] json(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (JacksonException impossible) {
            throw new IllegalStateException("eBox request could not be serialized");
        }
    }

    private JsonNode tree(byte[] body) {
        try {
            JsonNode node = mapper.readTree(body);
            if (node == null) {
                throw new IllegalStateException("ebox answered an empty JSON document");
            }
            return node;
        } catch (JacksonException unreadable) {
            throw new IllegalStateException("ebox answered an unreadable JSON document");
        }
    }

    private static String base64Url(byte[] digest) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    static String sha256Hex(String value) {
        if (value == null) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Corps {@code multipart/form-data} : une partie JSON en tête puis les fichiers, noms déjà contrôlés par le modèle. Une pièce
     * peut peser 100 Mo : le corps est un seul tableau à la taille exacte, où chaque pièce est copiée une fois depuis le document
     * ({@link EboxDocument#copyInto}), sans flux ni copie intermédiaire.
     */
    private static final class Multipart {
        private final String boundary = "myrmacta-" + UUID.randomUUID();
        private final List<byte[]> heads = new ArrayList<>();
        private final List<EboxDocument> documents = new ArrayList<>();
        private byte[] jsonHead;
        private byte[] json;

        String contentType() {
            return "multipart/form-data; boundary=" + boundary;
        }

        void jsonFirst(String name, byte[] value) {
            jsonHead = head("Content-Disposition: form-data; name=\"" + name + "\"\r\nContent-Type: application/json\r\n");
            json = value;
        }

        void file(String name, EboxDocument document) {
            heads.add(head("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + document.fileName()
                    + "\"; filename*=UTF-8''" + URLEncoder.encode(document.fileName(), StandardCharsets.UTF_8).replace("+", "%20")
                    + "\r\nContent-Type: " + document.mediaType() + "\r\n"));
            documents.add(document);
        }

        byte[] finish() {
            byte[] end = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
            long size = (long) jsonHead.length + json.length + CRLF.length + end.length;
            for (int i = 0; i < documents.size(); i++) {
                size += heads.get(i).length + documents.get(i).size() + CRLF.length;
            }
            if (size > Integer.MAX_VALUE - 16) {
                throw new EboxPublicationRejectedException("eBox request body is too large");
            }
            byte[] body = new byte[(int) size];
            int at = put(body, 0, jsonHead);
            at = put(body, at, json);
            at = put(body, at, CRLF);
            for (int i = 0; i < documents.size(); i++) {
                at = put(body, at, heads.get(i));
                documents.get(i).copyInto(body, at);
                at += (int) documents.get(i).size();
                at = put(body, at, CRLF);
            }
            put(body, at, end);
            return body;
        }

        private byte[] head(String headers) {
            return ("--" + boundary + "\r\n" + headers + "\r\n").getBytes(StandardCharsets.UTF_8);
        }

        private static int put(byte[] target, int at, byte[] bytes) {
            System.arraycopy(bytes, 0, target, at, bytes.length);
            return at + bytes.length;
        }

        private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);
    }
}
