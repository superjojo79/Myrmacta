package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierRelationView;
import java.util.List;
import java.util.UUID;

public interface ListDossierRelationsUseCase {

    List<DossierRelationView> list(UUID dossierId, AccessContext ctx);
}
