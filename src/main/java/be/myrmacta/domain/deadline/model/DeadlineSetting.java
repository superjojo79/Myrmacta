package be.myrmacta.domain.deadline.model;

import java.util.Objects;

/**
 * TODO §115 (D-DEADLINE-FIELD) — échéance d'un type de dossier : le champ {@code DATE} qui la porte, le rappel {@code reminderDays}
 * jours avant le terme (0 = pas de rappel) et le groupe d'escalade, facultatif.
 * TODO §120 (D-CAL-SITES, D-CAL-ESCALATE) — {@code reminderDayCount} commande le rappel et l'escalade. Absent : calendaire.
 */
public record DeadlineSetting(
        String tenantId, String dossierTypeCode, String fieldName, int reminderDays, String escalationGroup, DayCount reminderDayCount) {

    public static final int MAX_REMINDER_DAYS = 365;

    public DeadlineSetting(String tenantId, String dossierTypeCode, String fieldName, int reminderDays, String escalationGroup) {
        this(tenantId, dossierTypeCode, fieldName, reminderDays, escalationGroup, DayCount.CALENDAR);
    }

    public DeadlineSetting {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
        fieldName = fieldName == null ? "" : fieldName.strip();
        if (fieldName.isEmpty()) {
            throw new IllegalArgumentException("fieldName is required");
        }
        if (reminderDays < 0 || reminderDays > MAX_REMINDER_DAYS) {
            throw new IllegalArgumentException("reminderDays must be between 0 and " + MAX_REMINDER_DAYS);
        }
        escalationGroup = escalationGroup == null || escalationGroup.isBlank() ? null : escalationGroup.strip();
        reminderDayCount = reminderDayCount == null ? DayCount.CALENDAR : reminderDayCount;
    }
}
