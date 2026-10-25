package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.model.OutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    /** Lignes à traiter : en attente, et non louées par un autre exécutant (bail absent ou échu). */
    @Query("""
            select e from OutboxEventJpaEntity e
            where e.status = :status and (e.claimedUntil is null or e.claimedUntil < :now)
            order by e.createdAt asc, e.eventId asc
            """)
    List<OutboxEventJpaEntity> findClaimable(
            @Param("status") OutboxStatus status, @Param("now") Instant now, Pageable pageable);

    /**
     * D5 (TODO §79) : prise par UPDATE conditionnel — un seul des exécutants concurrents obtient 1 ligne modifiée ; les autres
     * sautent la ligne. Le bail ({@code leaseUntil}) rend la ligne à nouveau éligible si l'exécutant disparaît.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update OutboxEventJpaEntity e set e.claimedUntil = :leaseUntil
            where e.eventId = :id and e.status = :status and (e.claimedUntil is null or e.claimedUntil < :now)
            """)
    int claim(
            @Param("id") UUID id,
            @Param("status") OutboxStatus status,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil);

    int deleteByStatusAndProcessedAtBefore(OutboxStatus status, Instant before);

    @Query("select min(e.createdAt) from OutboxEventJpaEntity e where e.status = :status")
    Optional<Instant> findOldestCreatedAtByStatus(@Param("status") OutboxStatus status);
}
