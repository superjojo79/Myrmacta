package be.myrmacta.infrastructure.persistence.signature;

import be.myrmacta.domain.signature.model.SignerKind;
import be.myrmacta.domain.signature.model.SignerStatus;
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

interface SpringDataSignerRepository extends JpaRepository<SignerJpaEntity, UUID> {

    List<SignerJpaEntity> findByRequestIdOrderByPositionAsc(UUID requestId);

    Optional<SignerJpaEntity> findByTokenHash(String tokenHash);

    List<SignerJpaEntity> findByKindAndUserIdAndStatusInOrderByIdDesc(
            SignerKind kind, String userId, Collection<SignerStatus> statuses);

    @Query("select s from SignerJpaEntity s where s.kind = :kind and s.status = :status"
            + " and s.inviteSentAt is null and s.inviteAttempts < :maxAttempts order by s.id")
    List<SignerJpaEntity> findInvitable(
            @Param("kind") SignerKind kind,
            @Param("status") SignerStatus status,
            @Param("maxAttempts") int maxAttempts,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update SignerJpaEntity e set
              e.tokenHash = :tokenHash, e.status = :status, e.inviteSentAt = :inviteSentAt,
              e.inviteAttempts = :inviteAttempts, e.decidedAt = :decidedAt, e.declineReason = :declineReason,
              e.version = e.version + 1
            where e.id = :id and e.version = :version
            """)
    int cas(
            @Param("id") UUID id,
            @Param("version") long version,
            @Param("tokenHash") String tokenHash,
            @Param("status") SignerStatus status,
            @Param("inviteSentAt") Instant inviteSentAt,
            @Param("inviteAttempts") int inviteAttempts,
            @Param("decidedAt") Instant decidedAt,
            @Param("declineReason") String declineReason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SignerJpaEntity s where s.requestId in"
            + " (select r.id from SignatureRequestJpaEntity r where r.dossierId = :dossierId)")
    int deleteByDossierId(@Param("dossierId") UUID dossierId);
}
