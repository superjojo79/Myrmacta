package be.myrmacta.domain.signature.usecase;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.signature.SignatureAccessResolver;
import be.myrmacta.domain.signature.SignatureAccessResolver.InternalAccess;
import be.myrmacta.domain.signature.SignatureAccessResolver.ResolvedAccess;
import be.myrmacta.domain.signature.SignatureDecisionApplier;
import be.myrmacta.domain.signature.SignatureOutcomeHandler;
import be.myrmacta.domain.signature.SignatureWorkingDocuments;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.model.SignedUpload;
import be.myrmacta.domain.signature.model.SignedUploadEvidence;
import be.myrmacta.domain.signature.model.SignedUploadRejectedException;
import be.myrmacta.domain.signature.model.SignedUploadRejection;
import be.myrmacta.domain.signature.model.SignedUploadTooLargeException;
import be.myrmacta.domain.signature.model.SignedUploadVerdict;
import be.myrmacta.domain.signature.port.in.DecideSignatureUseCase;
import be.myrmacta.domain.signature.port.in.SubmitSignedDocumentUseCase;
import be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import be.myrmacta.domain.signature.port.out.SignatureTransaction;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * TODO §138 (D-EID-UPLOAD, D-EID-CHAIN) — un signataire apporte le PDF qu'il a signé hors de Myrmacta.
 *
 * <p>Déroulé : (1) accès, avec les règles et le refus uniforme du clic « Signer » (demande {@code PENDING},
 * échéance, signataire {@code PENDING}), avant toute lecture du fichier ; (2) taille bornée ; (3) document
 * courant et empreintes de certificats déjà utilisées ; (4) vérification par le fournisseur, <strong>hors
 * transaction</strong> (la révocation peut joindre le réseau) ; (5) refus : exception portant le motif ;
 * (6) une transaction courte : verrou de ligne sur la demande, relecture des téléversements ({@code
 * STALE_DOCUMENT} si le document courant a changé, {@code DUPLICATE_SIGNER} si le certificat vient d'être
 * utilisé), stockage du PDF, enregistrement du téléversement, puis décision {@code SIGN} par la même
 * progression que le clic ({@link SignatureDecisionApplier}).
 *
 * <p>Sous PostgreSQL, une contrainte d'unicité violée condamne la transaction même attrapée : on verrouille
 * et on cherche l'existant avant d'insérer. Un échec après le stockage supprime le blob (il ne serait
 * jamais référencé).
 */
public final class SubmitSignedDocumentUseCaseImpl implements SubmitSignedDocumentUseCase {

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final String tenantId;
    private final SignatureRequestRepository repository;
    private final SignedUploadRepository uploads;
    private final SignatureAccessResolver accessResolver;
    private final SignatureDecisionApplier applier;
    private final SignaturePort port;
    private final SignatureAvailabilityPort availability;
    private final DocumentStoragePort storage;
    private final SignatureWorkingDocuments documents;
    private final SignatureTransaction transaction;
    private final Clock clock;

    public SubmitSignedDocumentUseCaseImpl(
            String tenantId,
            SignatureRequestRepository repository,
            SignedUploadRepository uploads,
            SignatureAccessResolver accessResolver,
            SignatureOutcomeHandler outcomeHandler,
            SignaturePort port,
            SignatureAvailabilityPort availability,
            DocumentStoragePort storage,
            SignatureTransaction transaction,
            Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.uploads = Objects.requireNonNull(uploads, "uploads");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.applier = new SignatureDecisionApplier(repository, outcomeHandler, clock);
        this.port = Objects.requireNonNull(port, "port");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.documents = new SignatureWorkingDocuments(tenantId, storage, uploads, port);
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public DecideSignatureUseCase.Result submitByToken(String token, Upload upload) {
        ResolvedAccess access = accessResolver.resolve(token);
        return submit(access.request(), access.signer(), upload);
    }

    @Override
    public DecideSignatureUseCase.Result submitInternal(String userId, UUID signerId, Upload upload) {
        InternalAccess access = accessResolver.resolveInternal(userId, signerId);
        return submit(access.request(), access.signer(), upload);
    }

    private DecideSignatureUseCase.Result submit(SignatureRequest request, Signer signer, Upload upload) {
        Objects.requireNonNull(upload, "upload");
        requireOpen(request, signer);
        if (!port.capabilities().signedUpload()) {
            throw new PortalAccessDeniedException("provider " + port.code() + " takes no signed upload");
        }
        long max = availability.maxSignedUploadBytes();
        if (upload.size() > max) {
            throw new SignedUploadTooLargeException(max);
        }
        if (upload.size() <= 0) {
            throw new SignedUploadRejectedException(SignedUploadRejection.NOT_A_PDF);
        }

        // Base de la vérification : le document de travail courant, et les certificats déjà utilisés.
        List<SignedUpload> accepted = uploads.findByRequestId(request.id());
        SignatureWorkingDocuments.Current base = documents.current(request, accepted);
        byte[] baseContent = documents.read(request, base);
        Set<String> used = new HashSet<>();
        accepted.forEach(u -> used.add(u.evidence().certificateFingerprint()));

        byte[] signed = upload.read();
        if (signed.length > max) {
            throw new SignedUploadTooLargeException(max);
        }
        // Hors transaction : la révocation du certificat peut joindre le réseau.
        SignedUploadVerdict verdict =
                port.verifySignedUpload(new SignaturePort.SignedUploadCheck(baseContent, signed, used));
        if (!verdict.isAccepted()) {
            throw new SignedUploadRejectedException(verdict.rejection());
        }
        return record(request, signer, base, signed, verdict.evidence());
    }

    /** Mêmes conditions que la décision par clic : sinon, le refus uniforme. */
    private void requireOpen(SignatureRequest request, Signer signer) {
        if (request.status() != SignatureStatus.PENDING || !request.deadlineAt().isAfter(clock.instant())) {
            throw new PortalAccessDeniedException("request " + request.id() + " cannot take an upload ("
                    + request.status() + ")");
        }
        if (signer.status() != SignerStatus.PENDING) {
            throw new PortalAccessDeniedException("signer " + signer.id() + " cannot decide");
        }
    }

    private DecideSignatureUseCase.Result record(
            SignatureRequest request,
            Signer signer,
            SignatureWorkingDocuments.Current base,
            byte[] signed,
            SignedUploadEvidence evidence) {
        String[] storedRef = new String[1];
        try {
            return transaction.execute(() -> {
                SignatureRequest locked = repository.findByIdForUpdate(request.id())
                        .orElseThrow(() -> new PortalAccessDeniedException("request gone " + request.id()));
                requireOpen(locked, repository.findSigners(locked.id()).stream()
                        .filter(s -> s.id().equals(signer.id()))
                        .findFirst()
                        .orElseThrow(() -> new PortalAccessDeniedException("signer gone " + signer.id())));
                List<SignedUpload> accepted = uploads.findByRequestId(locked.id());
                if (!documents.current(locked, accepted).digest().equals(base.digest())) {
                    throw new SignedUploadRejectedException(SignedUploadRejection.STALE_DOCUMENT);
                }
                if (accepted.stream().anyMatch(u -> u.evidence().certificateFingerprint()
                        .equals(evidence.certificateFingerprint()))) {
                    throw new SignedUploadRejectedException(SignedUploadRejection.DUPLICATE_SIGNER);
                }
                storedRef[0] = storage.store(tenantId, PDF_CONTENT_TYPE, signed);
                int sequence = accepted.stream().mapToInt(SignedUpload::sequence).max().orElse(0) + 1;
                uploads.save(new SignedUpload(UUID.randomUUID(), tenantId, locked.id(), signer.id(), sequence,
                        storedRef[0], SignatureWorkingDocuments.digestOf(signed), evidence, clock.instant()));
                return applier.apply(locked, signer, DecideSignatureUseCase.Choice.SIGN, null);
            });
        } catch (RuntimeException ex) {
            // Transaction annulée (ou validation échouée) : le blob ne sera jamais référencé ni purgé.
            if (storedRef[0] != null) {
                try {
                    storage.delete(tenantId, storedRef[0]);
                } catch (RuntimeException ignored) {
                    // au mieux : l'erreur d'origine prime
                }
            }
            throw ex;
        }
    }
}
