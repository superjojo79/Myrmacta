package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.document.DocumentTemplateSupport;
import com.flowforge.domain.document.port.in.PreviewDocumentUseCase;
import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.infrastructure.config.DocumentConfig;
import com.flowforge.infrastructure.config.DocumentPreviewProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import com.flowforge.infrastructure.web.document.DocumentGenerationRateLimitExceededException;
import com.flowforge.infrastructure.web.document.DocumentPreviewHtmlRateLimiter;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * {@code preview_document} (LC4b) : rendu HTML d'un corps de modèle fourni en direct, avec des
 * variables d'exemple. Jamais par {@code templateKey} : ce mode lirait un modèle du tenant sans le
 * contrôle d'accès par type de dossier de l'API REST (D-TPLED-PREVIEW-LIVE-ONLY).
 */
@Component
public class PreviewDocumentTool {

    private static final TenantAdminGuard GUARD = new TenantAdminGuard();

    private final PreviewDocumentUseCase previewDocumentUseCase;
    private final McpAccessContextFactory accessContextFactory;
    private final DocumentPreviewHtmlRateLimiter rateLimiter;
    private final DocumentPreviewProperties previewProperties;

    public PreviewDocumentTool(
            PreviewDocumentUseCase previewDocumentUseCase,
            McpAccessContextFactory accessContextFactory,
            DocumentPreviewHtmlRateLimiter rateLimiter,
            DocumentPreviewProperties previewProperties) {
        this.previewDocumentUseCase = Objects.requireNonNull(previewDocumentUseCase, "previewDocumentUseCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.previewProperties = Objects.requireNonNull(previewProperties, "previewProperties");
    }

    @McpTool(
            name = "preview_document",
            description = "Render the HTML of a template body (Thymeleaf subset) with sample variables and "
                    + "return the resulting HTML. Nothing is stored. Only live HTML is accepted - a template "
                    + "cannot be previewed by key over MCP.")
    public Map<String, Object> previewDocument(
            @McpToolParam(required = true, description = "Template HTML to render") String htmlContent,
            @McpToolParam(required = false, description = "Sample variables (object), may be empty")
                    Map<String, Object> variables) {
        // Mêmes garde-fous que l'aperçu REST en direct : rôle administrateur, taille, débit.
        AccessContext.User actor = accessContextFactory.currentActor();
        GUARD.requireTenantAdminOrApiService(actor);
        McpArgs.requireNonBlank(htmlContent, "htmlContent");
        DocumentTemplateSupport.assertHtmlSize(htmlContent, DocumentConfig.MAX_HTML_BYTES);
        if (!rateLimiter.tryConsume(actor.userId(), previewProperties.rateLimitPerMinute())) {
            throw new DocumentGenerationRateLimitExceededException(actor.userId());
        }
        String html = previewDocumentUseCase.previewHtml(new PreviewDocumentUseCase.PreviewCommand(
                htmlContent, null, null, null, variables == null ? Map.of() : variables));
        return Map.of("html", html);
    }
}
