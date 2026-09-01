package com.flowforge.infrastructure.connector.builtin;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ServiceConnector;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.infrastructure.config.ConnectorProperties;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Reads/writes content from a {@code serviceTask} via {@link DocumentStoragePort} (already
 * agnostic S3/filesystem), for content unrelated to a {@code Dossier}/{@code DossierElement} —
 * that case is already covered by the existing attachment feature. No automatic purge: the BPMN
 * process is responsible for deleting a {@code storageRef} it no longer needs
 * (D-STORAGE-REF-LIFECYCLE, assumed choice).
 */
@Component
public class DocumentStorageConnector implements ConnectorDefinition, ServiceConnector {

    private static final Set<String> SUPPORTED_OPERATIONS = Set.of("STORE", "RETRIEVE", "EXISTS", "DELETE");

    private final DocumentStoragePort documentStoragePort;
    private final ConnectorProperties connectorProperties;

    public DocumentStorageConnector(
            DocumentStoragePort documentStoragePort, ConnectorProperties connectorProperties) {
        this.documentStoragePort = Objects.requireNonNull(documentStoragePort, "documentStoragePort");
        this.connectorProperties = Objects.requireNonNull(connectorProperties, "connectorProperties");
    }

    @Override
    public String getType() {
        return "document-storage";
    }

    @Override
    public String getLabel() {
        return "Document storage (store/retrieve/exists/delete)";
    }

    @Override
    public ConnectorCapability getCapability() {
        return ConnectorCapability.SERVICE;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
        return List.of();
    }

    @Override
    public void validate(Map<String, Object> config) {
        // No per-instance config — reuses the application-wide DocumentStoragePort.
    }

    @Override
    public Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> inputParams) {
        Objects.requireNonNull(inputParams, "inputParams");
        String operation = parseOperation(inputParams);
        String tenantId = instance.tenantId();
        return switch (operation) {
            case "STORE" -> store(tenantId, inputParams);
            case "RETRIEVE" -> retrieve(tenantId, inputParams);
            case "EXISTS" -> Map.of("exists", documentStoragePort.exists(tenantId, requireStorageRef(inputParams)));
            case "DELETE" -> {
                documentStoragePort.delete(tenantId, requireStorageRef(inputParams));
                yield Map.of("deleted", true);
            }
            default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
        };
    }

    private Map<String, Object> store(String tenantId, Map<String, Object> inputParams) {
        Object content = inputParams.get("content");
        if (!(content instanceof String contentBase64) || contentBase64.isBlank()) {
            throw new IllegalArgumentException("content is required for STORE");
        }
        Object contentType = inputParams.get("contentType");
        if (!(contentType instanceof String contentTypeString) || contentTypeString.isBlank()) {
            throw new IllegalArgumentException("contentType is required for STORE");
        }
        byte[] bytes = Base64.getDecoder().decode(contentBase64);
        long maxContentSize = connectorProperties.documentStorage().maxContentSize();
        if (bytes.length > maxContentSize) {
            throw new IllegalArgumentException(
                    "content exceeds maxContentSize (" + maxContentSize + " bytes)");
        }
        String storageRef = documentStoragePort.store(tenantId, contentTypeString, bytes);
        return Map.of("storageRef", storageRef);
    }

    private Map<String, Object> retrieve(String tenantId, Map<String, Object> inputParams) {
        byte[] bytes = documentStoragePort.retrieve(tenantId, requireStorageRef(inputParams));
        return Map.of("content", Base64.getEncoder().encodeToString(bytes));
    }

    private static String requireStorageRef(Map<String, Object> inputParams) {
        Object storageRef = inputParams.get("storageRef");
        if (!(storageRef instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("storageRef is required");
        }
        return text;
    }

    private static String parseOperation(Map<String, Object> inputParams) {
        Object operation = inputParams.get("operation");
        if (!(operation instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("operation is required");
        }
        String normalized = text.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_OPERATIONS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported operation: " + text);
        }
        return normalized;
    }
}
