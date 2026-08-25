package com.flowforge.infrastructure.connector.builtin;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.model.ServiceConnector;
import com.flowforge.infrastructure.notification.OutboundUrlGuard;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;

/**
 * D-SERVICE-HTTP skeleton: deliberately implements only {@link ServiceConnector}, never
 * {@code ConnectorDefinition} — so it can never be picked up by {@code ConnectorRegistry} (which
 * only collects {@code List<ConnectorDefinition>} beans) even if this class were ever annotated
 * {@code @Component} by mistake. Not wired anywhere; exercised only by direct instantiation in
 * tests. Proves {@link OutboundUrlGuard} is reusable for a SERVICE-role outbound call, ahead of
 * activating the {@code http-rest} connector's SERVICE capability (hors V1).
 *
 * <p>Contract for any future caller: the result map must never echo {@code instance.config()} —
 * {@code ExecuteConnectorUseCaseImpl} returns a {@code ServiceConnector.execute} result verbatim
 * as the HTTP response body, so a decrypted secret copied here would leak (D-SECRETS-AUDIT).
 */
public class HttpRestServiceConnector implements ServiceConnector {

    private final OutboundUrlGuard outboundUrlGuard;
    private final HttpClient httpClient;

    public HttpRestServiceConnector(OutboundUrlGuard outboundUrlGuard, HttpClient httpClient) {
        this.outboundUrlGuard = Objects.requireNonNull(outboundUrlGuard, "outboundUrlGuard");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public Map<String, Object> execute(ConnectorInstanceData instance, Map<String, Object> inputParams) {
        Objects.requireNonNull(inputParams, "inputParams");
        Object url = inputParams.get("url");
        if (!(url instanceof String urlString)) {
            throw new IllegalArgumentException("url is required");
        }
        OutboundUrlGuard.PinnedEndpoint pinned = outboundUrlGuard.resolveSafe(urlString);
        HttpRequest request = HttpRequest.newBuilder(URI.create(pinned.requestUrl())).GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("http-rest call failed: " + ex.getMessage(), ex);
        }
        return Map.of("status", response.statusCode(), "body", response.body());
    }
}
