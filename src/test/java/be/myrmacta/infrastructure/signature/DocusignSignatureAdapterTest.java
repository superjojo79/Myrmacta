package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignerKind;
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

/** Cérémonie DocuSign : ancre, vue à l'ouverture, retour sans jeton (TODO §109). */
class DocusignSignatureAdapterTest {

    private final FakeDocusign client = new FakeDocusign();
    private final DocusignSignatureAdapter adapter = new DocusignSignatureAdapter(client, "https://tenant.example");

    @Test
    void submitCreatesAnEnvelopeWithoutStoringTheLink() {
        SignatureSubmission submission = adapter.submit(command(SignatureMode.SEQUENTIAL, "Ada Lovelace", "Bob"));

        assertThat(client.calls).containsExactly("create:true:2");
        assertThat(client.signers).containsExactly("Ada Lovelace", "Bob");
        assertThat(submission.providerReference()).isEqualTo("docusign:" + FakeDocusign.ENVELOPE_ID);
        assertThat(submission.simulated()).isFalse();
        assertThat(submission.signingUrls()).isEmpty();
        assertThat(adapter.capabilities().hostedSigningUrl()).isTrue();
        assertThat(adapter.capabilities().optionalSigners()).isFalse();
        assertThat(adapter.capabilities().orderedSigning()).isTrue();
    }

    @Test
    void missingAnchorFailsTheRequest() {
        client.missingAnchor = true;

        assertThatThrownBy(() -> adapter.submit(command(SignatureMode.PARALLEL, "Ada Lovelace")))
                .isInstanceOf(DocusignAnchorMissingException.class)
                .hasMessageContaining("anchor");
        assertThat(client.calls).doesNotContain("void");
    }

    @Test
    void signingUrlIsAskedAtOpenTimeAndTheReturnUrlHasNoToken() {
        assertThat(adapter.signingUrl("docusign:" + FakeDocusign.ENVELOPE_ID, 0))
                .contains("https://demo.docusign.net/Signing/abc");
        assertThat(client.returnUrls).containsExactly("https://tenant.example/portal/");
        assertThat(client.returnUrls.getFirst()).doesNotContain("secret-token").doesNotContain("#");
    }

    @Test
    void aReturnUrlWithAFragmentIsRefused() {
        DocusignSignatureAdapter hosted = new DocusignSignatureAdapter(client, "https://tenant.example/portal/#signature:secret-token");

        assertThatThrownBy(() -> hosted.signingUrl("docusign:" + FakeDocusign.ENVELOPE_ID, 0))
                .isInstanceOf(DocusignCallException.class)
                .hasMessageNotContaining("secret-token");
    }

    @Test
    void completedEventBecomesSignedAndCancelVoidsTheEnvelope() {
        Map<String, Object> payload = Map.of(
                "event", "envelope-completed",
                "data", Map.of("envelopeId", FakeDocusign.ENVELOPE_ID.toString()));

        assertThat(adapter.parseCallback(payload).outcome()).isEqualTo(SignatureOutcome.SIGNED);
        adapter.cancel("docusign:" + FakeDocusign.ENVELOPE_ID);
        assertThat(client.calls).contains("void");
    }

    @Test
    void missingCredentialsFailBeforeAnyCall() {
        ConnectorInstanceRepository instances = mock(ConnectorInstanceRepository.class);
        when(instances.listByTenantId("tenant_dev")).thenReturn(List.of(new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "Signature", "signature", true,
                Map.of("docusignEnvironment", "sandbox"))));
        DocusignSignatureAdapter wired = new DocusignSignatureAdapter("tenant_dev", instances, "https://tenant.example");

        assertThatThrownBy(() -> wired.submit(command(SignatureMode.PARALLEL, "Ada")))
                .isInstanceOf(DocusignCallException.class)
                .hasMessageContaining("credentials");
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

    private static final class FakeDocusign implements DocusignClient {
        static final UUID ENVELOPE_ID = UUID.fromString("0ef334d2-7f36-4643-8a2a-4283b4a3e4a4");
        final List<String> calls = new ArrayList<>();
        final List<String> signers = new ArrayList<>();
        final List<String> returnUrls = new ArrayList<>();
        boolean missingAnchor;

        @Override
        public UUID createEnvelope(
                String name, boolean ordered, String externalId, Instant deadline, List<SignerSpec> specs, byte[] pdf) {
            calls.add("create:" + ordered + ":" + specs.size());
            specs.forEach(spec -> signers.add(spec.name()));
            if (missingAnchor) {
                throw new DocusignAnchorMissingException();
            }
            return ENVELOPE_ID;
        }

        @Override
        public void voidEnvelope(UUID envelopeId) {
            calls.add("void");
        }

        @Override
        public Optional<String> recipientView(UUID envelopeId, int signerPosition, String returnUrl) {
            returnUrls.add(returnUrl);
            return Optional.of("https://demo.docusign.net/Signing/abc");
        }

        @Override
        public byte[] downloadSignedPdf(UUID envelopeId) {
            return new byte[] {'%'};
        }

        @Override
        public byte[] downloadCertificate(UUID envelopeId) {
            return new byte[] {'C'};
        }
    }
}
