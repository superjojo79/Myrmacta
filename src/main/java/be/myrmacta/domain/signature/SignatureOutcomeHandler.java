package be.myrmacta.domain.signature;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.port.out.SignatureMetricsPort;
import be.myrmacta.domain.signature.port.out.SignatureOutcomeListener;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * TODO §72 — tout ce qui suit la fermeture d'une demande, quelle qu'en soit l'origine (décision d'un
 * signataire, annulation, échéance, callback du fournisseur) : documents signés et preuve, métriques,
 * notifications, reprise du process.
 *
 * <p>Le document signé et la preuve sont des éléments {@code PRODUCED_DOC} <strong>marqués</strong>
 * dans {@code metadata} (D-SIGNATURE-ELEMENT) : aucune nouvelle valeur d'{@code ElementType}.
 */
public final class SignatureOutcomeHandler {

    public static final String ROLE_SIGNED_DOCUMENT = "SIGNED_DOCUMENT";
    public static final String ROLE_SIGNATURE_PROOF = "SIGNATURE_PROOF";
    public static final String META_REQUEST_ID = "signatureRequestId";
    public static final String META_ROLE = "signatureRole";
    public static final String META_SIMULATED = "simulated";
    static final String ADDED_BY = "signature";

    private final String tenantId;
    private final SignatureRequestRepository repository;
    private final SignaturePort port;
    private final DocumentStoragePort storage;
    private final DossierElementRepository elementRepository;
    private final AddDossierElementUseCase addElementUseCase;
    private final SignatureNotifier notifier;
    private final SignatureMetricsPort metrics;
    private final SignatureOutcomeListener listener;
    private final Clock clock;

    public SignatureOutcomeHandler(
            String tenantId,
            SignatureRequestRepository repository,
            SignaturePort port,
            DocumentStoragePort storage,
            DossierElementRepository elementRepository,
            AddDossierElementUseCase addElementUseCase,
            SignatureNotifier notifier,
            SignatureMetricsPort metrics,
            SignatureOutcomeListener listener,
            Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.port = Objects.requireNonNull(port, "port");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.addElementUseCase = Objects.requireNonNull(addElementUseCase, "addElementUseCase");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Ferme une demande depuis l'extérieur d'une décision de signataire (annulation, échéance,
     * callback) : applique demande et signataires de façon atomique, puis les suites.
     *
     * @return {@code false} si un autre appel est passé avant (compare-and-set) : rien n'est fait
     */
    public boolean close(
            SignatureRequest request, List<Signer> signers, SignatureOutcome outcome, String reason) {
        SignatureRequest closed = request.closed(outcome, reason, clock.instant());
        if (!repository.update(closed, signers)) {
            return false;
        }
        afterClosed(closed, outcome);
        return true;
    }

    /** Suites d'une fermeture déjà persistée. Une erreur remonte : la transaction appelante est annulée. */
    public void afterClosed(SignatureRequest closed, SignatureOutcome outcome) {
        metrics.outcome(outcome);
        List<String> stored = new ArrayList<>();
        // TODO §109 — Yousign exige une réponse en moins d'une seconde : le PDF est téléchargé
        // par le répartiteur, qui reprend ensuite le process. Le stub reste synchrone.
        boolean deferToDispatcher = outcome == SignatureOutcome.SIGNED && port.capabilities().hostedSigningUrl();
        try {
            if (outcome == SignatureOutcome.SIGNED && !deferToDispatcher) {
                attachArtifacts(closed, stored);
            } else if (outcome == SignatureOutcome.CANCELLED || outcome == SignatureOutcome.EXPIRED
                    || (outcome == SignatureOutcome.DECLINED && port.capabilities().hostedSigningUrl())) {
                cancelAtProvider(closed);
            }
            notifier.notifyOutcome(closed, outcome);
            if (!deferToDispatcher) {
                listener.outcomeRecorded(closed.id());
            }
        } catch (RuntimeException ex) {
            // La transaction appelante est annulée : les blobs déjà écrits ne seront jamais référencés (ni purgés).
            for (String ref : stored) {
                try {
                    storage.delete(tenantId, ref);
                } catch (RuntimeException ignored) {
                    // au mieux : l'erreur d'origine prime
                }
            }
            throw ex;
        }
    }

    /** Les signataires devenus {@code PENDING} : les internes sont prévenus dans l'inbox. */
    public void notifyNewlyPending(SignatureRequest request, List<Signer> newlyPending) {
        for (Signer signer : newlyPending) {
            if (signer.kind() == be.myrmacta.domain.signature.model.SignerKind.INTERNAL) {
                notifier.notifyInternalSigner(request, signer.userId());
            }
        }
    }

    /**
     * Télécharge le PDF et la preuve d'une demande hébergée déjà {@code SIGNED}, une seule fois.
     * Appelé par le répartiteur, hors du webhook (TODO §109, D-SIGNATURE-WEBHOOK).
     */
    public void ensureSignedArtifacts(SignatureRequest request) {
        if (request.status() != be.myrmacta.domain.signature.model.SignatureStatus.SIGNED
                || !port.capabilities().hostedSigningUrl()
                || artifactsStored(request)) {
            return;
        }
        List<String> stored = new ArrayList<>();
        try {
            attachArtifacts(request, stored);
        } catch (RuntimeException ex) {
            for (String ref : stored) {
                try {
                    storage.delete(tenantId, ref);
                } catch (RuntimeException ignored) {
                    // au mieux : l'erreur d'origine prime
                }
            }
            throw ex;
        }
    }

    private boolean artifactsStored(SignatureRequest request) {
        String id = request.id().toString();
        for (DossierElement element : elementRepository.findByDossierIdOrderByAddedAtAsc(request.dossierId())) {
            if (element.type() == ElementType.PRODUCED_DOC
                    && ADDED_BY.equals(element.addedBy())
                    && id.equals(String.valueOf(element.metadata().get(META_REQUEST_ID)))
                    && ROLE_SIGNED_DOCUMENT.equals(element.metadata().get(META_ROLE))) {
                return true;
            }
        }
        return false;
    }

    private void attachArtifacts(SignatureRequest request, List<String> stored) {
        SignaturePort.SignedArtifacts artifacts = port.fetchArtifacts(request.providerReference());
        addProduced(request, "Document signé", artifacts.signedDocument(), artifacts.signedContentType(),
                ROLE_SIGNED_DOCUMENT, artifacts.simulated(), stored);
        if (artifacts.proof() != null && artifacts.proof().length > 0) {
            addProduced(request, "Preuve de signature", artifacts.proof(), artifacts.proofContentType(),
                    ROLE_SIGNATURE_PROOF, artifacts.simulated(), stored);
        }
    }

    private void addProduced(
            SignatureRequest request, String label, byte[] content, String contentType, String role, boolean simulated,
            List<String> stored) {
        String storageRef = storage.store(tenantId, contentType, content);
        stored.add(storageRef);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("contentType", contentType);
        metadata.put(META_REQUEST_ID, request.id().toString());
        metadata.put(META_ROLE, role);
        metadata.put(META_SIMULATED, simulated);
        addElementUseCase.add(
                new AddDossierElementUseCase.AddCommand(request.dossierId(), ElementType.PRODUCED_DOC,
                        simulated ? label + " (SIMULATION)" : label, storageRef, metadata, ADDED_BY,
                        request.activityId()),
                AccessContext.system("signature"));
    }

    private void cancelAtProvider(SignatureRequest request) {
        if (request.providerReference() == null) {
            return;
        }
        try {
            port.cancel(request.providerReference());
        } catch (RuntimeException ex) {
            // au mieux : ne jamais bloquer la fermeture (D-SIGNATURE-CANCEL-PROPAGATION)
        }
    }

    /** Signataires encore ouverts basculés dans {@code status} ; les autres inchangés. */
    public static List<Signer> closeOpenSigners(List<Signer> signers, SignerStatus status, Instant at) {
        List<Signer> out = new ArrayList<>();
        for (Signer s : signers) {
            out.add(s.status().isOpen() ? s.decided(status, at, null) : s);
        }
        return out;
    }
}
