package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.document.DocumentTemplateSupport;
import be.myrmacta.domain.document.port.in.PreviewDocumentUseCase;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.infrastructure.config.DocumentConfig;
import be.myrmacta.infrastructure.config.DocumentGenerationProperties;
import be.myrmacta.infrastructure.config.DocumentPreviewProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import be.myrmacta.infrastructure.web.document.DocumentGenerationRateLimitExceededException;
import be.myrmacta.infrastructure.web.document.DocumentPreviewHtmlRateLimiter;
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
    private final DocumentGenerationProperties generationProperties;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public PreviewDocumentTool(
            PreviewDocumentUseCase previewDocumentUseCase,
            McpAccessContextFactory accessContextFactory,
            DocumentPreviewHtmlRateLimiter rateLimiter,
            DocumentPreviewProperties previewProperties,
            DocumentGenerationProperties generationProperties,
            tools.jackson.databind.ObjectMapper objectMapper) {
        this.generationProperties = Objects.requireNonNull(generationProperties, "generationProperties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
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
        Map<String, Object> vars = variables == null ? Map.of() : variables;
        // Le filtre de taille du REST ne couvre pas MCP : les variables sont bornées ici (même plafond que le corps REST).
        if (objectMapper.writeValueAsBytes(vars).length > generationProperties.maxBodyBytes()) {
            throw new IllegalArgumentException("variables exceed " + generationProperties.maxBodyBytes() + " bytes");
        }
        if (!rateLimiter.tryConsume(actor.userId(), previewProperties.rateLimitPerMinute())) {
            throw new DocumentGenerationRateLimitExceededException(actor.userId());
        }
        String html = previewDocumentUseCase.previewHtml(new PreviewDocumentUseCase.PreviewCommand(
                htmlContent, null, null, null, vars));
        return Map.of("html", html);
    }
}
