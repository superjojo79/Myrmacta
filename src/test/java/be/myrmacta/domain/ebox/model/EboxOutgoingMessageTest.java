package be.myrmacta.domain.ebox.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TODO §155 (D-EBOX-SEND-MODEL) — modèle commun d'un envoi eBox et extension citoyen. */
class EboxOutgoingMessageTest {

    static final EboxRecipient ENTERPRISE = new EboxRecipient.Enterprise("0212345678");
    static final EboxRecipient CITIZEN = new EboxRecipient.Citizen("85073003328");

    static EboxDocument main() {
        return new EboxDocument("decision.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII), true);
    }

    static EboxDocument annex(int i) {
        return new EboxDocument("annexe-" + i + ".pdf", "application/pdf", ("%PDF " + i).getBytes(StandardCharsets.US_ASCII), false);
    }

    static EboxOutgoingMessage.Builder builder(EboxRecipient recipient) {
        return EboxOutgoingMessage.builder(recipient, "myrmacta-commune", "decision", EboxLanguage.FR, "Votre permis d'urbanisme")
                .document(main());
    }

    @Test
    void aMinimalEnterpriseMessageIsValid() {
        EboxOutgoingMessage message = builder(ENTERPRISE).build();

        assertThat(message.route()).isEqualTo(EboxRoute.ENTERPRISE);
        assertThat(message.mainDocument().fileName()).isEqualTo("decision.pdf");
        assertThat(message.citizen()).isNull();
    }

    @Test
    void recipientsAreValidated_andAnSsinIsNeverPrinted() {
        assertThatThrownBy(() -> new EboxRecipient.Enterprise("212345678")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EboxRecipient.Enterprise("2212345678")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EboxRecipient.Citizen("8507300332")).isInstanceOf(IllegalArgumentException.class);
        assertThat(CITIZEN.toString()).doesNotContain("85073003328");
        assertThat(new EboxRecipient.Enterprise("0212.345.678")).isEqualTo(ENTERPRISE);
    }

    @Test
    void exactlyOneMainDocumentIsRequired() {
        assertThatThrownBy(() -> EboxOutgoingMessage.builder(ENTERPRISE, "app", "type", EboxLanguage.FR, "Objet").build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("main");
        assertThatThrownBy(() -> builder(ENTERPRISE).document(main()).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("main");
    }

    @Test
    void attachmentsAreBoundedBySpec() {
        EboxOutgoingMessage.Builder twenty = builder(ENTERPRISE);
        for (int i = 1; i < 20; i++) {
            twenty.document(annex(i));
        }
        assertThat(twenty.build().documents()).hasSize(20);
        assertThatThrownBy(() -> twenty.document(annex(20)).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("20");
    }

    @Test
    void aRegisteredMailHasNoBody_andAtMostNineAnnexes() {
        assertThatThrownBy(() -> builder(ENTERPRISE).registered(true).body("texte").build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("body");
        EboxOutgoingMessage.Builder ten = builder(ENTERPRISE).registered(true);
        for (int i = 1; i <= 9; i++) {
            ten.document(annex(i));
        }
        assertThat(ten.build().registered()).isTrue();
        assertThatThrownBy(() -> ten.document(annex(10)).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("9");
    }

    @Test
    void aReplyDueDateNeedsRepliesToBeAuthorized() {
        OffsetDateTime due = OffsetDateTime.parse("2026-12-31T00:00:00+01:00");
        assertThatThrownBy(() -> builder(ENTERPRISE).replyDueDate(due).build()).isInstanceOf(IllegalArgumentException.class);
        assertThat(builder(ENTERPRISE).replyAuthorized(true).replyDueDate(due).build().replyDueDate()).isEqualTo(due);
    }

    @Test
    void theCitizenExtensionOnlyGoesToACitizen() {
        EboxCitizenExtension payment = new EboxCitizenExtension(new EboxPayment(new BigDecimal("25.00"), "EUR",
                "BE68539007547034", "GKCCBEBB", "Commune de Myrmacta", "+++090/9337/55493+++", null));

        assertThat(builder(CITIZEN).citizen(payment).build().citizen()).isEqualTo(payment);
        assertThatThrownBy(() -> builder(ENTERPRISE).citizen(payment).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("citizen");
    }

    @Test
    void aPaymentIsValidated() {
        assertThatThrownBy(() -> new EboxPayment(BigDecimal.ZERO, "EUR", "BE68539007547034", null, "Commune", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EboxPayment(BigDecimal.ONE, "eur", "BE68539007547034", null, "Commune", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EboxPayment(BigDecimal.ONE, "EUR", "pas un iban", null, "Commune", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textsAreBoundedAndControlCharactersRefused() {
        assertThatThrownBy(() -> EboxOutgoingMessage.builder(ENTERPRISE, "app", "type", EboxLanguage.FR, " ").document(main()).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EboxOutgoingMessage.builder(ENTERPRISE, "app", "type", EboxLanguage.FR, "a\u0000b")
                .document(main()).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EboxDocument("a\"b.pdf", "application/pdf", new byte[1], true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EboxDocument("a.pdf", "application/pdf", new byte[0], true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void documentsAreDefensivelyCopied_andDigested() {
        byte[] bytes = "%PDF".getBytes(StandardCharsets.US_ASCII);
        EboxDocument document = new EboxDocument("a.pdf", "application/pdf", bytes, true);
        bytes[0] = 'X';

        assertThat(document.content()[0]).isEqualTo((byte) '%');
        assertThat(document.sha256Hex()).hasSize(64);
        assertThat(List.of(document.size())).containsExactly(4L);
    }
}
