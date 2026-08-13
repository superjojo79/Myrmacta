package com.flowforge.infrastructure.web.document;

import com.flowforge.domain.document.port.in.GenerateDocumentUseCase;
import com.flowforge.infrastructure.config.DocumentGenerationProperties;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentGenerationController {

    private final GenerateDocumentUseCase generateDocumentUseCase;
    private final DocumentGenerationRateLimiter rateLimiter;
    private final DocumentGenerationProperties properties;

    public DocumentGenerationController(
            GenerateDocumentUseCase generateDocumentUseCase,
            DocumentGenerationRateLimiter rateLimiter,
            DocumentGenerationProperties properties) {
        this.generateDocumentUseCase = generateDocumentUseCase;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN','DOSSIER_MANAGER')")
    public ResponseEntity<byte[]> generate(@RequestBody GenerateRequest request, Authentication authentication) {
        if (!rateLimiter.tryConsume(authentication.getName(), properties.rateLimitPerMinute())) {
            throw new DocumentGenerationRateLimitExceededException(authentication.getName());
        }
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
