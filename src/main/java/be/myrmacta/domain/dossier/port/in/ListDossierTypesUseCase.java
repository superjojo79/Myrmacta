package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;

public interface ListDossierTypesUseCase {
    PageResult<DossierType> list(String tenantId, AccessContext ctx, PageQuery query);
}
