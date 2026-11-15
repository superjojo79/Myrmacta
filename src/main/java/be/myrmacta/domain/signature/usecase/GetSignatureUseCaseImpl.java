package be.myrmacta.domain.signature.usecase;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.signature.SignatureAccessResolver;
import be.myrmacta.domain.signature.SignatureAccessResolver.ResolvedAccess;
import be.myrmacta.domain.signature.SignatureWorkingDocuments;
import be.myrmacta.domain.signature.model.SignatureRequest;
import be.myrmacta.domain.signature.port.in.GetSignatureUseCase;
import be.myrmacta.domain.signature.port.out.SignatureAvailabilityPort;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignedUploadRepository;
import be.myrmacta.domain.workflow.portal.model.PortalAccessDeniedException;
import java.util.List;
import java.util.Objects;

/**
 * TODO §72 — contexte et document pour un signataire externe, dérivés du seul jeton. Le document remis est
 * le document de travail courant de la demande (TODO §138, D-EID-CHAIN).
 */
public final class GetSignatureUseCaseImpl implements GetSignatureUseCase {

    private final String tenantId;
    private final SignatureAccessResolver accessResolver;
    private final DossierElementRepository elementRepository;
    private final SignatureWorkingDocuments documents;
    private final SignaturePort port;
    private final SignatureAvailabilityPort availability;

    public GetSignatureUseCaseImpl(
            String tenantId,
            SignatureAccessResolver accessResolver,
            DossierElementRepository elementRepository,
            DocumentStoragePort storage,
            SignaturePort port,
            SignedUploadRepository uploads,
            SignatureAvailabilityPort availability) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.port = Objects.requireNonNull(port, "port");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.documents = new SignatureWorkingDocuments(tenantId, storage, uploads, port);
    }

    @Override
    public Context context(String token) {
        ResolvedAccess access = accessResolver.resolve(token);
        SignatureRequest request = access.request();
        String label = sourceElement(request).map(DossierElement::label).orElse("Document");
        SignaturePort.SignatureProviderCapabilities capabilities = port.capabilities();
        boolean hosted = capabilities.hostedSigningUrl();
        return new Context(access.dossier().reference(), label, access.signer().displayName(),
                access.signer().required(), request.deadlineAt(), request.simulated(), hosted,
                capabilities.signedUpload() && availability.signedUploadEnabled(),
                capabilities.signedUpload() ? availability.cardBridges() : List.of(), signingUrl(hosted, request, access.signer().position()));
    }

    /** Échec fournisseur : écran d'attente, sans journaliser l'adresse (elle contient un jeton). */
    private String signingUrl(boolean hosted, SignatureRequest request, int signerPosition) {
        if (!hosted || request.providerReference() == null) {
            return null;
        }
        try {
            return port.signingUrl(request.providerReference(), signerPosition).orElse(null);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Override
    public DocumentContent document(String token) {
        ResolvedAccess access = accessResolver.resolve(token);
        SignatureRequest request = access.request();
        DossierElement element = sourceElement(request)
                .orElseThrow(() -> new PortalAccessDeniedException("source element gone for " + request.id()));
        SignatureWorkingDocuments.Current current = documents.current(request);
        // Le contenu remis est celui de l'émission, ou du dernier téléversement accepté : une divergence
        // d'empreinte ferme l'accès.
        byte[] content = documents.read(request, current);
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
