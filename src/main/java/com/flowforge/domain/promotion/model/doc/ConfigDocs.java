package com.flowforge.domain.promotion.model.doc;

import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import java.util.List;
import java.util.Map;

/**
 * TODO §83 (D4) — représentations portables (sans identifiants ni audit, sans secret) des artefacts d'un bundle. Elles sont
 * comparées octet à octet après encodage canonique pour décider « inchangé ».
 */
public final class ConfigDocs {

    private ConfigDocs() {}

    public record DossierTypeDoc(
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

        public DossierTypeDoc {
            fieldDefinitions = fieldDefinitions == null ? List.of() : List.copyOf(fieldDefinitions);
            documentTemplateKeys = documentTemplateKeys == null ? List.of() : List.copyOf(documentTemplateKeys);
        }
    }

    public record TaskQuestionSchemaDoc(String schemaKey, String label, List<FieldDefinition> fields) {

        public TaskQuestionSchemaDoc {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    /**
     * @param config configuration SANS aucune valeur de champ secret
     * @param secretFields noms des champs secrets que la source possédait (à fournir à la création sur la cible)
     */
    public record ConnectorDoc(
            String name, String connectorType, boolean enabled, Map<String, Object> config, List<String> secretFields) {

        public ConnectorDoc {
            java.util.Objects.requireNonNull(name, "name");
            java.util.Objects.requireNonNull(connectorType, "connectorType");
            // null-safe (revue D4 M1) : une valeur null équivaut à une clé absente — jamais d'échec de l'export entier
            java.util.Map<String, Object> copy = new java.util.TreeMap<>();
            if (config != null) {
                config.forEach((k, v) -> {
                    if (v != null) {
                        copy.put(k, v);
                    }
                });
            }
            config = java.util.Collections.unmodifiableMap(copy);
            secretFields = secretFields == null ? List.of() : List.copyOf(secretFields);
        }
    }

    public record WorkflowConfigDoc(
            String workflowKey,
            String label,
            List<WorkflowStatusDefinition> statusDefinitions,
            List<DossierStatus> allowedDossierStatuses,
            DossierStatus onStartDossierStatus,
            List<String> observerUserIds) {

        public WorkflowConfigDoc {
            statusDefinitions = statusDefinitions == null ? List.of() : List.copyOf(statusDefinitions);
            allowedDossierStatuses = allowedDossierStatuses == null ? List.of() : List.copyOf(allowedDossierStatuses);
            observerUserIds = observerUserIds == null ? List.of() : List.copyOf(observerUserIds);
        }
    }
}
