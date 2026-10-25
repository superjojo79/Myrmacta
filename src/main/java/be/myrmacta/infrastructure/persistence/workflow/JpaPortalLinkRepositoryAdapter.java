package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.portal.model.PortalLink;
import be.myrmacta.domain.workflow.portal.model.PortalLinkStatus;
import be.myrmacta.domain.workflow.portal.port.out.PortalLinkConflictException;
import be.myrmacta.domain.workflow.portal.port.out.PortalLinkRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaPortalLinkRepositoryAdapter implements PortalLinkRepository {

    private static final EnumSet<PortalLinkStatus> LIVE =
            EnumSet.of(PortalLinkStatus.PENDING_SEND, PortalLinkStatus.ACTIVE, PortalLinkStatus.NOT_SENT);

    private final SpringDataPortalLinkRepository repository;

    JpaPortalLinkRepositoryAdapter(SpringDataPortalLinkRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void insert(PortalLink link) {
        Objects.requireNonNull(link, "link");
        try {
            repository.saveAndFlush(PortalLinkJpaEntity.fromDomain(link));
        } catch (DataIntegrityViolationException ex) {
            // ux_portal_link_task_live : un second lien vivant pour la même tâche (course entre
            // deux renvois) perd au niveau base — exigence H2.
            throw new PortalLinkConflictException("a live portal link already exists for task " + link.taskId(), ex);
        }
    }

    @Override
    @Transactional
    public boolean transition(PortalLink current, PortalLink next) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(next, "next");
        if (!current.id().equals(next.id())) {
            throw new IllegalArgumentException("transition must keep the same link id");
        }
        return repository.transition(
                        next.id(),
                        current.status(),
                        next.tokenHash(),
                        next.status(),
                        next.statusReason(),
                        next.expiresAt(),
                        next.sendAttempts(),
                        next.sentAt(),
                        next.lastAccessedAt(),
                        next.completedAt(),
                        next.revokedAt(),
                        next.revokedBy())
                == 1;
    }

    // Sans transaction englobante : la prise est un UPDATE atomique propre au dépôt.
    @Override
    public boolean claimForSend(PortalLink link, Instant now, java.time.Duration lease) {
        return repository.claimForSend(link.id(), PortalLinkStatus.PENDING_SEND, now, now.plus(lease)) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PortalLink> findById(UUID id) {
        return repository.findById(id).map(PortalLinkJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PortalLink> findByTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(tokenHash).map(PortalLinkJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortalLink> findByDossierId(UUID dossierId) {
        return repository.findByDossierIdOrderByCreatedAtDesc(dossierId).stream()
                .map(PortalLinkJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortalLink> findLiveByTaskId(String taskId) {
        return repository.findByTaskIdAndStatusIn(taskId, LIVE).stream()
                .map(PortalLinkJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortalLink> findByStatus(PortalLinkStatus status, int limit) {
        return repository.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(0, limit)).stream()
                .map(PortalLinkJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortalLink> findActiveExpiringBefore(Instant now, int limit) {
        return repository.findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        PortalLinkStatus.ACTIVE, now, PageRequest.of(0, limit))
                .stream()
                .map(PortalLinkJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public int deleteAllByDossierId(UUID dossierId) {
        return repository.deleteAllByDossierId(Objects.requireNonNull(dossierId, "dossierId"));
    }
}
