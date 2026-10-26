package be.myrmacta.infrastructure.precheck;

import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.port.in.RunDossierPrecheckUseCase;
import be.myrmacta.domain.precheck.port.out.PrecheckRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TODO §106 (lot B, C2, R7, D-PRECHECK-QUEUE) — relais de la file des relances (bouton, MCP) : prend une exécution avec un
 * bail ({@code SKIP LOCKED}, sûr à plusieurs répliques) et l'exécute ; un bail expiré (réplique arrêtée) la remet en jeu,
 * abandonnée après {@value RunDossierPrecheckUseCase#MAX_QUEUE_ATTEMPTS} tentatives. Une exécution à la fois par passage
 * et par réplique : l'appel au modèle est long et coûteux.
 */
@Component
@ConditionalOnProperty(prefix = "app.precheck", name = "enabled", havingValue = "true")
class PrecheckRunRelay {

    static final Duration LEASE = Duration.ofMinutes(5);
    private static final Logger LOG = LoggerFactory.getLogger(PrecheckRunRelay.class);

    private final PrecheckRunRepository repository;
    private final RunDossierPrecheckUseCase useCase;
    private final Clock clock;

    @Autowired
    PrecheckRunRelay(PrecheckRunRepository repository, RunDossierPrecheckUseCase useCase) {
        this(repository, useCase, Clock.systemUTC());
    }

    PrecheckRunRelay(PrecheckRunRepository repository, RunDossierPrecheckUseCase useCase, Clock clock) {
        this.repository = repository;
        this.useCase = useCase;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.precheck.queue-poll-delay:5s}")
    void relay() {
        relayOne();
    }

    /** @return l'exécution traitée, vide si la file est vide */
    Optional<PrecheckRun> relayOne() {
        Instant now = clock.instant();
        Optional<PrecheckRun> next = repository.lockNext(now, now.plus(LEASE));
        next.ifPresent(run -> {
            try {
                PrecheckRun done = useCase.runQueued(run);
                LOG.info("AI pre-check {} on dossier {}: {}", done.id(), done.dossierId(), done.status());
            } catch (RuntimeException ex) {
                // Le bail expirera et l'exécution sera reprise (puis abandonnée) : jamais de boucle serrée.
                LOG.warn("AI pre-check {} failed unexpectedly; it will be retried once its lease expires", run.id(), ex);
            }
        });
        return next;
    }
}
