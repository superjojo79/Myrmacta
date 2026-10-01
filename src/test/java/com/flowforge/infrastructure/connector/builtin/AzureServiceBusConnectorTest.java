package com.flowforge.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.infrastructure.config.ConnectorProperties;
import com.flowforge.infrastructure.config.InboundConnectorProperties;
import com.flowforge.infrastructure.connector.mapping.SimpleJsonPathMapper;
import com.flowforge.infrastructure.notification.OutboundUrlGuard;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** TODO §87 (D3d) — connecteur Azure Service Bus : validation (hôte limité), mapping, message illisible rendu, SAS. */
class AzureServiceBusConnectorTest {

    private final List<ServiceBusPort.Received> queue = new ArrayList<>();
    private final List<String> completed = new ArrayList<>();
    private final List<String> abandoned = new ArrayList<>();
    private boolean failComplete;
    private AzureServiceBusConnector connector;
    private ServiceBusPort.Endpoint endpoint;

    @BeforeEach
    void setUp() throws Exception {
        ServiceBusPort bus = new ServiceBusPort() {
            @Override
            public List<Received> receive(Endpoint e, int max, int wait, long maxBytes) {
                endpoint = e;
                return List.copyOf(queue);
            }

            @Override
            public void complete(Endpoint e, Received m) {
                if (failComplete) {
                    throw new IllegalStateException("lock lost");
                }
                completed.add(m.messageId());
            }

            @Override
            public void abandon(Endpoint e, Received m) {
                abandoned.add(m.messageId());
            }
        };
        OutboundUrlGuard guard = new OutboundUrlGuard(false, host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});
        connector = new AzureServiceBusConnector(new SimpleJsonPathMapper(), guard, JsonMapper.builder().build(),
                new ConnectorProperties(null, null, null, null, null, null), InboundConnectorProperties.defaults(), bus,
                Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC));
    }

    private static Map<String, Object> config() {
        Map<String, Object> c = new HashMap<>();
        c.put("namespaceHost", "contoso-bus.servicebus.windows.net");
        c.put("entityPath", "d365-events");
        c.put("sasKeyName", "listen");
        c.put("sasKey", "c2VjcmV0LWtleQ==");
        c.put("processKey", "onboarding-fournisseur");
        c.put("dossierTypeCode", "FOURNISSEUR");
        c.put("mapping", Map.of(
                "fields", Map.of("nom", "$.vendor.name"),
                "variables", Map.of("vendorAccount", "$.vendor.account"),
                "existingDossierReferencePath", "$.reference"));
        return c;
    }

    private static ConnectorInstanceData instance(Map<String, Object> config) {
        return new ConnectorInstanceData(UUID.randomUUID(), "t1", "Bus D365", "azure-service-bus", true, config);
    }

    private static ServiceBusPort.Received msg(String id, String body) {
        return new ServiceBusPort.Received(body, id, 1, "https://contoso-bus.servicebus.windows.net/d365-events/messages/" + id + "/lock");
    }

    @Test
    void aValidConfigPasses_andTheHostIsLimitedToServiceBusDomains() {
        connector.validate(config());
        for (String host : List.of("evil.example.com", "contoso-bus.servicebus.windows.net.evil.com", "a/b.servicebus.windows.net",
                "127.0.0.1", "contoso.servicebus.windows.net:8080", "-x.servicebus.windows.net")) {
            Map<String, Object> c = config();
            c.put("namespaceHost", host);
            assertThatThrownBy(() -> connector.validate(c)).as(host).isInstanceOf(ConnectorValidationException.class);
        }
        for (String host : List.of("a-b.servicebus.usgovcloudapi.net", "bus1.servicebus.chinacloudapi.cn")) {
            Map<String, Object> c = config();
            c.put("namespaceHost", host);
            connector.validate(c);
        }
    }

    @Test
    void entityPathSasAndIntervalAreValidated() {
        for (var entry : Map.<String, Object>of("entityPath", "../x", "sasKeyName", "bad name", "pollSeconds", "1").entrySet()) {
            Map<String, Object> c = config();
            c.put(entry.getKey(), entry.getValue());
            assertThatThrownBy(() -> connector.validate(c)).as(entry.getKey()).isInstanceOf(ConnectorValidationException.class);
        }
        Map<String, Object> subscription = config();
        subscription.put("entityPath", "topic1/subscriptions/sub1");
        connector.validate(subscription);
        Map<String, Object> noKey = config();
        noKey.remove("sasKey");
        assertThatThrownBy(() -> connector.validate(noKey)).isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void aJsonMessageBecomesAnEventAndIsCompleted() {
        queue.add(msg("m1", "{\"vendor\":{\"name\":\"ACME\",\"account\":\"V-001\"},\"reference\":\"DOS-1\"}"));

        var events = connector.onEvent(instance(config()), Map.of());

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().processKey()).isEqualTo("onboarding-fournisseur");
        assertThat(events.getFirst().dossierFields()).containsEntry("nom", "ACME");
        assertThat(events.getFirst().variables()).containsEntry("vendorAccount", "V-001");
        assertThat(events.getFirst().existingDossierReference()).isEqualTo("DOS-1");
        assertThat(completed).containsExactly("m1");
        assertThat(abandoned).isEmpty();
        assertThat(endpoint.baseUrl()).isEqualTo("https://contoso-bus.servicebus.windows.net");
        assertThat(endpoint.entityPath()).isEqualTo("d365-events");
        assertThat(endpoint.authorization().get()).startsWith("SharedAccessSignature sr=").contains("skn=listen");
        assertThat(endpoint.toString()).doesNotContain("Signature").doesNotContain("c2VjcmV0");
    }

    @Test
    void anUnreadableOrIncompleteMessageIsGivenBackToTheBroker_theOthersStillFlow() {
        queue.add(msg("bad", "not json"));
        queue.add(msg("empty-field", "{\"vendor\":{\"account\":\"x\"}}")); // nom absent : champ mappé manquant
        queue.add(msg("good", "{\"vendor\":{\"name\":\"OK\"}}"));

        var events = connector.onEvent(instance(config()), Map.of());

        assertThat(events).hasSize(1);
        assertThat(abandoned).containsExactly("bad", "empty-field");
        assertThat(completed).containsExactly("good");
    }

    @Test
    void aFailureToCompleteKeepsTheEvent_aDuplicateIsBetterThanALoss() {
        failComplete = true;
        queue.add(msg("m1", "{\"vendor\":{\"name\":\"ACME\"}}"));
        assertThat(connector.onEvent(instance(config()), Map.of())).hasSize(1);
    }

    @Test
    void theSasSignatureMatchesAnIndependentComputation() throws Exception {
        String token = AzureServiceBusConnector.sasToken("https://Contoso-Bus.servicebus.windows.net/d365-events", "listen", "c2VjcmV0LWtleQ==", 1_790_000_000L);

        String encodedUri = java.net.URLEncoder.encode("https://contoso-bus.servicebus.windows.net/d365-events", StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("c2VjcmV0LWtleQ==".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getEncoder().encodeToString(mac.doFinal((encodedUri + "\n1790000000").getBytes(StandardCharsets.UTF_8)));
        assertThat(token).isEqualTo("SharedAccessSignature sr=" + encodedUri + "&sig=" + java.net.URLEncoder.encode(expected, StandardCharsets.UTF_8)
                + "&se=1790000000&skn=listen");
    }
}
