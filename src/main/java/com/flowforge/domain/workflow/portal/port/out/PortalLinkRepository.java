package com.flowforge.domain.workflow.portal.port.out;

import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.model.PortalLinkStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortalLinkRepository {

    /**
     * Insère un nouveau lien. Un second lien vivant pour la même tâche est refusé par l'index unique
     * partiel ({@code ux_portal_link_task_live}, exigence H2) : {@link PortalLinkConflictException}.
     */
    void insert(PortalLink link);

    /**
     * Applique {@code next} seulement si le lien est toujours dans le statut de {@code current} —
     * compare-and-set. {@code false} si un autre appel a déjà changé le statut (double soumission,
     * renvoi concurrent, expiration).
     */
    boolean transition(PortalLink current, PortalLink next);

    Optional<PortalLink> findById(UUID id);

    Optional<PortalLink> findByTokenHash(String tokenHash);

    /** Historique complet du dossier, du plus récent au plus ancien. */
    List<PortalLink> findByDossierId(UUID dossierId);

    /** Liens vivants ({@link PortalLinkStatus#isLive()}) d'une tâche — au plus un en pratique. */
    List<PortalLink> findLiveByTaskId(String taskId);

    List<PortalLink> findByStatus(PortalLinkStatus status, int limit);

    List<PortalLink> findActiveExpiringBefore(Instant now, int limit);

    int deleteAllByDossierId(UUID dossierId);
}
