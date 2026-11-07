package be.myrmacta.infrastructure.notification.template;

import be.myrmacta.domain.document.model.InvalidTemplateSyntaxException;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateChecker;
import be.myrmacta.infrastructure.document.ThymeleafTemplateSyntaxChecker;
import java.util.List;
import java.util.Objects;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Contrôle d'activation d'un gabarit de notification (registre §123) : syntaxe et grammaire d'expression de l'objet et du
 * corps texte (mode TEXT) et du corps HTML (mode HTML), puis politique du corps HTML. Sans exécuter, sans nettoyer en
 * silence : un gabarit hors politique est refusé, avec la partie et la règle.
 */
public class ThymeleafNotificationTemplateChecker implements NotificationTemplateChecker {

    private static final String PREPROCESSING = "__";

    private static final int MAX_REPORTED_VIOLATIONS = 20;

    private final ThymeleafTemplateSyntaxChecker syntaxChecker;

    public ThymeleafNotificationTemplateChecker(ThymeleafTemplateSyntaxChecker syntaxChecker) {
        this.syntaxChecker = Objects.requireNonNull(syntaxChecker, "syntaxChecker");
    }

    @Override
    public void check(NotificationTemplate template) {
        Objects.requireNonNull(template, "template");
        checkSyntax("subject", template.subject(), TemplateMode.TEXT);
        checkSyntax("textBody", template.textBody(), TemplateMode.TEXT);
        String html = template.htmlBody();
        if (html != null) {
            checkSyntax("htmlBody", html, TemplateMode.HTML);
            List<String> violations = NotificationHtmlPolicy.violations(html);
            if (!violations.isEmpty()) {
                throw new NotificationTemplateValidationException("htmlBody", summary(violations));
            }
        }
    }

    private void checkSyntax(String part, String source, TemplateMode mode) {
        // Le pré-traitement __${…}__ évalue une donnée comme une expression : le contrôle d'expressions ne le voit pas.
        if (source != null && source.contains(PREPROCESSING)) {
            throw new NotificationTemplateValidationException(
                    part, "the Thymeleaf pre-processing sequence '__' is not allowed");
        }
        try {
            syntaxChecker.check(source, mode);
        } catch (InvalidTemplateSyntaxException ex) {
            throw new NotificationTemplateValidationException(part, ex.getMessage());
        }
    }

    private static String summary(List<String> violations) {
        List<String> shown = violations.stream().limit(MAX_REPORTED_VIOLATIONS).toList();
        String joined = String.join("; ", shown);
        int more = violations.size() - shown.size();
        return more > 0 ? joined + "; and " + more + " more" : joined;
    }
}
