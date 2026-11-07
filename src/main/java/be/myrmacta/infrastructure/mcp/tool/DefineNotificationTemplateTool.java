package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.notification.template.NotificationTemplateSupport;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateLimits;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;
import be.myrmacta.domain.notification.template.port.in.CreateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.ListNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.NewNotificationTemplateVersionUseCase;
import be.myrmacta.domain.notification.template.port.in.UpdateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateChecker;
import be.myrmacta.infrastructure.config.NotificationTemplateProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * {@code define_notification_template} (TODO §123) : écrit un gabarit de notification sous forme de
 * <b>brouillon</b>, jamais en ligne. Dernière version (clé + variante + langue) absente ou archivée : création d'un brouillon ;
 * en brouillon : remplacement ; active : nouvelle version brouillon puis remplacement (la version active, qui sert les envois,
 * n'est jamais modifiée). L'activation reste un geste humain. Aucun corps n'est renvoyé.
 *
 * <p>Contrairement à {@code define_document_template}, la syntaxe et la politique du corps HTML sont contrôlées <b>avant</b> toute
 * écriture : un gabarit refusé n'est pas enregistré (avec {@code dryRun}, le problème est rapporté sans lever d'erreur).
 */
@Component
public class DefineNotificationTemplateTool {

    private static final TenantAdminGuard GUARD = new TenantAdminGuard();

    private final ListNotificationTemplateUseCase listUseCase;
    private final CreateNotificationTemplateUseCase createUseCase;
    private final UpdateNotificationTemplateUseCase updateUseCase;
    private final NewNotificationTemplateVersionUseCase newVersionUseCase;
    private final NotificationTemplateChecker checker;
    private final NotificationTemplateProperties properties;
    private final McpAccessContextFactory accessContextFactory;

    public DefineNotificationTemplateTool(
            ListNotificationTemplateUseCase listUseCase,
            CreateNotificationTemplateUseCase createUseCase,
            UpdateNotificationTemplateUseCase updateUseCase,
            NewNotificationTemplateVersionUseCase newVersionUseCase,
            NotificationTemplateChecker checker,
            NotificationTemplateProperties properties,
            McpAccessContextFactory accessContextFactory) {
        this.listUseCase = Objects.requireNonNull(listUseCase, "listUseCase");
        this.createUseCase = Objects.requireNonNull(createUseCase, "createUseCase");
        this.updateUseCase = Objects.requireNonNull(updateUseCase, "updateUseCase");
        this.newVersionUseCase = Objects.requireNonNull(newVersionUseCase, "newVersionUseCase");
        this.checker = Objects.requireNonNull(checker, "checker");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "define_notification_template",
            description = "Write an e-mail notification template (subject, plain-text body, optional HTML body; Thymeleaf "
                    + "subset, variables such as ${dossier.reference} or ${variables.name}) as a DRAFT - never live. "
                    + "No version yet (or the latest is archived): creates a draft. Latest is a draft: replaces it. "
                    + "Latest is ACTIVE: creates a new draft version, then replaces it (the active version keeps "
                    + "serving e-mails). The whole template is replaced: omit htmlBody for a text-only template. "
                    + "Syntax and the HTML policy (no images, no style element, links limited to http, https and "
                    + "mailto) are checked BEFORE anything is written and a refused template is not saved. Activation "
                    + "is a human action in the admin UI. The bodies are never returned. Use "
                    + "preview_notification_template first to check the rendering. dryRun=true reports the operation "
                    + "and the check without writing.")
    public Map<String, Object> defineNotificationTemplate(
            @McpToolParam(required = true, description = "Template key: lowercase letters, digits and hyphens")
                    String templateKey,
            @McpToolParam(required = false, description = "Variant key (optional)") String variantKey,
            @McpToolParam(required = false, description = "Locale, e.g. fr or fr-BE (optional)") String locale,
            @McpToolParam(required = false, description = "Display label (kept when absent on update)") String label,
            @McpToolParam(required = true, description = "Subject line (text, a single line)") String subject,
            @McpToolParam(required = true, description = "Plain-text body") String textBody,
            @McpToolParam(required = false, description = "HTML body (optional); omit for a text-only template")
                    String htmlBody,
            @McpToolParam(required = false, description = "Report the operation without writing; default false")
                    Boolean dryRun) {
        AccessContext.User actor = accessContextFactory.currentActor();
        // Comme l'API REST (TENANT_ADMIN) : un acteur de session non administrateur (assistant) ne peut pas écrire un gabarit.
        GUARD.requireTenantAdminOrApiService(actor);
        String actorId = actor.userId();
        McpArgs.requireNonBlank(templateKey, "templateKey");
        String key = NotificationTemplateSupport.requireKey(templateKey);
        String variant = NotificationTemplateSupport.optionalVariant(variantKey);
        String loc = NotificationTemplateSupport.optionalLocale(locale);
        String effectiveLabel = NotificationTemplateSupport.optionalLabel(label);
        boolean dry = Boolean.TRUE.equals(dryRun);

        // Avant toute écriture : bornes, syntaxe et politique HTML. Un refus n'écrit rien.
        String problem = null;
        try {
            checkBeforeWriting(key, variant, loc, subject, textBody, htmlBody);
        } catch (NotificationTemplateValidationException ex) {
            if (!dry) {
                throw ex;
            }
            problem = ex.getMessage();
        }

        NotificationTemplate latest = NotificationTemplateVersions.latest(listUseCase, key, variant, loc);
        String operation;
        if (latest == null || latest.status() == TemplateStatus.ARCHIVED) {
            operation = "create";
        } else if (latest.status() == TemplateStatus.DRAFT) {
            operation = "update";
        } else {
            operation = "newVersion";
        }
        if (dry) {
            return result(operation, true, latest, problem);
        }
        // Un corps HTML vide supprime celui d'une version existante : l'appel décrit le gabarit entier.
        String html = htmlBody == null ? "" : htmlBody;
        NotificationTemplate saved;
        switch (operation) {
            case "create" -> saved = createUseCase.create(new CreateNotificationTemplateUseCase.CreateCommand(
                    key, variant, loc, effectiveLabel != null ? effectiveLabel : key, subject, textBody, htmlBody, actorId));
            case "update" -> saved = updateUseCase.update(new UpdateNotificationTemplateUseCase.UpdateCommand(
                    latest.id(), effectiveLabel, subject, textBody, html, actorId));
            default -> {
                NotificationTemplate draft = newVersionUseCase.newVersion(latest.id(), actorId);
                saved = updateUseCase.update(new UpdateNotificationTemplateUseCase.UpdateCommand(
                        draft.id(), effectiveLabel, subject, textBody, html, actorId));
            }
        }
        return result(operation, false, saved, null);
    }

    private void checkBeforeWriting(
            String key, String variant, String locale, String subject, String textBody, String htmlBody) {
        NotificationTemplateLimits limits = properties.limits();
        Instant now = Instant.now();
        checker.check(new NotificationTemplate(
                UUID.randomUUID(),
                key,
                variant,
                locale,
                null,
                0,
                TemplateStatus.DRAFT,
                NotificationTemplateSupport.requireSubject(subject, limits),
                NotificationTemplateSupport.requireTextBody(textBody, limits),
                NotificationTemplateSupport.optionalHtmlBody(htmlBody, limits),
                "mcp-check",
                now,
                now,
                "mcp-check"));
    }

    private static Map<String, Object> result(
            String operation, boolean dryRun, NotificationTemplate template, String problem) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("operation", operation);
        map.put("dryRun", dryRun);
        map.put("syntax", problem == null ? "OK" : "INVALID");
        if (problem != null) {
            map.put("syntaxProblem", problem);
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
