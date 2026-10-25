package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import java.util.UUID;

/** TENANT_ADMIN enqueue of an administrative purge (origin {@code ADMIN}). */
public interface EnqueueDossierPurgeUseCase {

    enum Result {
        CREATED,
        ALREADY_EXISTS
    }

    Result enqueue(UUID dossierId, AccessContext.User actor);
}
