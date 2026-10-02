package com.flowforge.domain.workflow.portal.usecase;

import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.model.PortalLinkStatus;
import com.flowforge.domain.workflow.portal.port.out.PortalLinkRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Dépôt en mémoire fidèle au contrat compare-and-set de {@link PortalLinkRepository}. */
final class InMemoryPortalLinkRepository implements PortalLinkRepository {

    final List<PortalLink> links = new ArrayList<>();
    /** Simule une transition perdue face à un écrivain concurrent (UPDATE conditionnel à 0 ligne). */
    boolean loseEveryRace;

    @Override
    public void insert(PortalLink link) {
        if (link.status().isLive() && !findLiveByTaskId(link.taskId()).isEmpty()) {
            throw new com.flowforge.domain.workflow.portal.port.out.PortalLinkConflictException("live", null);
        }
        links.add(link);
    }

    /** Prises en cours (id → échéance du bail) : fidèle à l'UPDATE conditionnel de l'adaptateur JPA. */
    final java.util.Map<UUID, java.time.Instant> sendClaims = new java.util.HashMap<>();

    @Override
    public boolean claimForSend(PortalLink link, java.time.Instant now, java.time.Duration lease) {
        PortalLink stored = links.stream().filter(l -> l.id().equals(link.id())).findFirst().orElse(null);
        if (stored == null || stored.status() != com.flowforge.domain.workflow.portal.model.PortalLinkStatus.PENDING_SEND) {
            return false;
        }
        java.time.Instant until = sendClaims.get(link.id());
        if (until != null && !until.isBefore(now)) {
            return false;
        }
        sendClaims.put(link.id(), now.plus(lease));
        return true;
    }

    @Override
    public boolean transition(PortalLink current, PortalLink next) {
        if (loseEveryRace) {
            return false;
        }
        for (int i = 0; i < links.size(); i++) {
            PortalLink stored = links.get(i);
            if (stored.id().equals(next.id())) {
                if (stored.status() != current.status()) {
                    return false;
                }
                links.set(i, next);
                sendClaims.remove(next.id()); // toute transition lève la prise
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<PortalLink> findById(UUID id) {
        return links.stream().filter(l -> l.id().equals(id)).findFirst();
    }

    @Override
    public Optional<PortalLink> findByTokenHash(String tokenHash) {
        return links.stream().filter(l -> tokenHash != null && tokenHash.equals(l.tokenHash())).findFirst();
    }

    @Override
    public List<PortalLink> findByDossierId(UUID dossierId) {
        return links.stream().filter(l -> l.dossierId().equals(dossierId)).toList();
    }

    @Override
    public List<PortalLink> findLiveByTaskId(String taskId) {
        return links.stream().filter(l -> l.taskId().equals(taskId) && l.status().isLive()).toList();
    }

    @Override
    public List<PortalLink> findByStatus(PortalLinkStatus status, int limit) {
        return links.stream().filter(l -> l.status() == status).limit(limit).toList();
    }

    @Override
    public List<PortalLink> findActiveExpiringBefore(Instant now, int limit) {
        return links.stream()
                .filter(l -> l.status() == PortalLinkStatus.ACTIVE && !l.expiresAt().isAfter(now))
                .limit(limit)
                .toList();
    }

    @Override
    public int deleteAllByDossierId(UUID dossierId) {
        int before = links.size();
        links.removeIf(l -> l.dossierId().equals(dossierId));
        return before - links.size();
    }
}
