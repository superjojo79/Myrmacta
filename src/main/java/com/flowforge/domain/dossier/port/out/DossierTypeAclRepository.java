package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.DossierTypeAclEntry;
import java.util.List;
import java.util.Optional;

public interface DossierTypeAclRepository {
    Optional<DossierTypeAclEntry> find(String tenantId, String dossierTypeCode, String subjectUserId);

    void save(DossierTypeAclEntry entry);

    void delete(String tenantId, String dossierTypeCode, String subjectUserId);

    List<DossierTypeAclEntry> findBySubject(String tenantId, String subjectUserId);
}
