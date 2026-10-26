package be.myrmacta.domain.precheck.port.out;

import be.myrmacta.domain.precheck.model.PrecheckFinding;
import be.myrmacta.domain.precheck.model.PrecheckRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TODO §106 (lot B, HC9, C2) — tables {@code precheck_run} et {@code precheck_finding} (changeSet 040). Les écritures se
 * font dans leur propre transaction : une exécution en échec reste rangée même si le job du workflow est annulé (HC5).
 */
public interface PrecheckRunRepository extends PrecheckUsagePort {

    /** @return {@code false} si une exécution de la file est déjà en cours pour ce dossier (index unique partiel). */
    boolean insert(PrecheckRun run);

    /** Range le résultat (statut, journal, constats) d'une exécution déjà insérée. */
    void complete(PrecheckRun run);

    Optional<PrecheckRun> findById(UUID runId);

    /** Exécutions du dossier, la plus récente d'abord, avec leurs constats. */
    List<PrecheckRun> listByDossier(UUID dossierId, int limit);

    /**
     * C2 — prend la prochaine exécution de la file ({@code PENDING}, ou {@code RUNNING} au bail expiré) avec un bail
     * jusqu'à {@code leaseUntil}, en {@code SKIP LOCKED} ; incrémente les tentatives.
     */
    Optional<PrecheckRun> lockNext(Instant now, Instant leaseUntil);

    /** Décision de l'agent sur un constat ; {@code false} si le constat n'appartient pas à l'exécution. */
    boolean decide(UUID runId, UUID findingId, PrecheckFinding.Decision decision, String by, Instant at);
}
