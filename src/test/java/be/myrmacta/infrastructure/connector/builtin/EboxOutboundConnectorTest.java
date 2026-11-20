package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.ebox.model.EboxDelivery;
import be.myrmacta.domain.ebox.model.EboxLanguage;
import be.myrmacta.domain.ebox.model.EboxProofOfDispatch;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxSendAuditEntry.Outcome;
import be.myrmacta.domain.ebox.model.EboxSendOutcome;
import be.myrmacta.domain.ebox.model.FallbackChannel;
import be.myrmacta.domain.ebox.model.SendEboxCommand;
import be.myrmacta.domain.ebox.port.in.SendEboxMessageUseCase;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** TODO §155 (lot 2) — type de connecteur {@code ebox-outbound} : configuration du compte expéditeur et envoi depuis un process. */
class EboxOutboundConnectorTest {

    private static final String TENANT = "tenant_dev";
    private static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-0000000000e5");
    private static final UUID DOSSIER = UUID.fromString("00000000-0000-0000-0000-00000000d055");
    private static final String REF = "tenant_dev/doc-1";
    private static final byte[] PDF = "%PDF-1.7 décision".getBytes(StandardCharsets.UTF_8);

    private final SendEboxMessageUseCase send = mock(SendEboxMessageUseCase.class);
    private final DossierElementRepository elements = mock(DossierElementRepository.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final OutboundUrlGuard guard =
            new OutboundUrlGuard(false, host -> new InetAddress[] {InetAddress.getByName("193.191.208.10")});
    private final EboxOutboundConnector connector = new EboxOutboundConnector(send, elements, storage, guard);
    private final ConnectorInstanceData instance =
            new ConnectorInstanceData(INSTANCE, TENANT, "eBox envoi", "ebox-outbound", true, Map.of());

    @BeforeEach
    void setUp() {
        when(elements.findByDossierIdOrderByAddedAtAsc(DOSSIER)).thenReturn(List.of(element(ElementType.PRODUCED_DOC, REF,
                "Décision \"permis\"/2026")));
        when(storage.retrieve(TENANT, REF)).thenReturn(PDF);
        when(send.send(any())).thenReturn(new EboxSendOutcome(Outcome.PUBLISHED, EboxDelivery.EBOX_AND_FALLBACK,
                FallbackChannel.PAPER, "msg-1", null, false));
    }

    private static Map<String, Object> config() {
        Map<String, Object> config = new HashMap<>();
        config.put("publicationUrl", "https://services.socialsecurity.be/REST/ebox/enterprise/messageRegistry/v3");
        config.put("federationUrl", "https://services.socialsecurity.be/REST/ebox/enterprise/federation/v1");
        config.put("oauthTokenUrl", "https://services.socialsecurity.be/REST/oauth/v5/token");
        config.put("oauthClientId", "myrmacta-commune");
        config.put("oauthPrivateKey", "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n");
        config.put("oauthCertThumbprint", "a".repeat(40));
        return config;
    }

    private static Map<String, Object> input() {
        Map<String, Object> input = new HashMap<>();
        input.put("recipientType", "ENTREPRISE");
        input.put("recipient", "0212.345.678");
        input.put("documentRef", REF);
        input.put("subject", "Votre permis");
        input.put("senderApplicationId", "myrmacta-commune");
        input.put("messageTypeId", "decision");
        input.put("language", "nl");
        input.put("registered", false);
        input.put("fallbackChannel", "PAPER");
        input.put("idempotencyKey", "myr1-abc");
        return input;
    }

    @Test
    void describesAServiceTypeWithTheSenderAccountSettings() {
        assertThat(connector.getType()).isEqualTo("ebox-outbound");
        assertThat(connector.getCapability()).isEqualTo(ConnectorCapability.SERVICE);
        assertThat(connector.getConfigSchema()).extracting(ConfigField::name).containsExactly("publicationUrl", "federationUrl",
                "registeredMailUrl", "onBehalfOf", "allowedHostSuffixes", "oauthTokenUrl", "oauthClientId", "oauthPrivateKey",
                "oauthCertThumbprint", "oauthScope", "registeredMailScope");
        assertThat(connector.getConfigSchema()).filteredOn(f -> f.name().equals("oauthPrivateKey"))
                .extracting(ConfigField::type).containsExactly(ConfigFieldType.SECRET_TEXTAREA);
    }

    @Test
    void aCompleteConfigurationIsAccepted() {
        connector.validate(config());
        Map<String, Object> withRegistered = config();
        withRegistered.put("registeredMailUrl", "https://services.socialsecurity.be/REST/registeredMail/v1");
        withRegistered.put("onBehalfOf", "0207.654.321");
        connector.validate(withRegistered);
    }

    @Test
    void anIncompleteOrUnsafeConfigurationIsRefused() {
        assertThatThrownBy(() -> connector.validate(null)).isInstanceOf(ConnectorValidationException.class);
        for (String key : List.of("publicationUrl", "federationUrl", "oauthTokenUrl", "oauthClientId", "oauthPrivateKey",
                "oauthCertThumbprint")) {
            Map<String, Object> config = config();
            config.remove(key);
            assertThatThrownBy(() -> connector.validate(config)).as(key).isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining(key);
        }
        Map<String, Object> foreign = config();
        foreign.put("publicationUrl", "https://ebox.attacker.example/REST");
        assertThatThrownBy(() -> connector.validate(foreign)).isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("publicationUrl");
        Map<String, Object> widened = config();
        widened.put("allowedHostSuffixes", "attacker.example");
        assertThatThrownBy(() -> connector.validate(widened)).isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("allowedHostSuffixes");
        Map<String, Object> secret = config();
        secret.put("oauthClientAuthMethod", "client_secret_post");
        assertThatThrownBy(() -> connector.validate(secret)).isInstanceOf(ConnectorValidationException.class);
        Map<String, Object> mandate = config();
        mandate.put("onBehalfOf", "12");
        assertThatThrownBy(() -> connector.validate(mandate)).isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("onBehalfOf");
    }

    @Test
    void aProcessStepSendsTheProducedPdfOfItsDossier() {
        Map<String, Object> result = connector.execute(instance, input(), ConnectorCallContext.processWithBuiltRequest(DOSSIER));

        ArgumentCaptor<SendEboxCommand> captor = ArgumentCaptor.forClass(SendEboxCommand.class);
        verify(send).send(captor.capture());
        SendEboxCommand command = captor.getValue();
        assertThat(command.sender().connectorInstanceId()).isEqualTo(INSTANCE);
        assertThat(command.dossierId()).isEqualTo(DOSSIER);
        assertThat(command.idempotencyKey()).isEqualTo("myr1-abc");
        assertThat(command.fallbackChannel()).isEqualTo(FallbackChannel.PAPER);
        assertThat(command.message().recipient()).isEqualTo(new EboxRecipient.Enterprise("0212345678"));
        assertThat(command.message().language()).isEqualTo(EboxLanguage.NL);
        assertThat(command.message().subject()).isEqualTo("Votre permis");
        assertThat(command.message().registered()).isFalse();
        assertThat(command.message().documents()).hasSize(1);
        assertThat(command.message().mainDocument().content()).isEqualTo(PDF);
        assertThat(command.message().mainDocument().fileName()).isEqualTo("Décision _permis__2026.pdf");
        assertThat(command.message().mainDocument().mediaType()).isEqualTo("application/pdf");

        assertThat(result).containsEntry("eboxIssue", "ENVOYE").containsEntry("eboxLivraison", "EBOX_AND_FALLBACK")
                .containsEntry("eboxSecoursRequis", true).containsEntry("eboxVoieSecours", "PAPER")
                .containsEntry("eboxEnvoiMessageId", "msg-1").containsEntry("eboxEnvoiRejoue", false)
                .doesNotContainKey("eboxPreuveDepot");
    }

    @Test
    void theOutcomeTellsTheProcessWhatToDo() {
        when(send.send(any())).thenReturn(new EboxSendOutcome(Outcome.FALLBACK, EboxDelivery.FALLBACK, FallbackChannel.EMAIL,
                null, null, false));
        Map<String, Object> input = input();
        input.put("recipientType", "CITOYEN");
        input.put("recipient", "85.07.30-033.28");
        input.put("fallbackChannel", "EMAIL");
        assertThat(connector.execute(instance, input, ConnectorCallContext.processWithBuiltRequest(DOSSIER)))
                .containsEntry("eboxIssue", "VOIE_SECOURS").containsEntry("eboxSecoursRequis", true)
                .containsEntry("eboxVoieSecours", "EMAIL").containsEntry("eboxEnvoiMessageId", "");

        EboxProofOfDispatch proof = new EboxProofOfDispatch("myr1-abc", "rm-1", "b".repeat(64), "{\"signature\":\"s\"}");
        when(send.send(any())).thenReturn(new EboxSendOutcome(Outcome.DUPLICATE, EboxDelivery.EBOX, FallbackChannel.PAPER,
                "rm-1", proof, true));
        input.put("recipientType", "ENTREPRISE");
        input.put("recipient", "0212345678");
        input.put("registered", true);
        assertThat(connector.execute(instance, input, ConnectorCallContext.processWithBuiltRequest(DOSSIER)))
                .containsEntry("eboxIssue", "DEJA_ENVOYE").containsEntry("eboxSecoursRequis", false)
                .containsEntry("eboxVoieSecours", "").containsEntry("eboxEnvoiRejoue", true)
                .containsEntry("eboxPreuveDepot", "{\"signature\":\"s\"}");
    }

    @Test
    void onlyAProcessStepBuiltByTheBpmnMaySend() {
        for (ConnectorCallContext context : List.of(ConnectorCallContext.direct(), ConnectorCallContext.process(DOSSIER),
                ConnectorCallContext.processWithBuiltRequest(null))) {
            assertThatThrownBy(() -> connector.execute(instance, input(), context)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> connector.execute(instance, input())).isInstanceOf(IllegalArgumentException.class);
        verify(send, never()).send(any());
    }

    @Test
    void theDocumentMustBeAProducedPdfOfThisDossier() {
        Map<String, Object> other = input();
        other.put("documentRef", "tenant_dev/other");
        assertThatThrownBy(() -> connector.execute(instance, other, ConnectorCallContext.processWithBuiltRequest(DOSSIER)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("produced document of this dossier")
                .hasMessageNotContaining("tenant_dev/other");

        when(elements.findByDossierIdOrderByAddedAtAsc(DOSSIER)).thenReturn(List.of(element(ElementType.ATTACHMENT, REF, "Pièce")));
        assertThatThrownBy(() -> connector.execute(instance, input(), ConnectorCallContext.processWithBuiltRequest(DOSSIER)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("produced document");

        when(elements.findByDossierIdOrderByAddedAtAsc(DOSSIER)).thenReturn(List.of(element(ElementType.PRODUCED_DOC, REF, "x")));
        when(storage.retrieve(TENANT, REF)).thenReturn("<html>".getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> connector.execute(instance, input(), ConnectorCallContext.processWithBuiltRequest(DOSSIER)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a PDF");
        verify(send, never()).send(any());
    }

    @Test
    void anInvalidRequestIsRefusedWithoutEchoingTheNationalNumber() {
        Map<String, Object> input = input();
        input.put("recipientType", "CITOYEN");
        input.put("recipient", "85073003329");
        assertThatThrownBy(() -> connector.execute(instance, input, ConnectorCallContext.processWithBuiltRequest(DOSSIER)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("85073003329");
        for (Map.Entry<String, Object> bad : Map.<String, Object>of("recipientType", "AUTRE", "fallbackChannel", "FAX",
                "language", "en", "subject", " ").entrySet()) {
            Map<String, Object> wrong = input();
            wrong.put(bad.getKey(), bad.getValue());
            assertThatThrownBy(() -> connector.execute(instance, wrong, ConnectorCallContext.processWithBuiltRequest(DOSSIER)))
                    .as(bad.getKey()).isInstanceOf(IllegalArgumentException.class);
        }
        verify(send, never()).send(any());
    }

    private static DossierElement element(ElementType type, String ref, String label) {
        return new DossierElement(UUID.randomUUID(), DOSSIER, TENANT, type, label, ref, Map.of(), "system",
                Instant.parse("2026-11-20T09:00:00Z"), "step");
    }
}
