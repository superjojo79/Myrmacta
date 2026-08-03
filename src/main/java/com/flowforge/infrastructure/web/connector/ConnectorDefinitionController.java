package com.flowforge.infrastructure.web.connector;

import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/connector-definitions")
class ConnectorDefinitionController {

    private final ConnectorCatalog catalog;

    ConnectorDefinitionController(ConnectorCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    List<ConnectorDefinitionResponse> list() {
        return catalog.listAll().stream()
                .map(ConnectorDefinitionResponse::from)
                .toList();
    }
}
