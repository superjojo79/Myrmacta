package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierElement;
import java.util.UUID;

public interface GetDossierElementUseCase {

    DossierElement get(UUID id, AccessContext ctx);
}
