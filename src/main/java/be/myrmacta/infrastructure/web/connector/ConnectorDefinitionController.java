package be.myrmacta.infrastructure.web.connector;

import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.connector.i18n.ConnectorCatalogLocalizer;
import be.myrmacta.infrastructure.web.ListSorts;
import be.myrmacta.infrastructure.web.PageQueryParser;
import be.myrmacta.infrastructure.web.PageResponse;
import java.util.Comparator;
import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.LocaleResolver;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/connector-definitions")
class ConnectorDefinitionController {

    private final ConnectorCatalog catalog;
    private final ConnectorCatalogLocalizer localizer;
    private final LocaleResolver localeResolver;

    ConnectorDefinitionController(
            ConnectorCatalog catalog, ConnectorCatalogLocalizer localizer, LocaleResolver localeResolver) {
        this.catalog = catalog;
        this.localizer = localizer;
        this.localeResolver = localeResolver;
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    PageResponse<ConnectorDefinitionResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            HttpServletRequest request) {
        Locale locale = localeResolver.resolveLocale(request);
        var sorted = catalog.listAll().stream()
                .sorted(Comparator.comparing(ConnectorDefinition::getType))
                .map(definition -> localizer.localize(definition, locale))
                .toList();
        var query = PageQueryParser.parse(page, size, sort, ListSorts.CONNECTOR_DEFINITIONS);
        return PageResponse.from(PageResult.slice(sorted, query).map(ConnectorDefinitionResponse::from));
    }
}
