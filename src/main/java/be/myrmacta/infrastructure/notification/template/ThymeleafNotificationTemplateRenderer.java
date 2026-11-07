package be.myrmacta.infrastructure.notification.template;

import be.myrmacta.domain.document.model.TemplateProcessingException;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.RenderedNotification;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRenderer;
import be.myrmacta.infrastructure.config.NotificationTemplateProperties;
import be.myrmacta.infrastructure.document.ExpressionLimits;
import be.myrmacta.infrastructure.document.SafeHtmlTemplateEngineFactory;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Rend un gabarit de notification (registre §123) : l'objet et le corps texte en mode TEXT, le corps HTML en mode HTML puis
 * nettoyé par {@link NotificationHtmlPolicy#clean(String)}. Mêmes moteurs filtrés que les templates de document
 * ({@link SafeHtmlTemplateEngineFactory}), même grammaire d'expression (D-40), évaluation stricte : une variable ou une
 * propriété absente ou nulle fait échouer le rendu.
 *
 * <p>Bornes de coût (revue de clôture) : le rendu écrit dans un {@link Writer} borné qui s'arrête dès que la somme des
 * parties dépasse {@code renderedMaxBytes} ; l'évaluateur borne {@code #numbers.sequence} et {@code #strings.repeat},
 * refuse {@code #strings.randomAlphanumeric} et débite un budget d'évaluations par rendu. Le pré-traitement
 * {@code __${…}__} est refusé (il évaluerait une donnée comme une expression).
 *
 * <p>Les messages d'erreur nomment le gabarit, la partie et l'expression, jamais une valeur ; la cause d'origine n'est pas
 * attachée (son message peut citer une valeur).
 */
public class ThymeleafNotificationTemplateRenderer implements NotificationTemplateRenderer {

    /** Messages de l'évaluateur sûr : ils ne citent que l'expression du gabarit. */
    private static final List<String> EXPRESSION_MESSAGES =
            List.of(
                    "Unknown template property:",
                    "Unknown template variable:",
                    "Unsupported Thymeleaf expression:",
                    "Template cost limit exceeded");

    private static final String PREPROCESSING = "__";

    private static final String TEMPLATE_LOCATION = " (template: ";

    /** Les cinq entités que l'échappement de Thymeleaf produit, y compris en mode TEXT. */
    private static final Pattern HTML_ENTITY = Pattern.compile("&(amp|lt|gt|quot|#39);");

    private static final Pattern SUBJECT_BLANKS = Pattern.compile("[\\p{Cntrl}\\s]+");

    private final TemplateEngine textEngine;
    private final TemplateEngine htmlEngine;
    private final ExpressionLimits limits;
    private final int renderedSubjectMaxChars;
    private final int renderedMaxBytes;

    public ThymeleafNotificationTemplateRenderer(NotificationTemplateProperties properties) {
        Objects.requireNonNull(properties, "properties");
        this.limits = new ExpressionLimits(properties.utilityMaxSize(), properties.renderBudget());
        this.textEngine = SafeHtmlTemplateEngineFactory.textStringEngine(limits);
        this.htmlEngine = SafeHtmlTemplateEngineFactory.htmlStringEngine(limits);
        this.renderedSubjectMaxChars = properties.renderedSubjectMaxChars();
        this.renderedMaxBytes = properties.renderedMaxBytes();
    }

    @Override
    public RenderedNotification render(NotificationTemplate template, Map<String, Object> model) {
        Objects.requireNonNull(template, "template");
        Context context = context(template, model);
        limits.newBudget(context);
        ByteBudget bytes = new ByteBudget(renderedMaxBytes);

        String subject = oneLine(
                unescapeText(process(textEngine, template, "subject", template.subject(), context, bytes)));
        if (subject.isEmpty()) {
            throw failure(template, "subject", "the rendered subject is empty");
        }
        if (subject.length() > renderedSubjectMaxChars) {
            throw failure(template, "subject", "the rendered subject exceeds " + renderedSubjectMaxChars + " characters");
        }
        String textBody = unescapeText(process(textEngine, template, "textBody", template.textBody(), context, bytes));
        String htmlBody = template.htmlBody() == null
                ? null
                : NotificationHtmlPolicy.clean(process(htmlEngine, template, "htmlBody", template.htmlBody(), context, bytes));

        long total = utf8Length(subject) + utf8Length(textBody) + (htmlBody == null ? 0 : utf8Length(htmlBody));
        if (total > renderedMaxBytes) {
            throw failure(template, "rendering", "the rendered message exceeds " + renderedMaxBytes + " bytes");
        }
        return new RenderedNotification(subject, textBody, htmlBody);
    }

    private static Context context(NotificationTemplate template, Map<String, Object> model) {
        Locale locale = template.locale() == null ? Locale.ROOT : Locale.forLanguageTag(template.locale());
        Context context = new Context(locale);
        if (model != null) {
            context.setVariables(model);
        }
        return context;
    }

    private static String process(
            TemplateEngine engine,
            NotificationTemplate template,
            String part,
            String source,
            Context context,
            ByteBudget bytes) {
        if (source.contains(PREPROCESSING)) {
            throw failure(template, part, "the pre-processing sequence '__' is not allowed");
        }
        BoundedWriter writer = new BoundedWriter(bytes);
        try {
            engine.process(source, context, writer);
            return writer.toString();
        } catch (RuntimeException ex) {
            RenderLimitExceededException limit = limitIn(ex);
            throw failure(template, part, limit != null ? limit.getMessage() : detail(ex));
        } catch (StackOverflowError | OutOfMemoryError ex) {
            throw failure(template, part, "the template is too expensive to render");
        }
    }

    private static RenderLimitExceededException limitIn(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof RenderLimitExceededException limit) {
                return limit;
            }
        }
        return null;
    }

    /** Octets restants pour l'ensemble des parties d'un rendu. */
    private static final class ByteBudget {
        private final int max;
        private long used;

        ByteBudget(int max) {
            this.max = max;
        }

        void add(long bytes) {
            used += bytes;
            if (used > max) {
                throw new RenderLimitExceededException("the rendered message exceeds " + max + " bytes");
            }
        }
    }

    private static final class RenderLimitExceededException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        RenderLimitExceededException(String message) {
            super(message, null, false, false);
        }
    }

    /** Écrit dans un tampon et s'arrête dès que la somme des parties dépasse le plafond. */
    private static final class BoundedWriter extends Writer {
        private final StringBuilder out = new StringBuilder();
        private final ByteBudget bytes;

        BoundedWriter(ByteBudget bytes) {
            this.bytes = bytes;
        }

        @Override
        public void write(char[] buffer, int offset, int length) {
            long size = 0;
            for (int i = offset; i < offset + length; i++) {
                char c = buffer[i];
                size += c < 0x80 ? 1 : c < 0x800 ? 2 : Character.isSurrogate(c) ? 2 : 3;
            }
            bytes.add(size);
            out.append(buffer, offset, length);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        @Override
        public String toString() {
            return out.toString();
        }
    }

    /** Remplace chaque blanc ou caractère de contrôle (CR et LF compris) par une espace, réduit les suites, rogne. */
    private static String oneLine(String value) {
        return SUBJECT_BLANKS.matcher(value).replaceAll(" ").strip();
    }

    /**
     * Thymeleaf échappe {@code [[${x}]]} en entités HTML dans tous les modes, TEXT compris ({@code &#39;} pour une
     * apostrophe). Dans un texte brut, l'entité serait lue telle quelle par le destinataire : on la défait, en une passe,
     * pour les seules cinq entités que l'échappement produit.
     */
    private static String unescapeText(String value) {
        return HTML_ENTITY.matcher(value).replaceAll(match -> switch (match.group(1)) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            default -> "'";
        });
    }

    /** Reprend le message de l'évaluateur sûr s'il est dans la chaîne des causes, sinon un message générique. */
    private static String detail(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && EXPRESSION_MESSAGES.stream().anyMatch(message::startsWith)) {
                // Thymeleaf ajoute « (template: "…" - line n, col m) » : on ne garde que l'expression.
                int location = message.indexOf(TEMPLATE_LOCATION);
                return location > 0 ? message.substring(0, location) : message;
            }
        }
        return "the template could not be evaluated";
    }

    private static TemplateProcessingException failure(NotificationTemplate template, String part, String detail) {
        return new TemplateProcessingException(
                "Notification template '%s' (%s): %s".formatted(template.templateKey(), part, detail));
    }

    private static long utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
