package com.flowforge.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ServiceConnector;
import com.flowforge.domain.notification.port.out.EmailPort;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SmtpConnectorTest {

    private static final ConnectorInstanceData INSTANCE = new ConnectorInstanceData(
            UUID.randomUUID(), "tenant_dev", "Smtp smoke", "smtp", true, Map.of());

    private EmailPort emailPort;
    private SmtpConnector connector;

    @BeforeEach
    void setUp() {
        emailPort = mock(EmailPort.class);
        connector = new SmtpConnector(emailPort);
    }

    @Test
    void implementsConnectorDefinitionAndServiceConnector() {
        assertThat(connector).isInstanceOf(ConnectorDefinition.class);
        assertThat(connector).isInstanceOf(ServiceConnector.class);
    }

    @Test
    void getType_returnsSmtp() {
        assertThat(connector.getType()).isEqualTo("smtp");
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
    void execute_sendsEmailViaEmailPort() {
        Map<String, Object> result = connector.execute(
                INSTANCE, Map.of("to", "user@example.com", "subject", "Subject", "body", "Body"));

        verify(emailPort).send("user@example.com", "Subject", "Body");
        assertThat(result).containsEntry("sent", true);
    }

    @Test
    void execute_toBlank_throwsWithoutCallingEmailPort() {
        assertThatThrownBy(() -> connector.execute(
                        INSTANCE, Map.of("to", "   ", "subject", "Subject", "body", "Body")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("to");

        verify(emailPort, never()).send(any(), any(), any());
    }

    @Test
    void execute_toMissingAtSign_throwsWithoutCallingEmailPort() {
        assertThatThrownBy(() -> connector.execute(
                        INSTANCE, Map.of("to", "not-an-email", "subject", "Subject", "body", "Body")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("to");

        verify(emailPort, never()).send(any(), any(), any());
    }

    @Test
    void execute_missingSubject_throwsWithoutCallingEmailPort() {
        assertThatThrownBy(() -> connector.execute(
                        INSTANCE, Map.of("to", "user@example.com", "body", "Body")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject");

        verify(emailPort, never()).send(any(), any(), any());
    }

    @Test
    void execute_missingBody_throwsWithoutCallingEmailPort() {
        assertThatThrownBy(() -> connector.execute(
                        INSTANCE, Map.of("to", "user@example.com", "subject", "Subject")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body");

        verify(emailPort, never()).send(any(), any(), any());
    }

    @Test
    void execute_neverEchoesInstanceConfigInResult() {
        ConnectorInstanceData instanceWithConfig = new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "Smtp smoke", "smtp", true,
                Map.of("secret", "should-never-be-echoed-back"));

        Map<String, Object> result = connector.execute(
                instanceWithConfig, Map.of("to", "user@example.com", "subject", "Subject", "body", "Body"));

        assertThat(result.values()).noneMatch(value -> "should-never-be-echoed-back".equals(value));
    }
}
