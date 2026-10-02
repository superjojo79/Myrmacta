package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.portal.model.PortalLinkStatus;
import com.flowforge.domain.workflow.portal.model.PortalLinkStatusReason;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPortalLinkRepository extends JpaRepository<PortalLinkJpaEntity, UUID> {

    Optional<PortalLinkJpaEntity> findByTokenHash(String tokenHash);

    List<PortalLinkJpaEntity> findByDossierIdOrderByCreatedAtDesc(UUID dossierId);

    List<PortalLinkJpaEntity> findByTaskIdAndStatusIn(String taskId, Collection<PortalLinkStatus> statuses);

    List<PortalLinkJpaEntity> findByStatusOrderByCreatedAtAsc(PortalLinkStatus status, Pageable pageable);

    List<PortalLinkJpaEntity> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            PortalLinkStatus status, Instant now, Pageable pageable);

    /** Compare-and-set sur le statut : toutes les colonnes mutables, seulement si le statut n'a pas bougé. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update PortalLinkJpaEntity e set
              e.tokenHash = :tokenHash, e.status = :status, e.statusReason = :statusReason,
              e.expiresAt = :expiresAt, e.sendAttempts = :sendAttempts, e.sentAt = :sentAt,
              e.lastAccessedAt = :lastAccessedAt, e.completedAt = :completedAt,
              e.revokedAt = :revokedAt, e.revokedBy = :revokedBy, e.claimedUntil = null
            where e.id = :id and e.status = :expectedStatus
            """)
    int transition(
            @Param("id") UUID id,
            @Param("expectedStatus") PortalLinkStatus expectedStatus,
            @Param("tokenHash") String tokenHash,
            @Param("status") PortalLinkStatus status,
            @Param("statusReason") PortalLinkStatusReason statusReason,
            @Param("expiresAt") Instant expiresAt,
            @Param("sendAttempts") int sendAttempts,
            @Param("sentAt") Instant sentAt,
            @Param("lastAccessedAt") Instant lastAccessedAt,
            @Param("completedAt") Instant completedAt,
            @Param("revokedAt") Instant revokedAt,
            @Param("revokedBy") String revokedBy);

    /** D5 : prise par UPDATE conditionnel (statut inchangé, bail absent ou échu) — un seul exécutant obtient 1 ligne. */
    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update PortalLinkJpaEntity e set e.claimedUntil = :until
            where e.id = :id and e.status = :status and (e.claimedUntil is null or e.claimedUntil < :now)
            """)
    int claimForSend(
            @Param("id") UUID id,
            @Param("status") PortalLinkStatus status,
            @Param("now") Instant now,
            @Param("until") Instant until);

    @Modifying(clearAutomatically = true)
    @Query("delete from PortalLinkJpaEntity e where e.dossierId = :dossierId")
    int deleteAllByDossierId(@Param("dossierId") UUID dossierId);
}
