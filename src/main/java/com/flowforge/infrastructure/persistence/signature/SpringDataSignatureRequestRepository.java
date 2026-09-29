package com.flowforge.infrastructure.persistence.signature;

import com.flowforge.domain.signature.model.SignatureStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataSignatureRequestRepository extends JpaRepository<SignatureRequestJpaEntity, UUID> {

    Optional<SignatureRequestJpaEntity> findFirstByProviderReference(String providerReference);

    Optional<SignatureRequestJpaEntity> findFirstByProcessInstanceIdAndActivityIdAndStatus(
            String processInstanceId, String activityId, SignatureStatus status);

    List<SignatureRequestJpaEntity> findByProcessInstanceIdAndStatus(String processInstanceId, SignatureStatus status);

    List<SignatureRequestJpaEntity> findByDossierIdOrderByCreatedAtDesc(UUID dossierId);

    List<SignatureRequestJpaEntity> findByStatusAndDeadlineAtLessThanEqualOrderByDeadlineAtAsc(
            SignatureStatus status, Instant now, Pageable pageable);

    @Query("select r from SignatureRequestJpaEntity r where r.status <> :pending and r.outcomeDispatchedAt is null"
            + " and r.dispatchAttempts < :maxAttempts order by r.completedAt asc")
    List<SignatureRequestJpaEntity> findTerminalNotDispatched(
            @Param("pending") SignatureStatus pending, @Param("maxAttempts") int maxAttempts, Pageable pageable);

    /** Compare-and-set sur la version : champs mutables uniquement. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update SignatureRequestJpaEntity e set
              e.status = :status, e.statusReason = :statusReason, e.providerReference = :providerReference,
              e.completedAt = :completedAt, e.outcomeDispatchedAt = :outcomeDispatchedAt,
              e.dispatchAttempts = :dispatchAttempts, e.simulated = :simulated, e.assuranceLevel = :assuranceLevel,
              e.version = e.version + 1
            where e.id = :id and e.version = :version
            """)
    int cas(
            @Param("id") UUID id,
            @Param("version") long version,
            @Param("status") SignatureStatus status,
            @Param("statusReason") String statusReason,
            @Param("providerReference") String providerReference,
            @Param("completedAt") Instant completedAt,
            @Param("outcomeDispatchedAt") Instant outcomeDispatchedAt,
            @Param("dispatchAttempts") int dispatchAttempts,
            @Param("simulated") boolean simulated,
            @Param("assuranceLevel") String assuranceLevel);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SignatureRequestJpaEntity r where r.dossierId = :dossierId")
    int deleteByDossierId(@Param("dossierId") UUID dossierId);
}
