package com.flowforge.infrastructure.signature;

import static com.flowforge.domain.signature.SignatureFixtures.NOW;
import static com.flowforge.domain.signature.SignatureFixtures.external;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.flowforge.domain.notification.port.out.EmailPort;
import com.flowforge.domain.signature.port.out.SignatureInviteSenderPort;
import com.flowforge.infrastructure.config.PortalProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** S-DEC-7 : contenu de l'invitation (lien portail à fragment, pas d'injection d'en-tête). */
class EmailSignatureInviteSenderTest {

    private final EmailPort emailPort = mock(EmailPort.class);
    private final PortalProperties portal = new PortalProperties(
            true, "https://ff.example.org/tenant-a/", null, null, null, null, null, 0);
    private final EmailSignatureInviteSender sender = new EmailSignatureInviteSender(emailPort, portal);

    @Test
    void linkCarriesTheTokenInTheFragmentAfterTheSignatureMarker() {
        String token = "T".repeat(43);
        sender.send(external(UUID.randomUUID(), 0, true, "alice@example.org"), token,
                new SignatureInviteSenderPort.InviteContext("doc", "SIN-2026-001", NOW.plusSeconds(86_400)));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailPort).send(org.mockito.ArgumentMatchers.eq("alice@example.org"), subject.capture(), body.capture());
        assertThat(subject.getValue()).isEqualTo("Signature demandée — dossier SIN-2026-001");
        assertThat(body.getValue()).contains("https://ff.example.org/tenant-a/portal/#signature:" + token);
        assertThat(body.getValue()).contains("1 octobre 2026");
    }

    @Test
    void simulatedRequestsSayTheSignatureHasNoLegalValue() {
        // revue adversariale n°14
        sender.send(external(UUID.randomUUID(), 0, true, "alice@example.org"), "t",
                new SignatureInviteSenderPort.InviteContext("doc", "SIN-1", NOW.plusSeconds(60), true));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailPort).send(org.mockito.ArgumentMatchers.anyString(), subject.capture(), body.capture());
        assertThat(subject.getValue()).startsWith("[SIMULATION]");
        assertThat(body.getValue()).contains("SIMULATION").contains("aucune valeur juridique");
    }

    @Test
    void referenceIsFlattenedToASingleLine() {
        sender.send(external(UUID.randomUUID(), 0, true, "alice@example.org"), "t",
                new SignatureInviteSenderPort.InviteContext("doc", "REF\r\nBcc: evil@example.org", NOW.plusSeconds(60)));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(emailPort).send(org.mockito.ArgumentMatchers.anyString(), subject.capture(), org.mockito.ArgumentMatchers.anyString());
        assertThat(subject.getValue()).doesNotContain("\n").doesNotContain("\r");
    }
}
