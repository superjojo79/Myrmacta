package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.domain.shared.PageQuery;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListConnectorDefinitionsToolTest {

    private ConnectorCatalog catalog;
    private ListConnectorDefinitionsTool tool;

    @BeforeEach
    void setUp() {
        catalog = mock(ConnectorCatalog.class);
        tool = new ListConnectorDefinitionsTool(catalog);
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

    private static ConnectorDefinition definition(String type) {
        ConnectorDefinition definition = mock(ConnectorDefinition.class);
        when(definition.getType()).thenReturn(type);
        when(definition.getLabel()).thenReturn(type);
        when(definition.getCapability()).thenReturn(ConnectorCapability.SERVICE);
        when(definition.getConfigSchema()).thenReturn(List.<ConfigField>of());
        return definition;
    }
}
