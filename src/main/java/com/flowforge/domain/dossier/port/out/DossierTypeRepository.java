package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface DossierTypeRepository {
    void save(DossierType type);

    Optional<DossierType> findById(UUID id);

    Optional<DossierType> findByCode(String tenantId, String code);

    List<DossierType> findAll(String tenantId);

    /**
     * @param allowedCodes {@code null} = all types for the tenant; empty = empty page
     */
    default PageResult<DossierType> page(String tenantId, Set<String> allowedCodes, PageQuery query) {
        if (allowedCodes != null && allowedCodes.isEmpty()) {
            return PageResult.empty(query.page(), query.size());
        }
        List<DossierType> all = findAll(tenantId);
        if (allowedCodes != null) {
            all = all.stream().filter(type -> allowedCodes.contains(type.code())).toList();
        }
        return PageResult.slice(all, query);
    }
}
