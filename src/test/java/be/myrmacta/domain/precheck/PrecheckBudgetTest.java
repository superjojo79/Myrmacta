package be.myrmacta.domain.precheck;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.precheck.model.PrecheckExceptions.PrecheckBudgetExceededException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** TODO §106 (H2) — plafond de jetons par jour UTC. */
class PrecheckBudgetTest {

    @Test
    void countsFromTheStartOfTheUtcDayAndRefusesOnceReached() {
        AtomicReference<Instant> since = new AtomicReference<>();
        Clock clock = Clock.fixed(Instant.parse("2026-10-25T22:30:00Z"), ZoneOffset.UTC);

        new PrecheckBudget(1_000, s -> {
            since.set(s);
            return 999;
        }, clock).requireAvailable();
        assertThatCode(() -> new PrecheckBudget(1_000, s -> 999, clock).requireAvailable()).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(since.get()).isEqualTo(Instant.parse("2026-10-25T00:00:00Z"));
        assertThatThrownBy(() -> new PrecheckBudget(1_000, s -> 1_000, clock).requireAvailable())
                .isInstanceOf(PrecheckBudgetExceededException.class)
                .hasMessageContaining("1000 / 1000");
    }
}
