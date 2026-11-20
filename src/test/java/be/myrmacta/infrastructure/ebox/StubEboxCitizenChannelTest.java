package be.myrmacta.infrastructure.ebox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.ebox.model.EboxConsent;
import be.myrmacta.domain.ebox.model.EboxDocument;
import be.myrmacta.domain.ebox.model.EboxLanguage;
import be.myrmacta.domain.ebox.model.EboxOutgoingMessage;
import be.myrmacta.domain.ebox.model.EboxPublication;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxSenderAccount;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TODO §155 (D-EBOX-SEND-CITIZEN-STUB) — voie citoyen simulée : sans consentement par défaut, publication marquée « STUB ». */
class StubEboxCitizenChannelTest {

    private static final EboxSenderAccount SENDER = new EboxSenderAccount(UUID.randomUUID());
    private static final EboxRecipient CITIZEN = new EboxRecipient.Citizen("85073003328");

    @Test
    void byDefaultNoCitizenHasConsented() {
        EboxConsent consent = new StubEboxCitizenChannel("NO_CONSENT").consent("t", SENDER, CITIZEN);

        assertThat(consent.level()).isEqualTo(EboxConsent.Level.NO_CONSENT);
        assertThat(consent.source()).isEqualTo("citizen-stub");
    }

    @Test
    void aConsentingStubSimulatesAPublicationThatTheAuditCanTellApart() {
        StubEboxCitizenChannel stub = new StubEboxCitizenChannel("consented", Clock.systemUTC());
        EboxOutgoingMessage message = EboxOutgoingMessage.builder(CITIZEN, "app", "type", EboxLanguage.FR, "Objet")
                .document(new EboxDocument("a.pdf", "application/pdf", new byte[] {1}, true)).build();

        assertThat(stub.consent("t", SENDER, CITIZEN).level()).isEqualTo(EboxConsent.Level.CONSENTED);
        EboxPublication publication = stub.publish("t", SENDER, message, "k-1");

        assertThat(publication.status()).isEqualTo(EboxPublication.Status.PUBLISHED);
        assertThat(publication.trace().method()).isEqualTo("STUB");
        assertThat(publication.trace().url()).startsWith("stub://");
        assertThat(publication.providerMessageId()).startsWith("stub-");
    }

    @Test
    void enterpriseLevelsAndRecipientsAreRefused() {
        assertThatThrownBy(() -> new StubEboxCitizenChannel("EXCLUSIVE_EBOX")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StubEboxCitizenChannel("NO_CONSENT").consent("t", SENDER, new EboxRecipient.Enterprise("0212345678")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
