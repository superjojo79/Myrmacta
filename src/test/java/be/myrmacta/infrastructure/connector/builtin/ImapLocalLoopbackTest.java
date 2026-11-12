package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.infrastructure.config.ConnectorProperties;
import be.myrmacta.infrastructure.config.InboundConnectorProperties;
import be.myrmacta.infrastructure.connector.mapping.SimpleJsonPathMapper;
import be.myrmacta.infrastructure.notification.OutboundUrlGuard;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §128 (D-IMAP-LOCAL-LOOPBACK), TODO §132 — en environnement local, le vrai connecteur IMAP relève une boîte sur la
 * boucle locale, sans chiffrement (le GreenMail de la démo vidéo). Le profil {@code test} avec {@code staging} refuse
 * la même boîte avant toute connexion.
 */
class ImapLocalLoopbackTest {

    @RegisterExtension
    static final GreenMailExtension GREEN = new GreenMailExtension(ServerSetupTest.SMTP_IMAP);

    private static ImapInboundConnector connector(String appEnvironment, String... profiles) {
        boolean allow = OutboundUrlGuard.allowsLocalLoopback(appEnvironment, profiles);
        return new ImapInboundConnector(new SimpleJsonPathMapper(), new OutboundUrlGuard(allow), JsonMapper.builder().build(),
                new ConnectorProperties(null, null, null, null, null, null), InboundConnectorProperties.defaults(),
                new AngusImapMailbox());
    }

    private static ConnectorInstanceData mailbox() {
        Map<String, Object> config = new HashMap<>();
        config.put("host", "localhost");
        config.put("port", ServerSetupTest.IMAP.getPort());
        config.put("security", "PLAIN");
        config.put("username", "courrier");
        config.put("password", "demo");
        config.put("processKey", "triCourrier");
        config.put("dossierTypeCode", "COURRIER");
        config.put("trustedAuthservId", "mx.demo.example");
        config.put("mapping", Map.of("fields", Map.of("objet", "$.subject", "expediteur", "$.from")));
        return new ConnectorInstanceData(UUID.randomUUID(), "t1", "Boîte courrier", "imap-inbound", true, config);
    }

    private static void deliverInvoice() throws Exception {
        GreenMailUser user = GREEN.setUser("courrier@demo.example", "courrier", "demo");
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("compta@fournisseur.example"));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress("courrier@demo.example"));
        message.setSubject("Facture F-117");
        message.setText("Veuillez trouver notre facture.");
        message.setHeader("Authentication-Results", "mx.demo.example; dkim=pass header.d=fournisseur.example");
        user.deliver(message);
    }

    @Test
    void inALocalProfile_theConnectorReadsALoopbackMailboxWithoutEncryption() throws Exception {
        deliverInvoice();
        ImapInboundConnector local = connector("local", "local");
        local.validate(mailbox().config());
        List<WorkflowTriggerEvent> events = new ArrayList<>();

        local.poll(mailbox(), events::add);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.dossierFields()).containsEntry("objet", "Facture F-117").containsEntry("expediteur", "compta@fournisseur.example");
            assertThat(event.variables()).containsEntry("mailSenderAuthenticated", true);
        });
    }

    @Test
    void outsideALocalProfile_theSameMailboxIsRefusedBeforeAnyConnection() throws Exception {
        deliverInvoice();
        List<WorkflowTriggerEvent> events = new ArrayList<>();

        assertThatThrownBy(() -> connector("staging", "test").poll(mailbox(), events::add))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("public");

        assertThat(events).isEmpty();
        assertThat(GREEN.getReceivedMessages()).hasSize(1);
    }
}
