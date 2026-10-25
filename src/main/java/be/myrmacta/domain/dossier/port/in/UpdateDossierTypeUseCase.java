package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.PieceType;
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
            List<String> documentTemplateKeys,
            List<PieceType> pieceTypes) {

        /**
         * Convenience for callers built before {@code pieceTypes} existed (TODO §105) — {@code null} keeps the piece types
         * already declared on the type.
         */
        public UpdateDossierTypeCommand(
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
                List<String> documentTemplateKeys) {
            this(tenantId, code, label, workflowKey, active, allowUnknownFields, fieldDefinitions,
                    retentionDays, retentionNever, slaTargetHours, documentTemplateKeys, null);
        }
    }
}
