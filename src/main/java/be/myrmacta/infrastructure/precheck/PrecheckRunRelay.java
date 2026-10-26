package be.myrmacta.infrastructure.precheck;

import be.myrmacta.domain.precheck.model.PrecheckRun;
import be.myrmacta.domain.precheck.port.in.RunDossierPrecheckUseCase;
import be.myrmacta.domain.precheck.port.out.PrecheckRunRepository;
import be.myrmacta.infrastructure.config.PrecheckProperties;
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

    /** Bail minimal ; il couvre toujours le pire cas d'une exécution (F4). */
    static final Duration MIN_LEASE = Duration.ofMinutes(5);
    private static final Logger LOG = LoggerFactory.getLogger(PrecheckRunRelay.class);

    private final PrecheckRunRepository repository;
    private final RunDossierPrecheckUseCase useCase;
    private final Duration lease;
    private final Clock clock;

    @Autowired
    PrecheckRunRelay(PrecheckRunRepository repository, RunDossierPrecheckUseCase useCase, PrecheckProperties properties) {
        this(repository, useCase, lease(properties), Clock.systemUTC());
    }

    PrecheckRunRelay(PrecheckRunRepository repository, RunDossierPrecheckUseCase useCase, Duration lease, Clock clock) {
        this.repository = repository;
        this.useCase = useCase;
        this.lease = lease;
        this.clock = clock;
    }

    /**
     * TODO §106 (revue adversariale de #211, F4) — le bail couvre le pire cas d'une exécution (lecture de chaque pièce dans
     * son délai, appel au modèle dans le sien, une minute de marge) : sans quoi une autre réplique la reprendrait pendant
     * qu'elle tourne encore, et le modèle serait appelé deux fois.
     */
    static Duration lease(PrecheckProperties properties) {
        Duration worst = PdfBoxPieceTextExtractor.MAX_EXTRACTION.multipliedBy(properties.maxPiecesRead())
                .plusSeconds(properties.timeoutSeconds())
                .plusMinutes(1);
        return worst.compareTo(MIN_LEASE) > 0 ? worst : MIN_LEASE;
    }

    @Scheduled(fixedDelayString = "${app.precheck.queue-poll-delay:5s}")
    void relay() {
        relayOne();
    }

    /** @return l'exécution traitée, vide si la file est vide */
    Optional<PrecheckRun> relayOne() {
        Instant now = clock.instant();
        Optional<PrecheckRun> next = repository.lockNext(now, now.plus(lease));
        next.ifPresent(run -> {
            try {
                PrecheckRun done = useCase.runQueued(run);
                if (done.finished()) {
                    LOG.info("AI pre-check {} on dossier {}: {}", done.id(), done.dossierId(), done.status());
                } else {
                    LOG.warn("AI pre-check {} was taken over by a later attempt; this result is dropped", done.id());
                }
            } catch (RuntimeException ex) {
                // Le bail expirera et l'exécution sera reprise (puis abandonnée) : jamais de boucle serrée.
                LOG.warn("AI pre-check {} failed unexpectedly; it will be retried once its lease expires", run.id(), ex);
            }
        });
        return next;
    }
}
