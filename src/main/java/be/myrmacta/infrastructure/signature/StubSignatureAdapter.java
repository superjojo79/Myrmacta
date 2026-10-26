package be.myrmacta.infrastructure.signature;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adaptateur de simulation (TODO §72, D-SIGNATURE-STUB-BEHAVIOR) : aucun fournisseur. Les décisions
 * des signataires sont prises par Myrmacta (portail, « Mes signatures ») et persistées ; le stub
 * relit cet état (référence {@code stub:<requestId>}) pour produire, à la demande, le document
 * « signé » filigrané et le PDF de preuve. Sans état propre, donc sûr au redémarrage.
 *
 * <p>Tout artefact porte « SIMULATION — sans valeur juridique ». Le stub est désactivé par défaut et
 * soumis à {@link SignatureStubGuard}.
 */
public final class StubSignatureAdapter implements SignaturePort {

    public static final String CODE = "stub";
    static final String REFERENCE_PREFIX = "stub:";
    static final String LEVEL = "SIMULATED";

    private final String tenantId;
    private final SignatureRequestRepository repository;
    private final DocumentStoragePort storage;
    private final StubPdfFactory pdf = new StubPdfFactory();

    public StubSignatureAdapter(String tenantId, SignatureRequestRepository repository, DocumentStoragePort storage) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public SignatureProviderCapabilities capabilities() {
        return new SignatureProviderCapabilities(true, true, false, true, Set.of(LEVEL));
    }

    @Override
    public SignatureSubmission submit(SubmitCommand command) {
        Objects.requireNonNull(command, "command");
        pdf.requireReadable(command.document());
        return new SignatureSubmission(REFERENCE_PREFIX + command.requestId(), true, LEVEL, Map.of());
    }

    @Override
    public SignatureProviderStatus status(String providerReference) {
        SignatureRequest request = load(providerReference);
        return new SignatureProviderStatus(
                request.status() == SignatureStatus.PENDING
                        ? Optional.empty()
                        : Optional.of(SignatureOutcome.valueOf(request.status().name())));
    }

    @Override
    public void cancel(String providerReference) {
        // Rien à annuler côté « fournisseur » : l'état est celui de Myrmacta.
    }

    @Override
    public SignedArtifacts fetchArtifacts(String providerReference) {
        SignatureRequest request = load(providerReference);
        if (request.status() != SignatureStatus.SIGNED) {
            throw new IllegalStateException("request " + request.id() + " is not SIGNED (" + request.status() + ")");
        }
        byte[] source = storage.retrieve(tenantId, request.sourceStorageRef());
        if (!sha256(source).equals(request.sourceDigest())) {
            throw new IllegalStateException("source document changed since emission of request " + request.id());
        }
        List<Signer> signers = repository.findSigners(request.id());
        List<StubPdfFactory.ProofSigner> lines = signers.stream()
                .map(s -> new StubPdfFactory.ProofSigner(
                        s.displayName() + " (" + (s.email() != null ? s.maskedEmail() : s.userId()) + ")",
                        s.kind().name(),
                        s.required(),
                        s.status().name(),
                        s.decidedAt()))
                .toList();
        byte[] proof = pdf.proof(new StubPdfFactory.ProofData(
                request.id(), request.sourceStorageRef(), request.sourceDigest(), CODE, request.mode().name(),
                request.createdAt(), request.completedAt(), lines));
        return new SignedArtifacts(pdf.watermark(source), "application/pdf", proof, "application/pdf", true);
    }

    private static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private SignatureRequest load(String providerReference) {
        if (providerReference == null || !providerReference.startsWith(REFERENCE_PREFIX)) {
            throw new IllegalArgumentException("not a stub reference: " + providerReference);
        }
        UUID id = UUID.fromString(providerReference.substring(REFERENCE_PREFIX.length()));
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("unknown request " + id));
    }
}
