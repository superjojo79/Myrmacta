package be.myrmacta.infrastructure.web.connector;

import be.myrmacta.domain.connector.port.in.ExecuteConnectorUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * BFF SERVICE connector execution (Order 3 chain, authenticated user + CSRF).
 * M2M twin URL: {@link M2mConnectorExecuteController} (DD-EXECUTE-ROUTE).
 */
@RestController
public class ConnectorExecuteController {

    private final TenantProperties tenantProperties;
    private final ExecuteConnectorUseCase executeUseCase;

    public ConnectorExecuteController(
            TenantProperties tenantProperties, ExecuteConnectorUseCase executeUseCase) {
        this.tenantProperties = tenantProperties;
        this.executeUseCase = executeUseCase;
    }

    @PostMapping("/api/connectors/{id}/execute")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public Map<String, Object> execute(
            @PathVariable UUID id,
            @RequestBody(required = false) ExecuteConnectorRequest request,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authentication required");
        }
        Map<String, Object> params =
                request == null ? Map.of() : request.toInputParams();
        return executeUseCase.execute(id, tenantProperties.id(), params);
    }
}
