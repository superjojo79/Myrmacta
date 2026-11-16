package be.myrmacta.infrastructure.connector.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.context.NoSuchMessageException;

/**
 * Issue #106 / D-CONNECTOR-CATALOG-I18N — chaque type et chaque champ du catalogue intégré a une
 * entrée explicite FR et EN (sans repli silencieux sur le libellé Java).
 */
@SpringBootTest(
        properties = {
            "app.connector.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "app.connector.kafka.max-instances-per-tenant=1"
        })
@Import(OidcClientTestConfiguration.class)
class ConnectorCatalogI18nCoverageTest {

    @Autowired
    private ConnectorCatalog catalog;

    @Autowired
    @Qualifier("connectorCatalogMessageSource")
    private MessageSource messages;

    @Autowired
    private ConnectorCatalogLocalizer localizer;

    @Test
    void everyBuiltinTypeAndField_hasFrAndEnTranslations() {
        List<String> gaps = new ArrayList<>();
        for (ConnectorDefinition definition : catalog.listAll()) {
            String type = definition.getType();
            requireMessage(gaps, "connector." + type + ".label", Locale.FRENCH);
            requireMessage(gaps, "connector." + type + ".label", Locale.ENGLISH);
            String frType = localizer.typeLabel(definition, Locale.FRENCH);
            String enType = localizer.typeLabel(definition, Locale.ENGLISH);
            if (frType.equals(enType)) {
                gaps.add(type + " type label identical in FR and EN: " + frType);
            }
            for (ConfigField field : definition.getConfigSchema()) {
                String labelKey = "connector." + type + ".field." + field.name() + ".label";
                requireMessage(gaps, labelKey, Locale.FRENCH);
                requireMessage(gaps, labelKey, Locale.ENGLISH);
                String frLabel = requireResolved(gaps, labelKey, Locale.FRENCH);
                String enLabel = requireResolved(gaps, labelKey, Locale.ENGLISH);
                if (frLabel != null && enLabel != null && frLabel.equals(enLabel)
                        && !isAcceptableIdenticalLabel(field.name(), enLabel)) {
                    gaps.add(type + "." + field.name() + " label identical in FR and EN: " + enLabel);
                }
                if (field.helpText() != null) {
                    String helpKey = "connector." + type + ".field." + field.name() + ".help";
                    requireMessage(gaps, helpKey, Locale.FRENCH);
                    requireMessage(gaps, helpKey, Locale.ENGLISH);
                }
            }
        }
        assertThat(gaps).as("i18n gaps").isEmpty();
    }

    private void requireMessage(List<String> gaps, String key, Locale locale) {
        try {
            messages.getMessage(key, null, locale);
        } catch (NoSuchMessageException ex) {
            gaps.add(key + " missing for " + locale.getLanguage());
        }
    }

    private String requireResolved(List<String> gaps, String key, Locale locale) {
        try {
            return messages.getMessage(key, null, locale);
        } catch (NoSuchMessageException ex) {
            gaps.add(key + " missing for " + locale.getLanguage());
            return null;
        }
    }

    /** Emprunts techniques volontairement identiques FR/EN. */
    private static boolean isAcceptableIdenticalLabel(String fieldName, String label) {
        return "topic".equals(fieldName) || "mapping".equals(fieldName) || "Mapping".equals(label) || "Topic".equals(label);
    }
}
