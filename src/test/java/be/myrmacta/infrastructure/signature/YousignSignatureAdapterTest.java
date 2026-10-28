package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignerKind;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignaturePort.SignatureSubmission;
import be.myrmacta.domain.signature.port.out.SignaturePort.SubmitCommand;
import be.myrmacta.domain.signature.port.out.SignaturePort.SubmitSigner;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Cérémonie Yousign : ancre, activation, lien non conservé (TODO §109). */
class YousignSignatureAdapterTest {

    private final FakeYousign client = new FakeYousign();
    private final YousignSignatureAdapter adapter = new YousignSignatureAdapter(client);

    @Test
    void submitCreatesUploadsAddsSignersAndActivatesWithoutStoringTheLink() {
        client.anchors = 2;
        SignatureSubmission submission = adapter.submit(command(SignatureMode.SEQUENTIAL, "Ada Lovelace", "Bob"));

        assertThat(client.calls).containsExactly("create:true", "upload", "signer:Ada:Lovelace", "signer:Bob:-", "activate");
        assertThat(submission.providerReference()).isEqualTo("yousign:" + FakeYousign.REQUEST_ID);
        assertThat(submission.simulated()).isFalse();
        assertThat(submission.signingUrls()).isEmpty();
        assertThat(adapter.capabilities().hostedSigningUrl()).isTrue();
        assertThat(adapter.capabilities().optionalSigners()).isFalse();
    }

    @Test
    void missingAnchorCancelsTheDraftAndFails() {
        client.anchors = 0;

        assertThatThrownBy(() -> adapter.submit(command(SignatureMode.PARALLEL, "Ada Lovelace")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("anchor");
        assertThat(client.calls).contains("cancel").doesNotContain("activate");
    }

    @Test
    void doneEventBecomesSigned() {
        Map<String, Object> payload = Map.of(
                "event_name", "signature_request.done",
                "data", Map.of("signature_request", Map.of("id", FakeYousign.REQUEST_ID.toString())));

        assertThat(adapter.parseCallback(payload).outcome()).isEqualTo(SignatureOutcome.SIGNED);
    }

    @Test
    void onlySandboxAndProductionHostsAreAccepted() {
        assertThat(YousignHttpClient.baseFor("sandbox")).isEqualTo(YousignHttpClient.SANDBOX);
        assertThat(YousignHttpClient.baseFor("production")).isEqualTo(YousignHttpClient.PRODUCTION);
        assertThatThrownBy(() -> YousignHttpClient.baseFor("https://evil.example"))
                .isInstanceOf(YousignCallException.class);
    }

    private static SubmitCommand command(SignatureMode mode, String... names) {
        List<SubmitSigner> signers = new ArrayList<>();
        int position = 0;
        for (String name : names) {
            signers.add(new SubmitSigner(UUID.randomUUID(), position++, SignerKind.EXTERNAL, true, name, "a@example.org"));
        }
        return new SubmitCommand(UUID.randomUUID(), "Contrat", "application/pdf", new byte[] {'%'}, "digest",
                signers, mode, null, Instant.parse("2026-11-11T00:00:00Z"), null);
    }

    private static final class FakeYousign implements YousignClient {
        static final UUID REQUEST_ID = UUID.fromString("0ef334d2-7f36-4643-8a2a-4283b4a3e4a4");
        final List<String> calls = new ArrayList<>();
        int anchors = 1;

        @Override
        public UUID createSignatureRequest(String name, boolean orderedSigners, String externalId, String expirationDate) {
            calls.add("create:" + orderedSigners);
            return REQUEST_ID;
        }

        @Override
        public Upload uploadSignablePdf(UUID requestId, String filename, byte[] pdf) {
            calls.add("upload");
            return new Upload(UUID.randomUUID(), anchors);
        }

        @Override
        public UUID addSigner(UUID requestId, String firstName, String lastName, String email) {
            calls.add("signer:" + firstName + ":" + lastName);
            return UUID.randomUUID();
        }

        @Override
        public void activate(UUID requestId) {
            calls.add("activate");
        }

        @Override
        public Optional<String> signatureLink(UUID requestId, int signerPosition) {
            return Optional.of("https://yousign.app/signatures/abc");
        }

        @Override
        public void cancel(UUID requestId) {
            calls.add("cancel");
        }

        @Override
        public byte[] downloadSignedPdf(UUID requestId) {
            return new byte[] {1};
        }

        @Override
        public byte[] downloadAuditTrail(UUID requestId) {
            return new byte[] {2};
        }
    }
}
