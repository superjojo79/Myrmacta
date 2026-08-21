package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DossierWorkflowLinkRepository {

    DossierWorkflowLink save(DossierWorkflowLink link);

    Optional<DossierWorkflowLink> findById(UUID id);

    Optional<DossierWorkflowLink> findByProcessInstanceId(String tenantId, String processInstanceId);

    Optional<DossierWorkflowLink> findActiveByDossierId(UUID dossierId);

    Optional<DossierWorkflowLink> findReservedByDossierAndProcess(UUID dossierId, String processDefinitionKey);

    List<DossierWorkflowLink> findByDossierIdOrderBySequence(UUID dossierId);

    PageResult<DossierWorkflowLink> pageByDossierId(UUID dossierId, PageQuery query);

    int nextSequence(UUID dossierId);

    List<DossierWorkflowLink> findOrphanReserved(Instant olderThan);

    void deleteAllByDossierId(UUID dossierId);
}
