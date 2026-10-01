package com.flowforge.infrastructure.persistence.parcours;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataParcoursRepository extends JpaRepository<ParcoursJpaEntity, UUID> {

    Optional<ParcoursJpaEntity> findByTenantIdAndKey(String tenantId, String key);

    List<ParcoursJpaEntity> findByTenantIdOrderByKeyAsc(String tenantId);

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ParcoursJpaEntity p where p.tenantId = :tenantId and p.key = :key")
    int deleteByKey(@Param("tenantId") String tenantId, @Param("key") String key);

    /** Mise à jour conditionnelle : ne réussit que si la version lue est encore la courante. */
    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ParcoursJpaEntity p set p.label = :label, p.definition = :definition, p.version = :next, p.updatedBy = :by, "
            + "p.updatedAt = :at where p.tenantId = :tenantId and p.key = :key and p.version = :expected")
    int updateIfVersion(
            @Param("tenantId") String tenantId,
            @Param("key") String key,
            @Param("expected") int expected,
            @Param("next") int next,
            @Param("label") String label,
            @Param("definition") String definition,
            @Param("by") String by,
            @Param("at") Instant at);

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ParcoursJpaEntity p set p.lastCompiledAt = :at, p.lastCompiledSha256 = :sha "
            + "where p.tenantId = :tenantId and p.key = :key")
    int markCompiled(@Param("tenantId") String tenantId, @Param("key") String key, @Param("at") Instant at, @Param("sha") String sha);
}
