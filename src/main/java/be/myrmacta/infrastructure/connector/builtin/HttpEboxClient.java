package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §150 (D-EBOX-REGISTRY-HOSTS) — {@link EboxClient} par {@link PinnedHttpClient} : chaque URL repasse par
 * {@link OutboundUrlGuard} (adresse figée, plages privées refusées), aucune redirection, réponses bornées en taille et en
 * durée, jeton jamais repris dans une erreur, corps d'erreur du service jamais relayé.
 */
@Component
class HttpEboxClient implements EboxClient {

    /** Une page de 100 messages avec leurs métadonnées tient largement dans cette borne. */
    static final long MAX_JSON_BYTES = 8L * 1024 * 1024;
    static final String ON_BEHALF_OF = "On-Behalf-Of-Enterprise-Number";
    /** Statut posé sur une réponse qui dépasse sa borne de taille. */
    static final int TOO_LARGE = 413;

    private final OutboundUrlGuard guard;
    private final ObjectMapper mapper;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration totalTimeout;

    @Autowired
    HttpEboxClient(OutboundUrlGuard guard, ObjectMapper mapper, ConnectorProperties properties) {
        this(guard, mapper, properties.http().connectTimeout(), properties.http().requestTimeout(), Duration.ofSeconds(60));
    }

    HttpEboxClient(OutboundUrlGuard guard, ObjectMapper mapper, Duration connectTimeout, Duration readTimeout, Duration totalTimeout) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.totalTimeout = totalTimeout;
    }

    @Override
    public List<JsonNode> registries(String registryUrl, Access access) {
        JsonNode json = json(trim(registryUrl) + "/messageRegistries", access);
        List<JsonNode> items = new ArrayList<>();
        json.path("items").forEach(items::add);
        return items;
    }

    @Override
    public MessagePage messages(String endpointUrl, Access access, String partition, LocalDate receivedAfter, int page, int pageSize) {
        String url = trim(endpointUrl) + "/ebox/messages?recipientPartition=" + enc(partition) + "&receivedAfter=" + receivedAfter
                + "&page=" + page + "&pageSize=" + pageSize;
        JsonNode json = json(url, access);
        List<JsonNode> items = new ArrayList<>();
        json.path("items").forEach(items::add);
        int total = json.path("totalItems").canConvertToInt() ? json.path("totalItems").asInt() : items.size();
        return new MessagePage(items, total);
    }

    @Override
    public byte[] attachment(String endpointUrl, Access access, String messageId, String attachmentId, long maxBytes) {
        String url = trim(endpointUrl) + "/ebox/messages/" + seg(messageId) + "/attachments/" + seg(attachmentId) + "/content";
        return fetch(url, access, maxBytes, "application/octet-stream");
    }

    @Override
    public Optional<JsonNode> body(String endpointUrl, Access access, String messageId) {
        try {
            return Optional.of(json(trim(endpointUrl) + "/ebox/messages/" + seg(messageId) + "/body", access));
        } catch (EboxResourceException missing) {
            if (missing.status == 404) {
                return Optional.empty();
            }
            throw missing;
        }
    }

    @Override
    public List<JsonNode> actionStatuses(String endpointUrl, Access access, String messageId) {
        JsonNode json;
        try {
            json = tree(fetch(trim(endpointUrl) + "/ebox/messages/" + seg(messageId) + "/messageActions", access, MAX_JSON_BYTES,
                    "application/json"));
        } catch (EboxResourceException missing) {
            if (missing.status == 404) {
                return List.of();
            }
            throw missing;
        }
        if (!json.isArray()) {
            throw new EboxTransportException("ebox answered a non-array list of message actions");
        }
        List<JsonNode> items = new ArrayList<>();
        json.forEach(items::add);
        return items;
    }

    @Override
    public void markActionExecuted(String endpointUrl, Access access, String messageId, String actionId) {
        String url = trim(endpointUrl) + "/ebox/messages/" + seg(messageId) + "/messageActions/" + seg(actionId);
        call("PATCH", url, access, "{\"isExecuted\":true}".getBytes(StandardCharsets.UTF_8), MAX_JSON_BYTES, "application/json");
    }

    private JsonNode json(String url, Access access) {
        JsonNode node = tree(fetch(url, access, MAX_JSON_BYTES, "application/json"));
        if (!node.isObject()) {
            throw new EboxTransportException("ebox answered a non-object JSON document");
        }
        return node;
    }

    private JsonNode tree(byte[] bytes) {
        try {
            JsonNode node = mapper.readTree(bytes);
            if (node == null) {
                throw new EboxTransportException("ebox answered an empty JSON document");
            }
            return node;
        } catch (JacksonException unreadable) {
            throw new EboxTransportException("ebox answered an unreadable JSON document");
        }
    }

    private byte[] fetch(String url, Access access, long maxBytes, String accept) {
        return call("GET", url, access, null, maxBytes, accept);
    }

    /** Appel borné ; {@code body} non nul part en {@code application/merge-patch+json}. Un 204 rend un tableau vide. */
    private byte[] call(String method, String url, Access access, byte[] body, long maxBytes, String accept) {
        OutboundUrlGuard.PinnedEndpoint endpoint;
        try {
            endpoint = guard.resolveSafe(url);
        } catch (IllegalArgumentException refused) {
            throw new EboxTransportException("ebox endpoint refused by the outbound guard");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + access.token());
        headers.put("Accept", accept);
        if (access.onBehalfOf() != null) {
            headers.put(ON_BEHALF_OF, access.onBehalfOf());
        }
        if (body != null) {
            headers.put("Content-Type", "application/merge-patch+json");
        }
        PinnedHttpClient.Fetched fetched;
        try {
            fetched = PinnedHttpClient.exchange(method, url, endpoint.address(), headers, body, connectTimeout, readTimeout,
                    totalTimeout, maxBytes);
        } catch (IOException failure) {
            if ("response too large".equals(failure.getMessage())) {
                // Une pièce trop grosse ne bloque pas le registre : seul ce document est écarté.
                throw new EboxResourceException(TOO_LARGE);
            }
            throw new EboxTransportException("ebox call failed: " + failure.getClass().getSimpleName());
        }
        int status = fetched.status();
        if (status == 200) {
            return fetched.body();
        }
        if (status == 204 && body != null) {
            return new byte[0];
        }
        if (status == 401) {
            throw new EboxUnauthorizedException();
        }
        if (status == 403 || status == 404 || status == 410 || (body != null && (status == 400 || status == 409))) {
            throw new EboxResourceException(status);
        }
        throw new EboxTransportException("ebox answered HTTP " + status);
    }

    private static String trim(String url) {
        return url.strip().replaceAll("/+$", "");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String seg(String value) {
        return enc(value).replace("+", "%20");
    }
}
