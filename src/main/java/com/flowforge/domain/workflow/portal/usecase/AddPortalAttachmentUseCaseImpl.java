package com.flowforge.domain.workflow.portal.usecase;

import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.AttachmentContentSniffer;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierConflictException;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.model.InvalidDossierElementException;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.AttachmentScanPort;
import com.flowforge.domain.dossier.port.out.AttachmentScanResult;
import com.flowforge.domain.workflow.portal.PortalAccessResolver;
import com.flowforge.domain.workflow.portal.model.PortalCredential;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.port.in.AddPortalAttachmentUseCase;
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
