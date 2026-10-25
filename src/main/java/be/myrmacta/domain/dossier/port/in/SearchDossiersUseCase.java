package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierPage;
import be.myrmacta.domain.dossier.model.SearchDossiersQuery;

public interface SearchDossiersUseCase {
    DossierPage search(String tenantId, SearchDossiersQuery query, AccessContext ctx);
}
