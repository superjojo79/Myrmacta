package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import java.util.UUID;

public interface GetDossierUseCase {
    Dossier get(UUID id, AccessContext ctx);

    Dossier getByReference(String tenantId, String reference, AccessContext ctx);
}
