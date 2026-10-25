package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierElement;
import java.util.UUID;

public interface GetDossierElementUseCase {

    DossierElement get(UUID id, AccessContext ctx);
}
