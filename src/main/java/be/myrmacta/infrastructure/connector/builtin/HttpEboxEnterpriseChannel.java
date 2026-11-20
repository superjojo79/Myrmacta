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
import java.io.ByteArrayOutputStream;
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
import java.util.UUID;
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
        Map<String, Object> search = new LinkedHashMap<>();
        search.put("eboxType", "ENTERPRISE");
        search.put("eboxIdType", "EnterpriseNumber");
        search.put("eboxIdValueList", List.of(bce));
        byte[] body = json(search);
        Response response = authorized(settings.publicationOauth(), token -> {
            Map<String, String> headers = headers(token, settings.onBehalfOf(), "application/json");
            headers.put("Content-Type", "application/json");
            return call("POST", settings.federationUrl() + "/eboxPreferences/search", headers, body);
        });
        if (response.status() == 404) {
            return new EboxConsent(EboxConsent.Level.NO_EBOX, clock.instant(), CONSENT_SOURCE);
        }
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

    private EboxPublication publishMessage(EboxOutboundSettings settings, EboxRecipient.Enterprise recipient,
            EboxOutgoingMessage message, String idempotencyKey) {
        String lang = message.language().code();
        Map<String, Object> toPublish = new LinkedHashMap<>();
        toPublish.put("recipient", Map.of("eboxType", "enterprise", "enterpriseNumber", recipient.enterpriseNumber()));
        toPublish.put("senderApplicationId", message.senderApplicationId());
        toPublish.put("messageTypeId", message.messageTypeId());
        toPublish.put("subject", Map.of(lang, message.subject()));
        toPublish.put("originalMessageId", idempotencyKey);
        if (message.expirationDate() != null) {
            toPublish.put("expirationDate", message.expirationDate().toString());
        }
        Multipart multipart = new Multipart();
        List<Map<String, Object>> attachments = new ArrayList<>();
        int index = 0;
        for (EboxDocument document : message.documents()) {
            String part = "upfile" + (++index);
            attachments.add(Map.of("httpPartName", part, "isMainContent", document.mainContent(), "digest",
                    Map.of("digestMethod", "sha_256", "digestValue", base64Url(document.sha256()))));
            multipart.file(part, document);
        }
        toPublish.put("attachments", attachments);
        if (message.body() != null) {
            toPublish.put("bodyContent", Map.of(lang, message.body()));
            toPublish.put("isBodyMainContent", false);
        }
        if (message.replyAuthorized()) {
            toPublish.put("isReplyAuthorized", true);
            if (message.replyDueDate() != null) {
                toPublish.put("replyDueDate", message.replyDueDate().toString());
            }
        }
        multipart.json("messageToPublish", json(toPublish));
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
                String id = tree(response.body()).path("messageId").asString("");
                if (id.isBlank()) {
                    yield new EboxPublication(EboxPublication.Status.UNCERTAIN, null, response.status(), trace, null);
                }
                yield new EboxPublication(EboxPublication.Status.PUBLISHED, id, response.status(), trace, null);
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
        String proofsUrl = settings.registeredMailUrl() + "/proofsOfDispatch?customerReference="
                + URLEncoder.encode(idempotencyKey, StandardCharsets.UTF_8);
        Response existing = authorized(settings.registeredMailOauth(),
                token -> call("GET", proofsUrl, headers(token, null, "application/json"), null));
        if (existing.status() == 200) {
            JsonNode items = tree(existing.body()).path("items");
            if (items.isArray() && !items.isEmpty()) {
                EboxPublication.Trace trace = new EboxPublication.Trace("GET", settings.registeredMailUrl() + "/proofsOfDispatch",
                        settings.clientId(), null, null);
                EboxProofOfDispatch proof = proof(items.get(0), idempotencyKey);
                return new EboxPublication(EboxPublication.Status.DUPLICATE, proof.providerMessageId(), 200, trace, proof);
            }
        } else if (existing.status() != 404) {
            throw new IllegalStateException("registered mail answered HTTP " + existing.status() + " to the proof lookup");
        }
        String lang = message.language().code();
        Multipart multipart = new Multipart();
        List<Map<String, Object>> metadata = new ArrayList<>();
        int annex = 0;
        for (EboxDocument document : message.documents()) {
            String part = document.mainContent() ? "mainContent" : "annex" + (++annex);
            metadata.add(Map.of("httpPartName", part, "digest", Map.of("method", "sha256", "value", base64Url(document.sha256()))));
            multipart.file(part, document);
        }
        Map<String, Object> toPublish = new LinkedHashMap<>();
        toPublish.put("recipient", Map.of("eboxType", "enterprise", "enterpriseNumber", recipient.enterpriseNumber()));
        toPublish.put("senderApplicationId", message.senderApplicationId());
        toPublish.put("messageTypeId", message.messageTypeId());
        toPublish.put("subject", Map.of(lang, message.subject()));
        toPublish.put("customerReference", idempotencyKey);
        toPublish.put("documentsMetaData", metadata);
        multipart.json("registeredMailToPublish", json(toPublish));
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
            EboxProofOfDispatch proof = proof(tree(response.body()), idempotencyKey);
            return new EboxPublication(EboxPublication.Status.PUBLISHED, proof.providerMessageId(), response.status(), trace, proof);
        }
        return outcomeOfFailure(response.status(), trace);
    }

    private EboxProofOfDispatch proof(JsonNode node, String idempotencyKey) {
        String messageId = node.path("messageId").asString("");
        String signature = node.path("signature").asString("");
        String reference = node.path("customerReference").asString("");
        if (messageId.isBlank() || signature.isBlank() || !idempotencyKey.equals(reference)) {
            throw new IllegalStateException("registered mail answered an unusable proof of dispatch");
        }
        return new EboxProofOfDispatch(reference, messageId, sha256Hex(signature), node.toString());
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

    /** Corps {@code multipart/form-data} : une partie JSON et des fichiers, noms déjà contrôlés par le modèle. */
    private static final class Multipart {
        private final String boundary = "myrmacta-" + UUID.randomUUID();
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        String contentType() {
            return "multipart/form-data; boundary=" + boundary;
        }

        void json(String name, byte[] json) {
            head("Content-Disposition: form-data; name=\"" + name + "\"\r\nContent-Type: application/json\r\n");
            out.writeBytes(json);
            out.writeBytes(CRLF);
        }

        void file(String name, EboxDocument document) {
            head("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + document.fileName() + "\"; filename*=UTF-8''"
                    + URLEncoder.encode(document.fileName(), StandardCharsets.UTF_8).replace("+", "%20") + "\r\nContent-Type: "
                    + document.mediaType() + "\r\n");
            out.writeBytes(document.content());
            out.writeBytes(CRLF);
        }

        byte[] finish() {
            out.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            return out.toByteArray();
        }

        private void head(String headers) {
            out.writeBytes(("--" + boundary + "\r\n" + headers + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);
    }
}
