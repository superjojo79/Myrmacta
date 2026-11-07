package be.myrmacta.domain.notification.template.model;

import java.util.Objects;

/** Clé de résolution d'un gabarit actif : la variante et la langue nulles désignent le défaut. */
public record TemplateResolutionKey(String templateKey, String variantKey, String locale) {

    public TemplateResolutionKey {
        Objects.requireNonNull(templateKey, "templateKey");
    }
}
