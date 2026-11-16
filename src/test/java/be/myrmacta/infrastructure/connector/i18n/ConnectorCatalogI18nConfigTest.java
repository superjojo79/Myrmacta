package be.myrmacta.infrastructure.connector.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

class ConnectorCatalogI18nConfigTest {

    private final ConnectorCatalogI18nConfig config = new ConnectorCatalogI18nConfig();

    @Test
    void messageSource_usesConnectorCatalogBasenameAndEnglishDefault() {
        MessageSource source = config.connectorCatalogMessageSource();
        assertThat(source).isInstanceOf(ReloadableResourceBundleMessageSource.class);
        ReloadableResourceBundleMessageSource reloadable = (ReloadableResourceBundleMessageSource) source;
        assertThat(reloadable.getBasenameSet()).containsExactly("classpath:i18n/connector-catalog");
        assertThat(source.getMessage("connector.smtp.label", null, Locale.ENGLISH))
                .isEqualTo("SMTP (ad hoc email send)");
        assertThat(source.getMessage("connector.smtp.label", null, Locale.FRENCH))
                .isEqualTo("SMTP (envoi d'e-mail ad hoc)");
    }

    @Test
    void localeResolver_supportsFrAndEn_defaultsToEnglish() {
        LocaleResolver resolver = config.localeResolver();
        assertThat(resolver).isInstanceOf(AcceptHeaderLocaleResolver.class);
        AcceptHeaderLocaleResolver accept = (AcceptHeaderLocaleResolver) resolver;
        assertThat(accept.getSupportedLocales()).containsExactly(Locale.FRENCH, Locale.ENGLISH);

        HttpServletRequest french = mock(HttpServletRequest.class);
        when(french.getHeader("Accept-Language")).thenReturn("fr");
        when(french.getLocales()).thenReturn(java.util.Collections.enumeration(List.of(Locale.FRENCH)));
        assertThat(resolver.resolveLocale(french)).isEqualTo(Locale.FRENCH);

        HttpServletRequest german = mock(HttpServletRequest.class);
        when(german.getHeader("Accept-Language")).thenReturn("de");
        when(german.getLocales()).thenReturn(java.util.Collections.enumeration(List.of(Locale.GERMAN)));
        assertThat(resolver.resolveLocale(german)).isEqualTo(Locale.ENGLISH);

        HttpServletRequest empty = mock(HttpServletRequest.class);
        when(empty.getHeader("Accept-Language")).thenReturn(null);
        when(empty.getLocales()).thenReturn(java.util.Collections.enumeration(List.of()));
        assertThat(resolver.resolveLocale(empty)).isEqualTo(Locale.ENGLISH);
    }
}
