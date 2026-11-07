package be.myrmacta.domain.deadline.port.in;

import be.myrmacta.domain.deadline.model.DayCount;
import be.myrmacta.domain.deadline.model.DeadlineSetting;
import java.util.Optional;

/** TODO §115 (D-DEADLINE-FIELD) — réglage d'échéance d'un type de dossier. */
public interface ManageDeadlineUseCase {

    Optional<DeadlineSetting> get(String dossierTypeCode);

    /** Refusé si le champ n'est pas un champ {@code DATE} du type, ou si le groupe d'escalade n'existe pas. */
    DeadlineSetting set(String dossierTypeCode, String fieldName, int reminderDays, String escalationGroup);

    /** Comme {@link #set(String, String, int, String)}, avec le mode du rappel. {@code null} vaut calendaire. */
    DeadlineSetting set(String dossierTypeCode, String fieldName, int reminderDays, String escalationGroup, DayCount reminderDayCount);

    void clear(String dossierTypeCode);
}
