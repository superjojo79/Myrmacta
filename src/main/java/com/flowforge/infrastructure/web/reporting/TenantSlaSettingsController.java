package com.flowforge.infrastructure.web.reporting;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.reporting.model.TenantSlaSettings;
import com.flowforge.domain.reporting.port.in.GetTenantSlaSettingsUseCase;
import com.flowforge.domain.reporting.port.in.UpsertTenantSlaSettingsUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

record UpsertTenantSlaSettingsRequest(int complianceTargetPercent) {}

record TenantSlaSettingsResponse(
        String tenantId, int complianceTargetPercent, Instant updatedAt, String updatedBy) {
    static TenantSlaSettingsResponse from(TenantSlaSettings settings) {
        return new TenantSlaSettingsResponse(
                settings.tenantId(),
                settings.complianceTargetPercent(),
                settings.updatedAt(),
                settings.updatedBy());
    }
}

@RestController
@RequestMapping("/api/tenant/sla-settings")
public class TenantSlaSettingsController {

    private final TenantProperties tenantProperties;
    private final GetTenantSlaSettingsUseCase getTenantSlaSettingsUseCase;
    private final UpsertTenantSlaSettingsUseCase upsertTenantSlaSettingsUseCase;
    private final AccessContextFactory accessContextFactory;

    public TenantSlaSettingsController(
            TenantProperties tenantProperties,
            GetTenantSlaSettingsUseCase getTenantSlaSettingsUseCase,
            UpsertTenantSlaSettingsUseCase upsertTenantSlaSettingsUseCase,
            AccessContextFactory accessContextFactory) {
        this.tenantProperties = tenantProperties;
        this.getTenantSlaSettingsUseCase = getTenantSlaSettingsUseCase;
        this.upsertTenantSlaSettingsUseCase = upsertTenantSlaSettingsUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public TenantSlaSettingsResponse get(Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        return TenantSlaSettingsResponse.from(getTenantSlaSettingsUseCase.get(tenantProperties.id(), actor));
    }

    @PutMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public TenantSlaSettingsResponse upsert(
            @RequestBody UpsertTenantSlaSettingsRequest request, Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        TenantSlaSettings settings = upsertTenantSlaSettingsUseCase.upsert(
                new UpsertTenantSlaSettingsUseCase.Command(
                        tenantProperties.id(), request.complianceTargetPercent()),
                actor);
        return TenantSlaSettingsResponse.from(settings);
    }
}
