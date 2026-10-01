package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.document.port.in.PreviewDocumentUseCase;
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

    private final PreviewDocumentUseCase previewDocumentUseCase;

    public PreviewDocumentTool(PreviewDocumentUseCase previewDocumentUseCase) {
        this.previewDocumentUseCase = Objects.requireNonNull(previewDocumentUseCase, "previewDocumentUseCase");
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
        McpArgs.requireNonBlank(htmlContent, "htmlContent");
        String html = previewDocumentUseCase.previewHtml(new PreviewDocumentUseCase.PreviewCommand(
                htmlContent, null, null, null, variables == null ? Map.of() : variables));
        return Map.of("html", html);
    }
}
