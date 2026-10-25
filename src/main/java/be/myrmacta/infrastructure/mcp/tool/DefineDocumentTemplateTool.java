package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.document.DocumentTemplateSupport;
import be.myrmacta.domain.document.model.InvalidTemplateSyntaxException;
import be.myrmacta.domain.document.port.out.TemplateSyntaxChecker;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.config.DocumentConfig;
import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.document.port.in.CreateDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.in.ListDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.in.NewDocumentTemplateVersionUseCase;
import be.myrmacta.domain.document.port.in.UpdateDocumentTemplateUseCase;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
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
    /** Garde-fou : au-delà (versions x variantes x langues d'une même clé), on refuse plutôt que de décider sur une vue partielle. */
    private static final int LOOKUP_MAX_PAGES = 50;
    private static final TenantAdminGuard GUARD = new TenantAdminGuard();

    private final ListDocumentTemplateUseCase listUseCase;
    private final CreateDocumentTemplateUseCase createUseCase;
    private final UpdateDocumentTemplateUseCase updateUseCase;
    private final NewDocumentTemplateVersionUseCase newVersionUseCase;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;
    private final TemplateSyntaxChecker syntaxChecker;

    public DefineDocumentTemplateTool(
            ListDocumentTemplateUseCase listUseCase,
            CreateDocumentTemplateUseCase createUseCase,
            UpdateDocumentTemplateUseCase updateUseCase,
            NewDocumentTemplateVersionUseCase newVersionUseCase,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory,
            TemplateSyntaxChecker syntaxChecker) {
        this.syntaxChecker = Objects.requireNonNull(syntaxChecker, "syntaxChecker");
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
        AccessContext.User actor = accessContextFactory.currentActor();
        // Comme l'API REST (TENANT_ADMIN) : un acteur de session non administrateur (assistant) ne peut pas écrire un modèle.
        GUARD.requireTenantAdminOrApiService(actor);
        String actorId = actor.userId();
        String key = McpArgs.requireNonBlank(templateKey, "templateKey");
        McpArgs.requireNonBlank(htmlContent, "htmlContent");
        // Avant toute écriture : un HTML trop gros ne doit pas laisser derrière lui une version brouillon vide de sens.
        DocumentTemplateSupport.assertHtmlSize(htmlContent, DocumentConfig.MAX_HTML_BYTES);
        String effectiveLabel = label != null && !label.isBlank() ? label : null;
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
        String syntaxProblem = syntaxProblem(htmlContent);
        if (dry) {
            return result(operation, true, latest, syntaxProblem);
        }
        DocumentTemplate saved;
        switch (operation) {
            case "create" -> saved = createUseCase.create(new CreateDocumentTemplateUseCase.CreateCommand(
                    key, variant, loc, effectiveLabel != null ? effectiveLabel : key, htmlContent, actorId));
            case "update" -> saved = updateUseCase.update(
                    new UpdateDocumentTemplateUseCase.UpdateCommand(latest.id(), effectiveLabel, htmlContent, actorId));
            default -> {
                DocumentTemplate draft = newVersionUseCase.newVersion(latest.id(), tenantProperties.id(), actorId);
                saved = updateUseCase.update(
                        new UpdateDocumentTemplateUseCase.UpdateCommand(draft.id(), effectiveLabel, htmlContent, actorId));
            }
        }
        return result(operation, false, saved, syntaxProblem);
    }

    /** Dernière version (clé, variante, langue) : toutes les pages sont lues, la liste étant triée par date et non par version. */
    private DocumentTemplate latest(String key, String variant, String locale) {
        DocumentTemplate best = null;
        for (int page = 0; page < LOOKUP_MAX_PAGES; page++) {
            PageResult<DocumentTemplate> result =
                    listUseCase.list(tenantProperties.id(), key, false, PageQuery.of(page, LOOKUP_PAGE_SIZE));
            for (DocumentTemplate t : result.content()) {
                if (Objects.equals(t.variantKey(), variant)
                        && Objects.equals(t.locale(), locale)
                        && (best == null || t.version() > best.version())) {
                    best = t;
                }
            }
            if (page + 1 >= result.totalPages()) {
                return best;
            }
        }
        throw new IllegalStateException("template key '" + key + "' has too many versions to be resolved over MCP");
    }

    /** Un brouillon peut être inachevé : le problème de syntaxe est signalé à l'appelant, jamais bloquant (l'activation, elle, vérifie). */
    private String syntaxProblem(String html) {
        try {
            syntaxChecker.check(html);
            return null;
        } catch (InvalidTemplateSyntaxException ex) {
            return ex.getMessage();
        }
    }

    private static Map<String, Object> result(
            String operation, boolean dryRun, DocumentTemplate template, String syntaxProblem) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("operation", operation);
        map.put("dryRun", dryRun);
        map.put("syntax", syntaxProblem == null ? "OK" : "INVALID");
        if (syntaxProblem != null) {
            map.put("syntaxProblem", syntaxProblem);
        }
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
