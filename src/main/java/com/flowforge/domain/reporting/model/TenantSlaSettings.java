package com.flowforge.domain.reporting.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public record TenantSlaSettings(
        String tenantId,
        int complianceTargetPercent,
        boolean alertingEnabled,
        int alertIntervalMinutes,
        List<String> alertEmails,
        List<UUID> alertWebhookIds,
        boolean biExportEnabled,
        int biIntervalHours,
        int biRetentionDays,
        Instant alertingEnabledAt,
        Instant lastAlertScanAt,
        Instant biEnabledAt,
        Instant lastBiExportAt,
        Instant updatedAt,
        String updatedBy) {

    public static final int DEFAULT_COMPLIANCE_TARGET_PERCENT = 95;
    public static final int DEFAULT_ALERT_INTERVAL_MINUTES = 60;
    public static final int MIN_ALERT_INTERVAL_MINUTES = 15;
    public static final int MAX_ALERT_INTERVAL_MINUTES = 10_080;
    public static final int DEFAULT_BI_INTERVAL_HOURS = 24;
    public static final int MIN_BI_INTERVAL_HOURS = 1;
    public static final int MAX_BI_INTERVAL_HOURS = 720;
    public static final int DEFAULT_BI_RETENTION_DAYS = 30;
    public static final int MIN_BI_RETENTION_DAYS = 1;
    public static final int MAX_BI_RETENTION_DAYS = 365;
    public static final int MAX_RECIPIENTS = 20;

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public TenantSlaSettings {
        Objects.requireNonNull(tenantId, "tenantId");
        if (complianceTargetPercent < 1 || complianceTargetPercent > 100) {
            throw new IllegalArgumentException("complianceTargetPercent must be between 1 and 100");
        }
        if (alertIntervalMinutes < MIN_ALERT_INTERVAL_MINUTES
                || alertIntervalMinutes > MAX_ALERT_INTERVAL_MINUTES) {
            throw new IllegalArgumentException(
                    "alertIntervalMinutes must be between "
                            + MIN_ALERT_INTERVAL_MINUTES
                            + " and "
                            + MAX_ALERT_INTERVAL_MINUTES);
        }
        if (biIntervalHours < MIN_BI_INTERVAL_HOURS || biIntervalHours > MAX_BI_INTERVAL_HOURS) {
            throw new IllegalArgumentException(
                    "biIntervalHours must be between " + MIN_BI_INTERVAL_HOURS + " and " + MAX_BI_INTERVAL_HOURS);
        }
        if (biRetentionDays < MIN_BI_RETENTION_DAYS || biRetentionDays > MAX_BI_RETENTION_DAYS) {
            throw new IllegalArgumentException(
                    "biRetentionDays must be between " + MIN_BI_RETENTION_DAYS + " and " + MAX_BI_RETENTION_DAYS);
        }
        alertEmails = normalizeEmails(alertEmails);
        alertWebhookIds = normalizeWebhookIds(alertWebhookIds);
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
    }

    /** Feature-off defaults. Existing call sites that only know the compliance target keep compiling. */
    public TenantSlaSettings(String tenantId, int complianceTargetPercent, Instant updatedAt, String updatedBy) {
        this(
                tenantId,
                complianceTargetPercent,
                false,
                DEFAULT_ALERT_INTERVAL_MINUTES,
                List.of(),
                List.of(),
                false,
                DEFAULT_BI_INTERVAL_HOURS,
                DEFAULT_BI_RETENTION_DAYS,
                null,
                null,
                null,
                null,
                updatedAt,
                updatedBy);
    }

    public TenantSlaSettings withLastAlertScanAt(Instant lastAlertScanAt) {
        return new TenantSlaSettings(
                tenantId,
                complianceTargetPercent,
                alertingEnabled,
                alertIntervalMinutes,
                alertEmails,
                alertWebhookIds,
                biExportEnabled,
                biIntervalHours,
                biRetentionDays,
                alertingEnabledAt,
                lastAlertScanAt,
                biEnabledAt,
                lastBiExportAt,
                updatedAt,
                updatedBy);
    }

    public TenantSlaSettings withLastBiExportAt(Instant lastBiExportAt) {
        return new TenantSlaSettings(
                tenantId,
                complianceTargetPercent,
                alertingEnabled,
                alertIntervalMinutes,
                alertEmails,
                alertWebhookIds,
                biExportEnabled,
                biIntervalHours,
                biRetentionDays,
                alertingEnabledAt,
                lastAlertScanAt,
                biEnabledAt,
                lastBiExportAt,
                updatedAt,
                updatedBy);
    }

    public static List<String> normalizeEmails(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_RECIPIENTS) {
            throw new IllegalArgumentException("alert emails exceed " + MAX_RECIPIENTS);
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String email : raw) {
            if (email == null) {
                throw new IllegalArgumentException("alert email is invalid");
            }
            String trimmed = email.trim();
            if (!EMAIL.matcher(trimmed).matches()) {
                throw new IllegalArgumentException("alert email is invalid");
            }
            unique.add(trimmed);
        }
        return List.copyOf(unique);
    }

    public static List<UUID> normalizeWebhookIds(List<UUID> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_RECIPIENTS) {
            throw new IllegalArgumentException("alert webhooks exceed " + MAX_RECIPIENTS);
        }
        List<UUID> unique = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        for (UUID id : raw) {
            if (id == null) {
                throw new IllegalArgumentException("alert webhook id is required");
            }
            if (seen.add(id)) {
                unique.add(id);
            }
        }
        return List.copyOf(unique);
    }
}
