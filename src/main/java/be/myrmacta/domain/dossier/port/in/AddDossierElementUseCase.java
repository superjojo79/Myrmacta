package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.ElementType;
import java.util.Map;
import java.util.UUID;

public interface AddDossierElementUseCase {

    DossierElement add(AddCommand command, AccessContext ctx);

    record AddCommand(
            UUID dossierId,
            ElementType type,
            String label,
            String storageRef,
            Map<String, Object> metadata,
            String addedBy,
            String workflowStepId) {}
}
