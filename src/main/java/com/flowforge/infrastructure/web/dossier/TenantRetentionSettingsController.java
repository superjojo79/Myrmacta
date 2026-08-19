package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.TenantRetentionSettings;
import com.flowforge.domain.dossier.port.in.GetTenantRetentionSettingsUseCase;
import com.flowforge.domain.dossier.port.in.UpsertTenantRetentionSettingsUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

record UpsertTenantRetentionSettingsRequest(int defaultRetentionDays) {}

record TenantRetentionSettingsResponse(
        String tenantId, int defaultRetentionDays, Instant updatedAt, String updatedBy) {

    static TenantRetentionSettingsResponse from(TenantRetentionSettings settings) {
        return new TenantRetentionSettingsResponse(
                settings.tenantId(),
                settings.defaultRetentionDays(),
                settings.updatedAt(),
                settings.updatedBy());
    }
}

@RestController
@RequestMapping("/api/tenant/retention-settings")
public class TenantRetentionSettingsController {

    private final TenantProperties tenantProperties;
    private final GetTenantRetentionSettingsUseCase getTenantRetentionSettingsUseCase;
    private final UpsertTenantRetentionSettingsUseCase upsertTenantRetentionSettingsUseCase;
    private final AccessContextFactory accessContextFactory;

    public TenantRetentionSettingsController(
            TenantProperties tenantProperties,
            GetTenantRetentionSettingsUseCase getTenantRetentionSettingsUseCase,
            UpsertTenantRetentionSettingsUseCase upsertTenantRetentionSettingsUseCase,
            AccessContextFactory accessContextFactory) {
        this.tenantProperties = tenantProperties;
        this.getTenantRetentionSettingsUseCase = getTenantRetentionSettingsUseCase;
        this.upsertTenantRetentionSettingsUseCase = upsertTenantRetentionSettingsUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public TenantRetentionSettingsResponse get(Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        return TenantRetentionSettingsResponse.from(
                getTenantRetentionSettingsUseCase.get(tenantProperties.id(), actor));
    }

    @PutMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public TenantRetentionSettingsResponse upsert(
            @RequestBody UpsertTenantRetentionSettingsRequest request, Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        TenantRetentionSettings settings = upsertTenantRetentionSettingsUseCase.upsert(
                new UpsertTenantRetentionSettingsUseCase.UpsertCommand(
                        tenantProperties.id(), request.defaultRetentionDays()),
                actor);
        return TenantRetentionSettingsResponse.from(settings);
    }
}
