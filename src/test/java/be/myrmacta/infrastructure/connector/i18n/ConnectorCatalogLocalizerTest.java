package be.myrmacta.infrastructure.connector.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConfigFieldType;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

class ConnectorCatalogLocalizerTest {

    private StaticMessageSource messages;
    private ConnectorCatalogLocalizer localizer;

    @BeforeEach
    void setUp() {
        messages = new StaticMessageSource();
        messages.addMessage("connector.smtp.label", Locale.ENGLISH, "SMTP (ad hoc email send)");
        messages.addMessage("connector.smtp.label", Locale.FRENCH, "SMTP (envoi d'e-mail ad hoc)");
        messages.addMessage("connector.http-rest.field.host.label", Locale.ENGLISH, "Host");
        messages.addMessage("connector.http-rest.field.host.label", Locale.FRENCH, "Hôte");
        messages.addMessage("connector.http-rest.field.host.help", Locale.ENGLISH, "Public host only");
        messages.addMessage("connector.http-rest.field.host.help", Locale.FRENCH, "Hôte public seulement");
        localizer = new ConnectorCatalogLocalizer(messages);
    }

    @Test
    void normalize_nullAndUnknownFallBackToEnglish_frStaysFrench() {
        assertThat(ConnectorCatalogLocalizer.normalize(null)).isEqualTo(Locale.ENGLISH);
        assertThat(ConnectorCatalogLocalizer.normalize(Locale.GERMAN)).isEqualTo(Locale.ENGLISH);
        assertThat(ConnectorCatalogLocalizer.normalize(Locale.FRENCH)).isEqualTo(Locale.FRENCH);
        assertThat(ConnectorCatalogLocalizer.normalize(Locale.forLanguageTag("fr-BE"))).isEqualTo(Locale.FRENCH);
    }

    @Test
    void typeLabel_resolvesInRequestedLocale_withFallbackWhenKeyMissing() {
        ConnectorDefinition smtp = definition("smtp", "SMTP raw", List.of());
        assertThat(localizer.typeLabel(smtp, Locale.FRENCH)).isEqualTo("SMTP (envoi d'e-mail ad hoc)");
        assertThat(localizer.typeLabel(smtp, Locale.ENGLISH)).isEqualTo("SMTP (ad hoc email send)");

        ConnectorDefinition unknown = definition("unknown", "Fallback label", List.of());
        assertThat(localizer.typeLabel(unknown, Locale.ENGLISH)).isEqualTo("Fallback label");
    }

    @Test
    void fieldHelp_returnsNullWhenSchemaHasNoHelp() {
        ConfigField field = new ConfigField("host", "Host", ConfigFieldType.STRING, true, null, List.of(), null);
        assertThat(localizer.fieldHelp("http-rest", field, Locale.FRENCH)).isNull();
    }

    @Test
    void fieldLabelAndHelp_resolveTranslatedText() {
        ConfigField field = new ConfigField(
                "host", "Host raw", ConfigFieldType.STRING, true, null, List.of(), "help raw");
        assertThat(localizer.fieldLabel("http-rest", field, Locale.FRENCH)).isEqualTo("Hôte");
        assertThat(localizer.fieldHelp("http-rest", field, Locale.FRENCH)).isEqualTo("Hôte public seulement");
        assertThat(localizer.fieldLabel("http-rest", field, Locale.ENGLISH)).isEqualTo("Host");
        assertThat(localizer.fieldHelp("http-rest", field, Locale.ENGLISH)).isEqualTo("Public host only");
    }

    @Test
    void localize_translatesTypeAndSchema_andRejectsNullDefinition() {
        ConfigField withHelp = new ConfigField(
                "host", "Host raw", ConfigFieldType.STRING, true, null, List.of(), "help raw");
        ConfigField withoutHelp = new ConfigField("port", "Port", ConfigFieldType.INTEGER, false, "22", List.of(), null);
        ConnectorDefinition definition = definition("http-rest", "HTTP raw", List.of(withHelp, withoutHelp));

        var localized = localizer.localize(definition, Locale.FRENCH);

        assertThat(localized.type()).isEqualTo("http-rest");
        assertThat(localized.label()).isEqualTo("HTTP raw"); // no FR key for http-rest in this fixture → fallback
        assertThat(localized.capability()).isEqualTo(ConnectorCapability.SERVICE);
        assertThat(localized.configSchema()).hasSize(2);
        assertThat(localized.configSchema().get(0).label()).isEqualTo("Hôte");
        assertThat(localized.configSchema().get(0).helpText()).isEqualTo("Hôte public seulement");
        assertThat(localized.configSchema().get(1).label()).isEqualTo("Port");
        assertThat(localized.configSchema().get(1).helpText()).isNull();

        assertThatThrownBy(() -> localizer.localize(null, Locale.ENGLISH)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void localizedConnectorDefinition_exposesLocalizedFieldsAndDelegatesValidate() {
        ConnectorDefinition delegate = mock(ConnectorDefinition.class);
        when(delegate.getType()).thenReturn("smtp");
        var localized = new ConnectorCatalogLocalizer.LocalizedDefinition(
                "smtp",
                "SMTP (envoi d'e-mail ad hoc)",
                ConnectorCapability.SERVICE,
                List.of(new ConfigField("x", "X", ConfigFieldType.STRING, false, null, List.of(), null)));

        var wrapper = new LocalizedConnectorDefinition(delegate, localized);

        assertThat(wrapper.getType()).isEqualTo("smtp");
        assertThat(wrapper.getLabel()).isEqualTo("SMTP (envoi d'e-mail ad hoc)");
        assertThat(wrapper.getCapability()).isEqualTo(ConnectorCapability.SERVICE);
        assertThat(wrapper.getConfigSchema()).hasSize(1);
        wrapper.validate(Map.of("a", 1));
        verify(delegate).validate(Map.of("a", 1));
    }

    private static ConnectorDefinition definition(String type, String label, List<ConfigField> schema) {
        ConnectorDefinition definition = mock(ConnectorDefinition.class);
        when(definition.getType()).thenReturn(type);
        when(definition.getLabel()).thenReturn(label);
        when(definition.getCapability()).thenReturn(ConnectorCapability.SERVICE);
        when(definition.getConfigSchema()).thenReturn(schema);
        return definition;
    }
}
