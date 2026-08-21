package com.flowforge.infrastructure.web.connector;

import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.infrastructure.web.ListSorts;
import com.flowforge.infrastructure.web.PageQueryParser;
import com.flowforge.infrastructure.web.PageResponse;
import java.util.Comparator;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    PageResponse<ConnectorDefinitionResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var sorted = catalog.listAll().stream()
                .sorted(Comparator.comparing(ConnectorDefinition::getType))
                .toList();
        var query = PageQueryParser.parse(page, size, sort, ListSorts.CONNECTOR_DEFINITIONS);
        return PageResponse.from(PageResult.slice(sorted, query).map(ConnectorDefinitionResponse::from));
    }
}
