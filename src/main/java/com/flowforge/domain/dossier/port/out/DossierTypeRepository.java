package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.DossierType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DossierTypeRepository {
    void save(DossierType type);

    Optional<DossierType> findById(UUID id);

    Optional<DossierType> findByCode(String tenantId, String code);

    List<DossierType> findAll(String tenantId);
}
