package be.myrmacta.domain.signature.usecase;

import be.myrmacta.domain.document.model.DocumentStorageException;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.signature.SignatureAccessResolver;
import be.myrmacta.domain.signature.SignatureAccessResolver.ResolvedAccess;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.port.in.GetSignatureUseCase;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import java.util.Objects;

/** TODO §72 — contexte et document pour un signataire externe, dérivés du seul jeton. */
public final class GetSignatureUseCaseImpl implements GetSignatureUseCase {

    private final String tenantId;
    private final SignatureAccessResolver accessResolver;
    private final DossierElementRepository elementRepository;
    private final DocumentStoragePort storage;

    public GetSignatureUseCaseImpl(
            String tenantId,
            SignatureAccessResolver accessResolver,
            DossierElementRepository elementRepository,
            DocumentStoragePort storage) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public Context context(String token) {
        ResolvedAccess access = accessResolver.resolve(token);
        SignatureRequest request = access.request();
        String label = sourceElement(request).map(DossierElement::label).orElse("Document");
        return new Context(access.dossier().reference(), label, access.signer().displayName(),
                access.signer().required(), request.deadlineAt(), request.simulated());
    }

    @Override
    public DocumentContent document(String token) {
        ResolvedAccess access = accessResolver.resolve(token);
        SignatureRequest request = access.request();
        DossierElement element = sourceElement(request)
                .orElseThrow(() -> new PortalAccessDeniedException("source element gone for " + request.id()));
        byte[] content;
        try {
            content = storage.retrieve(tenantId, request.sourceStorageRef());
        } catch (DocumentStorageException ex) {
            throw new PortalAccessDeniedException("source document unreadable for " + request.id());
        }
        // Le contenu signé est celui de l'émission : une divergence d'empreinte ferme l'accès.
        if (content == null || !RequestSignatureUseCaseImpl.sha256(content).equals(request.sourceDigest())) {
            throw new PortalAccessDeniedException("source digest mismatch for " + request.id());
        }
        // Toujours du PDF : le type des métadonnées n'est jamais reflété vers le navigateur du tiers.
        return new DocumentContent(content, RequestSignatureUseCaseImpl.PDF_CONTENT_TYPE, filename(element));
    }

    private java.util.Optional<DossierElement> sourceElement(SignatureRequest request) {
        if (request.sourceElementId() == null) {
            return java.util.Optional.empty();
        }
        return elementRepository.findByIdAndTenantId(request.sourceElementId(), tenantId)
                .filter(e -> e.dossierId().equals(request.dossierId()));
    }

    private static String filename(DossierElement element) {
        String base = element.label() == null ? "document" : element.label().replaceAll("[^A-Za-z0-9._-]+", "_");
        return (base.isBlank() ? "document" : base) + ".pdf";
    }
}
