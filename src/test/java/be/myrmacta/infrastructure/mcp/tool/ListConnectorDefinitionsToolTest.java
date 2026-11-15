package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.infrastructure.config.I18nProperties;
import be.myrmacta.infrastructure.connector.i18n.ConnectorCatalogLocalizer;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListConnectorDefinitionsToolTest {

    private ConnectorCatalog catalog;
    private ConnectorCatalogLocalizer localizer;
    private ListConnectorDefinitionsTool tool;

    @BeforeEach
    void setUp() {
        catalog = mock(ConnectorCatalog.class);
        localizer = mock(ConnectorCatalogLocalizer.class);
        when(localizer.localize(any(), any())).thenAnswer(invocation -> {
            ConnectorDefinition definition = invocation.getArgument(0);
            return new ConnectorCatalogLocalizer.LocalizedDefinition(
                    definition.getType(),
                    definition.getLabel(),
                    definition.getCapability(),
                    definition.getConfigSchema());
        });
        tool = new ListConnectorDefinitionsTool(catalog, localizer, new I18nProperties("fr"));
    }

    @Test
    void listsCatalogSortedByType() {
        ConnectorDefinition httpRest = definition("http-rest");
        ConnectorDefinition smtp = definition("smtp");
        when(catalog.listAll()).thenReturn(List.of(smtp, httpRest));

        var result = tool.listConnectorDefinitions(null, null);

        assertThat(result.content()).extracting(ConnectorDefinition::getType)
                .containsExactly("http-rest", "smtp");
    }

    @Test
    void pageAndSizeProvided_slicesAccordingly() {
        ConnectorDefinition httpRest = definition("http-rest");
        ConnectorDefinition smtp = definition("smtp");
        when(catalog.listAll()).thenReturn(List.of(smtp, httpRest));

        var result = tool.listConnectorDefinitions(1, 1);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.content()).extracting(ConnectorDefinition::getType).containsExactly("smtp");
    }

    @Test
    void localizesWithTenantDefaultLocale() {
        ConnectorDefinition smtp = definition("smtp");
        when(catalog.listAll()).thenReturn(List.of(smtp));
        when(localizer.localize(smtp, Locale.FRENCH)).thenReturn(new ConnectorCatalogLocalizer.LocalizedDefinition(
                "smtp", "SMTP (envoi d'e-mail ad hoc)", ConnectorCapability.SERVICE, List.of()));

        var result = tool.listConnectorDefinitions(0, 10);

        assertThat(result.content()).singleElement().extracting(ConnectorDefinition::getLabel)
                .isEqualTo("SMTP (envoi d'e-mail ad hoc)");
    }

    private static ConnectorDefinition definition(String type) {
        ConnectorDefinition definition = mock(ConnectorDefinition.class);
        when(definition.getType()).thenReturn(type);
        when(definition.getLabel()).thenReturn(type);
        when(definition.getCapability()).thenReturn(ConnectorCapability.SERVICE);
        when(definition.getConfigSchema()).thenReturn(List.<ConfigField>of());
        return definition;
    }
}
