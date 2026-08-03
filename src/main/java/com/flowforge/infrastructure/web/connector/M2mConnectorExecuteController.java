package com.flowforge.infrastructure.web.connector;

import com.flowforge.domain.connector.port.in.ExecuteConnectorUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * M2M SERVICE connector execution (Order 1 chain, JWT {@code API_SERVICE}).
 * BFF twin URL: {@link ConnectorExecuteController} (DD-EXECUTE-ROUTE).
 */
@RestController
public class M2mConnectorExecuteController {

    private final TenantProperties tenantProperties;
    private final ExecuteConnectorUseCase executeUseCase;

    public M2mConnectorExecuteController(
            TenantProperties tenantProperties, ExecuteConnectorUseCase executeUseCase) {
        this.tenantProperties = tenantProperties;
        this.executeUseCase = executeUseCase;
    }

    @PostMapping("/api/m2m/connectors/{id}/execute")
    @PreAuthorize("hasAuthority('API_SERVICE')")
    public Map<String, Object> execute(
            @PathVariable UUID id,
            @RequestBody(required = false) ExecuteConnectorRequest request) {
        Map<String, Object> params =
                request == null ? Map.of() : request.toInputParams();
        return executeUseCase.execute(id, tenantProperties.id(), params);
    }
}
