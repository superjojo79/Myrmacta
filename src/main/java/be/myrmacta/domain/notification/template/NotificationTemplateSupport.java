package be.myrmacta.domain.notification.template;

import be.myrmacta.domain.notification.template.model.NotificationTemplateLimits;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Normalisation et bornes communes aux cas d'usage d'écriture des gabarits de notification. */
public final class NotificationTemplateSupport {

    /** Clé et variante : {@code [a-z0-9][a-z0-9-]{0,99}}. */
    private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9-]{0,99}");

    /** Langue BCP 47 courte : {@code fr}, {@code nl}, {@code fr-BE}. */
    private static final Pattern LOCALE = Pattern.compile("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8}){0,3}");

    private static final int LABEL_MAX_CHARS = 200;
    private static final int LOCALE_MAX_CHARS = 35;

    private NotificationTemplateSupport() {}

    public static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String requireKey(String value) {
        String key = normalizeOptional(value);
        if (key == null) {
            throw new NotificationTemplateValidationException("templateKey", "must not be blank");
        }
        if (!KEY.matcher(key).matches()) {
            throw new NotificationTemplateValidationException("templateKey", "must match [a-z0-9][a-z0-9-]{0,99}");
        }
        return key;
    }

    public static String optionalVariant(String value) {
        String variant = normalizeOptional(value);
        if (variant != null && !KEY.matcher(variant).matches()) {
            throw new NotificationTemplateValidationException("variantKey", "must match [a-z0-9][a-z0-9-]{0,99}");
        }
        return variant;
    }

    public static String optionalLocale(String value) {
        String locale = normalizeOptional(value);
        if (locale != null && (locale.length() > LOCALE_MAX_CHARS || !LOCALE.matcher(locale).matches())) {
            throw new NotificationTemplateValidationException("locale", "must be a short BCP 47 tag such as fr or fr-BE");
        }
        return locale;
    }

    public static String optionalLabel(String value) {
        String label = normalizeOptional(value);
        if (label != null && label.length() > LABEL_MAX_CHARS) {
            throw new NotificationTemplateValidationException("label", "exceeds " + LABEL_MAX_CHARS + " characters");
        }
        return label;
    }

    public static String requireSubject(String value, NotificationTemplateLimits limits) {
        String subject = normalizeOptional(value);
        if (subject == null) {
            throw new NotificationTemplateValidationException("subject", "must not be blank");
        }
        if (subject.length() > limits.subjectMaxChars()) {
            throw new NotificationTemplateValidationException(
                    "subject", "exceeds " + limits.subjectMaxChars() + " characters");
        }
        return subject;
    }

    /** Le corps texte est conservé tel quel (retours à la ligne compris) ; seul un corps vide est refusé. */
    public static String requireTextBody(String value, NotificationTemplateLimits limits) {
        if (value == null || value.isBlank()) {
            throw new NotificationTemplateValidationException("textBody", "must not be blank");
        }
        assertBytes("textBody", value, limits.textBodyMaxBytes());
        return value;
    }

    /** Un corps HTML vide ou blanc vaut « pas de corps HTML ». */
    public static String optionalHtmlBody(String value, NotificationTemplateLimits limits) {
        if (value == null || value.isBlank()) {
            return null;
        }
        assertBytes("htmlBody", value, limits.htmlBodyMaxBytes());
        return value;
    }

    public static String updatedByOrSystem(String updatedBy) {
        return updatedBy == null || updatedBy.isBlank() ? "system" : updatedBy.trim();
    }

    private static void assertBytes(String part, String value, int maxBytes) {
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new NotificationTemplateValidationException(part, "exceeds " + maxBytes + " bytes");
        }
    }
}
