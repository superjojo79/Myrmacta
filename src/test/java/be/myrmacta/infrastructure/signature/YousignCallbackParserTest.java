package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.port.out.SignaturePort.SignatureCallbackEvent;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class YousignCallbackParserTest {

    private static final UUID REQUEST_ID = UUID.fromString("0ef334d2-7f36-4643-8a2a-4283b4a3e4a4");

    @Test
    void doneEventClosesAsSignedWithTheProviderReference() {
        SignatureCallbackEvent event = YousignCallbackParser.parse(event("signature_request.done")).orElseThrow();

        assertThat(event.providerReference()).isEqualTo("yousign:" + REQUEST_ID);
        assertThat(event.outcome()).isEqualTo(SignatureOutcome.SIGNED);
        assertThat(event.signerDecisions()).isEmpty();
    }

    @Test
    void terminalRequestEventsMapToTheNormalizedOutcome() {
        assertThat(YousignCallbackParser.parse(event("signature_request.declined")).orElseThrow().outcome())
                .isEqualTo(SignatureOutcome.DECLINED);
        assertThat(YousignCallbackParser.parse(event("signature_request.expired")).orElseThrow().outcome())
                .isEqualTo(SignatureOutcome.EXPIRED);
        assertThat(YousignCallbackParser.parse(event("signature_request.canceled")).orElseThrow().outcome())
                .isEqualTo(SignatureOutcome.CANCELLED);
        assertThat(YousignCallbackParser.parse(event("signature_request.rejected")).orElseThrow().outcome())
                .isEqualTo(SignatureOutcome.FAILED);
    }

    @Test
    void signerNotifiedDoesNotCloseTheRequest() {
        assertThat(YousignCallbackParser.parse(event("signer.notified"))).isEmpty();
    }

    @Test
    void unknownEventIsRejected() {
        assertThatThrownBy(() -> YousignCallbackParser.parse(event("signature_request.made_up")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature_request.made_up");
    }

    @Test
    void terminalEventWithoutSignatureRequestIdIsRejected() {
        assertThatThrownBy(() -> YousignCallbackParser.parse(Map.of("event_name", "signature_request.done")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, Object> event(String name) {
        return Map.of(
                "event_name", name,
                "data", Map.of("signature_request", Map.of("id", REQUEST_ID.toString())));
    }
}
