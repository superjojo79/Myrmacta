package be.myrmacta.domain.deadline.port.out;

import be.myrmacta.domain.deadline.model.DeadlineLevel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * TODO §115 (D-DEADLINE-ESCALATE) — prise d'un palier avant d'agir. Une seule prise réussit par dossier, palier et date ; un
 * exécutant concurrent reçoit {@code false} et n'agit pas.
 */
public interface DeadlineAlertClaims {

    boolean claim(String tenantId, UUID dossierId, DeadlineLevel level, LocalDate deadline, Instant at);
}
