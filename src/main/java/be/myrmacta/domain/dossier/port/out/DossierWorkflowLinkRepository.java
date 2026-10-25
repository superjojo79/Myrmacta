package be.myrmacta.domain.dossier.port.out;

import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DossierWorkflowLinkRepository {

    DossierWorkflowLink save(DossierWorkflowLink link);

    Optional<DossierWorkflowLink> findById(UUID id);

    Optional<DossierWorkflowLink> findByProcessInstanceId(String tenantId, String processInstanceId);

    /** TODO §94 (revue L4) — liens de plusieurs instances en une lecture ; défaut : une lecture par instance. */
    default List<DossierWorkflowLink> findByProcessInstanceIds(String tenantId, java.util.Collection<String> processInstanceIds) {
        return processInstanceIds.stream()
                .map(id -> findByProcessInstanceId(tenantId, id))
                .flatMap(Optional::stream)
                .toList();
    }

    Optional<DossierWorkflowLink> findActiveByDossierId(UUID dossierId);

    Optional<DossierWorkflowLink> findReservedByDossierAndProcess(UUID dossierId, String processDefinitionKey);

    List<DossierWorkflowLink> findByDossierIdOrderBySequence(UUID dossierId);

    PageResult<DossierWorkflowLink> pageByDossierId(UUID dossierId, PageQuery query);

    int nextSequence(UUID dossierId);

    List<DossierWorkflowLink> findOrphanReserved(Instant olderThan);

    /**
     * TODO §79 (D5) : prend le lien orphelin pour le réconcilier (bail jusqu'à {@code leaseUntil}). Un seul des réconciliateurs
     * concurrents obtient {@code true} ; une prise échue (exécutant disparu) peut être reprise. {@code false} aussi si le
     * lien n'est plus orphelin (démarré ou terminé entre-temps).
     */
    boolean claimForReconcile(UUID linkId, Instant now, Instant leaseUntil);

    void deleteAllByDossierId(UUID dossierId);
}
