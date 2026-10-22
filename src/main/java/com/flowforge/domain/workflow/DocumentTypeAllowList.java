package com.flowforge.domain.workflow;

import com.flowforge.domain.dossier.model.DossierType;
import java.util.Map;

/**
 * D-TASK-FORM-DOCTYPE-SCOPE — un {@code documentType} fourni par un utilisateur (complétion de tâche ; depuis TODO §93,
 * revue I8, variables de démarrage aussi) doit faire partie des templates autorisés du type de dossier
 * ({@link DossierType#documentTemplateKeys()}). Sinon la brique de génération, qui se replie sur cette variable quand son
 * attribut n'est pas renseigné, produirait un template que l'administrateur n'a jamais autorisé pour ce type.
 */
public final class DocumentTypeAllowList {

    private DocumentTypeAllowList() {}

    /** Vrai si les variables portent un {@code documentType} à contrôler. */
    public static boolean concerns(Map<String, Object> variables) {
        return variables != null && variables.get(WorkflowVariables.DOCUMENT_TYPE) != null;
    }

    public static void assertAllowed(DossierType type, Map<String, Object> variables) {
        if (!concerns(variables)) {
            return;
        }
        Object documentType = variables.get(WorkflowVariables.DOCUMENT_TYPE);
        if (!type.documentTemplateKeys().contains(documentType.toString())) {
            throw new IllegalArgumentException(
                    "documentType '" + documentType + "' is not an allowed template for dossier type '"
                            + type.code() + "' -- configure it in Admin > Types > Templates de documents autorisés");
        }
    }
}
