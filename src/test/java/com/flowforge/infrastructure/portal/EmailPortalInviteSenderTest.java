package com.flowforge.infrastructure.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.flowforge.domain.notification.port.out.EmailPort;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.port.out.PortalInviteSenderPort.InviteContext;
import com.flowforge.infrastructure.config.PortalProperties;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** TODO §68 — contenu de l'e-mail d'invitation et neutralisation des retours à la ligne. */
class EmailPortalInviteSenderTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private final EmailPort emailPort = mock(EmailPort.class);
    private final EmailPortalInviteSender sender = new EmailPortalInviteSender(emailPort,
            new PortalProperties(true, "https://ff.example.org/tenant-a/", null, null, null, null, null, 0));
    private final PortalLink link = PortalLink.pendingSend(UUID.randomUUID(), "tenant_dev", UUID.randomUUID(), "t",
            "pi", "avis", "alice@example.org", 7, NOW, "x");

    @Test
    void email_carriesTheFragmentLinkAndExpiry_toTheRecipient() {
        sender.send(link, "TOKEN123", new InviteContext("Avis du tiers", "DOS-42", Instant.parse("2026-10-01T10:00:00Z")));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailPort).send(eq("alice@example.org"), subject.capture(), body.capture());
        assertThat(subject.getValue()).isEqualTo("Avis du tiers — dossier DOS-42");
        assertThat(body.getValue())
                .contains("https://ff.example.org/tenant-a/portal/#TOKEN123")
                .contains("1 octobre 2026")
                .contains("(dossier DOS-42)");
    }

    @Test
    void newlinesInTaskNameOrReference_areFlattened_noHeaderInjection() {
        sender.send(link, "T", new InviteContext("Avis\r\nBcc: evil@x.org", "REF\n1", NOW));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(emailPort).send(eq("alice@example.org"), subject.capture(), org.mockito.ArgumentMatchers.anyString());
        assertThat(subject.getValue()).doesNotContain("\r").doesNotContain("\n").isEqualTo("Avis Bcc: evil@x.org — dossier REF 1");
    }

    @Test
    void missingTaskNameAndReference_useNeutralDefaults() {
        sender.send(link, "T", new InviteContext(null, "", NOW));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailPort).send(eq("alice@example.org"), subject.capture(), body.capture());
        assertThat(subject.getValue()).isEqualTo("Demande d'informations");
        assertThat(body.getValue()).doesNotContain("(dossier");
    }
}
