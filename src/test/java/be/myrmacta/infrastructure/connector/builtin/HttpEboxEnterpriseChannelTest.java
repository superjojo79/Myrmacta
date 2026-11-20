package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.ebox.model.EboxConsent;
import be.myrmacta.domain.ebox.model.EboxDelivery;
import be.myrmacta.domain.ebox.model.EboxDocument;
import be.myrmacta.domain.ebox.model.EboxLanguage;
import be.myrmacta.domain.ebox.model.EboxOutgoingMessage;
import be.myrmacta.domain.ebox.model.EboxPublication;
import be.myrmacta.domain.ebox.model.EboxPublicationRejectedException;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry.Outcome;
import be.myrmacta.domain.ebox.model.EboxSendOutcome;
import be.myrmacta.domain.ebox.model.EboxSendUncertainException;
import be.myrmacta.domain.ebox.model.EboxSenderAccount;
import be.myrmacta.domain.ebox.model.FallbackChannel;
import be.myrmacta.domain.ebox.model.SendEboxCommand;
import be.myrmacta.domain.ebox.port.out.EboxSendAuditTrail;
import be.myrmacta.domain.ebox.usecase.SendEboxMessageUseCaseImpl;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.connector.builtin.FakeEboxSenderServer.Received;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §155 (D-EBOX-SEND-CHANNELS, D-EBOX-SEND-IDEMPOTENCE, D-EBOX-SEND-REGISTERED) — voie entreprise contre le faux serveur :
 * vraie chaîne HTTP (jeton OAuth v5 signé, adresse figée, multipart), préférence eBox, publication, doublon 409, issue incertaine,
 * recommandé et preuve de dépôt.
 */
class HttpEboxEnterpriseChannelTest {

    private static final String TENANT = "tenant_dev";
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-0000000000e5");
    private static final EboxSenderAccount SENDER = new EboxSenderAccount(INSTANCE);
    private static final EboxRecipient.Enterprise RECIPIENT = new EboxRecipient.Enterprise("0212345678");
    private static final byte[] PDF = "%PDF-1.7 décision".getBytes(StandardCharsets.UTF_8);
    private static String privateKeyPem;

    private FakeEboxSenderServer ebox;
    private final Map<String, Object> config = new HashMap<>();
    private HttpEboxEnterpriseChannel channel;
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-20T09:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    @BeforeAll
    static void key() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        privateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    @BeforeEach
    void start() throws Exception {
        ebox = new FakeEboxSenderServer();
        config.put("publicationUrl", ebox.publicationUrl());
        config.put("federationUrl", ebox.federationUrl() + "/");
        config.put("registeredMailUrl", ebox.registeredMailUrl());
        config.put("onBehalfOf", "0207.654.321");
        config.put("oauthTokenUrl", ebox.tokenUrl());
        config.put("oauthClientId", "myrmacta-commune");
        config.put("oauthPrivateKey", privateKeyPem);
        config.put("oauthCertThumbprint", "a".repeat(40));
        channel = channel(Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() {
        ebox.close();
    }

    private HttpEboxEnterpriseChannel channel(Duration total) {
        OutboundUrlGuard guard = new OutboundUrlGuard(true);
        var mapper = JsonMapper.builder().build();
        ConnectorProperties properties = new ConnectorProperties(null, null, null, null, null, null);
        OAuth2TokenProvider tokens = new OAuth2TokenProvider(guard, mapper, properties, PinnedHttpClient.sender(), clock);
        return new HttpEboxEnterpriseChannel(new Instances(), guard, mapper, tokens, Clock.systemUTC(), Duration.ofSeconds(2),
                Duration.ofSeconds(2), total);
    }

    private static EboxOutgoingMessage.Builder message() {
        return EboxOutgoingMessage.builder(RECIPIENT, "myrmacta-commune", "decision", EboxLanguage.NL, "Uw vergunning")
                .document(new EboxDocument("beslissing é.pdf", "application/pdf", PDF, true))
                .document(new EboxDocument("bijlage.pdf", "application/pdf", "%PDF annex".getBytes(StandardCharsets.US_ASCII), false));
    }

    @Test
    void thePreferenceDecidesTheConsentLevel_andAnUnknownEnterpriseHasNoEbox() {
        ebox.preferences.put("0212345678", true);
        ebox.preferences.put("0312345678", false);

        assertThat(channel.consent(TENANT, SENDER, RECIPIENT).level()).isEqualTo(EboxConsent.Level.EXCLUSIVE_EBOX);
        assertThat(channel.consent(TENANT, SENDER, new EboxRecipient.Enterprise("0312345678")).level())
                .isEqualTo(EboxConsent.Level.EBOX_NOT_EXCLUSIVE);
        EboxConsent none = channel.consent(TENANT, SENDER, new EboxRecipient.Enterprise("0412345678"));
        assertThat(none.level()).isEqualTo(EboxConsent.Level.NO_EBOX);
        assertThat(none.source()).isEqualTo("federation-1.4");

        Received search = ebox.requestsTo("/eboxPreferences/search").getFirst();
        assertThat(search.body()).isEqualTo("{\"eboxType\":\"ENTERPRISE\",\"eboxIdType\":\"EnterpriseNumber\",\"eboxIdValueList\":[\"0212345678\"]}");
        assertThat(search.headers()).containsEntry("on-behalf-of-enterprise-number", "0207654321")
                .containsEntry("content-type", "application/json");
    }

    @Test
    void aMessageIsPublishedAsMultipart_withItsIdempotencyKeyAndMandate() {
        EboxPublication publication = channel.publish(TENANT, SENDER, message().body("Zie bijlage").replyAuthorized(true).build(),
                "dossier-42:send-1");

        assertThat(publication.status()).isEqualTo(EboxPublication.Status.PUBLISHED);
        assertThat(publication.providerMessageId()).isEqualTo("ebox-msg-1");
        assertThat(publication.responseCode()).isEqualTo(201);
        assertThat(publication.trace().url()).isEqualTo(ebox.publicationUrl() + "/ebox/messages");
        assertThat(publication.trace().clientId()).isEqualTo("myrmacta-commune");
        assertThat(publication.trace().onBehalfOf()).isEqualTo("0207654321");
        assertThat(publication.trace().tokenHash()).isEqualTo(HttpEboxEnterpriseChannel.sha256Hex("fake-send-token-1"));
        assertThat(ebox.scopes).containsExactly(EboxOutboundSettings.DEFAULT_SCOPE);

        Received post = ebox.requestsTo("/ebox/messages").getFirst();
        assertThat(post.headers()).containsEntry("idempotency-key", "dossier-42:send-1")
                .containsEntry("on-behalf-of-enterprise-number", "0207654321")
                .containsEntry("authorization", "Bearer fake-send-token-1");
        assertThat(post.headers().get("content-type")).startsWith("multipart/form-data; boundary=myrmacta-");
        String body = new String(post.body().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        assertThat(body).contains("name=\"messageToPublish\"", "\"recipient\":{\"eboxType\":\"enterprise\",\"enterpriseNumber\":\"0212345678\"}",
                "\"subject\":{\"nl\":\"Uw vergunning\"}", "\"bodyContent\":{\"nl\":\"Zie bijlage\"}", "\"isReplyAuthorized\":true",
                "\"originalMessageId\":\"dossier-42:send-1\"", "\"httpPartName\":\"upfile1\"", "\"isMainContent\":true",
                "\"digestMethod\":\"sha_256\"", "name=\"upfile1\"; filename=\"beslissing é.pdf\"",
                "filename*=UTF-8''beslissing%20%C3%A9.pdf", "name=\"upfile2\"; filename=\"bijlage.pdf\"", "%PDF-1.7 décision");
        assertThat(body).doesNotContain("\"nl\":null", "\"fr\"");
    }

    @Test
    void theSameKeyTwiceIsADuplicate() {
        channel.publish(TENANT, SENDER, message().build(), "k-dup");

        EboxPublication again = channel.publish(TENANT, SENDER, message().build(), "k-dup");

        assertThat(again.status()).isEqualTo(EboxPublication.Status.DUPLICATE);
        assertThat(again.responseCode()).isEqualTo(409);
        assertThat(again.providerMessageId()).isNull();
    }

    @Test
    void aRefusalIsCertain_andItsDetailIsNeverRelayed() {
        ebox.nextPublicationStatus = 400;

        assertThatThrownBy(() -> channel.publish(TENANT, SENDER, message().build(), "k-400"))
                .isInstanceOf(EboxPublicationRejectedException.class)
                .hasMessage("ebox refused the publication (HTTP 400)");
    }

    @Test
    void aServerErrorOrATimeoutIsUncertain() {
        ebox.nextPublicationStatus = 503;
        assertThat(channel.publish(TENANT, SENDER, message().build(), "k-503").status()).isEqualTo(EboxPublication.Status.UNCERTAIN);

        HttpEboxEnterpriseChannel impatient = channel(Duration.ofMillis(800));
        ebox.nextPublicationDelayMillis = 2_500;
        EboxPublication timedOut = impatient.publish(TENANT, SENDER, message().build(), "k-slow");

        assertThat(timedOut.status()).isEqualTo(EboxPublication.Status.UNCERTAIN);
        assertThat(timedOut.responseCode()).isEqualTo(EboxPublication.TIMEOUT);
    }

    @Test
    void aRejectedTokenIsRenewedOnce() {
        channel.consent(TENANT, SENDER, RECIPIENT);
        now.set(now.get().plusSeconds(60));
        ebox.unauthorizedBeforeAccept = 1;

        assertThat(channel.publish(TENANT, SENDER, message().build(), "k-401").status()).isEqualTo(EboxPublication.Status.PUBLISHED);
        assertThat(ebox.scopes).hasSize(2);

        ebox.unauthorizedBeforeAccept = 5;
        assertThatThrownBy(() -> channel.publish(TENANT, SENDER, message().build(), "k-401b")).hasMessageContaining("HTTP 401");
    }

    @Test
    void aRegisteredMailReturnsItsProofOfDispatch_andIsNeverSentTwice() {
        EboxPublication first = channel.publish(TENANT, SENDER, message().registered(true).build(), "k-rm");

        assertThat(first.status()).isEqualTo(EboxPublication.Status.PUBLISHED);
        assertThat(first.providerMessageId()).isEqualTo("rm-1");
        assertThat(first.proof().customerReference()).isEqualTo("k-rm");
        assertThat(first.proof().signatureSha256()).isEqualTo(HttpEboxEnterpriseChannel.sha256Hex("signed-proof-1"));
        assertThat(first.trace().onBehalfOf()).isNull();
        assertThat(ebox.scopes).contains(EboxOutboundSettings.DEFAULT_REGISTERED_MAIL_SCOPE);
        String body = new String(ebox.requestsTo("/registeredMails").getFirst().body().getBytes(StandardCharsets.ISO_8859_1),
                StandardCharsets.UTF_8);
        assertThat(body).contains("name=\"registeredMailToPublish\"", "\"customerReference\":\"k-rm\"", "name=\"mainContent\"",
                "name=\"annex1\"", "\"method\":\"sha256\"");

        EboxPublication again = channel.publish(TENANT, SENDER, message().registered(true).build(), "k-rm");

        assertThat(again.status()).isEqualTo(EboxPublication.Status.DUPLICATE);
        assertThat(again.proof().providerMessageId()).isEqualTo("rm-1");
        assertThat(ebox.requestsTo("/registeredMails")).hasSize(1);
    }

    @Test
    void aRegisteredMailNeedsItsService() {
        config.remove("registeredMailUrl");

        assertThatThrownBy(() -> channel.publish(TENANT, SENDER, message().registered(true).build(), "k-rm2"))
                .isInstanceOf(EboxPublicationRejectedException.class).hasMessageContaining("registeredMailUrl");
    }

    @Test
    void theTokenOnlyGoesToAllowedHttpsHosts() {
        HttpEboxEnterpriseChannel strict = new HttpEboxEnterpriseChannel(new Instances(), new OutboundUrlGuard(false),
                JsonMapper.builder().build(), new OAuth2TokenProvider(new OutboundUrlGuard(false), JsonMapper.builder().build(),
                        new ConnectorProperties(null, null, null, null, null, null), PinnedHttpClient.sender(), Clock.systemUTC()),
                Clock.systemUTC(), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1));
        config.put("publicationUrl", "https://ebox.attacker.example/REST");
        config.put("federationUrl", "https://services.socialsecurity.be/REST/ebox/enterprise/federation/v1");
        config.put("oauthTokenUrl", "https://services.socialsecurity.be/REST/oauth/v5/token");

        assertThatThrownBy(() -> strict.publish(TENANT, SENDER, message().build(), "k-host"))
                .hasMessageContaining("publicationUrl must be an https URL on an allowed domain");
        config.put("publicationUrl", "http://services.socialsecurity.be/REST");
        assertThatThrownBy(() -> strict.publish(TENANT, SENDER, message().build(), "k-host"))
                .hasMessageContaining("publicationUrl must be an https URL");
        assertThat(ebox.requests).isEmpty();
    }

    @Test
    void theInstanceMustBeAnEnabledEboxSender_andTheRecipientAnEnterprise() {
        assertThatThrownBy(() -> channel.consent(TENANT, new EboxSenderAccount(UUID.randomUUID()), RECIPIENT))
                .hasMessageContaining("not found");
        config.put("__type", "http-rest");
        assertThatThrownBy(() -> channel.consent(TENANT, SENDER, RECIPIENT)).hasMessageContaining("ebox-outbound");
        config.remove("__type");
        config.put("__enabled", false);
        assertThatThrownBy(() -> channel.consent(TENANT, SENDER, RECIPIENT)).hasMessageContaining("disabled");
        config.remove("__enabled");
        assertThatThrownBy(() -> channel.consent(TENANT, SENDER, new EboxRecipient.Citizen("85073003328")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theUseCaseDrivesTheChannel_withAPaperCopyForANonExclusiveEnterprise() {
        ebox.preferences.put("0212345678", false);
        List<EboxSendAuditEntry> entries = new ArrayList<>();
        EboxSendAuditTrail audit = new EboxSendAuditTrail() {
            @Override
            public void append(EboxSendAuditEntry entry) {
                entries.add(entry);
            }

            @Override
            public Optional<EboxSendAuditEntry.Summary> last(String tenantId, String key, EboxRecipient recipient) {
                return entries.stream().filter(e -> e.idempotencyKey().equals(key)).reduce((a, b) -> b).map(e -> e.summary(recipient));
            }
        };
        SendEboxMessageUseCaseImpl useCase = new SendEboxMessageUseCaseImpl(TENANT, List.of(channel), audit,
                Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC));
        SendEboxCommand command = new SendEboxCommand(SENDER, message().build(), "k-flow", null, FallbackChannel.PAPER);

        EboxSendOutcome outcome = useCase.send(command);

        assertThat(outcome.outcome()).isEqualTo(Outcome.PUBLISHED);
        assertThat(outcome.delivery()).isEqualTo(EboxDelivery.EBOX_AND_FALLBACK);
        assertThat(outcome.fallbackChannel()).isEqualTo(FallbackChannel.PAPER);
        assertThat(entries.getFirst().publication().trace().tokenHash()).hasSize(64);
        assertThat(entries.getFirst().consent().level()).isEqualTo(EboxConsent.Level.EBOX_NOT_EXCLUSIVE);

        ebox.nextPublicationDelayMillis = 0;
        ebox.nextPublicationStatus = 502;
        SendEboxCommand second = new SendEboxCommand(SENDER, message().build(), "k-flow-2", null, FallbackChannel.PAPER);
        assertThatThrownBy(() -> useCase.send(second)).isInstanceOf(EboxSendUncertainException.class);
        EboxSendOutcome retried = useCase.send(second);
        assertThat(retried.outcome()).isEqualTo(Outcome.PUBLISHED);
        assertThat(entries).extracting(EboxSendAuditEntry::outcome)
                .containsExactly(Outcome.PUBLISHED, Outcome.UNCERTAIN, Outcome.PUBLISHED);
    }

    /** Dépôt d'instances minimal : une seule instance, dont le type et l'état se règlent par {@code __type}/{@code __enabled}. */
    private final class Instances implements ConnectorInstanceRepository {

        @Override
        public Optional<ConnectorInstanceData> findByIdAndTenantId(UUID id, String tenantId) {
            if (!INSTANCE.equals(id) || !TENANT.equals(tenantId)) {
                return Optional.empty();
            }
            Map<String, Object> copy = new HashMap<>(config);
            String type = String.valueOf(copy.getOrDefault("__type", EboxOutboundSettings.TYPE));
            boolean enabled = !Boolean.FALSE.equals(copy.get("__enabled"));
            copy.remove("__type");
            copy.remove("__enabled");
            return Optional.of(new ConnectorInstanceData(id, tenantId, "eBox envoi", type, enabled, copy));
        }

        @Override
        public ConnectorInstanceData save(ConnectorInstanceData data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ConnectorInstanceData> findByIdAndTenantIdIncludingDeleted(UUID id, String tenantId) {
            return findByIdAndTenantId(id, tenantId);
        }

        @Override
        public List<ConnectorInstanceData> listByTenantId(String tenantId) {
            return List.of();
        }

        @Override
        public void softDelete(UUID id, String tenantId) {
            throw new UnsupportedOperationException();
        }
    }
}
