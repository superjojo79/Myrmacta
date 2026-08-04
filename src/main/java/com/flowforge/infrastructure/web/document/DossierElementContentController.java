package com.flowforge.infrastructure.web.document;

import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.model.InvalidDossierElementException;
import com.flowforge.domain.dossier.port.in.GetDossierElementUseCase;
import com.flowforge.domain.document.model.DocumentStorageException;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dossier-elements")
public class DossierElementContentController {

    private final GetDossierElementUseCase getDossierElementUseCase;
    private final DocumentStoragePort documentStoragePort;
    private final TenantProperties tenantProperties;
    private final AccessContextFactory accessContextFactory;

    public DossierElementContentController(
            GetDossierElementUseCase getDossierElementUseCase,
            DocumentStoragePort documentStoragePort,
            TenantProperties tenantProperties,
            AccessContextFactory accessContextFactory) {
        this.getDossierElementUseCase = getDossierElementUseCase;
        this.documentStoragePort = documentStoragePort;
        this.tenantProperties = tenantProperties;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID id, Authentication authentication) {
        DossierElement element =
                getDossierElementUseCase.get(id, accessContextFactory.user(authentication));
        if (element.type() != ElementType.PRODUCED_DOC
                || element.storageRef() == null
                || element.storageRef().isBlank()) {
            throw new InvalidDossierElementException("Element has no downloadable content");
        }
        byte[] bytes;
        try {
            bytes = documentStoragePort.retrieve(tenantProperties.id(), element.storageRef());
        } catch (DocumentStorageException ex) {
            throw ex;
        }
        String filename = sanitizeFilename(element.label()) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(bytes);
    }

    private static String sanitizeFilename(String label) {
        String base = label == null || label.isBlank() ? "document" : label.trim();
        return base.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
