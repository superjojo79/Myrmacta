package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierElementView;
import java.util.List;
import java.util.UUID;

public interface ListDossierElementsUseCase {

    List<DossierElementView> list(UUID dossierId, AccessContext ctx);
}
