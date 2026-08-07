package com.flowforge.infrastructure.notification;

import com.flowforge.domain.notification.model.WebhookDeliveryCommand;
import com.flowforge.domain.notification.port.out.WebhookPort;
import com.flowforge.infrastructure.config.NotificationProperties;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class WebhookRestAdapter implements WebhookPort {

    private static final String SIGNATURE_HEADER = "X-FlowForge-Signature";
    private static final String SIGNATURE_PREFIX = "sha256=";

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
        this(urlGuard, objectMapper, restClientSender(createRestClient(properties)));
    }

    WebhookRestAdapter(OutboundUrlGuard urlGuard, ObjectMapper objectMapper, HttpSender httpSender) {
        this.urlGuard = Objects.requireNonNull(urlGuard, "urlGuard");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.httpSender = Objects.requireNonNull(httpSender, "httpSender");
    }

    @Override
    public void send(WebhookDeliveryCommand command) {
        Objects.requireNonNull(command, "command");
        urlGuard.assertSafe(command.targetUrl());

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
        if (StringUtils.hasText(command.secretPlain())) {
            headers.put(SIGNATURE_HEADER, SIGNATURE_PREFIX + hmacHex(command.secretPlain(), body));
        }

        httpSender.post(command.targetUrl(), body, headers);
    }

    private static RestClient createRestClient(NotificationProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.webhook().connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.webhook().readTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }

    private static HttpSender restClientSender(RestClient restClient) {
        return (url, body, headers) -> {
            RestClient.RequestBodySpec spec = restClient
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
            headers.forEach((name, value) -> {
                if (!"Content-Type".equalsIgnoreCase(name)) {
                    spec.header(name, value);
                }
            });
            spec.retrieve().toBodilessEntity();
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
