package be.myrmacta.domain.dossier.port.out;

import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DossierElementRepository {

    DossierElement save(DossierElement element);

    Optional<DossierElement> findByIdAndTenantId(UUID id, String tenantId);

    List<DossierElement> findByDossierIdOrderByAddedAtAsc(UUID dossierId);

    PageResult<DossierElement> pageByDossierId(UUID dossierId, PageQuery query);

    void deleteAllByDossierId(UUID dossierId);
}
