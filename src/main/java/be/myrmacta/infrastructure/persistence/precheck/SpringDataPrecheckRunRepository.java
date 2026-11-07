package be.myrmacta.infrastructure.persistence.precheck;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPrecheckRunRepository extends JpaRepository<PrecheckRunJpaEntity, UUID> {

    Optional<PrecheckRunJpaEntity> findByIdAndTenantId(UUID id, String tenantId);

    @Query("select r from PrecheckRunJpaEntity r where r.dossierId = :dossierId and r.tenantId = :tenantId order by r.createdAt desc, r.id")
    List<PrecheckRunJpaEntity> listByDossier(@Param("dossierId") UUID dossierId, @Param("tenantId") String tenantId, Limit limit);

    /** Une relance (bouton, MCP) en file ou en cours ; la brique du workflow n'y est pas soumise (C2). */
    @Query("""
            select count(r) > 0 from PrecheckRunJpaEntity r
            where r.dossierId = :dossierId and r.status in ('PENDING', 'RUNNING') and r.triggerKind <> 'WORKFLOW'
            """)
    boolean hasActiveRequest(@Param("dossierId") UUID dossierId);

    /** Verrou consultatif de la transaction, par dossier : sérialise le contrôle puis l'insertion d'une relance. */
    @Query(nativeQuery = true, value = "select count(*) from (select pg_advisory_xact_lock(hashtext(current_schema() || ':precheck:' || cast(:dossierId as text)))) l")
    long lockDossier(@Param("dossierId") UUID dossierId);

    /** F4 : n'aboutit que si la tentative est encore celle qui détient l'exécution. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PrecheckRunJpaEntity r set r.status = :status, r.failure = :failure, r.provider = :provider, r.model = :model,
                r.promptVersion = :promptVersion, r.inputSha256 = :inputSha256, r.summary = :summary, r.tokensIn = :tokensIn,
                r.tokensOut = :tokensOut, r.discarded = :discarded, r.finishedAt = :finishedAt, r.leaseUntil = null
            where r.id = :id and r.status = 'RUNNING' and r.attempts = :attempts
            """)
    int complete(@Param("id") UUID id, @Param("attempts") int attempts, @Param("status") String status,
            @Param("failure") String failure, @Param("provider") String provider, @Param("model") String model,
            @Param("promptVersion") String promptVersion, @Param("inputSha256") String inputSha256,
            @Param("summary") String summary, @Param("tokensIn") long tokensIn, @Param("tokensOut") long tokensOut,
            @Param("discarded") int discarded, @Param("finishedAt") Instant finishedAt);

    /** Prend la plus ancienne exécution disponible (en file, ou au bail expiré) sans attendre les autres instances. */
    @Query(nativeQuery = true, value = """
            update precheck_run set status = 'RUNNING', attempts = attempts + 1, lease_until = :leaseUntil
            where id = (select id from precheck_run where tenant_id = :tenantId and trigger_kind <> 'WORKFLOW'
                and (status = 'PENDING' or (status = 'RUNNING' and lease_until < :now))
                order by created_at limit 1 for update skip locked)
            returning *
            """)
    Optional<PrecheckRunJpaEntity> lockNext(@Param("tenantId") String tenantId, @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil);

    @Query("select count(r) from PrecheckRunJpaEntity r where r.dossierId = :dossierId and r.tenantId = :tenantId and r.triggerKind <> 'WORKFLOW' and r.createdAt >= :since")
    int countRequestsSince(@Param("dossierId") UUID dossierId, @Param("tenantId") String tenantId, @Param("since") Instant since);

    @Query("select coalesce(sum(r.tokensIn + r.tokensOut), 0) from PrecheckRunJpaEntity r where r.tenantId = :tenantId and r.finishedAt >= :since")
    long tokensSince(@Param("tenantId") String tenantId, @Param("since") Instant since);
}
