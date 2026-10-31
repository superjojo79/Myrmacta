package be.myrmacta.domain.deadline.port.out;

import be.myrmacta.domain.deadline.model.DeadlineCandidate;
import be.myrmacta.domain.deadline.model.DeadlineSetting;
import java.util.List;

/** TODO §115 (D-DEADLINE-ESCALATE) — dossiers du type, ouverts, en cours ou suspendus, dont le champ d'échéance est renseigné. */
public interface DeadlineCandidates {

    List<DeadlineCandidate> list(DeadlineSetting setting);
}
