package be.myrmacta.infrastructure.persistence.precheck;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPrecheckFindingRepository extends JpaRepository<PrecheckFindingJpaEntity, UUID> {

    List<PrecheckFindingJpaEntity> findByRunIdInOrderByOrdinal(Collection<UUID> runIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PrecheckFindingJpaEntity f where f.runId = :runId")
    int deleteByRun(@Param("runId") UUID runId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PrecheckFindingJpaEntity f set f.decision = :decision, f.decidedBy = :by, f.decidedAt = :at
            where f.id = :id and f.runId = :runId
            """)
    int decide(@Param("runId") UUID runId, @Param("id") UUID id, @Param("decision") String decision,
            @Param("by") String by, @Param("at") Instant at);
}
