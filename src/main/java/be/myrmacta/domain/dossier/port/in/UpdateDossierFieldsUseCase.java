package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UpdateDossierFieldsUseCase {
    UpdateDossierFieldsResult update(UpdateDossierFieldsCommand command, AccessContext ctx);

    record UpdateDossierFieldsCommand(
            UUID dossierId, Map<String, Object> fieldsPatch, String updatedBy, Long expectedVersion) {}

    record UpdateDossierFieldsResult(Dossier dossier, List<String> sensitiveFieldsChanged) {}
}
