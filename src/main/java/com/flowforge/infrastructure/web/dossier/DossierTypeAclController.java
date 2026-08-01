package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.port.in.GrantDossierTypeAccessUseCase;
import com.flowforge.domain.dossier.port.in.RevokeDossierTypeAccessUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dossier-types/{code}/acl")
public class DossierTypeAclController {

    private final TenantProperties tenantProperties;
    private final GrantDossierTypeAccessUseCase grantDossierTypeAccessUseCase;
    private final RevokeDossierTypeAccessUseCase revokeDossierTypeAccessUseCase;
    private final AccessContextFactory accessContextFactory;
    private final TenantAdminGuard tenantAdminGuard;

    public DossierTypeAclController(
            TenantProperties tenantProperties,
            GrantDossierTypeAccessUseCase grantDossierTypeAccessUseCase,
            RevokeDossierTypeAccessUseCase revokeDossierTypeAccessUseCase,
            AccessContextFactory accessContextFactory,
            TenantAdminGuard tenantAdminGuard) {
        this.tenantProperties = tenantProperties;
        this.grantDossierTypeAccessUseCase = grantDossierTypeAccessUseCase;
        this.revokeDossierTypeAccessUseCase = revokeDossierTypeAccessUseCase;
        this.accessContextFactory = accessContextFactory;
        this.tenantAdminGuard = tenantAdminGuard;
    }

    @PostMapping
    public ResponseEntity<Void> grant(
            @PathVariable String code,
            @RequestBody GrantDossierTypeAccessRequest request,
            Authentication authentication) {
        tenantAdminGuard.requireTenantAdmin(authentication);
        AccessContext.User actor = accessContextFactory.user(authentication);
        if (request.permission() == null) {
            throw new IllegalArgumentException("permission is required");
        }
        grantDossierTypeAccessUseCase.grant(
                new GrantDossierTypeAccessUseCase.GrantCommand(
                        tenantProperties.id(),
                        code,
                        required(request.subjectUserId(), "subjectUserId"),
                        request.permission()),
                actor);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> revoke(
            @PathVariable String code,
            @PathVariable String userId,
            Authentication authentication) {
        tenantAdminGuard.requireTenantAdmin(authentication);
        revokeDossierTypeAccessUseCase.revoke(
                tenantProperties.id(),
                code,
                required(userId, "userId"),
                accessContextFactory.user(authentication));
        return ResponseEntity.noContent().build();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
