package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.portal.model.IdentityVerification;
import be.myrmacta.domain.workflow.portal.model.PortalIdentityAttempt;
import be.myrmacta.domain.workflow.portal.model.PortalLinkIdentity;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatus;
import be.myrmacta.domain.workflow.portal.port.out.PortalIdentityRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * TODO §104 (D-BEAUTH-STORE) et §122 — tentatives d'authentification forte et identités des liens du portail, en JPA.
 * L'usage unique d'une tentative et la soumission d'une identité sont des mises à jour conditionnelles : le nombre de
 * lignes modifiées arbitre les appels concurrents.
 */
@Component
class JpaPortalIdentityRepositoryAdapter implements PortalIdentityRepository {

    private final SpringDataPortalIdentityAttemptRepository attempts;
    private final SpringDataPortalLinkIdentityRepository identities;

    JpaPortalIdentityRepositoryAdapter(SpringDataPortalIdentityAttemptRepository attempts, SpringDataPortalLinkIdentityRepository identities) {
        this.attempts = attempts;
        this.identities = identities;
    }

    @Override
    @Transactional
    public void insertAttempt(PortalIdentityAttempt attempt) {
        attempts.saveAndFlush(PortalIdentityAttemptJpaEntity.fromDomain(attempt));
    }

    @Override
    @Transactional
    public Optional<PortalIdentityAttempt> consumeAttempt(String stateHash, Instant now) {
        if (attempts.consume(stateHash, now) != 1) {
            return Optional.empty();
        }
        return attempts.findByStateHash(stateHash).map(PortalIdentityAttemptJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void saveIdentity(PortalLinkIdentity identity) {
        PortalLinkIdentityJpaEntity entity = identities.findById(identity.linkId()).orElseGet(() -> PortalLinkIdentityJpaEntity.fromDomain(identity));
        entity.apply(identity);
        identities.saveAndFlush(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PortalLinkIdentity> findByProofHash(String proofHash) {
        return identities.findByProofHash(proofHash).map(PortalLinkIdentityJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PortalLinkIdentity> findByLinkId(UUID linkId) {
        return identities.findById(linkId).map(PortalLinkIdentityJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public boolean markSubmitted(UUID linkId, Instant now) {
        return identities.markSubmitted(linkId, now) == 1;
    }

    @Override
    @Transactional
    public void deleteUnsubmitted(UUID linkId) {
        identities.deleteUnsubmitted(linkId);
    }

    @Override
    @Transactional
    public int purgeExpired(Instant now) {
        return attempts.purgeExpiredOrConsumed(now) + identities.purgeUnsubmittedExpiredOrInactive(now, PortalLinkStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, IdentityVerification> tracesByLinkId(Collection<UUID> linkIds) {
        Map<UUID, IdentityVerification> traces = new LinkedHashMap<>();
        if (linkIds == null || linkIds.isEmpty()) {
            return traces;
        }
        for (PortalLinkIdentityJpaEntity entity : identities.findByLinkIdIn(linkIds)) {
            PortalLinkIdentity identity = entity.toDomain();
            traces.put(identity.linkId(), identity.trace());
        }
        return traces;
    }
}
