package be.myrmacta.infrastructure.notification;

import be.myrmacta.domain.notification.model.WebhookDeliveryCommand;
import be.myrmacta.domain.notification.port.out.WebhookPort;
import be.myrmacta.infrastructure.config.NotificationProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class WebhookRestAdapter implements WebhookPort {

    static final String SIGNATURE_HEADER = "X-Myrmacta-Signature";
    /** Encore émis, même valeur, pour un récepteur déjà déployé. */
    static final String LEGACY_SIGNATURE_HEADER = "X-FlowForge-Signature"; // flowforge-legacy
    private static final String SIGNATURE_PREFIX = "sha256=";

    /** Un vérificateur interne accepte le nom actuel et l'ancien. */
    static boolean acceptsSignatureHeader(String name) {
        return SIGNATURE_HEADER.equalsIgnoreCase(name) || LEGACY_SIGNATURE_HEADER.equalsIgnoreCase(name);
    }

    static {
        // Must run before the JDK's sun.net.www.protocol.http.HttpURLConnection class
        // initializes (it caches this flag in a static field on first load). Without it,
        // HttpURLConnection silently drops the restricted "Host" header we set below,
        // sending the pinned IP as Host instead of the original webhook hostname (D-44).
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    @FunctionalInterface
    public interface HttpSender {
        void post(String url, byte[] body, Map<String, String> headers);
    }

    private final OutboundUrlGuard urlGuard;
    private final ObjectMapper objectMapper;
    private final HttpSender httpSender;

    @Autowired
    public WebhookRestAdapter(
            OutboundUrlGuard urlGuard,
            ObjectMapper objectMapper,
            NotificationProperties properties) {
        this(urlGuard, objectMapper, urlConnectionSender(properties));
    }

    WebhookRestAdapter(OutboundUrlGuard urlGuard, ObjectMapper objectMapper, HttpSender httpSender) {
        this.urlGuard = Objects.requireNonNull(urlGuard, "urlGuard");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.httpSender = Objects.requireNonNull(httpSender, "httpSender");
    }

    @Override
    public void send(WebhookDeliveryCommand command) {
        Objects.requireNonNull(command, "command");
        OutboundUrlGuard.PinnedEndpoint pinned = urlGuard.resolveSafe(command.targetUrl());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "notification");
        envelope.put("outboxId", command.outboxId().toString());
        envelope.put("at", command.at().toString());
        envelope.put("payload", command.payload());

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(envelope);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize webhook envelope", ex);
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        headers.put("Host", pinned.originalHost());
        if (StringUtils.hasText(command.secretPlain())) {
            String signature = SIGNATURE_PREFIX + hmacHex(command.secretPlain(), body);
            headers.put(SIGNATURE_HEADER, signature);
            headers.put(LEGACY_SIGNATURE_HEADER, signature);
        }

        httpSender.post(pinned.requestUrl(), body, headers);
    }

    private static HttpSender urlConnectionSender(NotificationProperties properties) {
        int connectMs = Math.toIntExact(properties.webhook().connectTimeout().toMillis());
        int readMs = Math.toIntExact(properties.webhook().readTimeout().toMillis());
        return (url, body, headers) -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
                connection.setConnectTimeout(connectMs);
                connection.setReadTimeout(readMs);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                String originalHost = headers.get("Host");
                if (connection instanceof HttpsURLConnection https && originalHost != null) {
                    https.setHostnameVerifier((hostname, session) ->
                            HttpsURLConnection.getDefaultHostnameVerifier().verify(originalHost, session));
                }
                headers.forEach(connection::setRequestProperty);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }
                int status = connection.getResponseCode();
                if (status >= 400) {
                    throw new IllegalStateException("Webhook HTTP " + status);
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Webhook delivery failed: " + ex.getMessage(), ex);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        };
    }

    private static String hmacHex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 calculation failed", ex);
        }
    }
}
