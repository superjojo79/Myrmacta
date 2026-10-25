package be.myrmacta.domain.precheck;

import be.myrmacta.domain.precheck.model.PrecheckExceptions.PrecheckBudgetExceededException;
import be.myrmacta.domain.precheck.port.out.PrecheckUsagePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * TODO §106 (lot A, H2, D-PRECHECK-BUDGET) — plafond de jetons par jour (UTC) et par instance, vérifié avant chaque appel au modèle. Un
 * appel en cours peut faire dépasser le plafond d'au plus une réponse (pas de réservation) : le suivant est refusé.
 */
public final class PrecheckBudget {

    private final long maxTokensPerDay;
    private final PrecheckUsagePort usage;
    private final Clock clock;

    public PrecheckBudget(long maxTokensPerDay, PrecheckUsagePort usage, Clock clock) {
        if (maxTokensPerDay < 1) {
            throw new IllegalArgumentException("maxTokensPerDay must be positive");
        }
        this.maxTokensPerDay = maxTokensPerDay;
        this.usage = Objects.requireNonNull(usage, "usage");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void requireAvailable() {
        long used = usage.tokensSince(startOfDay());
        if (used >= maxTokensPerDay) {
            throw new PrecheckBudgetExceededException(used, maxTokensPerDay);
        }
    }

    private Instant startOfDay() {
        return clock.instant().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
