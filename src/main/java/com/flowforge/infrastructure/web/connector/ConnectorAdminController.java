package com.flowforge.infrastructure.web.connector;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD administrateur des connecteurs (BFF Order 3, {@code TENANT_ADMIN} + CSRF). */
@RestController
@RequestMapping("/api/connectors")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class ConnectorAdminController {

    private final TenantProperties tenantProperties;
    private final ManageConnectorInstanceUseCase manageUseCase;

    public ConnectorAdminController(
            TenantProperties tenantProperties, ManageConnectorInstanceUseCase manageUseCase) {
        this.tenantProperties = tenantProperties;
        this.manageUseCase = manageUseCase;
    }

    @PostMapping
    public ResponseEntity<ConnectorResponse> create(@RequestBody CreateConnectorRequest request) {
        ConnectorInstanceData created = manageUseCase.create(new ManageConnectorInstanceUseCase.CreateCommand(
                tenantProperties.id(),
                request.name(),
                request.connectorType(),
                request.enabled() == null || request.enabled(),
                request.config() == null ? Map.of() : request.config()));
        return ResponseEntity
                .created(URI.create("/api/connectors/" + created.id()))
                .body(ConnectorResponse.from(created));
    }

    @GetMapping
    public List<ConnectorResponse> list() {
        return manageUseCase.list(tenantProperties.id()).stream()
                .map(ConnectorResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ConnectorResponse get(@PathVariable UUID id) {
        return ConnectorResponse.from(manageUseCase.get(id, tenantProperties.id()));
    }

    @PatchMapping("/{id}")
    public ConnectorResponse update(
            @PathVariable UUID id, @RequestBody(required = false) UpdateConnectorRequest request) {
        if (request == null) {
            return ConnectorResponse.from(manageUseCase.restore(id, tenantProperties.id()));
        }
        ConnectorInstanceData updated = manageUseCase.update(new ManageConnectorInstanceUseCase.UpdateCommand(
                id,
                tenantProperties.id(),
                request.name(),
                request.enabled(),
                request.config(),
                Boolean.TRUE.equals(request.restore())));
        return ConnectorResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable UUID id) {
        manageUseCase.softDelete(id, tenantProperties.id());
        return ResponseEntity.noContent().build();
    }

}
