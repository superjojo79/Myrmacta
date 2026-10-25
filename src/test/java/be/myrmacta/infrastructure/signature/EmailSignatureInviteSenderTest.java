package be.myrmacta.infrastructure.signature;

import static be.myrmacta.domain.signature.SignatureFixtures.NOW;
import static be.myrmacta.domain.signature.SignatureFixtures.external;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import be.myrmacta.domain.notification.port.out.EmailPort;
import be.myrmacta.domain.signature.port.out.SignatureInviteSenderPort;
import be.myrmacta.infrastructure.config.PortalProperties;
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

    // ---- D7 : langue de l'invitation = langue par défaut du tenant (le signataire externe n'a pas de profil) ----

    @Test
    void anEnglishTenantSendsAnEnglishInvitationWhoseLinkAsksThePortalForEnglish() {
        EmailSignatureInviteSender english = new EmailSignatureInviteSender(emailPort, portal, java.util.Locale.ENGLISH);
        english.send(external(UUID.randomUUID(), 0, true, "alice@example.org"), "T".repeat(43),
                new SignatureInviteSenderPort.InviteContext("doc", "SIN-2026-001", NOW.plusSeconds(86_400)));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailPort).send(org.mockito.ArgumentMatchers.eq("alice@example.org"), subject.capture(), body.capture());
        assertThat(subject.getValue()).isEqualTo("Signature requested — dossier SIN-2026-001");
        assertThat(body.getValue()).contains("Hello,").contains("1 October 2026")
                .contains("https://ff.example.org/tenant-a/portal/?lang=en#signature:" + "T".repeat(43))
                .doesNotContain("Bonjour");
    }

    @Test
    void theFrenchInvitationKeepsItsExactLinkWithoutAQueryString() {
        sender.send(external(UUID.randomUUID(), 0, true, "alice@example.org"), "t",
                new SignatureInviteSenderPort.InviteContext("doc", "SIN-1", NOW.plusSeconds(60)));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailPort).send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), body.capture());
        assertThat(body.getValue()).contains("/portal/#signature:t").doesNotContain("lang=");
    }

    @Test
    void theEnglishSimulationNoticeStillSaysTheSignatureHasNoLegalValue() {
        new EmailSignatureInviteSender(emailPort, portal, java.util.Locale.ENGLISH).send(
                external(UUID.randomUUID(), 0, true, "alice@example.org"), "t",
                new SignatureInviteSenderPort.InviteContext("doc", "SIN-1", NOW.plusSeconds(60), true));
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailPort).send(org.mockito.ArgumentMatchers.anyString(), subject.capture(), body.capture());
        assertThat(subject.getValue()).startsWith("[SIMULATION]");
        assertThat(body.getValue()).contains("SIMULATION").contains("no legal value");
    }
}
