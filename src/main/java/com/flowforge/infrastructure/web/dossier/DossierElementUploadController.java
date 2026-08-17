package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierElementView;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.model.InvalidDossierElementException;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
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

    public DossierElementUploadController(
            DocumentStoragePort documentStoragePort,
            AddDossierElementUseCase addDossierElementUseCase,
            TenantProperties tenantProperties,
            AccessContextFactory accessContextFactory,
            AttachmentUploadProperties uploadProperties) {
        this.documentStoragePort = documentStoragePort;
        this.addDossierElementUseCase = addDossierElementUseCase;
        this.tenantProperties = tenantProperties;
        this.accessContextFactory = accessContextFactory;
        this.uploadProperties = uploadProperties;
    }

    @PostMapping(path = "/{id}/elements/attachment", consumes = "multipart/form-data")
    public ResponseEntity<DossierElementView> upload(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("label") String label,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        if (file == null || file.isEmpty()) {
            throw new InvalidDossierElementException("file is required");
        }
        String contentType = file.getContentType();
        if (!uploadProperties.isAllowed(contentType)) {
            throw new InvalidDossierElementException("content type not allowed: " + contentType);
        }

        String storageRef = documentStoragePort.store(tenantProperties.id(), contentType, readBytes(file));
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
