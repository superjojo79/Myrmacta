package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.portal.model.PortalLinkStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPortalLinkIdentityRepository extends JpaRepository<PortalLinkIdentityJpaEntity, UUID> {

    Optional<PortalLinkIdentityJpaEntity> findByProofHash(String proofHash);

    List<PortalLinkIdentityJpaEntity> findByLinkIdIn(Collection<UUID> linkIds);

    /** La soumission efface la preuve et le numéro national ; la trace reste. Une seule fois. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PortalLinkIdentityJpaEntity i
            set i.submittedAt = :now, i.proofHash = null, i.nrnEnc = null, i.expiresAt = null
            where i.linkId = :linkId and i.submittedAt is null
            """)
    int markSubmitted(@Param("linkId") UUID linkId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PortalLinkIdentityJpaEntity i where i.linkId = :linkId and i.submittedAt is null")
    int deleteUnsubmitted(@Param("linkId") UUID linkId);

    /** Les identités non soumises expirées, ou dont le lien n'est plus actif. La trace d'une soumission reste. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from PortalLinkIdentityJpaEntity i
            where i.submittedAt is null
              and (i.expiresAt <= :now
                   or not exists (select 1 from PortalLinkJpaEntity l where l.id = i.linkId and l.status = :active))
            """)
    int purgeUnsubmittedExpiredOrInactive(@Param("now") Instant now, @Param("active") PortalLinkStatus active);
}
