package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import java.util.UUID;

public interface GetDossierUseCase {
    Dossier get(UUID id, AccessContext ctx);

    Dossier getByReference(String tenantId, String reference, AccessContext ctx);
}
