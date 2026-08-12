package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElementView;
import com.flowforge.domain.dossier.port.in.CreateDossierUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.dossier.port.in.ListDossierElementsUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierStatusUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
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
    private final UpdateDossierStatusUseCase updateDossierStatusUseCase;
    private final AccessContextFactory accessContextFactory;

    public DossierController(
            TenantProperties tenantProperties,
            CreateDossierUseCase createDossierUseCase,
            GetDossierUseCase getDossierUseCase,
            ListDossierElementsUseCase listDossierElementsUseCase,
            UpdateDossierStatusUseCase updateDossierStatusUseCase,
            AccessContextFactory accessContextFactory) {
        this.tenantProperties = tenantProperties;
        this.createDossierUseCase = createDossierUseCase;
        this.getDossierUseCase = getDossierUseCase;
        this.listDossierElementsUseCase = listDossierElementsUseCase;
        this.updateDossierStatusUseCase = updateDossierStatusUseCase;
        this.accessContextFactory = accessContextFactory;
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
                .body(DossierResponse.from(dossier));
    }

    @GetMapping("/{id}")
    public DossierResponse getById(
            @PathVariable UUID id,
            Authentication authentication) {
        return DossierResponse.from(getDossierUseCase.get(
                id,
                accessContextFactory.user(authentication)));
    }

    @GetMapping(params = "ref")
    public DossierResponse getByReference(
            @RequestParam("ref") String reference,
            Authentication authentication) {
        return DossierResponse.from(getDossierUseCase.getByReference(
                tenantProperties.id(),
                required(reference, "ref"),
                accessContextFactory.user(authentication)));
    }

    @GetMapping("/{id}/elements")
    public List<DossierElementView> listElements(
            @PathVariable UUID id,
            Authentication authentication) {
        return listDossierElementsUseCase.list(id, accessContextFactory.user(authentication));
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
        return DossierResponse.from(updateDossierStatusUseCase.update(
                new UpdateDossierStatusUseCase.UpdateDossierStatusCommand(
                        id,
                        request.status(),
                        actor.userId(),
                        request.expectedVersion()),
                actor));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
