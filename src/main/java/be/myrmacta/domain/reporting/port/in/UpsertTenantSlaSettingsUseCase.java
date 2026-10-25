package be.myrmacta.domain.reporting.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.reporting.model.TenantSlaSettings;
import java.util.List;
import java.util.UUID;

public interface UpsertTenantSlaSettingsUseCase {

    TenantSlaSettings upsert(Command command, AccessContext.User actor);

    /**
     * A null alerting, cadence, recipient, or BI field means “not provided”.
     * The percent is always required.
     */
    record Command(
            String tenantId,
            int complianceTargetPercent,
            Boolean alertingEnabled,
            Integer alertIntervalMinutes,
            List<String> alertEmails,
            List<UUID> alertWebhookIds,
            Boolean biExportEnabled,
            Integer biIntervalHours,
            Integer biRetentionDays) {

        public Command(String tenantId, int complianceTargetPercent) {
            this(tenantId, complianceTargetPercent, null, null, null, null, null, null, null);
        }
    }
}
