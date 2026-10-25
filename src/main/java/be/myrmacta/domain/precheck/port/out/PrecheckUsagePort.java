package be.myrmacta.domain.precheck.port.out;

import java.time.Instant;

/** TODO §106 (H2) — jetons consommés par la pré-vérification depuis {@code since} (début du jour UTC). */
public interface PrecheckUsagePort {

    long tokensSince(Instant since);
}
