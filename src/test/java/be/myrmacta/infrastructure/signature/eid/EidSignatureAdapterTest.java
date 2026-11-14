package be.myrmacta.infrastructure.signature.eid;

import static be.myrmacta.domain.signature.SignatureFixtures.NOW;
import static be.myrmacta.domain.signature.SignatureFixtures.TENANT;
import static be.myrmacta.domain.signature.SignatureFixtures.external;
import static be.myrmacta.domain.signature.SignatureFixtures.internal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.signature.SignatureFixtures;
import be.myrmacta.domain.signature.SignatureWorkingDocuments;
import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.model.SignedUpload;
import be.myrmacta.domain.signature.model.SignedUploadEvidence;
import be.myrmacta.domain.signature.model.SignedUploadRejection;
import be.myrmacta.domain.signature.model.SignedUploadVerdict;
import be.myrmacta.domain.signature.port.SignaturePortContract;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import be.myrmacta.infrastructure.signature.TestPdfs;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;

/**
 * TODO §138 — adaptateur {@code eid} : contrat commun des fournisseurs, capacités, soumission, artefacts
 * (document du dernier téléversement, preuve non simulée) et délégation de la vérification.
 */
class EidSignatureAdapterTest extends SignaturePortContract {

    private static final byte[] SOURCE = TestPdfs.pdf(2, "Contrat");
    private static final byte[] SIGNED_1 = TestPdfs.pdf(2, "Contrat signé 1");
    private static final byte[] SIGNED_2 = TestPdfs.pdf(2, "Contrat signé 2");
    private static final String DIGEST = SignatureWorkingDocuments.digestOf(SOURCE);

    private final Map<UUID, SignatureRequest> requests = new ConcurrentHashMap<>();
    private final Map<UUID, List<Signer>> signers = new ConcurrentHashMap<>();
    private final Map<UUID, List<SignedUpload>> uploadsByRequest = new ConcurrentHashMap<>();
    private final SignatureRequestRepository repository = mock(SignatureRequestRepository.class);
    private final SignedUploadRepository uploads = mock(SignedUploadRepository.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final SignedPdfVerifier verifier = mock(SignedPdfVerifier.class);
    private final EidSignatureAdapter adapter = new EidSignatureAdapter(TENANT, repository, uploads, storage, verifier);

    EidSignatureAdapterTest() {
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(requests.get(inv.<UUID>getArgument(0))));
        when(repository.findSigners(any())).thenAnswer(inv -> signers.getOrDefault(inv.<UUID>getArgument(0), List.of()));
        when(uploads.findByRequestId(any()))
                .thenAnswer(inv -> uploadsByRequest.getOrDefault(inv.<UUID>getArgument(0), List.of()));
        when(storage.retrieve(TENANT, "storage/doc.pdf")).thenReturn(SOURCE);
        when(storage.retrieve(TENANT, "work-1")).thenReturn(SIGNED_1);
        when(storage.retrieve(TENANT, "work-2")).thenReturn(SIGNED_2);
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
        requests.put(requestId, SignatureFixtures.request(requestId, SignatureMode.PARALLEL, "proc-1", DIGEST));
    }

    @Override
    protected void completeWith(String providerReference, UUID requestId, SignatureOutcome outcome) {
        SignatureRequest base = SignatureFixtures.request(requestId, SignatureMode.PARALLEL, "proc-1", DIGEST);
        Signer alice = external(requestId, 0, true, "alice@example.org");
        List<Signer> list = List.of(
                alice.decided(outcome == SignatureOutcome.SIGNED ? SignerStatus.SIGNED : SignerStatus.DECLINED, NOW, null),
                internal(requestId, 1, false, "bob").withStatus(SignerStatus.REVOKED));
        requests.put(requestId, base.closed(outcome, null, NOW));
        signers.put(requestId, list);
        if (outcome == SignatureOutcome.SIGNED) {
            uploadsByRequest.put(requestId, List.of(upload(requestId, alice.id(), 1, "work-1", SIGNED_1, "fp-alice")));
        }
    }

    private static SignedUpload upload(UUID requestId, UUID signerId, int sequence, String ref, byte[] pdf, String fp) {
        return new SignedUpload(UUID.randomUUID(), TENANT, requestId, signerId, sequence, ref,
                SignatureWorkingDocuments.digestOf(pdf),
                new SignedUploadEvidence("Alice Martin", "CN=Citizen CA", "0A1B", fp, NOW.minusSeconds(30), NOW,
                        "PAdES-BASELINE-B", "RSA_SHA256", true, true, "OCSP", Instant.parse("2026-09-30T09:59:00Z")),
                NOW);
    }

    @Test
    void declaresTheUploadCapabilityAndTheQualifiedLevelWithoutSimulation() {
        UUID id = UUID.randomUUID();
        SignaturePort.SignatureSubmission submission = adapter.submit(command(id));

        assertThat(submission.providerReference()).isEqualTo("eid:" + id);
        assertThat(submission.simulated()).isFalse();
        assertThat(submission.assuranceLevel()).isEqualTo("QES");
        assertThat(submission.signingUrls()).isEmpty();
        SignaturePort.SignatureProviderCapabilities capabilities = adapter.capabilities();
        assertThat(adapter.code()).isEqualTo("eid");
        assertThat(capabilities.orderedSigning()).isTrue();
        assertThat(capabilities.optionalSigners()).isTrue();
        assertThat(capabilities.hostedSigningUrl()).isFalse();
        assertThat(capabilities.proofProvided()).isTrue();
        assertThat(capabilities.signedUpload()).isTrue();
        assertThat(capabilities.assuranceLevels()).containsExactly("QES");
        assertThat(capabilities.artifactsDeferred()).isTrue();
    }

    @Test
    void refusesUnreadableAndEncryptedPdfsAtSubmission() throws Exception {
        UUID id = UUID.randomUUID();
        SignaturePort.SubmitCommand ok = command(id);

        assertThatThrownBy(() -> adapter.submit(withDocument(ok, "%PDF-1.4 garbage".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.submit(withDocument(ok, encryptedPdf())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SignaturePort.SubmitCommand withDocument(SignaturePort.SubmitCommand base, byte[] document) {
        return new SignaturePort.SubmitCommand(base.requestId(), base.documentName(), base.documentContentType(), document,
                base.documentDigest(), base.signers(), base.mode(), base.message(), base.deadlineAt(),
                base.requestedAssuranceLevel());
    }

    private static byte[] encryptedPdf() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setEncryption(null, "owner".getBytes(), PdfWriter.ALLOW_PRINTING, PdfWriter.ENCRYPTION_AES_128);
        document.open();
        document.add(new Paragraph("secret"));
        document.close();
        return out.toByteArray();
    }

    @Test
    void verificationIsDelegatedToTheVerifierWithTheCheckData() {
        SignedUploadVerdict verdict = SignedUploadVerdict.rejected(SignedUploadRejection.CERTIFICATE_REVOKED);
        when(verifier.verify(any(), any(), any())).thenReturn(verdict);

        SignedUploadVerdict result = adapter.verifySignedUpload(
                new SignaturePort.SignedUploadCheck(SOURCE, SIGNED_1, Set.of("fp")));

        assertThat(result).isSameAs(verdict);
        verify(verifier).verify(SOURCE, SIGNED_1, Set.of("fp"));
    }

    @Test
    void finalDocumentIsTheLastUploadAndTheProofListsTheEvidenceOfEverySigner() {
        UUID id = UUID.randomUUID();
        SignatureRequest base = SignatureFixtures.request(id, SignatureMode.SEQUENTIAL, "proc-1", DIGEST);
        requests.put(id, base.closed(SignatureOutcome.SIGNED, null, NOW));
        Signer alice = external(id, 0, true, "alice@example.org").decided(SignerStatus.SIGNED, NOW, null);
        Signer bob = internal(id, 1, true, "bob").decided(SignerStatus.SIGNED, NOW, null);
        signers.put(id, List.of(alice, bob));
        uploadsByRequest.put(id, List.of(
                upload(id, alice.id(), 1, "work-1", SIGNED_1, "fp-alice"),
                upload(id, bob.id(), 2, "work-2", SIGNED_2, "fp-bob")));

        SignaturePort.SignedArtifacts artifacts = adapter.fetchArtifacts("eid:" + id);

        assertThat(artifacts.signedDocument()).isEqualTo(SIGNED_2);
        assertThat(artifacts.simulated()).isFalse();
        assertThat(artifacts.signedContentType()).isEqualTo("application/pdf");
        String proof = TestPdfs.text(artifacts.proof());
        assertThat(proof).doesNotContain("SIMULATION").doesNotContain("simulé");
        assertThat(proof).contains(id.toString(), DIGEST.substring(0, 32),
                SignatureWorkingDocuments.digestOf(SIGNED_2).substring(0, 32));
        assertThat(proof).contains("Alice Martin", "CN=Citizen CA", "0A1B", "fp-alice", "fp-bob", "PAdES-BASELINE-B",
                "RSA_SHA256", "OCSP", "QcSSCD", "Base de la qualification");
        assertThat(proof).doesNotContain("alice@example.org");
    }

    private UUID signedRequest(int dispatchAttempts) {
        UUID id = UUID.randomUUID();
        SignatureRequest base = SignatureFixtures.request(id, SignatureMode.SEQUENTIAL, "proc-1", DIGEST);
        requests.put(id, base.closed(SignatureOutcome.SIGNED, null, NOW).withDispatchAttempts(dispatchAttempts));
        Signer alice = external(id, 0, true, "alice@example.org").decided(SignerStatus.SIGNED, NOW, null);
        signers.put(id, List.of(alice));
        uploadsByRequest.put(id, List.of(upload(id, alice.id(), 1, "work-1", SIGNED_1, "fp-alice")));
        return id;
    }

    private EidSignatureAdapter withTimestamper(DocumentTimestamper timestamper, int maxAttempts) {
        return new EidSignatureAdapter(TENANT, repository, uploads, storage, verifier, timestamper, maxAttempts);
    }

    @Test
    void withoutTimestampConfigurationTheProofSaysSoAndTheDocumentIsUnchanged() {
        UUID id = signedRequest(0);

        SignaturePort.SignedArtifacts artifacts = adapter.fetchArtifacts("eid:" + id);

        assertThat(artifacts.signedDocument()).isEqualTo(SIGNED_1);
        assertThat(TestPdfs.text(artifacts.proof())).contains("non configur");
    }

    @Test
    void timestampedDocumentIsDeliveredAndTheProofNamesTheAuthorityTheTimeAndTheLevel() {
        UUID id = signedRequest(0);
        byte[] stamped = TestPdfs.pdf(2, "Contrat signé horodaté");
        DocumentTimestamper timestamper = signed -> new DocumentTimestamper.Result(stamped,
                Instant.parse("2026-11-13T10:00:05Z"), "CN=Test TSA,C=BE", true, DssDocumentTimestamper.LEVEL_LTA);

        SignaturePort.SignedArtifacts artifacts = withTimestamper(timestamper, 20).fetchArtifacts("eid:" + id);

        assertThat(artifacts.signedDocument()).isEqualTo(stamped);
        String proof = TestPdfs.text(artifacts.proof());
        assertThat(proof).contains("2026-11-13T10:00:05Z", "CN=Test TSA", "PAdES-BASELINE-LTA",
                SignatureWorkingDocuments.digestOf(stamped).substring(0, 32));
    }

    @Test
    void unavailableTimestampAuthorityFailsSoTheDispatcherRetriesThenDeliversWithoutTimestampAtThePenultimateAttempt() {
        DocumentTimestamper down = signed -> {
            throw new RfcTimestampSource.TimestampUnavailableException("timestamp authority cannot be reached", null);
        };
        UUID early = signedRequest(17);
        assertThatThrownBy(() -> withTimestamper(down, 20).fetchArtifacts("eid:" + early))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("timestamp authority unavailable");

        UUID penultimate = signedRequest(18);
        SignaturePort.SignedArtifacts artifacts = withTimestamper(down, 20).fetchArtifacts("eid:" + penultimate);

        assertThat(artifacts.signedDocument()).isEqualTo(SIGNED_1);
        assertThat(TestPdfs.text(artifacts.proof())).contains("NON HORODAT", "injoignable");
    }

    @Test
    void artifactsAreRefusedWithoutAnyAcceptedUploadOrWhenADocumentChanged() {
        UUID id = UUID.randomUUID();
        SignatureRequest base = SignatureFixtures.request(id, SignatureMode.PARALLEL, "proc-1", DIGEST);
        requests.put(id, base.closed(SignatureOutcome.SIGNED, null, NOW));
        Signer alice = external(id, 0, true, "alice@example.org").decided(SignerStatus.SIGNED, NOW, null);
        signers.put(id, List.of(alice));

        assertThatThrownBy(() -> adapter.fetchArtifacts("eid:" + id)).isInstanceOf(IllegalStateException.class);

        uploadsByRequest.put(id, List.of(upload(id, alice.id(), 1, "work-1", SIGNED_1, "fp")));
        when(storage.retrieve(eq(TENANT), eq("work-1"))).thenReturn(TestPdfs.pdf(1, "Falsifié"));
        assertThatThrownBy(() -> adapter.fetchArtifacts("eid:" + id)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed");

        when(storage.retrieve(eq(TENANT), eq("work-1"))).thenReturn(SIGNED_1);
        when(storage.retrieve(eq(TENANT), eq("storage/doc.pdf"))).thenReturn(TestPdfs.pdf(1, "Autre source"));
        assertThatThrownBy(() -> adapter.fetchArtifacts("eid:" + id)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source");
    }

    @Test
    void rejectsForeignOrUnknownReferences() {
        assertThatThrownBy(() -> adapter.status("stub:" + UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.status("eid:" + UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.status(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
