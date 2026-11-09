package be.myrmacta.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bornes de coût du rendu d'un template de document (issue #43). Mêmes défauts que les gabarits de notification.
 * Aucune variable d'environnement : un PDF légitime peut être volumineux, ces bornes visent l'évaluation, pas la taille
 * du fichier produit.
 *
 * @param utilityMaxSize taille maximale de {@code #numbers.sequence} et {@code #strings.repeat}
 * @param stringMaxChars longueur maximale d'une chaîne produite ou passée à un utilitaire
 * @param renderBudget budget par rendu
 * @param renderTimeoutMillis durée maximale d'un rendu
 */
@ConfigurationProperties(prefix = "app.document.template")
public record DocumentTemplateCostProperties(
        int utilityMaxSize, int stringMaxChars, long renderBudget, long renderTimeoutMillis) {

    public DocumentTemplateCostProperties {
        if (utilityMaxSize <= 0) {
            utilityMaxSize = NotificationTemplateProperties.DEFAULT_UTILITY_MAX_SIZE;
        }
        if (stringMaxChars <= 0) {
            stringMaxChars = NotificationTemplateProperties.DEFAULT_STRING_MAX_CHARS;
        }
        if (renderBudget <= 0) {
            renderBudget = NotificationTemplateProperties.DEFAULT_RENDER_BUDGET;
        }
        if (renderTimeoutMillis <= 0) {
            renderTimeoutMillis = NotificationTemplateProperties.DEFAULT_RENDER_TIMEOUT_MILLIS;
        }
    }

    public static DocumentTemplateCostProperties defaults() {
        return new DocumentTemplateCostProperties(0, 0, 0, 0);
    }
}
