package be.myrmacta.domain.workflow.portal.usecase;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.AttachmentContentSniffer;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierConflictException;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.model.InvalidDossierElementException;
import be.myrmacta.domain.dossier.port.in.AddDossierElementUseCase;
import be.myrmacta.domain.dossier.port.out.AttachmentScanPort;
import be.myrmacta.domain.dossier.port.out.AttachmentScanResult;
import be.myrmacta.domain.workflow.portal.PortalAccessResolver;
import be.myrmacta.domain.workflow.portal.model.PortalCredential;
import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.port.in.AddPortalAttachmentUseCase;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * TODO §68 — même pipeline que l'upload interne ({@code DossierElementUploadController}) : types
 * autorisés, magic bytes, antivirus (fail-open, D-PORTAL-SCAN — risque accepté), stockage, puis
 * élément {@code ATTACHMENT} tracé {@code portal:<linkId>}. Accès et statut du dossier vérifiés
 * <em>avant</em> toute écriture en stockage.
 */
public final class AddPortalAttachmentUseCaseImpl implements AddPortalAttachmentUseCase {

    private static final int MAX_LABEL_LENGTH = 200;

    private final String tenantId;
    private final PortalAccessResolver accessResolver;
    private final Predicate<String> allowedContentType;
    private final AttachmentScanPort scanPort;
    private final DocumentStoragePort storagePort;
    private final AddDossierElementUseCase addElementUseCase;

    public AddPortalAttachmentUseCaseImpl(
            String tenantId,
            PortalAccessResolver accessResolver,
            Predicate<String> allowedContentType,
            AttachmentScanPort scanPort,
            DocumentStoragePort storagePort,
            AddDossierElementUseCase addElementUseCase) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver");
        this.allowedContentType = Objects.requireNonNull(allowedContentType, "allowedContentType");
        this.scanPort = Objects.requireNonNull(scanPort, "scanPort");
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort");
        this.addElementUseCase = Objects.requireNonNull(addElementUseCase, "addElementUseCase");
    }

    @Override
    public DossierElement add(PortalCredential credential, AttachmentUpload upload) {
        Objects.requireNonNull(upload, "upload");
        PortalAccessResolver.ResolvedAccess access = accessResolver.resolve(credential);
        if (access.dossier().status().blocksMutation()) {
            throw new DossierConflictException("Dossier elements cannot be added in status " + access.dossier().status());
        }
        byte[] content = upload.content();
        if (content == null || content.length == 0) {
            throw new InvalidDossierElementException("file is required");
        }
        String contentType = upload.contentType();
        if (!allowedContentType.test(contentType)) {
            throw new InvalidDossierElementException("content type not allowed: " + contentType);
        }
        if (!AttachmentContentSniffer.matchesDeclaredType(content, contentType)) {
            throw new InvalidDossierElementException("file content does not match declared type: " + contentType);
        }
        AttachmentScanResult scan = scanPort.scan(content, contentType);
        if (!scan.clean()) {
            throw new InvalidDossierElementException("attachment rejected: " + scan.reason());
        }
        String storageRef = storagePort.store(tenantId, contentType, content);
        PortalLink link = access.link();
        String filename = upload.filename() == null ? "" : upload.filename();
        return addElementUseCase.add(
                new AddDossierElementUseCase.AddCommand(
                        link.dossierId(),
                        ElementType.ATTACHMENT,
                        label(upload.label(), filename),
                        storageRef,
                        Map.of("filename", filename, "contentType", contentType, "sizeBytes", (long) content.length),
                        link.actorId(),
                        null),
                AccessContext.external(link.id()));
    }

    private static String label(String requested, String filename) {
        String value = requested == null || requested.isBlank() ? filename : requested.strip();
        if (value.isBlank()) {
            value = "Pièce jointe";
        }
        return value.length() > MAX_LABEL_LENGTH ? value.substring(0, MAX_LABEL_LENGTH) : value;
    }
}
