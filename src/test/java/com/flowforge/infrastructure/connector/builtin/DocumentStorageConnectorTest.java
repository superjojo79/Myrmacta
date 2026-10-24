package com.flowforge.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.ConnectorCallContext;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ServiceConnector;
import com.flowforge.domain.connector.port.out.ConnectorStoredObjectRepository;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.infrastructure.config.ConnectorProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentStorageConnectorTest {

    private static final ConnectorInstanceData INSTANCE = new ConnectorInstanceData(
            UUID.randomUUID(), "tenant_dev", "Storage smoke", "document-storage", true, Map.of());
    private static final ConnectorProperties DEFAULT_PROPERTIES =
            new ConnectorProperties(null, null, null, null, null, null);

    private static final UUID DOSSIER = UUID.randomUUID();

    private DocumentStoragePort storagePort;
    private ConnectorStoredObjectRepository storedObjects;
    private DocumentStorageConnector connector;

    @BeforeEach
    void setUp() {
        storagePort = mock(DocumentStoragePort.class);
        storedObjects = mock(ConnectorStoredObjectRepository.class);
        connector = new DocumentStorageConnector(storagePort, DEFAULT_PROPERTIES, storedObjects);
    }

    @Test
    void implementsConnectorDefinitionAndServiceConnector() {
        assertThat(connector).isInstanceOf(ConnectorDefinition.class);
        assertThat(connector).isInstanceOf(ServiceConnector.class);
    }

    @Test
    void getType_returnsDocumentStorage() {
        assertThat(connector.getType()).isEqualTo("document-storage");
    }

    @Test
    void getCapability_isService() {
        assertThat(connector.getCapability()).isEqualTo(ConnectorCapability.SERVICE);
    }

    @Test
    void getConfigSchema_isEmpty() {
        assertThat(connector.getConfigSchema()).isEmpty();
    }

    @Test
    void execute_store_callsPortAndReturnsStorageRef() {
        String content = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
        when(storagePort.store("tenant_dev", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)))
                .thenReturn("tenant_dev/abc123");

        Map<String, Object> result = connector.execute(
                INSTANCE, Map.of("operation", "STORE", "content", content, "contentType", "text/plain"));

        assertThat(result).containsEntry("storageRef", "tenant_dev/abc123");
        verify(storedObjects).register("tenant_dev", "tenant_dev/abc123", null);
    }

    // --- TODO §101 (issue #205) : portée du dossier depuis un process -------------------------------------------

    @Test
    void execute_storeFromAProcess_registersTheObjectForTheDossier() {
        String content = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
        when(storagePort.store(any(), any(), any())).thenReturn("tenant_dev/abc123");

        connector.execute(INSTANCE, Map.of("operation", "STORE", "content", content, "contentType", "text/plain"),
                ConnectorCallContext.process(DOSSIER));

        verify(storedObjects).register("tenant_dev", "tenant_dev/abc123", DOSSIER);
    }

    @Test
    void execute_fromAProcess_onAnObjectStoredForTheDossier_isAllowed() {
        when(storedObjects.isStoredFor("tenant_dev", "tenant_dev/abc123", DOSSIER)).thenReturn(true);
        when(storagePort.retrieve("tenant_dev", "tenant_dev/abc123")).thenReturn(new byte[] {1});

        assertThat(connector.execute(INSTANCE, Map.of("operation", "RETRIEVE", "storageRef", "tenant_dev/abc123"),
                ConnectorCallContext.process(DOSSIER))).containsKey("content");
        connector.execute(INSTANCE, Map.of("operation", "DELETE", "storageRef", "tenant_dev/abc123"),
                ConnectorCallContext.process(DOSSIER));

        verify(storagePort).delete("tenant_dev", "tenant_dev/abc123");
        verify(storedObjects).forget("tenant_dev", "tenant_dev/abc123");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"RETRIEVE", "EXISTS", "DELETE"})
    void execute_fromAProcess_onAnyOtherObject_isRefusedWithoutTouchingStorage(String operation) {
        assertThatThrownBy(() -> connector.execute(INSTANCE, Map.of("operation", operation, "storageRef",
                        "tenant_dev/other"), ConnectorCallContext.process(DOSSIER)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not stored by the document-storage connector for this dossier");

        verify(storagePort, never()).retrieve(any(), any());
        verify(storagePort, never()).exists(any(), any());
        verify(storagePort, never()).delete(any(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"STORE", "RETRIEVE"})
    void execute_fromAProcessWithoutDossier_isRefused(String operation) {
        String content = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> connector.execute(INSTANCE, Map.of("operation", operation, "storageRef", "tenant_dev/x",
                        "content", content, "contentType", "text/plain"), ConnectorCallContext.process(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a dossier");

        verify(storagePort, never()).store(any(), any(), any());
        verify(storagePort, never()).retrieve(any(), any());
    }

    @Test
    void execute_retrieve_callsPortAndReturnsBase64Content() {
        when(storagePort.retrieve("tenant_dev", "tenant_dev/abc123"))
                .thenReturn("hello".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result =
                connector.execute(INSTANCE, Map.of("operation", "RETRIEVE", "storageRef", "tenant_dev/abc123"));

        assertThat(result.get("content"))
                .isEqualTo(Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void execute_exists_delegatesToPort() {
        when(storagePort.exists("tenant_dev", "tenant_dev/abc123")).thenReturn(true);

        Map<String, Object> result =
                connector.execute(INSTANCE, Map.of("operation", "EXISTS", "storageRef", "tenant_dev/abc123"));

        assertThat(result).containsEntry("exists", true);
    }

    @Test
    void execute_delete_delegatesToPort() {
        Map<String, Object> result =
                connector.execute(INSTANCE, Map.of("operation", "DELETE", "storageRef", "tenant_dev/abc123"));

        verify(storagePort).delete("tenant_dev", "tenant_dev/abc123");
        verify(storedObjects).forget("tenant_dev", "tenant_dev/abc123");
        assertThat(result).containsEntry("deleted", true);
    }

    @Test
    void execute_storeExceedingMaxContentSize_throwsWithoutCallingPort() {
        ConnectorProperties smallLimit = new ConnectorProperties(
                null, null, null, new ConnectorProperties.DocumentStorage(4L), null, null);
        connector = new DocumentStorageConnector(storagePort, smallLimit, storedObjects);
        String content = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> connector.execute(
                        INSTANCE, Map.of("operation", "STORE", "content", content, "contentType", "text/plain")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxContentSize");

        verify(storagePort, never()).store(any(), any(), any());
    }

    @Test
    void execute_storeEncodedPayloadLargerThanCap_throwsWithoutCallingPort() {
        ConnectorProperties smallLimit = new ConnectorProperties(
                null, null, null, new ConnectorProperties.DocumentStorage(4L), null, null);
        connector = new DocumentStorageConnector(storagePort, smallLimit, storedObjects);

        assertThatThrownBy(() -> connector.execute(
                        INSTANCE,
                        Map.of(
                                "operation",
                                "STORE",
                                "content",
                                "A".repeat(64),
                                "contentType",
                                "text/plain")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxContentSize");

        verify(storagePort, never()).store(any(), any(), any());
    }

    @Test
    void execute_retrieveExceedingMaxContentSize_throws() {
        ConnectorProperties smallLimit = new ConnectorProperties(
                null, null, null, new ConnectorProperties.DocumentStorage(4L), null, null);
        connector = new DocumentStorageConnector(storagePort, smallLimit, storedObjects);
        when(storagePort.retrieve("tenant_dev", "tenant_dev/abc123")).thenReturn(new byte[10]);

        assertThatThrownBy(() -> connector.execute(
                        INSTANCE, Map.of("operation", "RETRIEVE", "storageRef", "tenant_dev/abc123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxContentSize");
    }

    @Test
    void execute_missingOperation_throws() {
        assertThatThrownBy(() -> connector.execute(INSTANCE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operation");
    }

    @Test
    void execute_invalidOperation_throws() {
        assertThatThrownBy(() -> connector.execute(INSTANCE, Map.of("operation", "PURGE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operation");
    }

    @Test
    void execute_retrieveWithoutStorageRef_throwsWithoutCallingPort() {
        assertThatThrownBy(() -> connector.execute(INSTANCE, Map.of("operation", "RETRIEVE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storageRef");

        verify(storagePort, never()).retrieve(any(), any());
    }

    @Test
    void execute_storeWithoutContent_throwsWithoutCallingPort() {
        assertThatThrownBy(() -> connector.execute(
                        INSTANCE, Map.of("operation", "STORE", "contentType", "text/plain")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");

        verify(storagePort, never()).store(any(), any(), any());
    }
}
