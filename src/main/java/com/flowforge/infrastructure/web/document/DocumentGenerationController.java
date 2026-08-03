package com.flowforge.infrastructure.web.document;

import com.flowforge.domain.document.port.in.GenerateDocumentUseCase;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentGenerationController {

    private final GenerateDocumentUseCase generateDocumentUseCase;

    public DocumentGenerationController(GenerateDocumentUseCase generateDocumentUseCase) {
        this.generateDocumentUseCase = generateDocumentUseCase;
    }

    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN','DOSSIER_MANAGER')")
    public ResponseEntity<byte[]> generate(@RequestBody GenerateRequest request) {
        byte[] pdf = generateDocumentUseCase.generate(new GenerateDocumentUseCase.GenerateDocumentCommand(
                request.templateKey(),
                request.variantKey(),
                request.locale(),
                request.variables() == null ? Map.of() : request.variables()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .body(pdf);
    }

    public record GenerateRequest(
            String templateKey, String variantKey, String locale, Map<String, Object> variables) {}
}
