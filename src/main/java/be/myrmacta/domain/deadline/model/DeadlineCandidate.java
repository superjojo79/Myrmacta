package be.myrmacta.domain.deadline.model;

import java.util.Objects;
import java.util.UUID;

/** TODO §115 (D-DEADLINE-ESCALATE) — dossier en cours dont le champ d'échéance est renseigné ; {@code rawDeadline} tel que stocké. */
public record DeadlineCandidate(UUID dossierId, String reference, String dossierTypeCode, String rawDeadline) {

    public DeadlineCandidate {
        Objects.requireNonNull(dossierId, "dossierId");
    }
}
