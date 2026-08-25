package com.flowforge.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.notification.model.WebhookDeliveryCommand;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class WebhookRestAdapterTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /**
     * Deterministic, offline stand-in for real DNS resolution — literal-IP parsing below
     * performs no network I/O, so {@code hooks.example.com} resolves without touching the network.
     */
    private static final OutboundUrlGuard.HostResolver FAKE_RESOLVER = host -> {
        if ("hooks.example.com".equals(host.toLowerCase(Locale.ROOT))) {
            return new InetAddress[] {literalAddress("93.184.216.34")};
        }
        throw new UnknownHostException(host);
    };

    private OutboundUrlGuard guard;
    private List<CapturedPost> posts;
    private AtomicInteger guardCalls;
    private WebhookRestAdapter adapter;

    @BeforeEach
    void setUp() {
        guardCalls = new AtomicInteger();
        guard = new OutboundUrlGuard(false, FAKE_RESOLVER) {
            @Override
            public PinnedEndpoint resolveSafe(String url) {
                guardCalls.incrementAndGet();
                return super.resolveSafe(url);
            }
        };
        posts = new ArrayList<>();
        adapter = new WebhookRestAdapter(guard, MAPPER, (url, body, headers) -> {
            posts.add(new CapturedPost(url, body, Map.copyOf(headers)));
        });
    }

    @Test
    void send_postsEnvelopeJsonOnce_withContentTypeAndOptionalHmac() throws Exception {
        UUID outboxId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant at = Instant.parse("2026-08-07T12:00:00Z");
        var command = new WebhookDeliveryCommand(
                "https://hooks.example.com/notify",
                "super-secret",
                outboxId,
                at,
                Map.of("k", "v"));

        adapter.send(command);

        assertThat(guardCalls).hasValue(1);
        assertThat(posts).hasSize(1);
        CapturedPost post = posts.getFirst();
        assertThat(post.url()).isEqualTo("https://93.184.216.34/notify");
        assertThat(post.headers()).containsEntry("Host", "hooks.example.com");
        assertThat(post.headers()).containsEntry("Content-Type", "application/json");

        JsonNode envelope = MAPPER.readTree(post.body());
        assertThat(envelope.get("type").asText()).isEqualTo("notification");
        assertThat(envelope.get("outboxId").asText()).isEqualTo(outboxId.toString());
        assertThat(envelope.get("at").asText()).isEqualTo("2026-08-07T12:00:00Z");
        assertThat(envelope.get("payload").get("k").asText()).isEqualTo("v");

        String expectedSig = "sha256=" + hmacHex("super-secret", post.body());
        assertThat(post.headers()).containsEntry("X-FlowForge-Signature", expectedSig);
        assertThat(MessageDigest.isEqual(
                        expectedSig.getBytes(StandardCharsets.UTF_8),
                        post.headers().get("X-FlowForge-Signature").getBytes(StandardCharsets.UTF_8)))
                .isTrue();
    }

    @Test
    void send_omitsSignatureWhenSecretBlank() {
        var command = new WebhookDeliveryCommand(
                "https://hooks.example.com/notify",
                "  ",
                UUID.randomUUID(),
                Instant.parse("2026-08-07T12:00:00Z"),
                Map.of());

        adapter.send(command);

        assertThat(posts).hasSize(1);
        assertThat(posts.getFirst().headers()).doesNotContainKey("X-FlowForge-Signature");
    }

    @Test
    void send_callsGuardBeforeHttp_andDoesNotPostWhenUnsafe() {
        var command = new WebhookDeliveryCommand(
                "https://127.0.0.1/hook",
                null,
                UUID.randomUUID(),
                Instant.parse("2026-08-07T12:00:00Z"),
                Map.of());

        assertThatThrownBy(() -> adapter.send(command)).isInstanceOf(IllegalArgumentException.class);
        assertThat(guardCalls).hasValue(1);
        assertThat(posts).isEmpty();
    }

    @Test
    void send_propagatesHttpFailure_withoutRetry() {
        AtomicInteger attempts = new AtomicInteger();
        adapter = new WebhookRestAdapter(guard, MAPPER, (url, body, headers) -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("upstream 500");
        });
        var command = new WebhookDeliveryCommand(
                "https://hooks.example.com/notify",
                null,
                UUID.randomUUID(),
                Instant.parse("2026-08-07T12:00:00Z"),
                Map.of());

        assertThatThrownBy(() -> adapter.send(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("upstream 500");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void send_usesFirstPinnedIpWhenDnsWouldRebindOnSecondLookup() {
        AtomicInteger lookups = new AtomicInteger();
        OutboundUrlGuard.HostResolver flipping = host -> {
            int n = lookups.incrementAndGet();
            if (n == 1) {
                return new InetAddress[] {literalAddress("93.184.216.34")};
            }
            return new InetAddress[] {literalAddress("198.51.100.10")};
        };
        adapter = new WebhookRestAdapter(
                new OutboundUrlGuard(false, flipping),
                MAPPER,
                (url, body, headers) -> posts.add(new CapturedPost(url, body, Map.copyOf(headers))));

        adapter.send(new WebhookDeliveryCommand(
                "https://hooks.example.com/notify",
                null,
                UUID.randomUUID(),
                Instant.parse("2026-08-07T12:00:00Z"),
                Map.of()));

        assertThat(lookups).hasValue(1);
        assertThat(posts).hasSize(1);
        assertThat(posts.getFirst().url()).isEqualTo("https://93.184.216.34/notify");
        assertThat(posts.getFirst().headers()).containsEntry("Host", "hooks.example.com");
    }

    private static InetAddress literalAddress(String literalIp) {
        try {
            // A literal IP string is parsed locally — InetAddress.getByName performs no
            // DNS query in this case, so the fixture stays fully offline.
            return InetAddress.getByName(literalIp);
        } catch (UnknownHostException ex) {
            throw new AssertionError(ex);
        }
    }

    private static String hmacHex(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    private record CapturedPost(String url, byte[] body, Map<String, String> headers) {}
}
