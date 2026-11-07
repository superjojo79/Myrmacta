package be.myrmacta.domain.notification.template.model;

/**
 * Bornes d'un gabarit à l'écriture (registre §123, hypothèse H3, propriétés {@code app.notification.template.*}).
 *
 * @param subjectMaxChars longueur maximale de l'objet du gabarit
 * @param textBodyMaxBytes taille maximale du corps texte (UTF-8)
 * @param htmlBodyMaxBytes taille maximale du corps HTML (UTF-8)
 */
public record NotificationTemplateLimits(int subjectMaxChars, int textBodyMaxBytes, int htmlBodyMaxBytes) {

    public static final NotificationTemplateLimits DEFAULTS = new NotificationTemplateLimits(500, 65_536, 262_144);

    public NotificationTemplateLimits {
        if (subjectMaxChars <= 0 || textBodyMaxBytes <= 0 || htmlBodyMaxBytes <= 0) {
            throw new IllegalArgumentException("limits must be positive");
        }
    }
}
