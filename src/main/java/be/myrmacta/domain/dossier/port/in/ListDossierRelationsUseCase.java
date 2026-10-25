package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierRelationView;
import java.util.List;
import java.util.UUID;

public interface ListDossierRelationsUseCase {

    List<DossierRelationView> list(UUID dossierId, AccessContext ctx);
}
