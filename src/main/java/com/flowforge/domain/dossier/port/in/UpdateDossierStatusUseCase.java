package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import java.util.UUID;

public interface UpdateDossierStatusUseCase {
    Dossier update(UpdateDossierStatusCommand command, AccessContext ctx);

    record UpdateDossierStatusCommand(
            UUID dossierId, DossierStatus status, String updatedBy, Long expectedVersion) {}
}
