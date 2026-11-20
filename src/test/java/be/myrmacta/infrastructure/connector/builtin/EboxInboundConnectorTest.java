package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.PollingTriggerConnector.EventDelivery;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.domain.mailroom.model.EboxTakenMessage;
import be.myrmacta.domain.mailroom.port.out.EboxReadJournal;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.config.InboundConnectorProperties;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** TODO §150 — lecture de l'eBox Enterprise : validation, partitions dédiées, journal, curseur, registres, jeton. */
class EboxInboundConnectorTest {

    private static final String REGISTRY = "https://services.socialsecurity.be/REST/ebox/enterprise/federation/documentProviderRegistry/v1";
    private static final String ENDPOINT = "https://services.socialsecurity.be/REST/ebox/enterprise/messageRegistry/v3";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private static String privateKeyPem;

    private final ObjectMapper json = JsonMapper.builder().build();
    private final FakeEbox ebox = new FakeEbox();
    private final MemoryJournal journal = new MemoryJournal();
    private final List<String> tokenRequests = new ArrayList<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-20T09:00:00Z"));
    private EboxInboundConnector connector;

    @BeforeAll
    static void key() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        privateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    @BeforeEach
    void setUp() {
        connector = connector(InboundConnectorProperties.defaults());
        ebox.registries.add(registry("socsec_v3", ENDPOINT, "3.8", false, null, null));
    }

    private EboxInboundConnector connector(InboundConnectorProperties inbound) {
        OutboundUrlGuard guard = new OutboundUrlGuard(false, host -> new InetAddress[] {InetAddress.getByName("193.191.208.10")});
        HttpRestConnector.HttpSender tokenSender = request -> {
            tokenRequests.add(new String(request.body(), StandardCharsets.UTF_8));
            return new HttpRestConnector.HttpCallResponse(200,
                    "{\"access_token\":\"token-" + tokenRequests.size() + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        };
        OAuth2TokenProvider tokens = new OAuth2TokenProvider(guard, json, new ConnectorProperties(null, null, null, null, null, null),
                tokenSender, clock);
        return new EboxInboundConnector(ebox, journal, new SimpleJsonPathMapper(), json, guard, inbound, tokens,
                TransactionOperations.withoutTransaction(), clock);
    }

    private static Map<String, Object> config() {
        Map<String, Object> c = new HashMap<>();
        c.put("enterpriseNumber", "0212345678");
        c.put("partitions", "myrmacta, myrmacta-rh");
        c.put("registryUrl", REGISTRY);
        c.put("oauthTokenUrl", "https://services.socialsecurity.be/REST/oauth/v5/token");
        c.put("oauthClientId", "myrmacta-commune");
        c.put("oauthPrivateKey", privateKeyPem);
        c.put("oauthCertThumbprint", "a".repeat(40));
        c.put("processKey", "tri-courrier");
        c.put("dossierTypeCode", "COURRIER");
        c.put("defaultFields", Map.of("canal", "EBOX"));
        c.put("mapping", Map.of("fields", Map.of("objet", "$.message.subject.fr")));
        return c;
    }

    private static ConnectorInstanceData instance(Map<String, Object> config) {
        return new ConnectorInstanceData(UUID.fromString("00000000-0000-0000-0000-0000000000eb"), "t1", "eBox commune", "ebox-inbound", true,
                config);
    }

    private List<WorkflowTriggerEvent> poll(Map<String, Object> config) {
        List<WorkflowTriggerEvent> events = new ArrayList<>();
        connector.poll(instance(config), events::add);
        return events;
    }

    // --- validation -------------------------------------------------------------------------------------------------

    @Test
    void validConfigurationIsAccepted() {
        assertThatCode(() -> connector.validate(config())).doesNotThrowAnyException();
        assertThat(connector.getType()).isEqualTo("ebox-inbound");
        assertThat(connector.getConfigSchema()).extracting("name").contains("partitions", "enterpriseNumber", "registryUrl");
    }

    @Test
    void partitionsAreRequired() {
        Map<String, Object> config = config();
        config.put("partitions", " , ");
        assertThatThrownBy(() -> connector.validate(config)).isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("partitions is required");
        config.remove("partitions");
        assertThatThrownBy(() -> connector.validate(config)).isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void invalidPartitionOrTooManyPartitionsAreRefused() {
        Map<String, Object> config = config();
        config.put("partitions", "ok, pas valide");
        assertThatThrownBy(() -> connector.validate(config)).hasMessageContaining("invalid partition");
        config.put("partitions", IntStream.range(0, 21).mapToObj(i -> "p" + i).collect(Collectors.joining(",")));
        assertThatThrownBy(() -> connector.validate(config)).hasMessageContaining("at most 20");
    }

    @Test
    void enterpriseNumberMustBeACbeNumber() {
        Map<String, Object> config = config();
        config.put("enterpriseNumber", "0212.345.678");
        assertThatCode(() -> connector.validate(config)).doesNotThrowAnyException();
        config.put("enterpriseNumber", "2212345678");
        assertThatThrownBy(() -> connector.validate(config)).hasMessageContaining("enterpriseNumber");
    }

    @Test
    void registryAndTokenUrlsMustBeOnAnAllowedDomain() {
        Map<String, Object> config = config();
        config.put("registryUrl", "https://ebox.example.org/registry");
        assertThatThrownBy(() -> connector.validate(config)).hasMessageContaining("registryUrl");
        config.put("registryUrl", "http://services.socialsecurity.be/registry");
        assertThatThrownBy(() -> connector.validate(config)).hasMessageContaining("registryUrl");
        config.put("registryUrl", REGISTRY);
        config.put("oauthTokenUrl", "https://evil-socialsecurity.be/token");
        assertThatThrownBy(() -> connector.validate(config)).hasMessageContaining("oauthTokenUrl");
    }

    @Test
    void onlyPrivateKeyJwtIsAccepted() {
        Map<String, Object> config = config();
        config.put("oauthClientAuthMethod", "CLIENT_SECRET_POST");
        assertThatThrownBy(() -> connector.validate(config)).hasMessageContaining("PRIVATE_KEY_JWT");
        config.remove("oauthClientAuthMethod");
        config.remove("oauthPrivateKey");
        assertThatThrownBy(() -> connector.validate(config)).hasMessageContaining("oauthPrivateKey");
    }

    @Test
    void lookbackAndLanguageAreBounded() {
        Map<String, Object> config = config();
        config.put("initialLookbackDays", 91);
        assertThatThrownBy(() -> connector.validate(config)).hasMessageContaining("initialLookbackDays");
        config.put("initialLookbackDays", 30);
        config.put("language", "en");
        assertThatThrownBy(() -> connector.validate(config)).hasMessageContaining("language");
    }

    // --- lecture ----------------------------------------------------------------------------------------------------

    @Test
    void aMessageBecomesOneMailWithItsAttachmentsAndPlatformVariables() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));

        List<WorkflowTriggerEvent> events = poll(config());

        assertThat(events).hasSize(1);
        WorkflowTriggerEvent event = events.getFirst();
        assertThat(event.processKey()).isEqualTo("tri-courrier");
        assertThat(event.dossierTypeCode()).isEqualTo("COURRIER");
        assertThat(event.createWhenReferenceUnknown()).isTrue();
        assertThat(event.dossierFields()).containsEntry("canal", "EBOX").containsEntry("objet", "Avis de cotisation m-1");
        assertThat(event.variables())
                .containsEntry("eboxMessageId", "m-1")
                .containsEntry("eboxRegistre", "socsec_v3")
                .containsEntry("eboxPartition", "myrmacta")
                .containsEntry("eboxEntreprise", "0212345678")
                .containsEntry("eboxExpediteur", "0206731645")
                .containsEntry("eboxApplication", "onss-cotisations")
                .containsEntry("eboxTypeMessage", "avis")
                .containsEntry("eboxObjet", "Avis de cotisation m-1")
                .containsEntry("eboxRecuLe", "2026-11-19T08:00:00+01:00")
                .containsEntry("eboxRecommande", false)
                .containsEntry("eboxActionRequise", true)
                .containsEntry("eboxReponseAutorisee", false)
                .containsEntry("eboxCorps", "Veuillez trouver ci-joint votre avis.");
        assertThat(event.attachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.filename()).isEqualTo("avis-m-1.pdf");
            assertThat(attachment.contentType()).isEqualTo("application/pdf");
            assertThat(new String(attachment.content(), StandardCharsets.US_ASCII)).isEqualTo("%PDF-m-1/a-1");
        });
        assertThat(journal.taken).extracting(EboxTakenMessage::messageId).containsExactly("m-1");
        assertThat(journal.taken.getFirst().enterpriseNumber()).isEqualTo("0212345678");
    }

    @Test
    void everyListingIsFilteredOnAConfiguredPartitionAndNothingElseIsRead() {
        poll(config());

        assertThat(ebox.listedPartitions).containsExactlyInAnyOrder("myrmacta", "myrmacta-rh");
        assertThat(ebox.downloads).isEmpty();
    }

    @Test
    void aMessageOfAnotherPartitionReturnedByTheServerIsNeverOpened() {
        ebox.add("myrmacta", message("m-other", "comptabilite", "2026-11-19T08:00:00+01:00"));

        assertThat(poll(config())).isEmpty();
        assertThat(ebox.downloads).isEmpty();
        assertThat(ebox.bodies).isEmpty();
    }

    @Test
    void aMessageAlreadyTakenIsNotDeliveredAgainEvenByAnotherInstance() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        assertThat(poll(config())).hasSize(1);

        assertThat(poll(config())).isEmpty();

        List<WorkflowTriggerEvent> other = new ArrayList<>();
        connector.poll(new ConnectorInstanceData(UUID.randomUUID(), "t1", "eBox bis", "ebox-inbound", true, config()), other::add);
        assertThat(other).isEmpty();
        assertThat(journal.taken).hasSize(1);
    }

    @Test
    void aFailedDeliveryIsNotJournaledAndTheCursorStays() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        EventDelivery failing = event -> {
            throw new IllegalStateException("database down");
        };

        connector.poll(instance(config()), failing);

        assertThat(journal.taken).isEmpty();
        assertThat(journal.cursors).doesNotContainKey(cursorKey("myrmacta"));
        assertThat(journal.cursors).containsEntry(cursorKey("myrmacta-rh"), TODAY);
        assertThat(poll(config())).hasSize(1);
    }

    @Test
    void theFirstPollReadsTheInitialWindowThenFromTheDayBeforeTheCursor() {
        poll(config());
        assertThat(ebox.receivedAfter).containsOnly(TODAY.minusDays(14));
        assertThat(journal.cursors).containsEntry(cursorKey("myrmacta"), TODAY);

        ebox.receivedAfter.clear();
        clock.set(Instant.parse("2026-11-23T09:00:00Z"));
        poll(config());
        assertThat(ebox.receivedAfter).containsOnly(TODAY.minusDays(1));
        assertThat(journal.cursors).containsEntry(cursorKey("myrmacta"), LocalDate.of(2026, 11, 23));
    }

    @Test
    void theCapPerPollLeavesTheRestForTheNextPollWithoutMovingTheCursor() {
        connector = connector(new InboundConnectorProperties(1, 26_214_400L, 20_000, 10_485_760L, 30, 1_048_576L));
        ebox.add("myrmacta", message("m-2", "myrmacta", "2026-11-19T09:00:00+01:00"));
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));

        assertThat(poll(config())).extracting(event -> event.variables().get("eboxMessageId")).containsExactly("m-1");
        assertThat(journal.cursors).doesNotContainKey(cursorKey("myrmacta"));

        assertThat(poll(config())).extracting(event -> event.variables().get("eboxMessageId")).containsExactly("m-2");
    }

    @Test
    void allPagesAreRead() {
        for (int i = 0; i < 101; i++) {
            ebox.add("myrmacta", message("m-" + i, "myrmacta", "2026-11-19T08:00:00+01:00"));
        }
        connector = connector(new InboundConnectorProperties(200, 26_214_400L, 20_000, 10_485_760L, 30, 1_048_576L));

        assertThat(poll(config())).hasSize(101);
        assertThat(ebox.pagesRequested).contains(1, 2);
    }

    @Test
    void anAttachmentTooLargeIsNotDownloadedAndIsNamedOnTheMail() {
        ObjectMapperNode message = message("m-big", "myrmacta", "2026-11-19T08:00:00+01:00");
        message.attachment("a-2", "annexe.zip", "application/zip", 20_000.0);
        ebox.add("myrmacta", message);

        WorkflowTriggerEvent event = poll(config()).getFirst();

        assertThat(event.attachments()).extracting("filename").containsExactly("avis-m-big.pdf");
        assertThat(event.variables()).containsEntry("eboxPiecesNonImportees", List.of("annexe.zip"));
        assertThat(ebox.downloads).containsExactly("m-big/a-1");
    }

    @Test
    void aRefusedTokenIsRenewedOnceThenTheCallIsRetried() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());
        clock.set(clock.instant().plus(Duration.ofMinutes(5)));
        ebox.add("myrmacta", message("m-2", "myrmacta", "2026-11-19T09:00:00+01:00"));
        ebox.unauthorizedOnce = true;

        assertThat(poll(config())).hasSize(1);
        assertThat(tokenRequests).hasSize(2);
        assertThat(ebox.tokensSeen).contains("token-2");
        assertThat(tokenRequests.getFirst()).contains("client_assertion=").contains("scope=scope%3Adocument%3Amanagement%3Aconsult");
    }

    @Test
    void inactiveOrForeignRegistriesAreIgnored() {
        ebox.registries.clear();
        ebox.registries.add(registry("old", "https://services.socialsecurity.be/v2", "2.0", false, null, null));
        ebox.registries.add(registry("off", "https://services.socialsecurity.be/off", "3.8", true, null, null));
        ebox.registries.add(registry("later", "https://services.socialsecurity.be/later", "3.8", false, "2027-01-01", null));
        ebox.registries.add(registry("ended", "https://services.socialsecurity.be/ended", "3.8", false, null, "2026-11-20"));
        ebox.registries.add(registry("foreign", "https://ebox.example.org/v3", "3.8", false, null, null));
        ebox.registries.add(registry("fin", "https://ebox.minfin.fgov.be/v3", "3.6", false, "2020-01-01", "2030-01-01"));

        poll(config());

        assertThat(ebox.endpointsListed).containsOnly("https://ebox.minfin.fgov.be/v3");
    }

    @Test
    void theMandateHeaderIsSentOnlyForATechnicalIntermediary() {
        poll(config());
        assertThat(ebox.onBehalfOf).containsOnlyNulls();

        Map<String, Object> config = config();
        config.put("onBehalfOf", "true");
        ebox.onBehalfOf.clear();
        poll(config);
        assertThat(ebox.onBehalfOf).containsOnly("0212345678");
    }

    @Test
    void aMappingCannotOverwritePlatformVariables() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        Map<String, Object> config = config();
        config.put("mapping", Map.of("variables", Map.of("eboxMessageId", "$.registre", "application", "$.message.senderApplicationId")));

        WorkflowTriggerEvent event = poll(config).getFirst();

        assertThat(event.variables()).containsEntry("eboxMessageId", "m-1").containsEntry("application", "onss-cotisations");
    }

    // --- outillage ----------------------------------------------------------------------------------------------------

    private String cursorKey(String partition) {
        return instance(config()).id() + "/socsec_v3/" + partition;
    }

    private JsonNode registry(String id, String url, String version, boolean off, String activation, String deactivation) {
        Map<String, Object> endpoint = new LinkedHashMap<>();
        endpoint.put("endpointId", "1");
        endpoint.put("url", url);
        endpoint.put("apiVersion", version);
        endpoint.put("isTemporarilyDeactivated", off);
        if (activation != null) {
            endpoint.put("activationDate", activation);
        }
        if (deactivation != null) {
            endpoint.put("deactivationDate", deactivation);
        }
        return json.valueToTree(Map.of("id", id, "name", Map.of("fr", id), "description", Map.of("fr", id), "endpoints", List.of(endpoint)));
    }

    private ObjectMapperNode message(String id, String partition, String receipt) {
        ObjectMapperNode message = new ObjectMapperNode();
        message.values.put("messageId", id);
        message.values.put("subject", Map.of("fr", "Avis de cotisation " + id, "nl", "Bijdragebericht " + id));
        message.values.put("sender", Map.of("eboxType", "enterprise", "enterpriseNumber", "0206731645"));
        message.values.put("recipient", Map.of("eboxType", "enterprise", "enterpriseNumber", "0212345678", "partition", partition));
        message.values.put("senderApplicationId", "onss-cotisations");
        message.values.put("messageTypeId", "avis");
        message.values.put("receiptDate", receipt);
        message.values.put("expirationDate", "2027-11-19T08:00:00+01:00");
        message.values.put("isRead", false);
        message.values.put("hasMessageActions", true);
        message.values.put("hasBody", true);
        message.attachment("a-1", "avis-" + id + ".pdf", "application/pdf", 12.5);
        return message;
    }

    /** Message eBox sous forme de carte, converti en JSON à la lecture. */
    private static final class ObjectMapperNode {
        final Map<String, Object> values = new LinkedHashMap<>();
        final List<Map<String, Object>> attachments = new ArrayList<>();

        ObjectMapperNode() {
            values.put("attachments", attachments);
        }

        void attachment(String id, String name, String type, double sizeKb) {
            attachments.add(Map.of("attachmentId", id, "fileName", name, "isMainContent", attachments.isEmpty(), "mediaType", type,
                    "size", sizeKb));
        }
    }

    private final class FakeEbox implements EboxClient {
        final List<JsonNode> registries = new ArrayList<>();
        final Map<String, List<ObjectMapperNode>> byPartition = new LinkedHashMap<>();
        final List<String> listedPartitions = new ArrayList<>();
        final List<String> endpointsListed = new ArrayList<>();
        final List<LocalDate> receivedAfter = new ArrayList<>();
        final List<Integer> pagesRequested = new ArrayList<>();
        final List<String> downloads = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        final List<String> tokensSeen = new ArrayList<>();
        final List<String> onBehalfOf = new ArrayList<>();
        boolean unauthorizedOnce;

        void add(String partition, ObjectMapperNode message) {
            byPartition.computeIfAbsent(partition, key -> new ArrayList<>()).add(message);
        }

        private void seen(Access access) {
            tokensSeen.add(access.token());
            onBehalfOf.add(access.onBehalfOf());
            if (unauthorizedOnce) {
                unauthorizedOnce = false;
                throw new EboxUnauthorizedException();
            }
        }

        @Override
        public List<JsonNode> registries(String registryUrl, Access access) {
            seen(access);
            assertThat(registryUrl).isEqualTo(REGISTRY);
            return registries;
        }

        @Override
        public MessagePage messages(String endpointUrl, Access access, String partition, LocalDate after, int page, int pageSize) {
            seen(access);
            endpointsListed.add(endpointUrl);
            listedPartitions.add(partition);
            receivedAfter.add(after);
            pagesRequested.add(page);
            List<ObjectMapperNode> all = byPartition.getOrDefault(partition, List.of());
            List<JsonNode> items = all.stream().skip((long) (page - 1) * pageSize).limit(pageSize)
                    .map(node -> (JsonNode) json.valueToTree(node.values)).toList();
            return new MessagePage(items, all.size());
        }

        @Override
        public byte[] attachment(String endpointUrl, Access access, String messageId, String attachmentId, long maxBytes) {
            seen(access);
            downloads.add(messageId + "/" + attachmentId);
            return ("%PDF-" + messageId + "/" + attachmentId).getBytes(StandardCharsets.US_ASCII);
        }

        @Override
        public Optional<JsonNode> body(String endpointUrl, Access access, String messageId) {
            seen(access);
            bodies.add(messageId);
            return Optional.of(json.valueToTree(Map.of("fr", "Veuillez trouver ci-joint votre avis.", "nl", "Zie bijlage.")));
        }
    }

    private static final class MemoryJournal implements EboxReadJournal {
        final List<EboxTakenMessage> taken = new ArrayList<>();
        final Map<String, LocalDate> cursors = new HashMap<>();

        @Override
        public Set<String> alreadyTaken(String tenantId, String enterpriseNumber, String registryId, Collection<String> messageIds) {
            Set<String> found = new HashSet<>();
            for (EboxTakenMessage message : taken) {
                if (message.enterpriseNumber().equals(enterpriseNumber) && message.registryId().equals(registryId)
                        && messageIds.contains(message.messageId())) {
                    found.add(message.messageId());
                }
            }
            return found;
        }

        @Override
        public boolean recordTaken(EboxTakenMessage message) {
            if (alreadyTaken(message.tenantId(), message.enterpriseNumber(), message.registryId(), List.of(message.messageId())).isEmpty()) {
                taken.add(message);
                return true;
            }
            return false;
        }

        @Override
        public Optional<LocalDate> readUntil(UUID connectorInstanceId, String registryId, String partition) {
            return Optional.ofNullable(cursors.get(connectorInstanceId + "/" + registryId + "/" + partition));
        }

        @Override
        public void advance(UUID connectorInstanceId, String registryId, String partition, LocalDate readUntil) {
            cursors.merge(connectorInstanceId + "/" + registryId + "/" + partition, readUntil, (a, b) -> a.isAfter(b) ? a : b);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            MutableClock self = this;
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return zone;
                }

                @Override
                public Clock withZone(ZoneId other) {
                    return self.withZone(other);
                }

                @Override
                public Instant instant() {
                    return self.now;
                }
            };
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
