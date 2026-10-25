package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierElementView;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.util.UUID;

public interface ListDossierElementsUseCase {

    PageResult<DossierElementView> list(UUID dossierId, AccessContext ctx, PageQuery query);
}
