package com.flowforge.infrastructure.web.document;

import com.flowforge.domain.document.port.in.GenerateDocumentUseCase;
import com.flowforge.domain.document.port.in.PreviewDocumentUseCase;
import com.flowforge.domain.document.port.in.PreviewDocumentUseCase.PreviewCommand;
import com.flowforge.infrastructure.config.DocumentGenerationProperties;
import com.flowforge.infrastructure.config.DocumentPreviewProperties;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentGenerationController {

    private static final String LIVE_PREVIEW_DENIED = "Live preview requires TENANT_ADMIN";

    private final GenerateDocumentUseCase generateDocumentUseCase;
    private final PreviewDocumentUseCase previewDocumentUseCase;
    private final DocumentGenerationRateLimiter rateLimiter;
    private final DocumentPreviewHtmlRateLimiter previewHtmlRateLimiter;
    private final DocumentGenerationProperties properties;
    private final DocumentPreviewProperties previewProperties;

    public DocumentGenerationController(
            GenerateDocumentUseCase generateDocumentUseCase,
            PreviewDocumentUseCase previewDocumentUseCase,
            DocumentGenerationRateLimiter rateLimiter,
            DocumentPreviewHtmlRateLimiter previewHtmlRateLimiter,
            DocumentGenerationProperties properties,
            DocumentPreviewProperties previewProperties) {
        this.generateDocumentUseCase = generateDocumentUseCase;
        this.previewDocumentUseCase = previewDocumentUseCase;
        this.rateLimiter = rateLimiter;
        this.previewHtmlRateLimiter = previewHtmlRateLimiter;
        this.properties = properties;
        this.previewProperties = previewProperties;
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

    @PostMapping(value = "/preview", produces = MediaType.TEXT_HTML_VALUE)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN','DOSSIER_MANAGER')")
    public ResponseEntity<String> previewHtml(@RequestBody PreviewRequest request, Authentication authentication) {
        PreviewCommand command = request.toCommand();
        denyLiveUnlessTenantAdmin(command, authentication);
        if (!previewHtmlRateLimiter.tryConsume(authentication.getName(), previewProperties.rateLimitPerMinute())) {
            throw new DocumentGenerationRateLimitExceededException(authentication.getName());
        }
        String html = PrintPreviewHtml.wrap(previewDocumentUseCase.previewHtml(command));
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    @PostMapping(value = "/preview/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN','DOSSIER_MANAGER')")
    public ResponseEntity<byte[]> previewPdf(@RequestBody PreviewRequest request, Authentication authentication) {
        PreviewCommand command = request.toCommand();
        denyLiveUnlessTenantAdmin(command, authentication);
        if (!rateLimiter.tryConsume(authentication.getName(), properties.rateLimitPerMinute())) {
            throw new DocumentGenerationRateLimitExceededException(authentication.getName());
        }
        byte[] pdf = previewDocumentUseCase.previewPdf(command);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview.pdf\"")
                .body(pdf);
    }

    private static void denyLiveUnlessTenantAdmin(PreviewCommand command, Authentication authentication) {
        if (command.isLive() && !hasRole(authentication, "ROLE_TENANT_ADMIN")) {
            throw new AccessDeniedException(LIVE_PREVIEW_DENIED);
        }
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    public record GenerateRequest(
            String templateKey, String variantKey, String locale, Map<String, Object> variables) {}

    public record PreviewRequest(
            String htmlContent,
            String templateKey,
            String variantKey,
            String locale,
            Map<String, Object> variables) {

        PreviewCommand toCommand() {
            return new PreviewCommand(htmlContent, templateKey, variantKey, locale, variables);
        }
    }
}
