package com.flowforge.infrastructure.config;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * TODO §90 (D7, D-I18N-LANGS / D-I18N-DETECT) — langue par défaut du tenant : celle de l'interface tant que l'utilisateur n'a pas
 * choisi la sienne, et celle des messages que le serveur envoie à un tiers sans profil (invitation de signature). Français ou
 * anglais ; toute autre valeur arrête le démarrage plutôt que de retomber en silence sur une langue que personne n'a choisie.
 */
@ConfigurationProperties(prefix = "app.i18n")
public record I18nProperties(@DefaultValue("fr") String defaultLocale) {

    public static final List<String> SUPPORTED = List.of("fr", "en");

    public I18nProperties {
        String normalized = defaultLocale == null ? "" : defaultLocale.strip().toLowerCase(Locale.ROOT);
        if (!SUPPORTED.contains(normalized)) {
            throw new IllegalArgumentException("app.i18n.default-locale must be one of " + SUPPORTED + ", was: " + defaultLocale);
        }
        defaultLocale = normalized;
    }

    public Locale locale() {
        return Locale.forLanguageTag(defaultLocale);
    }
}
