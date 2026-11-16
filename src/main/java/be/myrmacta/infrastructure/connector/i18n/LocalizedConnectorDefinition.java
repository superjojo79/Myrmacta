package be.myrmacta.infrastructure.connector.i18n;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Définition dont le libellé et le schéma sont déjà résolus dans une langue (D-CONNECTOR-CATALOG-I18N). */
public final class LocalizedConnectorDefinition implements ConnectorDefinition {

    private final String type;
    private final String label;
    private final ConnectorCapability capability;
    private final List<ConfigField> configSchema;
    private final ConnectorDefinition delegate;

    public LocalizedConnectorDefinition(ConnectorDefinition delegate, ConnectorCatalogLocalizer.LocalizedDefinition localized) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(localized, "localized");
        this.type = localized.type();
        this.label = localized.label();
        this.capability = localized.capability();
        this.configSchema = localized.configSchema();
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public ConnectorCapability getCapability() {
        return capability;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return configSchema;
    }

    @Override
    public void validate(Map<String, Object> config) {
        delegate.validate(config);
    }
}
