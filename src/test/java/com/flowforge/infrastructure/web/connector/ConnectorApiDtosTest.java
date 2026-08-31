package com.flowforge.infrastructure.web.connector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * TODO §48 (connecteur-http-rest-service) : {@code toInputParams()} sert les deux familles de
 * connecteurs SERVICE (flowable-signal, http-rest) — chaque champ optionnel doit être omis quand
 * absent/blanc, jamais posé avec une valeur vide.
 */
class ConnectorApiDtosTest {

    @Test
    void toInputParams_allFieldsBlankOrNull_returnsEmptyMap() {
        var request = new ExecuteConnectorRequest(null, "  ", null, "", "  ", null, null);

        assertThat(request.toInputParams()).isEmpty();
    }

    @Test
    void toInputParams_signalNamePresent_isIncluded() {
        var request = new ExecuteConnectorRequest("sig", null, null, null, null, null, null);

        assertThat(request.toInputParams()).containsEntry("signalName", "sig");
    }

    @Test
    void toInputParams_processInstanceIdPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(null, "proc-1", null, null, null, null, null);

        assertThat(request.toInputParams()).containsEntry("processInstanceId", "proc-1");
    }

    @Test
    void toInputParams_variablesPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(null, null, Map.of("k", "v"), null, null, null, null);

        assertThat(request.toInputParams()).containsEntry("variables", Map.of("k", "v"));
    }

    @Test
    void toInputParams_urlPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(null, null, null, "https://example.com", null, null, null);

        assertThat(request.toInputParams()).containsEntry("url", "https://example.com");
    }

    @Test
    void toInputParams_methodPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(null, null, null, null, "POST", null, null);

        assertThat(request.toInputParams()).containsEntry("method", "POST");
    }

    @Test
    void toInputParams_bodyPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(null, null, null, null, null, "raw body", null);

        assertThat(request.toInputParams()).containsEntry("body", "raw body");
    }

    @Test
    void toInputParams_headersPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(
                null, null, null, null, null, null, Map.of("X-Custom", "value"));

        assertThat(request.toInputParams()).containsEntry("headers", Map.of("X-Custom", "value"));
    }
}
