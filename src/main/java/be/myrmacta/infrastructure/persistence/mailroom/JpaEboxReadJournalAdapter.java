package be.myrmacta.infrastructure.persistence.mailroom;

import be.myrmacta.domain.mailroom.model.EboxTakenMessage;
import be.myrmacta.domain.mailroom.port.out.EboxReadJournal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** TODO §150 (D-EBOX-JOURNAL, D-EBOX-WINDOW) et §122 — journal de lecture de l'eBox Enterprise, en JPA. */
@Component
class JpaEboxReadJournalAdapter implements EboxReadJournal {

    /** Taille d'un lot de la clause {@code in} : une page eBox compte au plus 100 messages. */
    private static final int BATCH = 200;

    private final SpringDataEboxMessageJournalRepository journal;
    private final SpringDataEboxReadCursorRepository cursors;

    JpaEboxReadJournalAdapter(SpringDataEboxMessageJournalRepository journal, SpringDataEboxReadCursorRepository cursors) {
        this.journal = journal;
        this.cursors = cursors;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> alreadyTaken(String tenantId, String enterpriseNumber, String registryId, Collection<String> messageIds) {
        Set<String> taken = new HashSet<>();
        List<String> ids = List.copyOf(new HashSet<>(messageIds));
        for (int from = 0; from < ids.size(); from += BATCH) {
            taken.addAll(journal.findTaken(tenantId, enterpriseNumber, registryId, ids.subList(from, Math.min(ids.size(), from + BATCH))));
        }
        return taken;
    }

    @Override
    @Transactional
    public boolean recordTaken(EboxTakenMessage message) {
        return journal.insertIfAbsent(UUID.randomUUID(), message.tenantId(), message.enterpriseNumber(), message.registryId(),
                message.messageId(), message.partition(), message.connectorInstanceId(), message.takenAt()) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> readUntil(UUID connectorInstanceId, String registryId, String partition) {
        return cursors.find(connectorInstanceId, registryId, partition).map(EboxReadCursorJpaEntity::readUntil);
    }

    @Override
    @Transactional
    public void advance(UUID connectorInstanceId, String registryId, String partition, LocalDate readUntil) {
        cursors.upsert(UUID.randomUUID(), connectorInstanceId, registryId, partition, readUntil);
    }
}
