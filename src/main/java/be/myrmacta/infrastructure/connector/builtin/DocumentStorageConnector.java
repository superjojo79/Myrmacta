package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.ConfigField;
import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.model.ConnectorCapability;
import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ServiceConnector;
import be.myrmacta.domain.connector.port.out.ConnectorStoredObjectRepository;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.infrastructure.config.ConnectorProperties;
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
 *
 * <p>Issue #205 (TODO §101, D-STORAGE-REF-DOSSIER) : {@code connectorInput} est posable à la complétion d'une tâche. Depuis
 * un process, {@code RETRIEVE}, {@code EXISTS} et {@code DELETE} n'acceptent donc qu'un objet que ce connecteur a écrit
 * pour le dossier de l'exécution ({@link ConnectorStoredObjectRepository}) : ni une pièce d'un dossier, ni le contenu
 * stocké pour un autre dossier. Un appel direct ({@code TENANT_ADMIN}, {@code API_SERVICE}) garde la portée du tenant.
 */
@Component
public class DocumentStorageConnector implements ConnectorDefinition, ServiceConnector {

    private static final Set<String> SUPPORTED_OPERATIONS = Set.of("STORE", "RETRIEVE", "EXISTS", "DELETE");

    private final DocumentStoragePort documentStoragePort;
    private final ConnectorProperties connectorProperties;
    private final ConnectorStoredObjectRepository storedObjects;

    public DocumentStorageConnector(
            DocumentStoragePort documentStoragePort,
            ConnectorProperties connectorProperties,
            ConnectorStoredObjectRepository storedObjects) {
        this.documentStoragePort = Objects.requireNonNull(documentStoragePort, "documentStoragePort");
        this.connectorProperties = Objects.requireNonNull(connectorProperties, "connectorProperties");
        this.storedObjects = Objects.requireNonNull(storedObjects, "storedObjects");
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
        return execute(instance, inputParams, ConnectorCallContext.direct());
    }

    @Override
    public Map<String, Object> execute(
            ConnectorInstanceData instance, Map<String, Object> inputParams, ConnectorCallContext context) {
        Objects.requireNonNull(inputParams, "inputParams");
        Objects.requireNonNull(context, "context");
        String operation = parseOperation(inputParams);
        String tenantId = instance.tenantId();
        if ("STORE".equals(operation)) {
            return store(tenantId, inputParams, context);
        }
        String storageRef = requireStorageRef(inputParams);
        assertReachable(tenantId, storageRef, context);
        return switch (operation) {
            case "RETRIEVE" -> retrieve(tenantId, storageRef);
            case "EXISTS" -> Map.of("exists", documentStoragePort.exists(tenantId, storageRef));
            case "DELETE" -> {
                documentStoragePort.delete(tenantId, storageRef);
                storedObjects.forget(tenantId, storageRef);
                yield Map.of("deleted", true);
            }
            default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
        };
    }

    /** TODO §101 : depuis un process, seul un objet écrit par ce connecteur pour le dossier de l'exécution. */
    private void assertReachable(String tenantId, String storageRef, ConnectorCallContext context) {
        if (!context.fromProcess()) {
            return;
        }
        if (context.dossierId() == null) {
            throw new IllegalArgumentException("document-storage requires a dossier when called from a process");
        }
        if (!storedObjects.isStoredFor(tenantId, storageRef, context.dossierId())) {
            throw new IllegalArgumentException(
                    "storageRef was not stored by the document-storage connector for this dossier");
        }
    }

    private Map<String, Object> store(String tenantId, Map<String, Object> inputParams, ConnectorCallContext context) {
        if (context.fromProcess() && context.dossierId() == null) {
            throw new IllegalArgumentException("document-storage requires a dossier when called from a process");
        }
        Object content = inputParams.get("content");
        if (!(content instanceof String contentBase64) || contentBase64.isBlank()) {
            throw new IllegalArgumentException("content is required for STORE");
        }
        Object contentType = inputParams.get("contentType");
        if (!(contentType instanceof String contentTypeString) || contentTypeString.isBlank()) {
            throw new IllegalArgumentException("contentType is required for STORE");
        }
        long maxContentSize = connectorProperties.documentStorage().maxContentSize();
        long maxEncodedLength = 4L * ((maxContentSize + 2) / 3);
        if (contentBase64.length() > maxEncodedLength) {
            throw new IllegalArgumentException(
                    "content exceeds maxContentSize (" + maxContentSize + " bytes)");
        }
        byte[] bytes = Base64.getDecoder().decode(contentBase64);
        if (bytes.length > maxContentSize) {
            throw new IllegalArgumentException(
                    "content exceeds maxContentSize (" + maxContentSize + " bytes)");
        }
        String storageRef = documentStoragePort.store(tenantId, contentTypeString, bytes);
        storedObjects.register(tenantId, storageRef, context.dossierId());
        return Map.of("storageRef", storageRef);
    }

    private Map<String, Object> retrieve(String tenantId, String storageRef) {
        byte[] bytes = documentStoragePort.retrieve(tenantId, storageRef);
        long maxContentSize = connectorProperties.documentStorage().maxContentSize();
        if (bytes.length > maxContentSize) {
            throw new IllegalArgumentException(
                    "retrieved content exceeds maxContentSize (" + maxContentSize + " bytes)");
        }
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
