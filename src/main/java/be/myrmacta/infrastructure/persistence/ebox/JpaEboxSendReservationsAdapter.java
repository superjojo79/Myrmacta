package be.myrmacta.infrastructure.persistence.ebox;

import be.myrmacta.domain.ebox.port.out.EboxSendReservations;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * TODO §155 (D-EBOX-SEND-REGISTERED) et §122 — réservation de clés de recommandé, en JPA. Chaque opération est validée tout de
 * suite ({@code REQUIRES_NEW}) : une tentative concurrente voit la réservation avant que le recommandé ne parte.
 */
@Component
class JpaEboxSendReservationsAdapter implements EboxSendReservations {

    private final SpringDataEboxSendReservationRepository repository;

    JpaEboxSendReservationsAdapter(SpringDataEboxSendReservationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserve(String tenantId, String idempotencyKey, Instant at) {
        return repository.insertIfAbsent(UUID.randomUUID(), tenantId, idempotencyKey, at) > 0;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String tenantId, String idempotencyKey) {
        repository.release(tenantId, idempotencyKey);
    }
}
