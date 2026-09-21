package com.flowforge.domain.reporting.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.reporting.model.TenantSlaSettings;
import java.util.List;
import java.util.UUID;

public interface UpsertTenantSlaSettingsUseCase {

    TenantSlaSettings upsert(Command command, AccessContext.User actor);

    record Command(
            String tenantId,
            int complianceTargetPercent,
            boolean alertingEnabled,
            int alertIntervalMinutes,
            List<String> alertEmails,
            List<UUID> alertWebhookIds,
            boolean biExportEnabled,
            int biIntervalHours,
            int biRetentionDays) {

        public Command(String tenantId, int complianceTargetPercent) {
            this(
                    tenantId,
                    complianceTargetPercent,
                    false,
                    TenantSlaSettings.DEFAULT_ALERT_INTERVAL_MINUTES,
                    List.of(),
                    List.of(),
                    false,
                    TenantSlaSettings.DEFAULT_BI_INTERVAL_HOURS,
                    TenantSlaSettings.DEFAULT_BI_RETENTION_DAYS);
        }
    }
}
