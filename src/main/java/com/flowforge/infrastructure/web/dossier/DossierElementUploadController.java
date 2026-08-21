package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.AttachmentContentSniffer;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierConflictException;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierElementView;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.model.InvalidDossierElementException;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.AttachmentScanPort;
import com.flowforge.domain.dossier.port.out.AttachmentScanResult;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.infrastructure.config.AttachmentUploadProperties;
import com.flowforge.infrastructure.config.TenantProperties;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Dedicated multipart entry point for {@link ElementType#ATTACHMENT} — see design decision n°1. */
@RestController
@RequestMapping("/api/dossiers")
public class DossierElementUploadController {

    private final DocumentStoragePort documentStoragePort;
    private final AddDossierElementUseCase addDossierElementUseCase;
    private final TenantProperties tenantProperties;
    private final AccessContextFactory accessContextFactory;
    private final AttachmentUploadProperties uploadProperties;
    private final DossierAccessGuard accessGuard;
    private final AttachmentScanPort attachmentScanPort;

    public DossierElementUploadController(
            DocumentStoragePort documentStoragePort,
            AddDossierElementUseCase addDossierElementUseCase,
            TenantProperties tenantProperties,
            AccessContextFactory accessContextFactory,
            AttachmentUploadProperties uploadProperties,
            DossierAccessGuard accessGuard,
            AttachmentScanPort attachmentScanPort) {
        this.documentStoragePort = documentStoragePort;
        this.addDossierElementUseCase = addDossierElementUseCase;
        this.tenantProperties = tenantProperties;
        this.accessContextFactory = accessContextFactory;
        this.uploadProperties = uploadProperties;
        this.accessGuard = accessGuard;
        this.attachmentScanPort = attachmentScanPort;
    }

    @PostMapping(path = "/{id}/elements/attachment", consumes = "multipart/form-data")
    public ResponseEntity<DossierElementView> upload(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("label") String label,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        // ACL and status checked before any storage write, so a caller without EDIT on this
        // dossier's type, or targeting a CLOTURE/ARCHIVE dossier, never causes an object to be
        // stored just to be rejected afterwards. AddDossierElementUseCase re-checks both
        // internally, which costs one extra findById + checks — accepted trade-off, not a second
        // source of truth (same policy, same rule, same DossierStatus.blocksMutation()).
        Dossier dossier = accessGuard.requireEditable(id, actor);
        if (dossier.status().blocksMutation()) {
            throw new DossierConflictException("Dossier elements cannot be added in status " + dossier.status());
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidDossierElementException("file is required");
        }
        String contentType = file.getContentType();
        if (!uploadProperties.isAllowed(contentType)) {
            throw new InvalidDossierElementException("content type not allowed: " + contentType);
        }
        byte[] content = readBytes(file);
        if (!AttachmentContentSniffer.matchesDeclaredType(content, contentType)) {
            throw new InvalidDossierElementException("file content does not match declared type: " + contentType);
        }
        AttachmentScanResult scanResult = attachmentScanPort.scan(content, contentType);
        if (!scanResult.clean()) {
            throw new InvalidDossierElementException("attachment rejected: " + scanResult.reason());
        }

        String storageRef = documentStoragePort.store(tenantProperties.id(), contentType, content);
        Map<String, Object> metadata = Map.of(
                "filename", file.getOriginalFilename() == null ? "" : file.getOriginalFilename(),
                "contentType", contentType,
                "sizeBytes", file.getSize());

        DossierElement element = addDossierElementUseCase.add(
                new AddDossierElementUseCase.AddCommand(
                        id, ElementType.ATTACHMENT, label, storageRef, metadata, actor.userId(), null),
                actor);
        return ResponseEntity
                .created(URI.create("/api/dossiers/" + id + "/elements/" + element.id()))
                .body(DossierElementView.from(element));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new InvalidDossierElementException("Failed to read uploaded file: " + ex.getMessage());
        }
    }
}
