package be.myrmacta.infrastructure.precheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.model.PrecheckSpec;
import be.myrmacta.domain.precheck.port.in.RunDossierPrecheckUseCase;
import be.myrmacta.domain.precheck.port.out.PrecheckRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TODO §106 (lot B, C2, D-PRECHECK-QUEUE) — relais de la file : bail de cinq minutes, jamais d'exception propagée. */
class PrecheckRunRelayTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    private final PrecheckRunRepository repository = mock(PrecheckRunRepository.class);
    private final RunDossierPrecheckUseCase useCase = mock(RunDossierPrecheckUseCase.class);
    private final PrecheckRunRelay relay = new PrecheckRunRelay(repository, useCase, Clock.fixed(NOW, ZoneOffset.UTC));
    private final PrecheckRun run = PrecheckRun.pending(UUID.randomUUID(), PrecheckRun.Trigger.MANUAL, "agent", null,
            PrecheckSpec.parse(Map.of(PrecheckSpec.QUESTIONS, "q = Question ?"), "check"), NOW);

    @Test
    void anEmptyQueueDoesNothing() {
        when(repository.lockNext(any(), any())).thenReturn(Optional.empty());
        assertThat(relay.relayOne()).isEmpty();
        verify(useCase, never()).runQueued(any());
    }

    @Test
    void aRunIsTakenWithAFiveMinuteLease() {
        when(repository.lockNext(NOW, NOW.plusSeconds(300))).thenReturn(Optional.of(run));
        when(useCase.runQueued(run)).thenReturn(run);
        assertThat(relay.relayOne()).contains(run);
        verify(useCase).runQueued(run);
    }

    @Test
    void anUnexpectedFailureIsLeftToTheLease() {
        when(repository.lockNext(any(), any())).thenReturn(Optional.of(run));
        when(useCase.runQueued(run)).thenThrow(new IllegalStateException("database down"));
        assertThat(relay.relayOne()).contains(run);
    }
}
