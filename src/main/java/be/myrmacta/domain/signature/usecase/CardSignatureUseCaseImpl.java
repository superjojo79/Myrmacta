package be.myrmacta.domain.signature.usecase;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.signature.SignatureAccessResolver;
import be.myrmacta.domain.signature.SignatureAccessResolver.InternalAccess;
import be.myrmacta.domain.signature.SignatureAccessResolver.ResolvedAccess;
import be.myrmacta.domain.signature.SignatureWorkingDocuments;
import be.myrmacta.domain.signature.model.InvalidCardSignatureInputException;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignedUploadRejectedException;
import be.myrmacta.domain.signature.model.SignedUploadRejection;
import be.myrmacta.domain.signature.model.SigningSessionExpiredException;
import be.myrmacta.domain.signature.port.in.CardSignatureUseCase;
import be.myrmacta.domain.signature.port.in.DecideSignatureUseCase;
import be.myrmacta.domain.signature.port.in.SubmitSignedDocumentUseCase;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §138 (D-EID-BRIDGE, D-EID-PIN) — signature dans la page par un pont navigateur, en deux temps.
 *
 * <p>{@code prepare} : accès résolu (mêmes règles et même refus uniforme que le téléversement), entrées bornées,
 * base = document courant, heure de signature = horloge du serveur à la seconde, appel du port ; aucune écriture.
 * {@code complete} : accès résolu, entrées bornées, document courant inchangé ({@code STALE_DOCUMENT}), session
 * de quinze minutes au plus ({@link SigningSessionExpiredException}), assemblage par le port, puis le PDF assemblé
 * suit exactement le chemin du téléversement ({@link SubmitSignedDocumentUseCase} : vérification hors transaction,
 * transaction courte, décision {@code SIGN}). Le PIN ne passe jamais ici.
 */
public final class CardSignatureUseCaseImpl implements CardSignatureUseCase {

    /** Durée de vie d'une préparation : le temps de saisir le PIN, pas d'oublier la page. */
    static final Duration SESSION_LIFETIME = Duration.ofMinutes(15);
    /** Écart d'horloge toléré entre deux instances, pour une heure de signature « dans le futur ». */
    static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    static final int MAX_CERTIFICATE_BYTES = 8 * 1024;
    static final int MAX_CHAIN_LENGTH = 8;
    static final int MAX_SIGNATURE_BYTES = 1024;

    private final SignatureAccessResolver accessResolver;
    private final SignaturePort port;
    private final SignatureWorkingDocuments documents;
    private final SubmitSignedDocumentUseCase submitUseCase;
    private final Clock clock;

    public CardSignatureUseCaseImpl(
            String tenantId,
            SignatureAccessResolver accessResolver,
            SignaturePort port,
            DocumentStoragePort storage,
            SignedUploadRepository uploads,
            SubmitSignedDocumentUseCase submitUseCase,
            Clock clock) {
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.port = Objects.requireNonNull(port, "port");
        this.documents = new SignatureWorkingDocuments(tenantId, storage, uploads, port);
        this.submitUseCase = Objects.requireNonNull(submitUseCase, "submitUseCase");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Prepared prepareByToken(String token, CardInput input) {
        ResolvedAccess access = accessResolver.resolve(token);
        return prepare(access.request(), access.signer(), input);
    }

    @Override
    public Prepared prepareInternal(String userId, UUID signerId, CardInput input) {
        InternalAccess access = accessResolver.resolveInternal(userId, signerId);
        return prepare(access.request(), access.signer(), input);
    }

    @Override
    public DecideSignatureUseCase.Result completeByToken(String token, Completion completion) {
        ResolvedAccess access = accessResolver.resolve(token);
        SignedPdf pdf = assemble(access.request(), access.signer(), completion);
        return submitUseCase.submitAssembledByToken(token, SubmitSignedDocumentUseCase.Upload.of(pdf.content()));
    }

    @Override
    public DecideSignatureUseCase.Result completeInternal(String userId, UUID signerId, Completion completion) {
        InternalAccess access = accessResolver.resolveInternal(userId, signerId);
        SignedPdf pdf = assemble(access.request(), access.signer(), completion);
        return submitUseCase.submitAssembledInternal(userId, signerId, SubmitSignedDocumentUseCase.Upload.of(pdf.content()));
    }

    private Prepared prepare(SignatureRequest request, Signer signer, CardInput input) {
        requireCardSignature(request, signer);
        requireBounded(input.certificate(), input.certificateChain());
        SignatureWorkingDocuments.Current current = documents.current(request);
        byte[] base = documents.read(request, current);
        Instant signingTime = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        SignaturePort.CardSignatureDigest digest;
        try {
            digest = port.prepareCardSignature(
                    new SignaturePort.CardSignatureInput(base, input.certificate(), input.certificateChain(), signingTime,
                            signer.position() + 1));
        } catch (IllegalArgumentException ex) {
            throw new InvalidCardSignatureInputException();
        }
        return new Prepared(digest.digest(), digest.hashFunction(), signingTime, current.digest());
    }

    private record SignedPdf(byte[] content) {}

    private SignedPdf assemble(SignatureRequest request, Signer signer, Completion completion) {
        requireCardSignature(request, signer);
        requireBounded(completion.certificate(), completion.certificateChain());
        if (completion.signature() == null || completion.signature().length == 0
                || completion.signature().length > MAX_SIGNATURE_BYTES || completion.signingTime() == null
                || completion.documentDigest() == null) {
            throw new InvalidCardSignatureInputException();
        }
        SignatureWorkingDocuments.Current current = documents.current(request);
        if (!current.digest().equals(completion.documentDigest())) {
            throw new SignedUploadRejectedException(SignedUploadRejection.STALE_DOCUMENT);
        }
        Instant now = clock.instant();
        if (completion.signingTime().isBefore(now.minus(SESSION_LIFETIME))
                || completion.signingTime().isAfter(now.plus(CLOCK_SKEW))) {
            throw new SigningSessionExpiredException();
        }
        byte[] base = documents.read(request, current);
        try {
            byte[] assembled = port.assembleCardSignature(
                    new SignaturePort.CardSignatureInput(
                            base, completion.certificate(), completion.certificateChain(), completion.signingTime(),
                            signer.position() + 1),
                    completion.signature());
            return new SignedPdf(assembled);
        } catch (IllegalArgumentException ex) {
            throw new InvalidCardSignatureInputException();
        }
    }

    /** Accès ouvert (comme le téléversement) et fournisseur qui signe avec une carte : sinon, le refus uniforme. */
    private void requireCardSignature(SignatureRequest request, Signer signer) {
        accessResolver.requireOpen(request, signer);
        if (!port.capabilities().signedUpload()) {
            throw new PortalAccessDeniedException("provider " + port.code() + " does not sign with a card");
        }
    }

    private static void requireBounded(byte[] certificate, List<byte[]> chain) {
        if (certificate == null || certificate.length == 0 || certificate.length > MAX_CERTIFICATE_BYTES
                || chain.size() > MAX_CHAIN_LENGTH) {
            throw new InvalidCardSignatureInputException();
        }
        for (byte[] entry : chain) {
            if (entry == null || entry.length == 0 || entry.length > MAX_CERTIFICATE_BYTES) {
                throw new InvalidCardSignatureInputException();
            }
        }
    }
}
