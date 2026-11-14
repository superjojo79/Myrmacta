package be.myrmacta.domain.signature.usecase;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.signature.SignatureWorkingDocuments;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.model.SignatureStatus;
import be.myrmacta.domain.signature.model.Signer;
import be.myrmacta.domain.signature.model.SignerKind;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.port.in.CardSignatureUseCase;
import be.myrmacta.domain.signature.port.in.DecideSignatureUseCase;
import be.myrmacta.domain.signature.port.in.GetSignatureUseCase;
import be.myrmacta.domain.signature.port.in.MySignaturesUseCase;
import be.myrmacta.domain.signature.port.in.SubmitSignedDocumentUseCase;
import be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** TODO §72 (US-10, A4) — « Mes signatures » : filtrage par identité, tour et droit de lecture du dossier. */
public final class MySignaturesUseCaseImpl implements MySignaturesUseCase {

    private final String tenantId;
    private final SignatureRequestRepository repository;
    private final DossierRepository dossierRepository;
    private final DossierAccessPolicy accessPolicy;
    private final DossierElementRepository elementRepository;
    private final SignatureWorkingDocuments documents;
    private final DecideSignatureUseCase decideUseCase;
    private final SubmitSignedDocumentUseCase submitUseCase;
    private final CardSignatureUseCase cardUseCase;
    private final SignatureAvailabilityPort availability;
    private final SignaturePort port;
    private final Clock clock;

    public MySignaturesUseCaseImpl(
            String tenantId,
            SignatureRequestRepository repository,
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy,
            DossierElementRepository elementRepository,
            DocumentStoragePort storage,
            DecideSignatureUseCase decideUseCase,
            SignaturePort port,
            Clock clock,
            SignedUploadRepository uploads,
            SubmitSignedDocumentUseCase submitUseCase,
            CardSignatureUseCase cardUseCase,
            SignatureAvailabilityPort availability) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.decideUseCase = Objects.requireNonNull(decideUseCase, "decideUseCase");
        this.submitUseCase = Objects.requireNonNull(submitUseCase, "submitUseCase");
        this.cardUseCase = Objects.requireNonNull(cardUseCase, "cardUseCase");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.port = Objects.requireNonNull(port, "port");
        this.documents = new SignatureWorkingDocuments(tenantId, storage, uploads, port);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public List<Item> list(AccessContext.User user) {
        return repository.findOpenInternalSigners(user.userId()).stream()
                .map(signer -> item(user, signer))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public GetSignatureUseCase.DocumentContent document(AccessContext.User user, UUID signerId) {
        Access access = requireAccess(user, signerId);
        SignatureRequest request = access.request();
        DossierElement element = sourceElement(request)
                .orElseThrow(() -> new PortalAccessDeniedException("source element gone for " + request.id()));
        // Document de travail courant : source, ou dernier téléversement accepté (TODO §138, D-EID-CHAIN).
        byte[] content = documents.read(request, documents.current(request));
        String base = element.label() == null ? "document" : element.label().replaceAll("[^A-Za-z0-9._-]+", "_");
        return new GetSignatureUseCase.DocumentContent(content,
                RequestSignatureUseCaseImpl.PDF_CONTENT_TYPE,
                (base.isBlank() ? "document" : base) + ".pdf");
    }

    @Override
    public DecideSignatureUseCase.Result decide(
            AccessContext.User user, UUID signerId, DecideSignatureUseCase.Choice choice, String reason) {
        requireAccess(user, signerId);
        return decideUseCase.decideInternal(user.userId(), signerId, choice, reason);
    }

    @Override
    public DecideSignatureUseCase.Result submitSigned(
            AccessContext.User user, UUID signerId, SubmitSignedDocumentUseCase.Upload upload) {
        requireAccess(user, signerId);
        return submitUseCase.submitInternal(user.userId(), signerId, upload);
    }

    @Override
    public CardSignatureUseCase.Prepared prepareCardSignature(
            AccessContext.User user, UUID signerId, CardSignatureUseCase.CardInput input) {
        requireAccess(user, signerId);
        return cardUseCase.prepareInternal(user.userId(), signerId, input);
    }

    @Override
    public DecideSignatureUseCase.Result completeCardSignature(
            AccessContext.User user, UUID signerId, CardSignatureUseCase.Completion completion) {
        requireAccess(user, signerId);
        return cardUseCase.completeInternal(user.userId(), signerId, completion);
    }

    private Optional<Item> item(AccessContext.User user, Signer signer) {
        Optional<SignatureRequest> request = repository.findById(signer.requestId())
                .filter(r -> r.status() == SignatureStatus.PENDING && r.deadlineAt().isAfter(clock.instant()));
        if (request.isEmpty()) {
            return Optional.empty();
        }
        Optional<Dossier> dossier = dossierRepository.findById(request.get().dossierId())
                .filter(d -> tenantId.equals(d.tenantId()) && accessPolicy.canViewType(tenantId, d.dossierTypeCode(), user));
        if (dossier.isEmpty()) {
            return Optional.empty();
        }
        String label = sourceElement(request.get()).map(DossierElement::label).orElse("Document");
        boolean myTurn = signer.status() == SignerStatus.PENDING;
        SignaturePort.SignatureProviderCapabilities capabilities = port.capabilities();
        return Optional.of(new Item(signer.id(), request.get().id(), dossier.get().id(), dossier.get().reference(),
                label, signer.required(), myTurn, request.get().deadlineAt(), request.get().simulated(),
                capabilities.hostedSigningUrl(), capabilities.signedUpload(),
                capabilities.signedUpload() ? availability.cardBridges() : List.of(), null));
    }

    @Override
    public String signingUrl(AccessContext.User user, UUID signerId) {
        Access access = requireAccess(user, signerId);
        if (!port.capabilities().hostedSigningUrl() || access.request().providerReference() == null) {
            return null;
        }
        try {
            return port.signingUrl(access.request().providerReference(), access.signer().position()).orElse(null);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Identité, tour, demande ouverte, dossier ouvert et lisible : sinon, comme si la ressource n'existait pas. */
    private Access requireAccess(AccessContext.User user, UUID signerId) {
        Signer signer = repository.findSignerById(signerId)
                .filter(s -> s.kind() == SignerKind.INTERNAL && user.userId().equals(s.userId())
                        && s.status() == SignerStatus.PENDING)
                .orElseThrow(() -> new PortalAccessDeniedException("no pending internal signer " + signerId));
        SignatureRequest request = repository.findById(signer.requestId())
                .filter(r -> r.status() == SignatureStatus.PENDING && r.deadlineAt().isAfter(clock.instant()))
                .orElseThrow(() -> new PortalAccessDeniedException("request not open for signer " + signerId));
        Dossier dossier = dossierRepository.findById(request.dossierId())
                .filter(d -> tenantId.equals(d.tenantId()) && !d.status().blocksMutation()
                        && accessPolicy.canViewType(tenantId, d.dossierTypeCode(), user))
                .orElseThrow(() -> new PortalAccessDeniedException("dossier not readable for signer " + signerId));
        return new Access(request, signer, dossier);
    }

    private Optional<DossierElement> sourceElement(SignatureRequest request) {
        if (request.sourceElementId() == null) {
            return Optional.empty();
        }
        return elementRepository.findByIdAndTenantId(request.sourceElementId(), tenantId)
                .filter(e -> e.dossierId().equals(request.dossierId()));
    }

    private record Access(SignatureRequest request, Signer signer, Dossier dossier) {}
}
