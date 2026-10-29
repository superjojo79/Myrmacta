package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.port.out.SignaturePort.SignatureCallbackEvent;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocusignCallbackParserTest {

    private static final UUID ENVELOPE_ID = UUID.fromString("0ef334d2-7f36-4643-8a2a-4283b4a3e4a4");

    @Test
    void completedEventClosesAsSignedWithTheProviderReference() {
        SignatureCallbackEvent event = DocusignCallbackParser.parse(event("envelope-completed", null)).orElseThrow();

        assertThat(event.providerReference()).isEqualTo("docusign:" + ENVELOPE_ID);
        assertThat(event.outcome()).isEqualTo(SignatureOutcome.SIGNED);
        assertThat(event.signerDecisions()).isEmpty();
    }

    @Test
    void declinedAndRemovedMapToTheNormalizedOutcome() {
        assertThat(DocusignCallbackParser.parse(event("envelope-declined", null)).orElseThrow().outcome())
                .isEqualTo(SignatureOutcome.DECLINED);
        assertThat(DocusignCallbackParser.parse(event("envelope-removed", null)).orElseThrow().outcome())
                .isEqualTo(SignatureOutcome.FAILED);
    }

    @Test
    void voidedByTheSenderIsCancelledAndTheExpirationReasonIsExpired() {
        assertThat(DocusignCallbackParser.parse(event("envelope-voided", "cancelled")).orElseThrow().outcome())
                .isEqualTo(SignatureOutcome.CANCELLED);
        assertThat(DocusignCallbackParser.parse(event("envelope-voided", null)).orElseThrow().outcome())
                .isEqualTo(SignatureOutcome.CANCELLED);
        assertThat(DocusignCallbackParser.parse(event("envelope-voided", "Envelope has expired.")).orElseThrow().outcome())
                .isEqualTo(SignatureOutcome.EXPIRED);
        assertThat(DocusignCallbackParser.parse(event("envelope-voided", "envelope has expired")).orElseThrow().outcome())
                .isEqualTo(SignatureOutcome.EXPIRED);
    }

    @Test
    void envelopeSentDoesNotCloseTheRequest() {
        assertThat(DocusignCallbackParser.parse(event("envelope-sent", null))).isEmpty();
        assertThat(DocusignCallbackParser.parse(event("recipient-declined", null))).isEmpty();
    }

    @Test
    void unknownEventIsRejected() {
        assertThatThrownBy(() -> DocusignCallbackParser.parse(event("envelope-made-up", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("envelope-made-up");
    }

    @Test
    void terminalEventWithoutEnvelopeIdIsRejected() {
        assertThatThrownBy(() -> DocusignCallbackParser.parse(Map.of("event", "envelope-completed")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, Object> event(String name, String voidedReason) {
        Map<String, Object> data = voidedReason == null
                ? Map.of("envelopeId", ENVELOPE_ID.toString())
                : Map.of("envelopeId", ENVELOPE_ID.toString(), "envelopeSummary", Map.of("voidedReason", voidedReason));
        return Map.of("event", name, "data", data);
    }
}
