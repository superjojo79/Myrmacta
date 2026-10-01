package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.document.DocumentTemplateSupport;
import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.in.CreateDocumentTemplateUseCase;
import com.flowforge.domain.document.port.in.ListDocumentTemplateUseCase;
import com.flowforge.domain.document.port.in.NewDocumentTemplateVersionUseCase;
import com.flowforge.domain.document.port.in.UpdateDocumentTemplateUseCase;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * {@code define_document_template} (LC4b) : écrit le HTML d'un modèle de document sous forme de
 * <b>brouillon</b>, jamais en ligne. Dernière version (clé + variante + langue) absente ou archivée
 * : création d'un brouillon ; en brouillon : mise à jour ; active : nouvelle version brouillon puis
 * mise à jour (la version active, qui sert les générations, n'est jamais modifiée). L'activation
 * reste un geste humain (D-TPLED-NO-ACTIVATE). Le corps HTML n'est jamais renvoyé.
 */
@Component
public class DefineDocumentTemplateTool {

    private static final int LOOKUP_PAGE_SIZE = PageQuery.MAX_SIZE;

    private final ListDocumentTemplateUseCase listUseCase;
    private final CreateDocumentTemplateUseCase createUseCase;
    private final UpdateDocumentTemplateUseCase updateUseCase;
    private final NewDocumentTemplateVersionUseCase newVersionUseCase;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;

    public DefineDocumentTemplateTool(
            ListDocumentTemplateUseCase listUseCase,
            CreateDocumentTemplateUseCase createUseCase,
            UpdateDocumentTemplateUseCase updateUseCase,
            NewDocumentTemplateVersionUseCase newVersionUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory) {
        this.listUseCase = Objects.requireNonNull(listUseCase, "listUseCase");
        this.createUseCase = Objects.requireNonNull(createUseCase, "createUseCase");
        this.updateUseCase = Objects.requireNonNull(updateUseCase, "updateUseCase");
        this.newVersionUseCase = Objects.requireNonNull(newVersionUseCase, "newVersionUseCase");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "define_document_template",
            description = "Write the HTML (Thymeleaf subset) of a document template as a DRAFT - never live. "
                    + "No version yet (or the latest is archived): creates a draft. Latest is a draft: updates "
                    + "it. Latest is ACTIVE: creates a new draft version, then updates it (the active version "
                    + "keeps serving documents). Activation is a human action in the admin UI. The HTML body "
                    + "is never returned. Use preview_document first to check the rendering. dryRun=true "
                    + "reports the operation without writing.")
    public Map<String, Object> defineDocumentTemplate(
            @McpToolParam(required = true, description = "Template key") String templateKey,
            @McpToolParam(required = false, description = "Variant key (optional)") String variantKey,
            @McpToolParam(required = false, description = "Locale, e.g. fr-FR (optional)") String locale,
            @McpToolParam(required = false, description = "Display label (kept when absent on update)")
                    String label,
            @McpToolParam(required = true, description = "Full HTML body of the template") String htmlContent,
            @McpToolParam(required = false, description = "Report the operation without writing; default false")
                    Boolean dryRun) {
        String actorId = accessContextFactory.currentActor().userId();
        String key = McpArgs.requireNonBlank(templateKey, "templateKey");
        McpArgs.requireNonBlank(htmlContent, "htmlContent");
        String variant = DocumentTemplateSupport.normalizeOptional(variantKey);
        String loc = DocumentTemplateSupport.normalizeOptional(locale);
        boolean dry = Boolean.TRUE.equals(dryRun);

        DocumentTemplate latest = latest(key, variant, loc);
        String operation;
        if (latest == null || latest.status() == TemplateStatus.ARCHIVED) {
            operation = "create";
        } else if (latest.status() == TemplateStatus.DRAFT) {
            operation = "update";
        } else {
            operation = "newVersion";
        }
        if (dry) {
            return result(operation, true, latest);
        }
        DocumentTemplate saved;
        switch (operation) {
            case "create" -> saved = createUseCase.create(new CreateDocumentTemplateUseCase.CreateCommand(
                    key, variant, loc, label, htmlContent, actorId));
            case "update" -> saved = updateUseCase.update(
                    new UpdateDocumentTemplateUseCase.UpdateCommand(latest.id(), label, htmlContent, actorId));
            default -> {
                DocumentTemplate draft = newVersionUseCase.newVersion(latest.id(), tenantProperties.id(), actorId);
                saved = updateUseCase.update(
                        new UpdateDocumentTemplateUseCase.UpdateCommand(draft.id(), label, htmlContent, actorId));
            }
        }
        return result(operation, false, saved);
    }

    private DocumentTemplate latest(String key, String variant, String locale) {
        return listUseCase.list(tenantProperties.id(), key, false, PageQuery.of(0, LOOKUP_PAGE_SIZE)).content().stream()
                .filter(t -> Objects.equals(t.variantKey(), variant) && Objects.equals(t.locale(), locale))
                .max(Comparator.comparingInt(DocumentTemplate::version))
                .orElse(null);
    }

    private static Map<String, Object> result(String operation, boolean dryRun, DocumentTemplate template) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("operation", operation);
        map.put("dryRun", dryRun);
        if (template != null) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("id", template.id());
            meta.put("templateKey", template.templateKey());
            meta.put("variantKey", template.variantKey());
            meta.put("locale", template.locale());
            meta.put("label", template.label());
            meta.put("version", template.version());
            meta.put("status", template.status());
            map.put(dryRun ? "currentVersion" : "template", meta);
        }
        return map;
    }
}
