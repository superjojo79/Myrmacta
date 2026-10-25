package be.myrmacta.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** TODO §90 (D7) — langue par défaut du tenant ({@code app.i18n.default-locale}). */
@Configuration
@EnableConfigurationProperties(I18nProperties.class)
class I18nConfig {}
