package be.myrmacta.domain.promotion.model.doc;

import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.PieceType;
import be.myrmacta.domain.workflow.model.WorkflowStatusDefinition;
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
            List<String> documentTemplateKeys,
            List<PieceType> pieceTypes) {

        public DossierTypeDoc {
            fieldDefinitions = fieldDefinitions == null ? List.of() : List.copyOf(fieldDefinitions);
            documentTemplateKeys = documentTemplateKeys == null ? List.of() : List.copyOf(documentTemplateKeys);
            // TODO §106 — un bundle antérieur aux types de pièces n'en déclare aucun (même règle que documentTemplateKeys).
            pieceTypes = pieceTypes == null ? List.of() : List.copyOf(pieceTypes);
        }

        public DossierTypeDoc(String code, String label, String workflowKey, boolean active, boolean allowUnknownFields,
                List<FieldDefinition> fieldDefinitions, Integer retentionDays, boolean retentionNever,
                Integer slaTargetHours, List<String> documentTemplateKeys) {
            this(code, label, workflowKey, active, allowUnknownFields, fieldDefinitions, retentionDays, retentionNever,
                    slaTargetHours, documentTemplateKeys, List.of());
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

    /**
     * TODO §117 (D-PROMO-GROUP) — groupe d'utilisateurs, <b>sans ses membres</b>. {@code idpGroup} peut porter {@code {{paramètre}}},
     * remplacé à l'import ; il n'est appliqué qu'à la création.
     */
    public record UserGroupDoc(String code, String label, String idpGroup) {

        public UserGroupDoc {
            java.util.Objects.requireNonNull(code, "code");
            idpGroup = idpGroup == null || idpGroup.isBlank() ? null : idpGroup;
        }
    }

    /** TODO §117 (D-PROMO-TYPE-SETTINGS) — workflow « réponse » d'un type de dossier (D-MAILROOM-REPLY-WORKFLOW). */
    public record ReplyWorkflowDoc(
            String dossierTypeCode,
            be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting.Mode mode,
            String workflowKey,
            String childTypeCode) {

        public ReplyWorkflowDoc {
            java.util.Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
        }
    }

    /** TODO §117 (D-PROMO-TYPE-SETTINGS) — échéance d'un type de dossier (D-DEADLINE-FIELD). */
    public record DeadlineDoc(
            String dossierTypeCode, String fieldName, int reminderDays, String escalationGroup,
            be.myrmacta.domain.deadline.model.DayCount reminderDayCount) {

        public DeadlineDoc(String dossierTypeCode, String fieldName, int reminderDays, String escalationGroup) {
            this(dossierTypeCode, fieldName, reminderDays, escalationGroup, be.myrmacta.domain.deadline.model.DayCount.CALENDAR);
        }

        public DeadlineDoc {
            java.util.Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
            if (reminderDayCount == null) {
                reminderDayCount = be.myrmacta.domain.deadline.model.DayCount.CALENDAR;
            }
        }
    }

    /** TODO §120 (D-CAL-PROMO) — calendrier du tenant. {@code holidays} : dates ISO. */
    public record CalendarDoc(String zoneId, String weekend, java.util.List<String> holidays) {

        public CalendarDoc {
            holidays = holidays == null ? java.util.List.of() : java.util.List.copyOf(holidays);
        }
    }

    /**
     * TODO §117 (D-PROMO-TEMPLATE) — version active d'un template pour une combinaison (clé, variante, langue) ; variante et langue
     * vides = valeur par défaut, comme à la création d'un template.
     */
    public record DocumentTemplateDoc(String templateKey, String variantKey, String locale, String label, String htmlContent) {

        public DocumentTemplateDoc {
            java.util.Objects.requireNonNull(templateKey, "templateKey");
            variantKey = be.myrmacta.domain.document.DocumentTemplateSupport.normalizeOptional(variantKey);
            locale = be.myrmacta.domain.document.DocumentTemplateSupport.normalizeOptional(locale);
            // comme CreateDocumentTemplateUseCase, qui enregistre le contenu sans blancs de tête ni de fin (revue de clôture #279, B7)
            htmlContent = htmlContent == null ? "" : htmlContent.trim();
        }
    }

    /**
     * TODO §117 (D-PROMO-PARCOURS) — définition d'un parcours. {@code inSync} (repère de l'export, hors comparaison) : faux si, sur
     * la source, la définition a changé depuis sa dernière compilation ou si le processus déployé n'est pas cette compilation.
     */
    public record ParcoursDoc(String key, String label, be.myrmacta.domain.parcours.model.ParcoursDefinition definition, Boolean inSync) {

        public ParcoursDoc {
            java.util.Objects.requireNonNull(key, "key");
            java.util.Objects.requireNonNull(definition, "definition");
        }
    }
}
