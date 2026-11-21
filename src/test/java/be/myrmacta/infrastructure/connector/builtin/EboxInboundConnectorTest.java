package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.PollingTriggerConnector.EventDelivery;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
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
    private final List<ConnectorInstanceData> instances = new ArrayList<>();
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
        return new EboxInboundConnector(ebox, journal, new MemoryInstances(), new SimpleJsonPathMapper(), json, guard, inbound, tokens,
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
    void aConfiguredPartitionUnknownToTheEboxIsNotRead() {
        Map<String, Object> config = config();
        config.put("partitionApiUrl", "https://services.socialsecurity.be/REST/ebox/enterprise/partition/v1");
        ebox.knownPartitions = List.of("myrmacta");

        poll(config);

        assertThat(ebox.listedPartitions).containsOnly("myrmacta");
    }

    @Test
    void anUnavailablePartitionListRemovesNothing() {
        Map<String, Object> config = config();
        config.put("partitionApiUrl", "https://services.socialsecurity.be/REST/ebox/enterprise/partition/v1");
        ebox.partitionsUnavailable = true;

        poll(config);

        assertThat(ebox.listedPartitions).containsExactlyInAnyOrder("myrmacta", "myrmacta-rh");
    }

    @Test
    void anEmptyPartitionListRemovesNothing() {
        Map<String, Object> config = config();
        config.put("partitionApiUrl", "https://services.socialsecurity.be/REST/ebox/enterprise/partition/v1");
        ebox.knownPartitions = List.of();

        poll(config);

        assertThat(ebox.listedPartitions).containsExactlyInAnyOrder("myrmacta", "myrmacta-rh");
    }

    @Test
    void aRefusedTokenIsRenewedOnceForThePartitionCheck() {
        Map<String, Object> config = config();
        config.put("partitionApiUrl", "https://services.socialsecurity.be/REST/ebox/enterprise/partition/v1");
        ebox.knownPartitions = List.of("myrmacta");
        poll(config);
        // Un jeton n'est renouvelé de force qu'après quelques minutes (OAuth2TokenProvider).
        clock.set(clock.instant().plus(Duration.ofMinutes(5)));
        ebox.listedPartitions.clear();
        ebox.tokensSeen.clear();
        ebox.unauthorizedOnce = true;

        poll(config);

        assertThat(tokenRequests).hasSize(2);
        assertThat(ebox.tokensSeen.subList(0, 2)).containsExactly("token-1", "token-2");
        assertThat(ebox.listedPartitions).containsExactly("myrmacta");
    }

    @Test
    void thePartitionApiMustBeOnAnAllowedDomain() {
        Map<String, Object> config = config();
        config.put("partitionApiUrl", "https://evil.example.com/partition/v1");

        assertThatThrownBy(() -> connector.validate(config)).isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("partitionApiUrl");
    }

    @Test
    void theRegisteredMailTypeIsExposed_notApplicableIsNotRegistered() {
        ObjectMapperNode qualified = message("m-q", "myrmacta", "2026-11-19T08:00:00+01:00");
        qualified.values.put("registeredMailType", "qualifiedRegisteredMailNotification");
        ObjectMapperNode plain = message("m-p", "myrmacta", "2026-11-19T09:00:00+01:00");
        plain.values.put("registeredMailType", "notApplicable");
        ebox.add("myrmacta", qualified);
        ebox.add("myrmacta", plain);

        List<WorkflowTriggerEvent> events = poll(config());

        assertThat(events.get(0).variables()).containsEntry("eboxRecommande", true)
                .containsEntry("eboxTypeRecommande", "qualifiedRegisteredMailNotification");
        assertThat(events.get(1).variables()).containsEntry("eboxRecommande", false).doesNotContainKey("eboxTypeRecommande");
    }

    @Test
    void aMessageWithoutPartitionIsTreatedAsForeign() {
        ObjectMapperNode message = message("m-main", "myrmacta", "2026-11-19T08:00:00+01:00");
        message.values.put("recipient", Map.of("eboxType", "enterprise", "enterpriseNumber", "0212345678"));
        ebox.add("myrmacta", message);

        assertThat(poll(config())).isEmpty();
        assertThat(ebox.downloads).isEmpty();
    }

    @Test
    void attachmentsBeyondTheMessageCapAreNamedNotKept() {
        connector = connector(new InboundConnectorProperties(20, 20_000L, 20_000, 10_485_760L, 30, 1_048_576L));
        ObjectMapperNode message = message("m-heavy", "myrmacta", "2026-11-19T08:00:00+01:00");
        message.attachment("a-2", "annexe.pdf", "application/pdf", 19.6);
        ebox.add("myrmacta", message);

        WorkflowTriggerEvent event = poll(config()).getFirst();

        assertThat(event.attachments()).extracting("filename").containsExactly("avis-m-heavy.pdf");
        assertThat(event.variables()).containsEntry("eboxPiecesNonImportees", List.of("annexe.pdf"));
    }

    @Test
    void aStuckCursorNeverWidensTheWindowBeyondTheMaximumLookback() {
        journal.cursors.put(cursorKey("myrmacta"), TODAY.minusDays(400));

        poll(config());

        assertThat(ebox.receivedAfter.getFirst()).isEqualTo(TODAY.minusDays(90));
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

    // --- action faite (SERVICE) --------------------------------------------------------------------------------------

    private Map<String, Object> markAction(String messageId, String actionId) {
        Map<String, Object> input = new HashMap<>();
        input.put("operation", "markActionExecuted");
        input.put("registry", "socsec_v3");
        input.put("messageId", messageId);
        if (actionId != null) {
            input.put("actionId", actionId);
        }
        return connector.execute(instance(config()), input);
    }

    private JsonNode actionStatus(String id, boolean executed) {
        return json.valueToTree(Map.of("messageActionId", id, "isExecuted", executed));
    }

    @Test
    void theConnectorIsAlsoAServiceForTheActionDoneStep() {
        assertThat(connector.getCapability()).isEqualTo(ConnectorCapability.BOTH);
    }

    @Test
    void theNamedActionOfATakenMessageIsConfirmedOnItsRegistry() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());

        assertThat(markAction("m-1", "act-1")).containsEntry("eboxActionExecutee", true).containsEntry("eboxActionId", "act-1");
        assertThat(ebox.confirmed).containsExactly(ENDPOINT + " m-1/act-1");
    }

    @Test
    void withoutActionIdTheOnlyOpenActionIsConfirmed() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());
        ebox.actionStatuses.add(actionStatus("done-before", true));
        ebox.actionStatuses.add(actionStatus("act-2", false));

        markAction("m-1", null);

        assertThat(ebox.confirmed).containsExactly(ENDPOINT + " m-1/act-2");
    }

    @Test
    void withoutActionIdSeveralOpenActionsAreAmbiguousAndAllDoneIsANoOp() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());
        ebox.actionStatuses.add(actionStatus("a", false));
        ebox.actionStatuses.add(actionStatus("b", false));
        assertThatThrownBy(() -> markAction("m-1", null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("2 open actions");

        ebox.actionStatuses.clear();
        ebox.actionStatuses.add(actionStatus("a", true));
        assertThat(markAction("m-1", null)).containsEntry("eboxActionDejaFaite", true);
        assertThat(ebox.confirmed).isEmpty();
    }

    @Test
    void aMessageThisTenantDidNotTakeIsNeverTouched() {
        assertThatThrownBy(() -> markAction("m-404", "act-1")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not taken");
        assertThat(ebox.confirmed).isEmpty();
        assertThat(ebox.tokensSeen).isEmpty();
    }

    @Test
    void onlyAProcessStepMayConfirmAnAction() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());
        Map<String, Object> input = Map.of("operation", "markActionExecuted", "registry", "socsec_v3", "messageId", "m-1",
                "actionId", "act-1");

        assertThatThrownBy(() -> connector.execute(instance(config()), input, ConnectorCallContext.direct()))
                .isInstanceOf(IllegalArgumentException.class);
        connector.execute(instance(config()), input, ConnectorCallContext.process(null));
        assertThat(ebox.confirmed).hasSize(1);
    }

    @Test
    void anOpenActionWithAnInvalidIdMakesTheChoiceAmbiguous() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());
        ebox.actionStatuses.add(actionStatus("\u0000bad", false));
        ebox.actionStatuses.add(actionStatus("act-2", false));

        assertThatThrownBy(() -> markAction("m-1", null)).isInstanceOf(IllegalStateException.class);
        assertThat(ebox.confirmed).isEmpty();
    }

    @Test
    void aTakenMessageIsMovedToAPartitionThatIsNotRead() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());

        Map<String, Object> result = connector.execute(instance(config()), Map.of("operation", "moveToPartition", "registry",
                "socsec_v3", "messageId", "m-1", "targetPartition", "myrmacta-traite"));

        assertThat(result).containsEntry("eboxClasse", true);
        assertThat(ebox.moved).containsExactly("m-1->myrmacta-traite");
    }

    @Test
    void aMoveToAReadPartitionOrOfAnUntakenMessageIsRefused() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());

        assertThatThrownBy(() -> connector.execute(instance(config()), Map.of("operation", "moveToPartition", "registry", "socsec_v3",
                "messageId", "m-1", "targetPartition", "myrmacta-rh"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be one of the partitions read");
        assertThatThrownBy(() -> connector.execute(instance(config()), Map.of("operation", "moveToPartition", "registry", "socsec_v3",
                "messageId", "m-404", "targetPartition", "myrmacta-traite"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.execute(instance(config()), Map.of("operation", "moveToPartition", "registry", "socsec_v3",
                "messageId", "m-1", "targetPartition", "bad partition"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(ebox.moved).isEmpty();
    }

    private Map<String, Object> move(String messageId, String target) {
        return connector.execute(instance(config()), Map.of("operation", "moveToPartition", "registry", "socsec_v3", "messageId",
                messageId, "targetPartition", target));
    }

    @Test
    void aReplayedMoveOfAMessageAlreadyInTheTargetSucceeds() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());
        for (int status : List.of(400, 404, 409)) {
            ebox.moveRefusal = status;
            ebox.currentPartition.put("m-1", "myrmacta-traite");

            assertThat(move("m-1", "myrmacta-traite")).as("HTTP " + status).containsEntry("eboxClasse", true)
                    .containsEntry("eboxDejaClasse", true);
        }
        assertThat(ebox.reread).containsExactly("m-1", "m-1", "m-1");
    }

    @Test
    void aRefusedMoveOfAMessageOutsideTheTargetFailsWithAnExplicitMessage() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());
        ebox.currentPartition.put("m-1", "myrmacta");

        ebox.moveRefusal = 409;
        assertThatThrownBy(() -> move("m-1", "myrmacta-traite")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 409").hasMessageContaining("myrmacta-traite");
        ebox.moveRefusal = 400;
        assertThatThrownBy(() -> move("m-1", "myrmacta-traite")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 400");
        ebox.moveRefusal = 404;
        assertThatThrownBy(() -> move("m-1", "myrmacta-traite")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 404").hasMessageContaining("does not exist");
        ebox.currentPartition.remove("m-1");
        assertThatThrownBy(() -> move("m-1", "myrmacta-traite")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 404").hasMessageContaining("no longer");
        ebox.moveRefusal = 410;
        assertThatThrownBy(() -> move("m-1", "myrmacta-traite")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 410").hasMessageContaining("no longer");
        assertThat(ebox.moved).isEmpty();
    }

    @Test
    void aServerErrorOnAMoveFailsWithoutRereading() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());
        ebox.moveTransportFailure = true;

        assertThatThrownBy(() -> move("m-1", "myrmacta-traite")).isInstanceOf(EboxClient.EboxTransportException.class);
        assertThat(ebox.reread).isEmpty();
    }

    @Test
    void theNormalMoveIsNotMarkedAsAlreadyDone() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());

        assertThat(move("m-1", "myrmacta-traite")).containsEntry("eboxClasse", true).containsEntry("eboxDejaClasse", false);
        assertThat(ebox.reread).isEmpty();
    }

    @Test
    void aMoveToAPartitionReadByAnotherActiveInstanceOfTheSameEboxIsRefused() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());
        Map<String, Object> archives = config();
        archives.put("partitions", "archives");
        instances.add(new ConnectorInstanceData(UUID.randomUUID(), "t1", "eBox archives", "ebox-inbound", true, archives));

        assertThatThrownBy(() -> move("m-1", "archives")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("read by an active ebox-inbound instance");
        assertThat(ebox.moved).isEmpty();
    }

    @Test
    void aPartitionReadByADisabledInstanceAnotherEboxOrAnotherTypeIsAValidTarget() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());
        Map<String, Object> archives = config();
        archives.put("partitions", "archives");
        instances.add(new ConnectorInstanceData(UUID.randomUUID(), "t1", "eBox éteinte", "ebox-inbound", false, archives));
        Map<String, Object> otherEbox = new HashMap<>(archives);
        otherEbox.put("enterpriseNumber", "0999999999");
        instances.add(new ConnectorInstanceData(UUID.randomUUID(), "t1", "autre eBox", "ebox-inbound", true, otherEbox));
        instances.add(new ConnectorInstanceData(UUID.randomUUID(), "t1", "REST", "http-rest", true, Map.of("partitions", "archives")));
        instances.add(new ConnectorInstanceData(UUID.randomUUID(), "t1", "invalide", "ebox-inbound", true, Map.of()));

        assertThat(move("m-1", "archives")).containsEntry("eboxClasse", true);
        assertThat(ebox.moved).containsExactly("m-1->archives");
    }

    @Test
    void aMessageTakenFromAPartitionThisInstanceDoesNotReadIsNotMoved() {
        journal.taken.add(new EboxTakenMessage("t1", "0212345678", "socsec_v3", "m-9", "archives", UUID.randomUUID(),
                Instant.parse("2026-11-20T08:00:00Z")));

        assertThatThrownBy(() -> move("m-9", "myrmacta-traite")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not read from a partition of this instance");
        assertThat(ebox.moved).isEmpty();
    }

    @Test
    void aMappingCannotSetTheResultVariablesOfTheEboxSteps() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        Map<String, Object> config = config();
        config.put("mapping", Map.of("variables", Map.of("eboxClasse", "$.registre", "eboxDejaClasse", "$.registre",
                "eboxActionExecutee", "$.registre", "eboxActionDejaFaite", "$.registre")));

        WorkflowTriggerEvent event = poll(config).getFirst();

        assertThat(event.variables()).doesNotContainKeys("eboxClasse", "eboxDejaClasse", "eboxActionExecutee", "eboxActionDejaFaite");
    }

    @Test
    void anUnknownOperationOrAMissingMessageIsRefused() {
        assertThatThrownBy(() -> connector.execute(instance(config()), Map.of("operation", "delete", "registry", "socsec_v3",
                "messageId", "m-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.execute(instance(config()), Map.of("operation", "markActionExecuted")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theActionOfARegistryWithoutActiveEndpointFails() {
        ebox.add("myrmacta", message("m-1", "myrmacta", "2026-11-19T08:00:00+01:00"));
        poll(config());
        ebox.registries.clear();

        assertThatThrownBy(() -> markAction("m-1", "act-1")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no active endpoint");
    }

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
        final List<JsonNode> actionStatuses = new ArrayList<>();
        final List<String> confirmed = new ArrayList<>();
        final List<String> moved = new ArrayList<>();
        final List<String> reread = new ArrayList<>();
        final Map<String, String> currentPartition = new HashMap<>();
        int moveRefusal;
        boolean moveTransportFailure;
        List<String> knownPartitions = List.of("myrmacta", "myrmacta-rh");
        boolean partitionsUnavailable;
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

        @Override
        public List<String> partitions(String partitionApiUrl, Access access, String enterpriseNumber) {
            seen(access);
            assertThat(enterpriseNumber).isEqualTo("0212345678");
            if (partitionsUnavailable) {
                throw new EboxClient.EboxTransportException("ebox answered HTTP 503");
            }
            return knownPartitions;
        }

        @Override
        public void moveToPartition(String endpointUrl, Access access, String messageId, String targetPartition) {
            seen(access);
            if (moveTransportFailure) {
                throw new EboxClient.EboxTransportException("ebox answered HTTP 503");
            }
            if (moveRefusal != 0) {
                throw new EboxClient.EboxResourceException(moveRefusal);
            }
            moved.add(messageId + "->" + targetPartition);
        }

        @Override
        public Optional<JsonNode> message(String endpointUrl, Access access, String messageId) {
            seen(access);
            reread.add(messageId);
            String partition = currentPartition.get(messageId);
            return partition == null ? Optional.empty()
                    : Optional.of(json.valueToTree(Map.of("messageId", messageId, "recipient", Map.of("partition", partition))));
        }

        @Override
        public List<JsonNode> actionStatuses(String endpointUrl, Access access, String messageId) {
            seen(access);
            return actionStatuses;
        }

        @Override
        public void markActionExecuted(String endpointUrl, Access access, String messageId, String actionId) {
            seen(access);
            confirmed.add(endpointUrl + " " + messageId + "/" + actionId);
        }
    }

    /** Instances du tenant : celle du test, puis celles que le test ajoute. */
    private final class MemoryInstances implements ConnectorInstanceRepository {
        @Override
        public ConnectorInstanceData save(ConnectorInstanceData data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ConnectorInstanceData> findByIdAndTenantId(UUID id, String tenantId) {
            return listByTenantId(tenantId).stream().filter(candidate -> candidate.id().equals(id)).findFirst();
        }

        @Override
        public Optional<ConnectorInstanceData> findByIdAndTenantIdIncludingDeleted(UUID id, String tenantId) {
            return findByIdAndTenantId(id, tenantId);
        }

        @Override
        public List<ConnectorInstanceData> listByTenantId(String tenantId) {
            List<ConnectorInstanceData> all = new ArrayList<>();
            all.add(instance(config()));
            all.addAll(instances);
            return all.stream().filter(candidate -> candidate.tenantId().equals(tenantId)).toList();
        }

        @Override
        public void softDelete(UUID id, String tenantId) {
            throw new UnsupportedOperationException();
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
        public Optional<EboxTakenMessage> taken(String tenantId, String enterpriseNumber, String registryId, String messageId) {
            return taken.stream().filter(message -> message.tenantId().equals(tenantId) && message.enterpriseNumber().equals(enterpriseNumber)
                    && message.registryId().equals(registryId) && message.messageId().equals(messageId)).findFirst();
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
