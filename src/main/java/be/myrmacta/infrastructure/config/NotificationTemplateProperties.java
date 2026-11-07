package be.myrmacta.infrastructure.config;

import be.myrmacta.domain.notification.template.model.NotificationTemplateLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bornes des gabarits de notification (registre §123, hypothèse H3). Toutes ont un défaut : aucune variable
 * d'environnement n'est nécessaire.
 *
 * @param subjectMaxChars longueur maximale de l'objet d'un gabarit
 * @param renderedSubjectMaxChars longueur maximale de l'objet rendu (limite pratique d'un en-tête)
 * @param textBodyMaxBytes taille maximale du corps texte d'un gabarit
 * @param htmlBodyMaxBytes taille maximale du corps HTML d'un gabarit
 * @param renderedMaxBytes taille maximale de la somme des parties rendues (borne un {@code th:each} sur une grande liste)
 * @param previewModelMaxBytes taille maximale du modèle JSON d'un aperçu
 * @param utilityMaxSize taille maximale (éléments ou caractères) produite par {@code #numbers.sequence} et
 *     {@code #strings.repeat}, et plafond des chiffres demandés à {@code #numbers.format*}
 * @param stringMaxChars longueur maximale de toute chaîne produite par une évaluation ou passée à un utilitaire
 * @param renderBudget budget par rendu : une évaluation coûte 1, plus la longueur de l'expression, la taille des opérandes
 *     et du résultat (une chaîne pèse sa longueur divisée par 64, une collection sa taille) ; borne les {@code th:each}
 *     imbriqués à sortie vide
 * @param renderTimeoutMillis durée maximale d'un rendu, contrôlée à chaque évaluation
 */
@ConfigurationProperties(prefix = "app.notification.template")
public record NotificationTemplateProperties(
        int subjectMaxChars,
        int renderedSubjectMaxChars,
        int textBodyMaxBytes,
        int htmlBodyMaxBytes,
        int renderedMaxBytes,
        int previewModelMaxBytes,
        int utilityMaxSize,
        int stringMaxChars,
        long renderBudget,
        long renderTimeoutMillis) {

    public static final int DEFAULT_RENDERED_SUBJECT_MAX_CHARS = 255;
    public static final int DEFAULT_RENDERED_MAX_BYTES = 1_048_576;
    public static final int DEFAULT_PREVIEW_MODEL_MAX_BYTES = 65_536;
    public static final int DEFAULT_UTILITY_MAX_SIZE = 1_000;
    public static final int DEFAULT_STRING_MAX_CHARS = 100_000;
    public static final long DEFAULT_RENDER_BUDGET = 100_000;
    public static final long DEFAULT_RENDER_TIMEOUT_MILLIS = 2_000;

    public NotificationTemplateProperties {
        if (subjectMaxChars <= 0) {
            subjectMaxChars = NotificationTemplateLimits.DEFAULTS.subjectMaxChars();
        }
        if (renderedSubjectMaxChars <= 0) {
            renderedSubjectMaxChars = DEFAULT_RENDERED_SUBJECT_MAX_CHARS;
        }
        if (textBodyMaxBytes <= 0) {
            textBodyMaxBytes = NotificationTemplateLimits.DEFAULTS.textBodyMaxBytes();
        }
        if (htmlBodyMaxBytes <= 0) {
            htmlBodyMaxBytes = NotificationTemplateLimits.DEFAULTS.htmlBodyMaxBytes();
        }
        if (renderedMaxBytes <= 0) {
            renderedMaxBytes = DEFAULT_RENDERED_MAX_BYTES;
        }
        if (previewModelMaxBytes <= 0) {
            previewModelMaxBytes = DEFAULT_PREVIEW_MODEL_MAX_BYTES;
        }
        if (utilityMaxSize <= 0) {
            utilityMaxSize = DEFAULT_UTILITY_MAX_SIZE;
        }
        if (stringMaxChars <= 0) {
            stringMaxChars = DEFAULT_STRING_MAX_CHARS;
        }
        if (renderBudget <= 0) {
            renderBudget = DEFAULT_RENDER_BUDGET;
        }
        if (renderTimeoutMillis <= 0) {
            renderTimeoutMillis = DEFAULT_RENDER_TIMEOUT_MILLIS;
        }
    }

    public static NotificationTemplateProperties defaults() {
        return new NotificationTemplateProperties(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Plafond du corps d'une requête d'aperçu : un gabarit au maximum, plus un modèle, avec une marge pour l'échappement
     * JSON. Rejeté sur {@code Content-Length} avant toute lecture du corps.
     */
    public long previewRequestMaxBytes() {
        long content = 4L * subjectMaxChars + textBodyMaxBytes + htmlBodyMaxBytes + previewModelMaxBytes;
        return 2 * content + 4_096;
    }

    public NotificationTemplateLimits limits() {
        return new NotificationTemplateLimits(subjectMaxChars, textBodyMaxBytes, htmlBodyMaxBytes);
    }
}
