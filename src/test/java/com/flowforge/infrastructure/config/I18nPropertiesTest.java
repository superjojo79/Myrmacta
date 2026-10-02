package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/** D7 (TODO §90) — langue par défaut du tenant : fr ou en, refus net sinon (pas de repli silencieux). */
class I18nPropertiesTest {

    @Test
    void frenchAndEnglishAreSupportedCaseInsensitively() {
        assertThat(new I18nProperties("fr").locale()).isEqualTo(Locale.FRENCH);
        assertThat(new I18nProperties(" EN ").locale()).isEqualTo(Locale.ENGLISH);
        assertThat(new I18nProperties("en").defaultLocale()).isEqualTo("en");
    }

    @Test
    void anUnsupportedOrBlankLanguageStopsTheStartup() {
        for (String bad : new String[] {"de", "", null, "fr-FR", "en_US"}) {
            assertThatThrownBy(() -> new I18nProperties(bad)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("app.i18n.default-locale");
        }
    }

    @Test
    void theSupportedListIsWhatTheSpaOffers() {
        assertThat(I18nProperties.SUPPORTED).containsExactly("fr", "en");
    }
}
