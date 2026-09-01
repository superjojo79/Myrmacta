package com.flowforge.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ServiceConnector;
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
    private static final ConnectorProperties DEFAULT_PROPERTIES = new ConnectorProperties(null, null, null, null);

    private DocumentStoragePort storagePort;
    private DocumentStorageConnector connector;

    @BeforeEach
    void setUp() {
        storagePort = mock(DocumentStoragePort.class);
        connector = new DocumentStorageConnector(storagePort, DEFAULT_PROPERTIES);
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
        assertThat(result).containsEntry("deleted", true);
    }

    @Test
    void execute_storeExceedingMaxContentSize_throwsWithoutCallingPort() {
        ConnectorProperties smallLimit = new ConnectorProperties(
                null, null, null, new ConnectorProperties.DocumentStorage(4L));
        connector = new DocumentStorageConnector(storagePort, smallLimit);
        String content = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> connector.execute(
                        INSTANCE, Map.of("operation", "STORE", "content", content, "contentType", "text/plain")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxContentSize");

        verify(storagePort, never()).store(any(), any(), any());
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
