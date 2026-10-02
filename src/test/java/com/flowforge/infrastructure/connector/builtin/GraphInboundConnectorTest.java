package com.flowforge.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.InboundAttachment;
import com.flowforge.domain.connector.model.WorkflowTriggerEvent;
import com.flowforge.infrastructure.config.ConnectorProperties;
import com.flowforge.infrastructure.config.InboundConnectorProperties;
import com.flowforge.infrastructure.connector.mapping.SimpleJsonPathMapper;
import com.flowforge.infrastructure.notification.OutboundUrlGuard;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** TODO §87 (D3c-bis) — connecteur Microsoft Graph : validation, hôtes Graph seuls, jeton OAuth2, routage partagé avec l'IMAP. */
class GraphInboundConnectorTest {

    private final List<ImapMailbox.Mail> inbox = new ArrayList<>();
    private final List<ImapMailbox.Outcome> outcomes = new ArrayList<>();
    private final List<GraphMailbox.Settings> seen = new ArrayList<>();
    private final List<HttpRestConnector.HttpCallRequest> tokenCalls = new ArrayList<>();
    private GraphInboundConnector connector;

    @BeforeEach
    void setUp() throws Exception {
        GraphMailbox mailbox = (settings, max, limits, handler) -> {
            seen.add(settings);
            for (ImapMailbox.Mail mail : inbox) {
                outcomes.add(handler.handle(mail));
            }
        };
        OutboundUrlGuard guard = new OutboundUrlGuard(false, host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});
        HttpRestConnector.HttpSender token = request -> {
            tokenCalls.add(request);
            return new HttpRestConnector.HttpCallResponse(200, "{\"access_token\":\"graph-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}");
        };
        connector = new GraphInboundConnector(new SimpleJsonPathMapper(), guard, JsonMapper.builder().build(),
                new ConnectorProperties(null, null, null, null, null, null), InboundConnectorProperties.defaults(), mailbox, token,
                Clock.systemUTC());
    }

    private static Map<String, Object> config() {
        Map<String, Object> c = new HashMap<>();
        c.put("mailbox", "dossiers@example.com");
        c.put("oauthTokenUrl", "https://login.microsoftonline.com/t/oauth2/v2.0/token");
        c.put("oauthClientId", "app");
        c.put("oauthClientSecret", "s");
        c.put("oauthScope", "https://graph.microsoft.com/.default");
        c.put("processKey", "reclamation");
        c.put("dossierTypeCode", "RECLAMATION");
        c.put("referencePattern", "\\[(DOS-[0-9-]+)\\]");
        c.put("trustedAuthservId", "spf.protection.outlook.com");
        c.put("mapping", Map.of("fields", Map.of("objet", "$.subject", "texte", "$.body", "expediteur", "$.from")));
        return c;
    }

    private static ConnectorInstanceData instance(Map<String, Object> config) {
        return new ConnectorInstanceData(UUID.randomUUID(), "t1", "Boîte Graph", "graph-mail-inbound", true, config);
    }

    private static ImapMailbox.Mail mail(String subject, boolean automatic) {
        return new ImapMailbox.Mail("<g@x>", "client@example.org", "Client", subject, Instant.parse("2026-10-01T10:00:00Z"), "Bonjour",
                automatic, "spf.protection.outlook.com; dmarc=pass header.from=example.org", List.<InboundAttachment>of(), 0);
    }

    private List<WorkflowTriggerEvent> events(ConnectorInstanceData instance) {
        List<WorkflowTriggerEvent> events = new ArrayList<>();
        connector.poll(instance, events::add);
        return events;
    }

    @Test
    void aValidConfigPasses_andTheSchemaDeclaresGraphAndOAuthFields() {
        connector.validate(config());
        assertThat(connector.getType()).isEqualTo("graph-mail-inbound");
        assertThat(connector.getConfigSchema()).extracting(f -> f.name())
                .contains("mailbox", "graphBaseUrl", "folder", "oauthClientSecret", "referencePattern", "processKey")
                .doesNotContain("password", "host");
    }

    @Test
    void invalidConfigsAreRefusedWithAReason() {
        for (var entry : Map.<String, Object>of(
                "mailbox", "a b/../c", "graphBaseUrl", "https://evil.example/v1.0", "pollSeconds", "5", "referencePattern", "(a)(b)",
                "folder", "bad\nname", "authMode", "PASSWORD", "maxAttachments", "99").entrySet()) {
            Map<String, Object> c = config();
            c.put(entry.getKey(), entry.getValue());
            assertThatThrownBy(() -> connector.validate(c)).as(entry.getKey()).isInstanceOf(ConnectorValidationException.class);
        }
        for (String missing : List.of("oauthClientSecret", "oauthScope", "oauthTokenUrl", "mailbox", "processKey")) {
            Map<String, Object> c = config();
            c.remove(missing);
            assertThatThrownBy(() -> connector.validate(c)).as(missing).isInstanceOf(ConnectorValidationException.class);
        }
        Map<String, Object> same = config();
        same.put("processedFolder", "INBOX");
        assertThatThrownBy(() -> connector.validate(same)).hasMessageContaining("differ");
        for (String system : List.of("Inbox", "drafts", "Deleted Items")) {
            Map<String, Object> c = config();
            c.put("folder", "Autre");
            c.put("processedFolder", system);
            assertThatThrownBy(() -> connector.validate(c)).as(system).hasMessageContaining("system folder");
        }
        Map<String, Object> noAuthserv = config();
        noAuthserv.remove("trustedAuthservId");
        assertThatThrownBy(() -> connector.validate(noAuthserv)).hasMessageContaining("trustedAuthservId");
    }

    @Test
    void onlyGraphHostsOverHttpsAreAcceptedAsEndpoint_theTokenNeverGoesAnywhereElse() {
        for (String ok : List.of("https://graph.microsoft.com/v1.0", "https://graph.microsoft.us/v1.0", "https://microsoftgraph.chinacloudapi.cn/v1.0")) {
            Map<String, Object> c = config();
            c.put("graphBaseUrl", ok);
            connector.validate(c);
        }
        for (String bad : List.of("http://graph.microsoft.com/v1.0", "https://graph.microsoft.com.evil.example/v1.0",
                "https://graph.microsoft.com:8443/v1.0", "https://graph.microsoft.com/v1.0#x",
                "https://user@graph.microsoft.com/v1.0", "https://graph.microsoft.com/v1.0?x=1", "https://127.0.0.1/v1.0")) {
            Map<String, Object> c = config();
            c.put("graphBaseUrl", bad);
            assertThatThrownBy(() -> connector.validate(c)).as(bad).isInstanceOf(ConnectorValidationException.class);
        }
    }

    @Test
    void pollingFetchesATokenAndPassesItWithTheSettingsToTheMailbox_neverLoggedInTheirToString() {
        events(instance(config()));

        assertThat(tokenCalls).hasSize(1);
        assertThat(tokenCalls.getFirst().url()).endsWith("/t/oauth2/v2.0/token"); // adresse épinglée par OutboundUrlGuard
        GraphMailbox.Settings settings = seen.getFirst();
        assertThat(settings.accessToken()).isEqualTo("graph-token");
        assertThat(settings.baseUrl()).isEqualTo("https://graph.microsoft.com/v1.0");
        assertThat(settings.mailbox()).isEqualTo("dossiers@example.com");
        assertThat(settings.folder()).isEqualTo("Inbox");
        assertThat(settings.toString()).doesNotContain("graph-token");
    }

    @Test
    void anEndpointThatIsNoLongerAGraphHostIsRefusedBeforeAnyRequest() {
        Map<String, Object> c = config();
        c.put("graphBaseUrl", "https://evil.example/v1.0");
        assertThatThrownBy(() -> events(instance(c))).isInstanceOf(IllegalStateException.class);
        assertThat(tokenCalls).isEmpty();
        assertThat(seen).isEmpty();
    }

    @Test
    void sharedRoutingApplies_aReferenceFromAnAttestedSenderJoinsTheDossier_automaticRepliesAreRefused() {
        inbox.add(mail("Re: [DOS-2026-0042] suite", false));
        inbox.add(mail("Absence du bureau", true));

        List<WorkflowTriggerEvent> events = events(instance(config()));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().existingDossierReference()).isEqualTo("DOS-2026-0042");
        assertThat(outcomes).containsExactly(ImapMailbox.Outcome.PROCESSED, ImapMailbox.Outcome.REJECTED);
    }

    @Test
    void aSenderNotAttestedByTheTrustedServerCannotJoinAnExistingDossier() {
        inbox.add(new ImapMailbox.Mail("<g@x>", "attaquant@evil.example", "X", "[DOS-2026-0042] suite", null, "x", false,
                "other.example; dmarc=pass header.from=evil.example", List.of(), 0));

        WorkflowTriggerEvent event = events(instance(config())).getFirst();

        assertThat(event.existingDossierReference()).isNull();
        assertThat(event.variables()).containsEntry("mailReferenceIgnored", "DOS-2026-0042").containsEntry("mailSenderAuthenticated", false);
    }
}
