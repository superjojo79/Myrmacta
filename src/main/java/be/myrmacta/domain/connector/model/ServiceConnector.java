package be.myrmacta.domain.connector.model;

import java.util.Map;

public interface ServiceConnector {

    Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> inputParams);

    /**
     * Variante qui reçoit l'origine de l'appel (TODO §101). Par défaut, l'origine est ignorée : seul un connecteur dont le
     * comportement en dépend la surcharge ({@code document-storage}).
     */
    default Map<String, Object> execute(
            ConnectorInstanceData instance, Map<String, Object> inputParams, ConnectorCallContext context) {
        return execute(instance, inputParams);
    }
}
