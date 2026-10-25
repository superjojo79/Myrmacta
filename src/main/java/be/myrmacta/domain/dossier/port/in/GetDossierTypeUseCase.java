package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierType;

public interface GetDossierTypeUseCase {
    DossierType getByCode(String tenantId, String code, AccessContext ctx);
}
