package be.myrmacta.domain.deadline.port.in;

import be.myrmacta.domain.reporting.UnitOfWork;
import java.time.Instant;

/** TODO §115 (D-DEADLINE-ESCALATE) — rappel puis escalade des dossiers dont l'échéance approche ou est dépassée. */
public interface EvaluateDeadlinesUseCase {

    /** @return le nombre de dossiers en échec dans leur propre unité de travail ; ils sont repris au passage suivant. */
    int scan(String tenantId, Instant now, UnitOfWork unitOfWork);
}
