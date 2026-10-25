package be.myrmacta.infrastructure.web.connector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * TODO §48 (connecteur-http-rest-service) / TODO §50 (connecteurs-hors-v1) :
 * {@code toInputParams()} sert toutes les familles de connecteurs SERVICE (flowable-signal,
 * http-rest, smtp, document-storage) — chaque champ optionnel doit être omis quand absent/blanc,
 * jamais posé avec une valeur vide.
 */
class ConnectorApiDtosTest {

    @Test
    void toInputParams_allFieldsBlankOrNull_returnsEmptyMap() {
        var request = new ExecuteConnectorRequest(
                null, "  ", null, "", "  ", null, null, "  ", null, "", null, "  ", null);

        assertThat(request.toInputParams()).isEmpty();
    }

    @Test
    void toInputParams_signalNamePresent_isIncluded() {
        var request = new ExecuteConnectorRequest(
                "sig", null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(request.toInputParams()).containsEntry("signalName", "sig");
    }

    @Test
    void toInputParams_processInstanceIdPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(
                null, "proc-1", null, null, null, null, null, null, null, null, null, null, null);

        assertThat(request.toInputParams()).containsEntry("processInstanceId", "proc-1");
    }

    @Test
    void toInputParams_variablesPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(
                null, null, Map.of("k", "v"), null, null, null, null, null, null, null, null, null, null);

        assertThat(request.toInputParams()).containsEntry("variables", Map.of("k", "v"));
    }

    @Test
    void toInputParams_urlPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(
                null, null, null, "https://example.com", null, null, null, null, null, null, null, null, null);

        assertThat(request.toInputParams()).containsEntry("url", "https://example.com");
    }

    @Test
    void toInputParams_methodPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(
                null, null, null, null, "POST", null, null, null, null, null, null, null, null);

        assertThat(request.toInputParams()).containsEntry("method", "POST");
    }

    @Test
    void toInputParams_bodyPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(
                null, null, null, null, null, "raw body", null, null, null, null, null, null, null);

        assertThat(request.toInputParams()).containsEntry("body", "raw body");
    }

    @Test
    void toInputParams_headersPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(
                null, null, null, null, null, null, Map.of("X-Custom", "value"),
                null, null, null, null, null, null);

        assertThat(request.toInputParams()).containsEntry("headers", Map.of("X-Custom", "value"));
    }

    @Test
    void toInputParams_toPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(
                null, null, null, null, null, null, null, "user@example.com", null, null, null, null, null);

        assertThat(request.toInputParams()).containsEntry("to", "user@example.com");
    }

    @Test
    void toInputParams_subjectPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(
                null, null, null, null, null, null, null, null, "Subject", null, null, null, null);

        assertThat(request.toInputParams()).containsEntry("subject", "Subject");
    }

    @Test
    void toInputParams_operationPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(
                null, null, null, null, null, null, null, null, null, "STORE", null, null, null);

        assertThat(request.toInputParams()).containsEntry("operation", "STORE");
    }

    @Test
    void toInputParams_storageRefPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(
                null, null, null, null, null, null, null, null, null, null, "tenant/ref", null, null);

        assertThat(request.toInputParams()).containsEntry("storageRef", "tenant/ref");
    }

    @Test
    void toInputParams_contentPresent_isIncluded() {
        var request = new ExecuteConnectorRequest(
                null, null, null, null, null, null, null, null, null, null, null, "aGVsbG8=", null);

        assertThat(request.toInputParams()).containsEntry("content", "aGVsbG8=");
    }

    @Test
    void toInputParams_contentTypePresent_isIncluded() {
        var request = new ExecuteConnectorRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, "text/plain");

        assertThat(request.toInputParams()).containsEntry("contentType", "text/plain");
    }
}
