package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.port.in.CreateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierTypeUseCase;
import com.flowforge.domain.dossier.port.in.ListDossierTypesUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierTypeUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.web.AdminListSorts;
import com.flowforge.infrastructure.web.PageQueryParser;
import com.flowforge.infrastructure.web.PageResponse;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dossier-types")
public class DossierTypeController {

    private final TenantProperties tenantProperties;
    private final CreateDossierTypeUseCase createDossierTypeUseCase;
    private final UpdateDossierTypeUseCase updateDossierTypeUseCase;
    private final GetDossierTypeUseCase getDossierTypeUseCase;
    private final ListDossierTypesUseCase listDossierTypesUseCase;
    private final AccessContextFactory accessContextFactory;

    public DossierTypeController(
            TenantProperties tenantProperties,
            CreateDossierTypeUseCase createDossierTypeUseCase,
            UpdateDossierTypeUseCase updateDossierTypeUseCase,
            GetDossierTypeUseCase getDossierTypeUseCase,
            ListDossierTypesUseCase listDossierTypesUseCase,
            AccessContextFactory accessContextFactory) {
        this.tenantProperties = tenantProperties;
        this.createDossierTypeUseCase = createDossierTypeUseCase;
        this.updateDossierTypeUseCase = updateDossierTypeUseCase;
        this.getDossierTypeUseCase = getDossierTypeUseCase;
        this.listDossierTypesUseCase = listDossierTypesUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<DossierTypeResponse> create(
            @RequestBody CreateDossierTypeRequest request,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        DossierType type = createDossierTypeUseCase.create(
                new CreateDossierTypeUseCase.CreateDossierTypeCommand(
                        tenantProperties.id(),
                        required(request.code(), "code"),
                        required(request.label(), "label"),
                        required(request.workflowKey(), "workflowKey"),
                        request.active() == null || request.active(),
                        Boolean.TRUE.equals(request.allowUnknownFields()),
                        listOrEmpty(request.fieldDefinitions()),
                        request.retentionDays(),
                        Boolean.TRUE.equals(request.retentionNever())),
                actor);
        return ResponseEntity
                .created(URI.create("/api/dossier-types/" + type.code()))
                .body(DossierTypeResponse.from(type));
    }

    @PatchMapping("/{code}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<DossierTypeResponse> update(
            @PathVariable String code,
            @RequestBody UpdateDossierTypeRequest request,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        DossierType existing = getDossierTypeUseCase.getByCode(tenantProperties.id(), code, actor);
        // A-PATCH-INHERIT: only both retention fields absent keeps the existing policy; any
        // explicit value (including retentionNever=false + retentionDays=null) overrides it.
        boolean keepExistingRetention = request.retentionNever() == null && request.retentionDays() == null;
        DossierType type = updateDossierTypeUseCase.update(
                new UpdateDossierTypeUseCase.UpdateDossierTypeCommand(
                        tenantProperties.id(),
                        code,
                        request.label() == null ? existing.label() : required(request.label(), "label"),
                        request.workflowKey() == null
                                ? existing.workflowKey()
                                : required(request.workflowKey(), "workflowKey"),
                        request.active() == null ? existing.active() : request.active(),
                        request.allowUnknownFields() == null
                                ? existing.allowUnknownFields()
                                : request.allowUnknownFields(),
                        request.fieldDefinitions() == null
                                ? existing.fieldDefinitions()
                                : listOrEmpty(request.fieldDefinitions()),
                        keepExistingRetention ? existing.retentionDays() : request.retentionDays(),
                        keepExistingRetention
                                ? existing.retentionNever()
                                : Boolean.TRUE.equals(request.retentionNever())),
                actor);
        return ResponseEntity.ok(DossierTypeResponse.from(type));
    }

    @GetMapping
    public PageResponse<DossierTypeResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            Authentication authentication) {
        return PageResponse.from(listDossierTypesUseCase
                .list(
                        tenantProperties.id(),
                        accessContextFactory.user(authentication),
                        PageQueryParser.parse(page, size, sort, AdminListSorts.DOSSIER_TYPES))
                .map(DossierTypeResponse::from));
    }

    @GetMapping("/{code}")
    public DossierTypeResponse getByCode(
            @PathVariable String code,
            Authentication authentication) {
        return DossierTypeResponse.from(getDossierTypeUseCase.getByCode(
                tenantProperties.id(),
                code,
                accessContextFactory.user(authentication)));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static List<FieldDefinition> listOrEmpty(List<FieldDefinition> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
