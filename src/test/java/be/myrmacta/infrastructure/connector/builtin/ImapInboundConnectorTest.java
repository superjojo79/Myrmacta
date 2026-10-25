package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.InboundAttachment;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.config.InboundConnectorProperties;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** TODO §87 (D3c) — connecteur IMAP : validation, mapping, corrélation, refus des messages automatiques, bornes. */
class ImapInboundConnectorTest {

    private final List<ImapMailbox.Mail> inbox = new ArrayList<>();
    private final List<ImapMailbox.Outcome> outcomes = new ArrayList<>();
    private final List<ImapMailbox.Settings> seen = new ArrayList<>();
    private ImapInboundConnector connector;

    @BeforeEach
    void setUp() throws Exception {
        ImapMailbox mailbox = (settings, max, limits, handler) -> {
            seen.add(settings);
            for (ImapMailbox.Mail mail : inbox) {
                outcomes.add(handler.handle(mail));
            }
        };
        OutboundUrlGuard guard = new OutboundUrlGuard(false, host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});
        connector = new ImapInboundConnector(new SimpleJsonPathMapper(), guard, JsonMapper.builder().build(),
                new ConnectorProperties(null, null, null, null, null, null), InboundConnectorProperties.defaults(), mailbox);
    }

    private static Map<String, Object> config() {
        Map<String, Object> c = new HashMap<>();
        c.put("host", "imap.example.com");
        c.put("security", "SSL");
        c.put("username", "claims@example.com");
        c.put("password", "pw");
        c.put("processKey", "reclamation");
        c.put("dossierTypeCode", "RECLAMATION");
        c.put("referencePattern", "\\[(DOS-[0-9-]+)\\]");
        c.put("trustedAuthservId", "mx.example.com");
        c.put("mapping", Map.of("fields", Map.of("objet", "$.subject", "texte", "$.body", "expediteur", "$.from")));
        return c;
    }

    private static ConnectorInstanceData instance(Map<String, Object> config) {
        return new ConnectorInstanceData(UUID.randomUUID(), "t1", "Boîte réclamations", "imap-inbound", true, config);
    }

    private static ImapMailbox.Mail mail(String subject, String body, boolean automatic, List<InboundAttachment> attachments) {
        return new ImapMailbox.Mail("<id@x>", "client@example.org", "Client", subject, Instant.parse("2026-10-01T10:00:00Z"), body,
                automatic, "mx.example.com; dmarc=pass header.from=example.org; dkim=pass header.d=example.org", attachments, 0);
    }

    private List<WorkflowTriggerEvent> events(ConnectorInstanceData instance) {
        List<WorkflowTriggerEvent> events = new ArrayList<>();
        connector.poll(instance, events::add);
        return events;
    }

    // ---- validation ----

    @Test
    void aValidConfigPasses_andTheDeclaredSchemaMatches() {
        connector.validate(config());
        assertThat(connector.getType()).isEqualTo("imap-inbound");
        assertThat(connector.getConfigSchema()).extracting(f -> f.name()).contains("host", "password", "oauthClientSecret", "referencePattern");
        assertThat(connector.getConfigSchema().stream().filter(f -> f.name().equals("password")).findFirst().orElseThrow().type().name())
                .isEqualTo("PASSWORD");
    }

    @Test
    void invalidConfigsAreRefusedWithAReason() {
        for (var entry : Map.<String, Object>of(
                "host", "imap.example.com/../x", "security", "NONE", "port", "70000", "pollSeconds", "5",
                "referencePattern", "(a)(b)", "folder", "bad\nname").entrySet()) {
            Map<String, Object> c = config();
            c.put(entry.getKey(), entry.getValue());
            assertThatThrownBy(() -> connector.validate(c)).as(entry.getKey()).isInstanceOf(ConnectorValidationException.class);
        }
        Map<String, Object> noPassword = config();
        noPassword.remove("password");
        assertThatThrownBy(() -> connector.validate(noPassword)).isInstanceOf(ConnectorValidationException.class);
        Map<String, Object> sameFolder = config();
        sameFolder.put("processedFolder", "INBOX");
        assertThatThrownBy(() -> connector.validate(sameFolder)).hasMessageContaining("differ");
        Map<String, Object> tooLong = config();
        tooLong.put("referencePattern", "a".repeat(201));
        assertThatThrownBy(() -> connector.validate(tooLong)).isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void aHostThatResolvesToAPrivateAddressIsRefused() throws Exception {
        OutboundUrlGuard strict = new OutboundUrlGuard(false, host -> new InetAddress[] {InetAddress.getByName("10.0.0.5")});
        var c = new ImapInboundConnector(new SimpleJsonPathMapper(), strict, JsonMapper.builder().build(),
                new ConnectorProperties(null, null, null, null, null, null), InboundConnectorProperties.defaults(), (a, b, d, e) -> { });
        assertThatThrownBy(() -> c.validate(config())).hasMessageContaining("public");
    }

    @Test
    void oauthModeRequiresItsSettings_andNeverNeedsAPassword() {
        Map<String, Object> c = config();
        c.remove("password");
        c.put("authMode", "OAUTH2_CLIENT_CREDENTIALS");
        assertThatThrownBy(() -> connector.validate(c)).isInstanceOf(ConnectorValidationException.class);
        c.put("oauthTokenUrl", "https://login.microsoftonline.com/t/oauth2/v2.0/token");
        c.put("oauthClientId", "app");
        c.put("oauthClientSecret", "s");
        connector.validate(c);
    }

    // ---- traitement ----

    @Test
    void aMessageBecomesAnEventWithMappedFieldsVariablesAndAttachments() {
        var file = new InboundAttachment("devis.pdf", "application/pdf", new byte[] {1, 2});
        inbox.add(mail("Panne de chauffage", "Bonjour, ma chaudière est en panne.", false, List.of(file)));

        List<WorkflowTriggerEvent> events = events(instance(config()));

        assertThat(events).hasSize(1);
        var event = events.getFirst();
        assertThat(event.processKey()).isEqualTo("reclamation");
        assertThat(event.dossierTypeCode()).isEqualTo("RECLAMATION");
        assertThat(event.dossierFields()).containsEntry("objet", "Panne de chauffage").containsEntry("expediteur", "client@example.org")
                .containsEntry("texte", "Bonjour, ma chaudière est en panne.");
        assertThat(event.variables()).containsEntry("mailSubject", "Panne de chauffage").containsEntry("mailFrom", "client@example.org");
        assertThat(event.attachments()).containsExactly(file);
        assertThat(event.existingDossierReference()).isNull();
        assertThat(event.createWhenReferenceUnknown()).isTrue();
        assertThat(outcomes).containsExactly(ImapMailbox.Outcome.PROCESSED);
        assertThat(seen.getFirst().host()).isEqualTo("imap.example.com");
        assertThat(seen.getFirst().toString()).doesNotContain("pw");
    }

    @Test
    void theDossierReferenceInTheSubjectCorrelatesTheMessage() {
        inbox.add(mail("RE: [DOS-2026-0042] Votre demande", "Merci", false, List.of()));
        var event = events(instance(config())).getFirst();
        assertThat(event.existingDossierReference()).isEqualTo("DOS-2026-0042");
    }

    @Test
    void automaticRepliesListsAndSenderlessMessagesAreRefusedNeverADossier() {
        inbox.add(mail("Absence du bureau", "je suis absent", true, List.of()));
        inbox.add(new ImapMailbox.Mail("<n@x>", "", "", "sans expéditeur", null, "x", false, "mx.example.com; dmarc=pass header.from=example.org; dkim=pass header.d=example.org", List.of(), 0));
        assertThat(events(instance(config()))).isEmpty();
        assertThat(outcomes).containsExactly(ImapMailbox.Outcome.REJECTED, ImapMailbox.Outcome.REJECTED);
    }

    @Test
    void aMappedFieldThatIsEmptyRefusesTheMessage() {
        inbox.add(mail("", "corps", false, List.of())); // objet vide, mappé
        assertThat(events(instance(config()))).isEmpty();
        assertThat(outcomes).containsExactly(ImapMailbox.Outcome.REJECTED);
    }

    @Test
    void aCatastrophicReferencePatternNeverBlocksTheMailbox() {
        Map<String, Object> c = config();
        c.put("referencePattern", "(a+)+$"); // retour arrière exponentiel sur un objet de « a » suivi d'un autre caractère
        inbox.add(mail("a".repeat(400) + "!", "corps", false, List.of()));
        long start = System.nanoTime();
        var events = events(instance(c));
        assertThat((System.nanoTime() - start) / 1_000_000).as("ms").isLessThan(5_000);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().existingDossierReference()).isNull();
    }

    @Test
    void skippedAttachmentsAreSignalledToTheProcess() {
        inbox.add(new ImapMailbox.Mail("<id>", "c@x.org", "C", "objet", null, "corps", false, "mx.example.com; dmarc=pass header.from=example.org; dkim=pass header.d=example.org", List.of(), 2));
        var event = events(instance(config())).getFirst();
        assertThat(event.variables()).containsEntry("inboundAttachmentsSkipped", 2);
    }

    // ---- revue adversariale D3c/D3d ----

    @Test
    void anUnauthenticatedSenderCannotJoinAnExistingDossier_aNewOneIsCreatedAndTheProcessIsTold() {
        inbox.add(new ImapMailbox.Mail("<s>", "attaquant@evil.example", "X", "[DOS-2026-0042] urgent", null, "corps", false, "", List.of(), 0));
        var event = events(instance(config())).getFirst();
        assertThat(event.existingDossierReference()).as("jamais rattaché sans attestation de notre serveur").isNull();
        assertThat(event.variables()).containsEntry("mailReferenceIgnored", "DOS-2026-0042").containsEntry("mailSenderAuthenticated", false);
    }

    @Test
    void theAdminCanAcceptTheRiskExplicitly() {
        Map<String, Object> c = config();
        c.put("requireAuthenticatedSender", "false");
        inbox.add(new ImapMailbox.Mail("<s>", "x@y.org", "X", "[DOS-1] suite", null, "corps", false, "", List.of(), 0));
        assertThat(events(instance(c)).getFirst().existingDossierReference()).isEqualTo("DOS-1");
    }

    @Test
    void aMessageTheRuntimeCannotTakeInChargeIsNotAcknowledgedAsProcessed() {
        inbox.add(mail("Panne", "corps", false, List.of()));
        connector.poll(instance(config()), event -> {
            throw new IllegalStateException("unknown dossier type");
        });
        assertThat(outcomes).containsExactly(ImapMailbox.Outcome.REJECTED);
    }

    @Test
    void theHostIsCheckedAgainAtEveryPoll_aNameThatTurnsPrivateIsRefusedBeforeAnyConnection() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> address = new java.util.concurrent.atomic.AtomicReference<>("93.184.216.34");
        OutboundUrlGuard flipping = new OutboundUrlGuard(false, host -> new InetAddress[] {InetAddress.getByName(address.get())});
        var c = new ImapInboundConnector(new SimpleJsonPathMapper(), flipping, JsonMapper.builder().build(),
                new ConnectorProperties(null, null, null, null, null, null), InboundConnectorProperties.defaults(),
                (a, b, d, e) -> seen.add(a));
        c.validate(config());
        address.set("127.0.0.1");
        assertThatThrownBy(() -> c.poll(instance(config()), e -> { })).isInstanceOf(IllegalStateException.class).hasMessageContaining("public");
        assertThat(seen).isEmpty();
    }

    @Test
    void joiningADossierNeedsTheTrustedServerIdUnlessTheRiskIsAcceptedExplicitly() {
        Map<String, Object> c = config();
        c.remove("trustedAuthservId");
        assertThatThrownBy(() -> connector.validate(c)).hasMessageContaining("trustedAuthservId");
        c.put("requireAuthenticatedSender", "false");
        connector.validate(c);
    }

    @Test
    void folderNamesDifferingOnlyByCaseAreTheSameFolder() {
        Map<String, Object> c = config();
        c.put("processedFolder", "inbox");
        assertThatThrownBy(() -> connector.validate(c)).hasMessageContaining("differ");
        Map<String, Object> same = config();
        same.put("processedFolder", "Traités");
        same.put("rejectedFolder", "traités");
        assertThatThrownBy(() -> connector.validate(same)).hasMessageContaining("differ");
        assertThatThrownBy(() -> connector.poll(instance(c), e -> { })).isInstanceOf(IllegalStateException.class);
        assertThat(seen).isEmpty();
    }
}
