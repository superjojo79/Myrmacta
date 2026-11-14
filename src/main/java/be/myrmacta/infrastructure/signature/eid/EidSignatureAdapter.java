package be.myrmacta.infrastructure.signature.eid;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.signature.SignatureWorkingDocuments;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignedUpload;
import be.myrmacta.domain.signature.model.SignedUploadVerdict;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.openpdf.text.pdf.PdfReader;

/**
 * Fournisseur {@code eid} (TODO §138, D-EID-UPLOAD) : signature qualifiée par carte eID belge, sans agent
 * sur le poste. Le signataire télécharge le PDF, le signe avec l'outil qu'il possède, puis téléverse le PDF
 * signé ; ce fournisseur n'appelle personne pour signer, il <em>vérifie</em> ({@link SignedPdfVerifier}).
 *
 * <p>Comme le stub, il est sans état propre : la référence est {@code eid:<requestId>} et l'état est celui
 * de Myrmacta (demande, signataires, téléversements acceptés), donc sûr au redémarrage. Le document signé
 * final est le PDF du dernier téléversement accepté — il porte toutes les signatures — et la preuve reprend
 * les constats de chacune (D-EID-EVIDENCE). Ni l'un ni l'autre n'est marqué simulé.
 */
public final class EidSignatureAdapter implements SignaturePort {

    public static final String CODE = "eid";
    public static final String LEVEL = "QES";
    static final String REFERENCE_PREFIX = "eid:";
    private static final String PDF = "application/pdf";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(EidSignatureAdapter.class);

    private final String tenantId;
    private final SignatureRequestRepository repository;
    private final SignedUploadRepository uploads;
    private final DocumentStoragePort storage;
    private final SignedPdfVerifier verifier;
    private final CardSignatureAssembler cardAssembler;
    private final EidProofPdfFactory proofFactory = new EidProofPdfFactory();
    private final DocumentTimestamper timestamper;
    private final int dispatchMaxAttempts;

    public EidSignatureAdapter(
            String tenantId,
            SignatureRequestRepository repository,
            SignedUploadRepository uploads,
            DocumentStoragePort storage,
            SignedPdfVerifier verifier,
            CardSignatureAssembler cardAssembler) {
        this(tenantId, repository, uploads, storage, verifier, cardAssembler, null, 0);
    }

    /**
     * @param timestamper horodatage du document final (D-EID-TIMESTAMP) ; {@code null} : non configuré
     * @param dispatchMaxAttempts tentatives du répartiteur ({@code app.signature.dispatch.max-attempts}) : à
     *     l'avant-dernière, une TSA injoignable n'empêche plus la livraison, qui se fait sans horodatage, dit dans la
     *     preuve et journalisé
     */
    public EidSignatureAdapter(
            String tenantId,
            SignatureRequestRepository repository,
            SignedUploadRepository uploads,
            DocumentStoragePort storage,
            SignedPdfVerifier verifier,
            CardSignatureAssembler cardAssembler,
            DocumentTimestamper timestamper,
            int dispatchMaxAttempts) {
        this.timestamper = timestamper;
        this.dispatchMaxAttempts = dispatchMaxAttempts;
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.uploads = Objects.requireNonNull(uploads, "uploads");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.cardAssembler = Objects.requireNonNull(cardAssembler, "cardAssembler");
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public SignatureProviderCapabilities capabilities() {
        return new SignatureProviderCapabilities(true, true, false, true, true, Set.of(LEVEL));
    }

    /** Refuse, dès l'émission, un contenu que la signature ne saurait traiter : PDF illisible ou chiffré. */
    @Override
    public SignatureSubmission submit(SubmitCommand command) {
        Objects.requireNonNull(command, "command");
        requireSignablePdf(command.document());
        return new SignatureSubmission(REFERENCE_PREFIX + command.requestId(), false, LEVEL, Map.of());
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
        // Rien à annuler chez un tiers : l'état est celui de Myrmacta.
    }

    @Override
    public SignedUploadVerdict verifySignedUpload(SignedUploadCheck check) {
        return verifier.verify(check.baseDocument(), check.signedDocument(), check.usedCertificateFingerprints());
    }

    @Override
    public CardSignatureDigest prepareCardSignature(CardSignatureInput input) {
        return cardAssembler.prepare(input);
    }

    @Override
    public byte[] assembleCardSignature(CardSignatureInput input, byte[] signatureValue) {
        return cardAssembler.assemble(input, signatureValue);
    }

    @Override
    public SignedArtifacts fetchArtifacts(String providerReference) {
        SignatureRequest request = load(providerReference);
        if (request.status() != SignatureStatus.SIGNED) {
            throw new IllegalStateException("request " + request.id() + " is not SIGNED (" + request.status() + ")");
        }
        List<SignedUpload> accepted = uploads.findByRequestId(request.id()).stream()
                .sorted(Comparator.comparingInt(SignedUpload::sequence))
                .toList();
        if (accepted.isEmpty()) {
            throw new IllegalStateException("request " + request.id() + " has no accepted signed upload");
        }
        SignedUpload last = accepted.get(accepted.size() - 1);
        byte[] signed = storage.retrieve(tenantId, last.storageRef());
        if (signed == null || !SignatureWorkingDocuments.digestOf(signed).equals(last.digest())) {
            throw new IllegalStateException("signed document changed since acceptance for request " + request.id());
        }
        byte[] source = storage.retrieve(tenantId, request.sourceStorageRef());
        if (source == null || !SignatureWorkingDocuments.digestOf(source).equals(request.sourceDigest())) {
            throw new IllegalStateException("source document changed since emission of request " + request.id());
        }
        EidProofPdfFactory.ProofTimestamp timestamp = EidProofPdfFactory.ProofTimestamp.NOT_CONFIGURED;
        if (timestamper != null) {
            try {
                DocumentTimestamper.Result result = timestamper.timestamp(signed);
                signed = result.document();
                timestamp = new EidProofPdfFactory.ProofTimestamp(true, true, result.tokenTime(), result.authority(),
                        result.authorityTrusted(), result.level());
            } catch (RfcTimestampSource.TimestampUnavailableException unavailable) {
                if (request.dispatchAttempts() < dispatchMaxAttempts - 2) {
                    // Le répartiteur reprendra (D-EID-TIMESTAMP).
                    throw new IllegalStateException("timestamp authority unavailable for request " + request.id(), unavailable);
                }
                // Avant-dernière tentative : livrer sans horodatage plutôt que bloquer le dossier, et le dire.
                LOG.warn("eID signed document of request {} delivered WITHOUT timestamp at dispatch attempt {} of {}: {}",
                        request.id(), request.dispatchAttempts() + 1, dispatchMaxAttempts, unavailable.getMessage());
                timestamp = EidProofPdfFactory.ProofTimestamp.UNAVAILABLE;
            }
        }
        Map<UUID, SignedUpload> bySigner = new java.util.HashMap<>();
        accepted.forEach(u -> bySigner.put(u.signerId(), u));
        List<EidProofPdfFactory.ProofSigner> lines = repository.findSigners(request.id()).stream()
                .map(s -> proofSigner(s, bySigner.get(s.id())))
                .toList();
        byte[] proof = proofFactory.proof(new EidProofPdfFactory.ProofData(
                request.id(), CODE, request.mode().name(), request.createdAt(), request.completedAt(),
                request.sourceDigest(), SignatureWorkingDocuments.digestOf(signed), lines, timestamp));
        return new SignedArtifacts(signed, PDF, proof, PDF, false);
    }

    private static EidProofPdfFactory.ProofSigner proofSigner(Signer s, SignedUpload upload) {
        return new EidProofPdfFactory.ProofSigner(
                s.displayName() + " (" + (s.email() != null ? s.maskedEmail() : s.userId()) + ")",
                s.kind().name(),
                s.required(),
                s.status().name(),
                s.decidedAt(),
                upload == null ? null : upload.sequence(),
                upload == null ? null : upload.evidence());
    }

    private static void requireSignablePdf(byte[] pdf) {
        if (pdf == null) {
            throw new IllegalArgumentException("no document");
        }
        try {
            PdfReader reader = new PdfReader(pdf);
            try {
                if (reader.isEncrypted()) {
                    throw new IllegalArgumentException("encrypted PDF");
                }
                if (reader.getNumberOfPages() < 1) {
                    throw new IllegalArgumentException("PDF without page");
                }
            } finally {
                reader.close();
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw new IllegalArgumentException("unreadable PDF: " + ex.getClass().getSimpleName(), ex);
        }
    }

    private SignatureRequest load(String providerReference) {
        if (providerReference == null || !providerReference.startsWith(REFERENCE_PREFIX)) {
            throw new IllegalArgumentException("not an eid reference: " + providerReference);
        }
        UUID id = UUID.fromString(providerReference.substring(REFERENCE_PREFIX.length()));
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("unknown request " + id));
    }
}
