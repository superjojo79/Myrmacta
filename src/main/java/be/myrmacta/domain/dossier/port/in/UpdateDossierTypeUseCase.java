package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import java.util.List;

public interface UpdateDossierTypeUseCase {
    DossierType update(UpdateDossierTypeCommand command, AccessContext.User actor);

    record UpdateDossierTypeCommand(
            String tenantId,
            String code,
            String label,
            String workflowKey,
            boolean active,
            boolean allowUnknownFields,
            List<FieldDefinition> fieldDefinitions,
            Integer retentionDays,
            boolean retentionNever,
            Integer slaTargetHours,
            List<String> documentTemplateKeys) {}
}
