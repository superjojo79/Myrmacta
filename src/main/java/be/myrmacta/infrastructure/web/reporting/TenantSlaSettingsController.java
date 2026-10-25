package be.myrmacta.infrastructure.web.reporting;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.reporting.model.TenantSlaSettings;
import be.myrmacta.domain.reporting.port.in.GetTenantSlaSettingsUseCase;
import be.myrmacta.domain.reporting.port.in.UpsertTenantSlaSettingsUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.web.dossier.AccessContextFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

record UpsertTenantSlaSettingsRequest(
        int complianceTargetPercent,
        Boolean alertingEnabled,
        Integer alertIntervalMinutes,
        List<String> alertEmails,
        List<UUID> webhookIds,
        Boolean biExportEnabled,
        Integer biIntervalHours,
        Integer biRetentionDays) {}

record TenantSlaSettingsResponse(
        String tenantId,
        int complianceTargetPercent,
        boolean alertingEnabled,
        int alertIntervalMinutes,
        List<String> alertEmails,
        List<UUID> webhookIds,
        boolean biExportEnabled,
        int biIntervalHours,
        int biRetentionDays,
        Instant alertingEnabledAt,
        Instant lastAlertScanAt,
        Instant biEnabledAt,
        Instant lastBiExportAt,
        Instant updatedAt,
        String updatedBy) {
    static TenantSlaSettingsResponse from(TenantSlaSettings settings) {
        return new TenantSlaSettingsResponse(
                settings.tenantId(),
                settings.complianceTargetPercent(),
                settings.alertingEnabled(),
                settings.alertIntervalMinutes(),
                settings.alertEmails(),
                settings.alertWebhookIds(),
                settings.biExportEnabled(),
                settings.biIntervalHours(),
                settings.biRetentionDays(),
                settings.alertingEnabledAt(),
                settings.lastAlertScanAt(),
                settings.biEnabledAt(),
                settings.lastBiExportAt(),
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
    @Transactional
    public TenantSlaSettingsResponse upsert(
            @RequestBody UpsertTenantSlaSettingsRequest request, Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        TenantSlaSettings settings = upsertTenantSlaSettingsUseCase.upsert(toCommand(request), actor);
        return TenantSlaSettingsResponse.from(settings);
    }

    private UpsertTenantSlaSettingsUseCase.Command toCommand(UpsertTenantSlaSettingsRequest request) {
        return new UpsertTenantSlaSettingsUseCase.Command(
                tenantProperties.id(),
                request.complianceTargetPercent(),
                request.alertingEnabled(),
                request.alertIntervalMinutes(),
                request.alertEmails(),
                request.webhookIds(),
                request.biExportEnabled(),
                request.biIntervalHours(),
                request.biRetentionDays());
    }
}
