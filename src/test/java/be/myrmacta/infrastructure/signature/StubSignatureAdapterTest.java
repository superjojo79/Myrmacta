package be.myrmacta.infrastructure.signature;

import static be.myrmacta.domain.signature.SignatureFixtures.NOW;
import static be.myrmacta.domain.signature.SignatureFixtures.TENANT;
import static be.myrmacta.domain.signature.SignatureFixtures.external;
import static be.myrmacta.domain.signature.SignatureFixtures.internal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.signature.SignatureFixtures;
import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.port.SignaturePortContract;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/** S-STUB-1, S-STUB-3, S-STUB-9, S-CONTRACT-1 : le stub respecte le contrat et signe en simulation. */
class StubSignatureAdapterTest extends SignaturePortContract {

    private final Map<UUID, SignatureRequest> requests = new ConcurrentHashMap<>();
    private final Map<UUID, List<Signer>> signers = new ConcurrentHashMap<>();
    private final SignatureRequestRepository repository = mock(SignatureRequestRepository.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private static final byte[] SOURCE = TestPdfs.pdf(2, "Contrat");
    private static final String DIGEST = sha256(SOURCE);
    private final StubSignatureAdapter adapter = new StubSignatureAdapter(TENANT, repository, storage);

    StubSignatureAdapterTest() {
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(requests.get(inv.<UUID>getArgument(0))));
        when(repository.findSigners(any()))
                .thenAnswer(inv -> signers.getOrDefault(inv.<UUID>getArgument(0), List.of()));
        when(storage.retrieve(any(), any())).thenReturn(SOURCE);
    }

    private static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    protected byte[] document() {
        return SOURCE;
    }

    @Override
    protected SignaturePort port() {
        return adapter;
    }

    @Override
    protected void onSubmitted(UUID requestId, SignaturePort.SignatureSubmission submission) {
        // le stub relit la demande Myrmacta, insérée PENDING avant l'appel au port (RequestSignatureUseCase)
        requests.put(requestId, SignatureFixtures.request(requestId, SignatureMode.PARALLEL, "proc-1", DIGEST));
    }

    @Override
    protected void completeWith(String providerReference, UUID requestId, SignatureOutcome outcome) {
        SignatureRequest base = SignatureFixtures.request(requestId, SignatureMode.PARALLEL, "proc-1", DIGEST);
        List<Signer> list = List.of(
                external(requestId, 0, true, "alice@example.org")
                        .decided(outcome == SignatureOutcome.SIGNED ? SignerStatus.SIGNED : SignerStatus.DECLINED, NOW, null),
                internal(requestId, 1, false, "bob").withStatus(SignerStatus.REVOKED));
        requests.put(requestId, base.closed(outcome, null, NOW));
        signers.put(requestId, list);
    }

    @Test
    void referenceCarriesTheRequestIdAndCapabilitiesAreDeclared() {
        UUID id = UUID.randomUUID();
        SignaturePort.SignatureSubmission submission = adapter.submit(command(id));

        assertThat(submission.providerReference()).isEqualTo("stub:" + id);
        assertThat(submission.simulated()).isTrue();
        assertThat(adapter.capabilities().orderedSigning()).isTrue();
        assertThat(adapter.capabilities().optionalSigners()).isTrue();
        assertThat(adapter.capabilities().hostedSigningUrl()).isFalse();
    }

    @Test
    void signedArtifactsCarryTheSimulationMarkAndMaskedIdentities() {
        UUID id = UUID.randomUUID();
        completeWith("stub:" + id, id, SignatureOutcome.SIGNED);

        SignaturePort.SignedArtifacts artifacts = adapter.fetchArtifacts("stub:" + id);

        assertThat(artifacts.simulated()).isTrue();
        assertThat(TestPdfs.text(artifacts.signedDocument())).contains("SIMULATION", "Contrat page 2");
        String proof = TestPdfs.text(artifacts.proof());
        assertThat(proof).contains("SIGNED", "REVOKED", id.toString(), DIGEST.substring(0, 32));
        assertThat(proof).doesNotContain("alice@example.org");
        assertThat(proof).contains("bob");
    }

    @Test
    void rejectsForeignOrUnknownReferences() {
        assertThatThrownBy(() -> adapter.status("docusign:123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.status("stub:" + UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.status(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pendingRequestHasNoArtifacts() {
        UUID id = UUID.randomUUID();
        requests.put(id, SignatureFixtures.request(id, SignatureMode.PARALLEL));
        assertThat(requests.get(id).status()).isEqualTo(SignatureStatus.PENDING);
        assertThatThrownBy(() -> adapter.fetchArtifacts("stub:" + id)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void corruptPdfIsRefusedAtSubmission() {
        // revue adversariale n°1 : un « %PDF- » illisible ne doit pas bloquer la dernière signature
        UUID id = UUID.randomUUID();
        SignaturePort.SubmitCommand ok = command(id);
        SignaturePort.SubmitCommand corrupt = new SignaturePort.SubmitCommand(id, ok.documentName(),
                ok.documentContentType(), "%PDF-1.4 garbage".getBytes(), ok.documentDigest(), ok.signers(),
                ok.mode(), ok.message(), ok.deadlineAt(), ok.requestedAssuranceLevel());

        assertThatThrownBy(() -> adapter.submit(corrupt)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void artifactsAreRefusedWhenTheSourceChangedSinceEmission() {
        // revue adversariale n°7 : le document signé est celui émis, sinon on refuse
        UUID id = UUID.randomUUID();
        adapter.submit(command(id));
        onSubmitted(id, null);
        completeWith("stub:" + id, id, SignatureOutcome.SIGNED);
        when(storage.retrieve(any(), any())).thenReturn(TestPdfs.pdf(1, "Autre contenu"));

        assertThatThrownBy(() -> adapter.fetchArtifacts("stub:" + id)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed");
    }
}
