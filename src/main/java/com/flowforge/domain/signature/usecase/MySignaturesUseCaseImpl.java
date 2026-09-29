package com.flowforge.domain.signature.usecase;

import com.flowforge.domain.document.model.DocumentStorageException;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.SignatureStatus;
import com.flowforge.domain.signature.model.Signer;
import com.flowforge.domain.signature.model.SignerKind;
import com.flowforge.domain.signature.model.SignerStatus;
import com.flowforge.domain.signature.port.in.DecideSignatureUseCase;
import com.flowforge.domain.signature.port.in.GetSignatureUseCase;
import com.flowforge.domain.signature.port.in.MySignaturesUseCase;
import com.flowforge.domain.signature.port.out.SignatureRequestRepository;
import com.flowforge.domain.workflow.portal.model.PortalAccessDeniedException;
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
    private final DocumentStoragePort storage;
    private final DecideSignatureUseCase decideUseCase;
    private final Clock clock;

    public MySignaturesUseCaseImpl(
            String tenantId,
            SignatureRequestRepository repository,
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy,
            DossierElementRepository elementRepository,
            DocumentStoragePort storage,
            DecideSignatureUseCase decideUseCase,
            Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.decideUseCase = Objects.requireNonNull(decideUseCase, "decideUseCase");
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
        byte[] content;
        try {
            content = storage.retrieve(tenantId, request.sourceStorageRef());
        } catch (DocumentStorageException ex) {
            throw new PortalAccessDeniedException("source document unreadable for " + request.id());
        }
        if (content == null || !RequestSignatureUseCaseImpl.sha256(content).equals(request.sourceDigest())) {
            throw new PortalAccessDeniedException("source digest mismatch for " + request.id());
        }
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
        return Optional.of(new Item(signer.id(), request.get().id(), dossier.get().id(), dossier.get().reference(),
                label, signer.required(), signer.status() == SignerStatus.PENDING, request.get().deadlineAt(),
                request.get().simulated()));
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
