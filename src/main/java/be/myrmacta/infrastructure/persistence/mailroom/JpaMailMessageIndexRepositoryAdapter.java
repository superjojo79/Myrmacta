package be.myrmacta.infrastructure.persistence.mailroom;

import be.myrmacta.domain.mailroom.model.MailIndexEntry;
import be.myrmacta.domain.mailroom.port.out.MailMessageIndex;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** TODO §113 (D-MAILROOM-INDEX) et §122 — index des e-mails reçus et envoyés, en JPA. */
@Component
class JpaMailMessageIndexRepositoryAdapter implements MailMessageIndex {

    private final SpringDataMailMessageIndexRepository repository;

    JpaMailMessageIndexRepositoryAdapter(SpringDataMailMessageIndexRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void record(MailIndexEntry entry) {
        repository.recordUnlessShadowing(UUID.randomUUID(), entry.tenantId(), entry.kind().name(), entry.key(), entry.direction().name(),
                entry.dossierId(), entry.counterpart(), entry.recordedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MailIndexEntry> find(String tenantId, MailIndexEntry.Kind kind, String key) {
        return repository.findByKey(tenantId, kind.name(), key, Limit.of(1)).stream().findFirst().map(MailMessageIndexJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MailIndexEntry> findByDossier(String tenantId, UUID dossierId, int limit) {
        return repository.findNewestOfDossier(tenantId, dossierId, Limit.of(Math.max(1, Math.min(limit, 200)))).stream()
                .map(MailMessageIndexJpaEntity::toDomain)
                .toList();
    }
}
