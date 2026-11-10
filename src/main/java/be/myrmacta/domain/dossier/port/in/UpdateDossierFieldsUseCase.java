package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UpdateDossierFieldsUseCase {
    UpdateDossierFieldsResult update(UpdateDossierFieldsCommand command, AccessContext ctx);

    /**
     * @param workflowMapping {@code true} seulement depuis {@code fieldMappingTask} (TODO §126, D-MANAGED-WRITE) :
     *     autorise l'écriture des champs {@code managedByWorkflow}
     */
    record UpdateDossierFieldsCommand(
            UUID dossierId,
            Map<String, Object> fieldsPatch,
            String updatedBy,
            Long expectedVersion,
            boolean workflowMapping) {
        public UpdateDossierFieldsCommand(
                UUID dossierId, Map<String, Object> fieldsPatch, String updatedBy, Long expectedVersion) {
            this(dossierId, fieldsPatch, updatedBy, expectedVersion, false);
        }
    }

    record UpdateDossierFieldsResult(Dossier dossier, List<String> sensitiveFieldsChanged) {}
}
