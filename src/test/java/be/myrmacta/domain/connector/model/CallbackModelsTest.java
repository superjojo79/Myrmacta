package be.myrmacta.domain.connector.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CallbackModelsTest {

    @Test
    void connectorCapability_callback_existsAndImpliesNeitherTriggerNorService() {
        assertThat(ConnectorCapability.CALLBACK).isNotNull();
        assertThat(ConnectorCapability.CALLBACK.includesTrigger()).isFalse();
        assertThat(ConnectorCapability.CALLBACK.includesService()).isFalse();
    }

    @Test
    void connectorCapability_hasFourValues() {
        assertThat(ConnectorCapability.values()).hasSize(4);
    }

    @Test
    void callbackResolution_copiesVariablesDefensively() {
        var variables = new java.util.HashMap<String, Object>();
        variables.put("orderId", "42");
        var resolution = new CallbackResolution("callbackReceived", "correlationKey", "42", variables);
        variables.put("mutatedAfterConstruction", "x");

        assertThat(resolution.messageName()).isEqualTo("callbackReceived");
        assertThat(resolution.correlationVariableName()).isEqualTo("correlationKey");
        assertThat(resolution.correlationValue()).isEqualTo("42");
        assertThat(resolution.variables())
                .containsEntry("orderId", "42")
                .doesNotContainKey("mutatedAfterConstruction");
    }

    @Test
    void callbackResolution_nullVariables_becomeEmpty() {
        var resolution = new CallbackResolution("m", "k", "v", null);
        assertThat(resolution.variables()).isEmpty();
    }

    @Test
    void callbackResolution_requiresMessageNameAndCorrelationVariableName() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new CallbackResolution(null, "k", "v", Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("messageName");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new CallbackResolution("m", null, "v", Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("correlationVariableName");
    }

    @Test
    void callbackNoMatchingInstanceException_carriesCorrelationContext() {
        var ex = new CallbackNoMatchingInstanceException("callbackReceived", "correlationKey", "42");

        assertThat(ex.messageName()).isEqualTo("callbackReceived");
        assertThat(ex.correlationKey()).isEqualTo("correlationKey");
        assertThat(ex.correlationValue()).isEqualTo("42");
        assertThat(ex.getMessage())
                .contains("callbackReceived")
                .contains("correlationKey")
                .contains("42");
    }

    @Test
    void callbackAmbiguousCorrelationException_carriesMatchCount() {
        var ex = new CallbackAmbiguousCorrelationException("callbackReceived", "correlationKey", "42", 3);

        assertThat(ex.messageName()).isEqualTo("callbackReceived");
        assertThat(ex.correlationKey()).isEqualTo("correlationKey");
        assertThat(ex.correlationValue()).isEqualTo("42");
        assertThat(ex.matchCount()).isEqualTo(3);
        assertThat(ex.getMessage()).contains("3");
    }
}
