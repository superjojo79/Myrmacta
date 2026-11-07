package be.myrmacta.infrastructure.persistence.workflow;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPortalIdentityAttemptRepository extends JpaRepository<PortalIdentityAttemptJpaEntity, UUID> {

    /** Usage unique, atomique : seule une des tentatives concurrentes voit une ligne modifiée. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PortalIdentityAttemptJpaEntity a set a.consumedAt = :now
            where a.stateHash = :stateHash and a.consumedAt is null and a.expiresAt > :now
            """)
    int consume(@Param("stateHash") String stateHash, @Param("now") Instant now);

    Optional<PortalIdentityAttemptJpaEntity> findByStateHash(String stateHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PortalIdentityAttemptJpaEntity a where a.expiresAt <= :now or a.consumedAt is not null")
    int purgeExpiredOrConsumed(@Param("now") Instant now);
}
