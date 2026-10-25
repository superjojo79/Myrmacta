package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.port.in.ListDocumentTemplateUseCase;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ListDocumentTemplatesTool {

    private final ListDocumentTemplateUseCase listDocumentTemplateUseCase;
    private final TenantProperties tenantProperties;

    public ListDocumentTemplatesTool(
            ListDocumentTemplateUseCase listDocumentTemplateUseCase, TenantProperties tenantProperties) {
        this.listDocumentTemplateUseCase =
                Objects.requireNonNull(listDocumentTemplateUseCase, "listDocumentTemplateUseCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
    }

    @McpTool(
            name = "list_document_templates",
            description = "List document templates (metadata only, never the HTML body), paginated.")
    public PageResult<DocumentTemplate> listDocumentTemplates(
            @McpToolParam(required = false, description = "Template key filter") String templateKey,
            @McpToolParam(required = false, description = "Must be absent or false — the HTML body is never "
                    + "returned over MCP") Boolean includeContent,
            @McpToolParam(required = false, description = "Zero-based page index, default 0") Integer page,
            @McpToolParam(required = false, description = "Page size, 1-100, default 20") Integer size) {
        if (Boolean.TRUE.equals(includeContent)) {
            throw new IllegalArgumentException("includeContent=true is not supported over MCP (A-TPL-FORCE-FALSE)");
        }
        PageQuery query = PageQuery.of(page == null ? 0 : page, size == null ? PageQuery.DEFAULT_SIZE : size);
        return listDocumentTemplateUseCase.list(tenantProperties.id(), templateKey, false, query);
    }
}
