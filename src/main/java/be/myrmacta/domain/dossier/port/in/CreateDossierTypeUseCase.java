package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.PieceType;
import java.util.List;

public interface CreateDossierTypeUseCase {
    DossierType create(CreateDossierTypeCommand command, AccessContext.User actor);

    record CreateDossierTypeCommand(
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

        /** Convenience for callers built before {@code pieceTypes} existed (TODO §105) — no piece type. */
        public CreateDossierTypeCommand(
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
                    retentionDays, retentionNever, slaTargetHours, documentTemplateKeys, List.of());
        }

        /** Convenience for callers built before {@code documentTemplateKeys} existed (TODO §52). */
        public CreateDossierTypeCommand(
                String tenantId,
                String code,
                String label,
                String workflowKey,
                boolean active,
                boolean allowUnknownFields,
                List<FieldDefinition> fieldDefinitions,
                Integer retentionDays,
                boolean retentionNever,
                Integer slaTargetHours) {
            this(tenantId, code, label, workflowKey, active, allowUnknownFields, fieldDefinitions,
                    retentionDays, retentionNever, slaTargetHours, List.of(), List.of());
        }
    }
}
