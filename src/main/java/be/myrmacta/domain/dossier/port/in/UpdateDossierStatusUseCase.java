package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import java.util.UUID;

public interface UpdateDossierStatusUseCase {
    Dossier update(UpdateDossierStatusCommand command, AccessContext ctx);

    record UpdateDossierStatusCommand(
            UUID dossierId, DossierStatus status, String updatedBy, Long expectedVersion) {}
}
