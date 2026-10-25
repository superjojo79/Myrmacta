package be.myrmacta.infrastructure.web.connector;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.in.ManageConnectorInstanceUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.web.ListSorts;
import be.myrmacta.infrastructure.web.PageQueryParser;
import be.myrmacta.infrastructure.web.PageResponse;
import be.myrmacta.infrastructure.web.dossier.AccessContextFactory;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CRUD administrateur des connecteurs (BFF Order 3, {@code TENANT_ADMIN} + CSRF). */
@RestController
@RequestMapping("/api/connectors")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class ConnectorAdminController {

    private final TenantProperties tenantProperties;
    private final ManageConnectorInstanceUseCase manageUseCase;
    private final ConnectorCatalog connectorCatalog;
    private final AccessContextFactory accessContextFactory;

    public ConnectorAdminController(
            TenantProperties tenantProperties,
            ManageConnectorInstanceUseCase manageUseCase,
            ConnectorCatalog connectorCatalog,
            AccessContextFactory accessContextFactory) {
        this.tenantProperties = tenantProperties;
        this.manageUseCase = manageUseCase;
        this.connectorCatalog = connectorCatalog;
        this.accessContextFactory = accessContextFactory;
    }

    @PostMapping
    public ResponseEntity<ConnectorResponse> create(
            @RequestBody CreateConnectorRequest request, Authentication authentication) {
        ConnectorInstanceData created = manageUseCase.create(
                new ManageConnectorInstanceUseCase.CreateCommand(
                        tenantProperties.id(),
                        request.name(),
                        request.connectorType(),
                        request.enabled() == null || request.enabled(),
                        request.config() == null ? Map.of() : request.config()),
                accessContextFactory.user(authentication));
        return ResponseEntity
                .created(URI.create("/api/connectors/" + created.id()))
                .body(ConnectorResponse.from(created, connectorCatalog));
    }

    @GetMapping
    public PageResponse<ConnectorResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            Authentication authentication) {
        return PageResponse.from(manageUseCase
                .list(
                        tenantProperties.id(),
                        PageQueryParser.parse(page, size, sort, ListSorts.CONNECTORS),
                        accessContextFactory.user(authentication))
                .map(data -> ConnectorResponse.from(data, connectorCatalog)));
    }

    @GetMapping("/{id}")
    public ConnectorResponse get(@PathVariable UUID id, Authentication authentication) {
        return ConnectorResponse.from(
                manageUseCase.get(id, tenantProperties.id(), accessContextFactory.user(authentication)),
                connectorCatalog);
    }

    @PatchMapping("/{id}")
    public ConnectorResponse update(
            @PathVariable UUID id, @RequestBody UpdateConnectorRequest request, Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        ConnectorInstanceData updated = manageUseCase.update(
                new ManageConnectorInstanceUseCase.UpdateCommand(
                        id,
                        tenantProperties.id(),
                        request.name(),
                        request.enabled(),
                        request.config(),
                        Boolean.TRUE.equals(request.restore())),
                accessContextFactory.user(authentication));
        return ConnectorResponse.from(updated, connectorCatalog);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDelete(@PathVariable UUID id, Authentication authentication) {
        manageUseCase.softDelete(id, tenantProperties.id(), accessContextFactory.user(authentication));
        return ResponseEntity.noContent().build();
    }

}
