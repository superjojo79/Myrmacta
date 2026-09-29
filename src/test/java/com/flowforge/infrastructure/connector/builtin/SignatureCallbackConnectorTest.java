package com.flowforge.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConfigFieldType;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.usecase.CallbackConnectorRuntimeImpl;
import com.flowforge.domain.signature.model.SignatureOutcome;
import com.flowforge.domain.signature.port.in.HandleSignatureCallbackUseCase;
import com.flowforge.domain.signature.port.out.SignaturePort;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.infrastructure.connector.registry.ConnectorRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** S-CONN-1, S-OUT-4 — entrée de catalogue « signature » (TODO §72, M8). */
class SignatureCallbackConnectorTest {

    private static final String SECRET = "s".repeat(40);

    private final SignaturePort port = mock(SignaturePort.class);
    private final HandleSignatureCallbackUseCase useCase = mock(HandleSignatureCallbackUseCase.class);
    private final SignatureCallbackConnector connector = new SignatureCallbackConnector(port, useCase);

    private static ConnectorInstanceData instance() {
        return new ConnectorInstanceData(UUID.randomUUID(), "tenant_dev", "sig", "signature", true,
                Map.of("webhookAuthType", "HMAC_SHA256", "webhookSecret", SECRET));
    }

    @Test
    void schemaCarriesTheSecretAsAPasswordFieldAndTheCapabilityIsCallback() {
        assertThat(connector.getType()).isEqualTo("signature");
        assertThat(connector.getCapability()).isEqualTo(ConnectorCapability.CALLBACK);
        List<ConfigField> schema = connector.getConfigSchema();
        assertThat(schema).extracting(ConfigField::name).containsExactly("webhookAuthType", "webhookSecret");
        assertThat(schema.stream().filter(f -> f.name().equals("webhookSecret")).findFirst().orElseThrow().type())
                .isEqualTo(ConfigFieldType.PASSWORD);
    }

    @Test
    void validationRequiresAStrongSecretAndAKnownAuthType() {
        connector.validate(Map.of("webhookAuthType", "HMAC_SHA256", "webhookSecret", SECRET));
        connector.validate(Map.of("webhookAuthType", "SHARED_SECRET_TOKEN", "webhookSecret", SECRET));
        assertThatThrownBy(() -> connector.validate(null)).isInstanceOf(ConnectorValidationException.class);
        assertThatThrownBy(() -> connector.validate(Map.of("webhookSecret", SECRET)))
                .isInstanceOf(ConnectorValidationException.class);
        assertThatThrownBy(() -> connector.validate(Map.of("webhookAuthType", "IP_ALLOWLIST", "webhookSecret", SECRET)))
                .hasMessageContaining("IP_ALLOWLIST");
        assertThatThrownBy(() -> connector.validate(Map.of("webhookAuthType", "NOPE", "webhookSecret", SECRET)))
                .isInstanceOf(ConnectorValidationException.class);
        assertThatThrownBy(() -> connector.validate(Map.of("webhookAuthType", "HMAC_SHA256")))
                .isInstanceOf(ConnectorValidationException.class);
        assertThatThrownBy(() -> connector.validate(Map.of("webhookAuthType", "HMAC_SHA256", "webhookSecret", "court")))
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void theRuntimeDelegatesTheCallbackToTheConnectorInsteadOfCorrelatingAMessage() {
        SignaturePort.SignatureCallbackEvent event =
                new SignaturePort.SignatureCallbackEvent("ref", SignatureOutcome.SIGNED, Map.of());
        when(port.parseCallback(Map.of("k", "v"))).thenReturn(event);
        ConnectorRegistry registry = new ConnectorRegistry(List.of(connector));
        registry.init();
        WorkflowEnginePort engine = mock(WorkflowEnginePort.class);

        new CallbackConnectorRuntimeImpl(registry, engine).onIncomingCallback(instance(), Map.of("k", "v"));

        verify(useCase).apply(event);
        verifyNoInteractions(engine);
    }

    @Test
    void invalidPayloadFromTheAdapterIsARequestError() {
        when(port.parseCallback("junk")).thenThrow(new IllegalArgumentException("bad event"));
        assertThatThrownBy(() -> connector.handleCallback(instance(), "junk"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(useCase);
    }
}
