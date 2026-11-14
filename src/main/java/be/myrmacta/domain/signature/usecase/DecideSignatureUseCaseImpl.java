package be.myrmacta.domain.signature.usecase;

import be.myrmacta.domain.signature.SignatureAccessResolver;
import be.myrmacta.domain.signature.SignatureAccessResolver.InternalAccess;
import be.myrmacta.domain.signature.SignatureAccessResolver.ResolvedAccess;
import be.myrmacta.domain.signature.SignatureDecisionApplier;
import be.myrmacta.domain.signature.SignatureOutcomeHandler;
import be.myrmacta.domain.signature.model.HostedSignatureException;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignedUploadRequiredException;
import be.myrmacta.domain.signature.port.in.DecideSignatureUseCase;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §72 — un signataire signe ou refuse. La progression (modes, requis / facultatifs) et le
 * compare-and-set atomique sont ceux de {@link SignatureDecisionApplier} : une double soumission ou une
 * décision concurrente perd et reçoit le refus uniforme (S-DEC-3). L'actrice est tracée par
 * {@link Signer#actorId()} (D-SIGNATURE-ACTOR).
 *
 * <p>Un fournisseur hébergé refuse le clic « signer » dans Myrmacta (D-SIGNATURE-LINK) : la cérémonie
 * se fait sur son site. Un fournisseur à PDF apporté le refuse aussi (TODO §138, D-EID-UPLOAD) : la
 * signature passe par le téléversement. Le refus, lui, reste local.
 */
public final class DecideSignatureUseCaseImpl implements DecideSignatureUseCase {

    static final String REASON_REQUIRED_DECLINED = SignatureDecisionApplier.REASON_REQUIRED_DECLINED;
    static final String REASON_UNREACHABLE = SignatureDecisionApplier.REASON_UNREACHABLE;

    private final SignatureAccessResolver accessResolver;
    private final SignatureDecisionApplier applier;
    private final SignaturePort signaturePort;

    public DecideSignatureUseCaseImpl(
            SignatureRequestRepository repository,
            SignatureAccessResolver accessResolver,
            SignatureOutcomeHandler outcomeHandler,
            SignaturePort signaturePort,
            Clock clock) {
        Objects.requireNonNull(repository, "repository");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.applier = new SignatureDecisionApplier(repository, outcomeHandler, clock);
        this.signaturePort = Objects.requireNonNull(signaturePort, "signaturePort");
    }

    @Override
    public Result decideByToken(String token, Choice choice, String reason) {
        ResolvedAccess access = accessResolver.resolve(token);
        return apply(access.request(), access.signer(), choice, reason);
    }

    @Override
    public Result decideInternal(String userId, UUID signerId, Choice choice, String reason) {
        InternalAccess access = accessResolver.resolveInternal(userId, signerId);
        return apply(access.request(), access.signer(), choice, reason);
    }

    private Result apply(SignatureRequest request, Signer signer, Choice choice, String rawReason) {
        if (choice == Choice.SIGN) {
            SignaturePort.SignatureProviderCapabilities capabilities = signaturePort.capabilities();
            if (capabilities.hostedSigningUrl()) {
                throw new HostedSignatureException();
            }
            if (capabilities.signedUpload()) {
                throw new SignedUploadRequiredException();
            }
        }
        return applier.apply(request, signer, choice, choice == Choice.DECLINE ? cleanReason(rawReason) : null);
    }

    /** Motif libre borné : caractères de contrôle retirés, 500 caractères au plus (échappé à l'affichage). */
    static String cleanReason(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("\\p{Cntrl}", " ").strip();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > Signer.MAX_DECLINE_REASON) {
            throw new IllegalArgumentException("reason must be at most " + Signer.MAX_DECLINE_REASON + " characters");
        }
        return cleaned;
    }
}
