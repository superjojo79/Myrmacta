package be.myrmacta.infrastructure.connector.i18n;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

/**
 * TODO §142 (D-CONNECTOR-CATALOG-I18N) — résout les libellés et aides du catalogue dans la langue
 * demandée. Les clés sont structurelles ({@code connector.{type}.label},
 * {@code connector.{type}.field.{name}.label|help}) ; l'API et le MCP rendent déjà les textes
 * traduits, jamais les clés.
 */
@Component
public class ConnectorCatalogLocalizer {

    private final MessageSourceAccessor messages;

    public ConnectorCatalogLocalizer(@Qualifier("connectorCatalogMessageSource") MessageSource connectorCatalogMessageSource) {
        this.messages = new MessageSourceAccessor(
                Objects.requireNonNull(connectorCatalogMessageSource, "connectorCatalogMessageSource"),
                Locale.ENGLISH);
    }

    public String typeLabel(ConnectorDefinition definition, Locale locale) {
        Objects.requireNonNull(definition, "definition");
        return message("connector." + definition.getType() + ".label", definition.getLabel(), locale);
    }

    public String fieldLabel(String type, ConfigField field, Locale locale) {
        Objects.requireNonNull(field, "field");
        return message("connector." + type + ".field." + field.name() + ".label", field.label(), locale);
    }

    public String fieldHelp(String type, ConfigField field, Locale locale) {
        Objects.requireNonNull(field, "field");
        if (field.helpText() == null) {
            return null;
        }
        return message("connector." + type + ".field." + field.name() + ".help", field.helpText(), locale);
    }

    public LocalizedDefinition localize(ConnectorDefinition definition, Locale locale) {
        Objects.requireNonNull(definition, "definition");
        Locale lang = normalize(locale);
        List<ConfigField> schema = new ArrayList<>(definition.getConfigSchema().size());
        for (ConfigField field : definition.getConfigSchema()) {
            schema.add(new ConfigField(
                    field.name(),
                    fieldLabel(definition.getType(), field, lang),
                    field.type(),
                    field.required(),
                    field.defaultValue(),
                    field.options(),
                    fieldHelp(definition.getType(), field, lang)));
        }
        return new LocalizedDefinition(
                definition.getType(), typeLabel(definition, lang), definition.getCapability(), List.copyOf(schema));
    }

    private String message(String key, String fallback, Locale locale) {
        return messages.getMessage(key, null, fallback, normalize(locale));
    }

    static Locale normalize(Locale locale) {
        if (locale == null) {
            return Locale.ENGLISH;
        }
        String language = locale.getLanguage();
        if ("fr".equalsIgnoreCase(language)) {
            return Locale.FRENCH;
        }
        return Locale.ENGLISH;
    }

    /** Vue présentation du catalogue (type, libellé, schéma déjà traduits). */
    public record LocalizedDefinition(
            String type,
            String label,
            be.myrmacta.domain.connector.model.ConnectorCapability capability,
            List<ConfigField> configSchema) {}
}
