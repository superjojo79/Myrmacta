package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElementView;
import com.flowforge.domain.dossier.model.DossierRelation;
import com.flowforge.domain.dossier.model.DossierRelationView;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.in.CreateDossierRelationUseCase;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.dossier.port.in.ListDossierElementsUseCase;
import com.flowforge.domain.dossier.port.in.ListDossierRelationsUseCase;
import com.flowforge.domain.dossier.port.in.SearchDossiersUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierFieldsUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierStatusUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.observability.MdcScope;
import com.flowforge.infrastructure.security.SecurityAuditLog;
import com.flowforge.infrastructure.web.ListSorts;
import com.flowforge.infrastructure.web.PageQueryParser;
import com.flowforge.infrastructure.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/dossiers")
public class DossierController {

    private final TenantProperties tenantProperties;
    private final CreateDossierUseCase createDossierUseCase;
    private final GetDossierUseCase getDossierUseCase;
    private final ListDossierElementsUseCase listDossierElementsUseCase;
    private final AddDossierElementUseCase addDossierElementUseCase;
    private final UpdateDossierStatusUseCase updateDossierStatusUseCase;
    private final UpdateDossierFieldsUseCase updateDossierFieldsUseCase;
    private final SearchDossiersUseCase searchDossiersUseCase;
    private final CreateDossierRelationUseCase createDossierRelationUseCase;
    private final ListDossierRelationsUseCase listDossierRelationsUseCase;
    private final AccessContextFactory accessContextFactory;
    private final DossierAccessPolicy accessPolicy;

    public DossierController(
            TenantProperties tenantProperties,
            CreateDossierUseCase createDossierUseCase,
            GetDossierUseCase getDossierUseCase,
            ListDossierElementsUseCase listDossierElementsUseCase,
            AddDossierElementUseCase addDossierElementUseCase,
            UpdateDossierStatusUseCase updateDossierStatusUseCase,
            UpdateDossierFieldsUseCase updateDossierFieldsUseCase,
            SearchDossiersUseCase searchDossiersUseCase,
            CreateDossierRelationUseCase createDossierRelationUseCase,
            ListDossierRelationsUseCase listDossierRelationsUseCase,
            AccessContextFactory accessContextFactory,
            DossierAccessPolicy accessPolicy) {
        this.tenantProperties = tenantProperties;
        this.createDossierUseCase = createDossierUseCase;
        this.getDossierUseCase = getDossierUseCase;
        this.listDossierElementsUseCase = listDossierElementsUseCase;
        this.addDossierElementUseCase = addDossierElementUseCase;
        this.updateDossierStatusUseCase = updateDossierStatusUseCase;
        this.updateDossierFieldsUseCase = updateDossierFieldsUseCase;
        this.searchDossiersUseCase = searchDossiersUseCase;
        this.createDossierRelationUseCase = createDossierRelationUseCase;
        this.listDossierRelationsUseCase = listDossierRelationsUseCase;
        this.accessContextFactory = accessContextFactory;
        this.accessPolicy = accessPolicy;
    }

    @PostMapping
    public ResponseEntity<DossierResponse> create(
            @RequestBody CreateDossierRequest request,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        Dossier dossier = createDossierUseCase.create(
                new CreateDossierUseCase.CreateDossierCommand(
                        tenantProperties.id(),
                        required(request.dossierTypeCode(), "dossierTypeCode"),
                        required(request.label(), "label"),
                        request.fields() == null ? Map.of() : Map.copyOf(request.fields()),
                        actor.userId()),
                actor);
        return ResponseEntity
                .created(URI.create("/api/dossiers/" + dossier.id()))
                .body(DossierResponse.from(dossier, accessPolicy.canEdit(dossier, actor)));
    }

    @GetMapping
    public DossierPageResponse list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dossierTypeCode,
            @RequestParam(required = false) String reference,
            Authentication authentication) {
        return DossierPageResponse.from(searchDossiersUseCase.search(
                tenantProperties.id(),
                DossierListQueryParser.parse(page, size, sort, status, dossierTypeCode, reference),
                accessContextFactory.user(authentication)));
    }

    @GetMapping("/{id}")
    public DossierResponse getById(
            @PathVariable UUID id,
            HttpServletRequest httpRequest,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        try (MdcScope ignored = MdcScope.tenant(tenantProperties.id()).dossier(id)) {
            Dossier dossier = getDossierUseCase.get(id, actor);
            DossierResponse response = DossierResponse.from(dossier, accessPolicy.canEdit(dossier, actor));
            SecurityAuditLog.dossierRead(httpRequest, authentication, id);
            return response;
        }
    }

    @GetMapping(params = "ref")
    public DossierResponse getByReference(
            @RequestParam("ref") String reference,
            HttpServletRequest httpRequest,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        Dossier dossier = getDossierUseCase.getByReference(
                tenantProperties.id(),
                required(reference, "ref"),
                actor);
        try (MdcScope ignored = MdcScope.tenant(tenantProperties.id()).dossier(dossier.id())) {
            SecurityAuditLog.dossierRead(httpRequest, authentication, dossier.id());
            return DossierResponse.from(dossier, accessPolicy.canEdit(dossier, actor));
        }
    }

    @GetMapping("/{id}/elements")
    public PageResponse<DossierElementView> listElements(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            Authentication authentication) {
        return PageResponse.from(listDossierElementsUseCase.list(
                id,
                accessContextFactory.user(authentication),
                PageQueryParser.parse(page, size, sort, ListSorts.DOSSIER_ELEMENTS)));
    }

    @PostMapping("/{id}/elements")
    public ResponseEntity<DossierElementView> addElement(
            @PathVariable UUID id,
            @RequestBody AddDossierElementRequest request,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        if (request.type() == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (request.type() == ElementType.PRODUCED_DOC || request.type() == ElementType.ATTACHMENT) {
            throw new IllegalArgumentException(
                    "type " + request.type() + " must be added via its dedicated endpoint");
        }
        var element = addDossierElementUseCase.add(
                new AddDossierElementUseCase.AddCommand(
                        id, request.type(), request.label(), null, request.metadata(), actor.userId(), null),
                actor);
        return ResponseEntity
                .created(URI.create("/api/dossiers/" + id + "/elements/" + element.id()))
                .body(DossierElementView.from(element));
    }

    @GetMapping("/{id}/relations")
    public List<DossierRelationResponse> listRelations(
            @PathVariable UUID id,
            Authentication authentication) {
        return listDossierRelationsUseCase.list(id, accessContextFactory.user(authentication)).stream()
                .map(DossierRelationResponse::from)
                .toList();
    }

    @PostMapping("/{id}/relations")
    public ResponseEntity<DossierRelationResponse> createRelation(
            @PathVariable UUID id,
            @RequestBody CreateDossierRelationRequest request,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        if (request == null || request.targetDossierId() == null) {
            throw new IllegalArgumentException("targetDossierId is required");
        }
        if (request.type() == null) {
            throw new IllegalArgumentException("type is required");
        }
        DossierRelation created = createDossierRelationUseCase.create(
                new CreateDossierRelationUseCase.CreateDossierRelationCommand(
                        tenantProperties.id(),
                        id,
                        request.targetDossierId(),
                        request.type(),
                        actor.userId(),
                        null),
                actor);
        DossierRelationView view = listDossierRelationsUseCase.list(id, actor).stream()
                .filter(item -> item.id().equals(created.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Created relation not visible: " + created.id()));
        return ResponseEntity
                .created(URI.create("/api/dossiers/" + id + "/relations/" + created.id()))
                .body(DossierRelationResponse.from(view));
    }

    @PatchMapping("/{id}/status")
    public DossierResponse updateStatus(
            @PathVariable UUID id,
            @RequestBody UpdateDossierStatusRequest request,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        if (request.status() == null) {
            throw new IllegalArgumentException("status is required");
        }
        Dossier dossier = updateDossierStatusUseCase.update(
                new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(
                        id,
                        request.status(),
                        actor.userId(),
                        request.expectedVersion()),
                actor);
        return DossierResponse.from(dossier, accessPolicy.canEdit(dossier, actor));
    }

    @PatchMapping("/{id}/fields")
    public DossierResponse updateFields(
            @PathVariable UUID id,
            @RequestBody UpdateDossierFieldsRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        if (request.fields() == null || request.fields().isEmpty()) {
            throw new IllegalArgumentException("fields is required");
        }
        UpdateDossierFieldsUseCase.UpdateDossierFieldsResult result = updateDossierFieldsUseCase.update(
                new UpdateDossierFieldsUseCase.UpdateDossierFieldsCommand(
                        id,
                        request.fields(),
                        actor.userId(),
                        request.expectedVersion()),
                actor);
        for (String field : result.sensitiveFieldsChanged()) {
            SecurityAuditLog.sensitiveFieldChanged(httpRequest, authentication, id, field);
        }
        return DossierResponse.from(result.dossier(), accessPolicy.canEdit(result.dossier(), actor));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
