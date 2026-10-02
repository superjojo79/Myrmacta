package com.flowforge.domain.workflow.palette;

import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.signature.model.SignatureMode;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * TODO §73 — les 11 briques de la première tranche (D-PALETTE-SCOPE). Le nom d'un paramètre {@code ATTRIBUTE} est
 * le nom de la variable de process que le delegate lit (D-PALETTE-PARAMS : l'attribut est le défaut, la variable
 * gagne — D-PALETTE-PRECEDENCE). Aucune donnée du tenant : les listes viennent de {@code valueSource}.
 */
public final class BuildingBlockCatalog {

    /** Incrémenté à chaque changement de contenu du catalogue (contrat MCP / client). */
    public static final int VERSION = 1;

    public static final Set<String> VALUE_SOURCES = Set.of(
            "documentTemplates", "connectorInstances", "notificationWebhooks", "dossierTypes", "workflowTypes",
            "workflowStatuses", "dmnTables", "questionSchemas");

    private static final int SHORT = 200;
    private static final int LONG = 4000;

    private static final List<BuildingBlock> BLOCKS = List.of(
            block("generate-document", "Générer un document",
                    "Génère un PDF depuis un template de document ; à faire suivre de « Joindre le document ».",
                    BlockCategory.DOCUMENT, BlockElement.SERVICE_TASK, BlockMarker.delegate("documentGenerationTask"),
                    reference("documentType", "Template de document", true, "documentTemplates",
                            "Clé du template actif à utiliser."),
                    text("documentVariant", "Variante", 60, "Variante du template (facultatif)."),
                    text("documentLocale", "Langue", 20, "Langue du destinataire, ex. fr (facultatif).")),
            block("attach-document", "Joindre le document",
                    "Rattache au dossier le PDF généré par la brique précédente.",
                    BlockCategory.DOCUMENT, BlockElement.SERVICE_TASK,
                    BlockMarker.delegate("attachGeneratedDocumentTask")),
            block("call-connector", "Appeler un connecteur",
                    "Appelle une instance de connecteur configurée en rôle service.",
                    BlockCategory.INTEGRATION, BlockElement.SERVICE_TASK, BlockMarker.delegate("connectorServiceTask"),
                    reference("connectorInstanceId", "Connecteur", true, "connectorInstances",
                            "Instance de connecteur (rôle service). Les données envoyées restent la variable connectorInput.")),
            block("notify", "Notifier",
                    "Envoie une notification par e-mail, webhook ou navigateur.",
                    BlockCategory.INTEGRATION, BlockElement.SERVICE_TASK, BlockMarker.delegate("notificationDispatchTask"),
                    enumMulti("notificationChannels", "Canaux", true, names(NotificationChannel.values()),
                            "Un ou plusieurs canaux."),
                    text("notificationTo", "Destinataire e-mail", 254,
                            "Pour une personne physique, préférer une variable ou un identifiant utilisateur."),
                    text("notificationSubject", "Objet", SHORT, "Objet du message."),
                    text("notificationBody", "Message", LONG, "Texte littéral du message."),
                    text("notificationUserId", "Utilisateur", 100, "Identifiant de l'utilisateur à notifier (navigateur)."),
                    reference("notificationWebhookConfigId", "Webhook", false, "notificationWebhooks",
                            "Configuration de webhook sortant.")),
            block("create-child-dossier", "Créer un dossier enfant",
                    "Crée un dossier lié et démarre son workflow.",
                    BlockCategory.DOSSIER, BlockElement.SERVICE_TASK, BlockMarker.delegate("createChildDossierTask"),
                    reference("childDossierTypeCode", "Type du dossier enfant", true, "dossierTypes",
                            "Code du type de dossier à créer."),
                    text("childDossierLabel", "Libellé du dossier enfant", SHORT, "Libellé littéral.", true),
                    reference("workflowProcessKey", "Workflow à démarrer", true, "workflowTypes",
                            "Clé du process à démarrer sur le dossier enfant."),
                    enumSingle("relationType", "Type de lien", true, List.of("PARENT_CHILD", "SUBSEQUENT"),
                            "Lien créé entre le dossier courant et l'enfant.")),
            block("set-dossier-status", "Changer le statut du dossier",
                    "Modifie le statut du dossier courant.",
                    BlockCategory.DOSSIER, BlockElement.SERVICE_TASK, BlockMarker.delegate("updateDossierStatusTask"),
                    enumSingle("targetDossierStatus", "Statut du dossier", true, names(DossierStatus.values()),
                            "Nouveau statut.")),
            block("set-workflow-status", "Changer le statut du workflow",
                    "Modifie le statut métier de l'exécution en cours.",
                    BlockCategory.DOSSIER, BlockElement.SERVICE_TASK, BlockMarker.delegate("updateWorkflowLinkStatusTask"),
                    reference("targetWorkflowStatus", "Statut du workflow", true, "workflowStatuses",
                            "Statut déclaré dans la configuration de ce workflow.")),
            block("dmn-decision", "Décision DMN",
                    "Évalue une table de décision déployée ; le résultat est lisible en aval.",
                    BlockCategory.DECISION, BlockElement.SERVICE_TASK, BlockMarker.dmn(),
                    new BlockParameter("decisionTableReferenceKey", "Table de décision", ParameterType.REFERENCE,
                            ParameterBinding.FIELD_ELEMENT, true, "dmnTables", List.of(), 100, null, null,
                            "Clé de la table de décision déployée.")),
            block("evaluate-expression", "Évaluer une expression",
                    "Calcule une valeur avec une expression métier bornée (JSONata) ; le résultat alimente une variable "
                            + "que les passerelles comparent ensuite.",
                    BlockCategory.DECISION, BlockElement.SERVICE_TASK, BlockMarker.delegate("businessExpressionTask"),
                    text("formula", "Expression", 2000,
                            "Ex. dossier.fields.montant > 5000 and dossier.fields.pays = \"FR\". Racine : dossier, "
                                    + "connectorResult, variables, now.", true),
                    text("formulaResult", "Variable de résultat", 60,
                            "Nom de la variable de process qui reçoit le résultat (lettres, chiffres, _).", true)),
            block("map-fields", "Enregistrer des valeurs dans le dossier",
                    "Calcule des valeurs (expressions métier bornées) à partir du dossier, du résultat d'un connecteur et "
                            + "des variables, et les enregistre dans des champs du dossier — tout ou rien.",
                    BlockCategory.DOSSIER, BlockElement.SERVICE_TASK, BlockMarker.delegate("fieldMappingTask"),
                    text("fieldMapping", "Valeurs à enregistrer", 8000,
                            "Une ligne par champ : champ = expression. Ex. score = connectorResult.json.score. "
                                    + "connectorResult.json est le corps de la réponse lu comme JSON. 20 champs au plus.",
                            true)),
            block("user-question", "Question utilisateur",
                    "Tâche humaine qui pose un choix indépendant du dossier.",
                    BlockCategory.HUMAN, BlockElement.USER_TASK, BlockMarker.userQuestion(),
                    new BlockParameter("schemaKey", "Question", ParameterType.REFERENCE,
                            ParameterBinding.FORM_KEY_SUFFIX, true, "questionSchemas", List.of(), 100, null, null,
                            "Schéma de question défini dans l'administration.")),
            block("external-task", "Tâche externe (portail)",
                    "Un tiers sans compte répond via un lien personnel envoyé par e-mail.",
                    BlockCategory.HUMAN, BlockElement.USER_TASK, BlockMarker.externalTask(),
                    new BlockParameter("portalRecipient", "Adresse du tiers", ParameterType.SOURCE_REF,
                            ParameterBinding.ATTRIBUTE, true, null, List.of(), 200, null, null,
                            "dossierField:<champ> ou variable:<nom>."),
                    new BlockParameter("schemaKey", "Question posée", ParameterType.REFERENCE,
                            ParameterBinding.FORM_KEY_SUFFIX, true, "questionSchemas", List.of(), 100, null, null,
                            "Schéma de question rempli par le tiers."),
                    new BlockParameter("portalLinkTtlDays", "Validité du lien (jours)", ParameterType.INTEGER,
                            ParameterBinding.ATTRIBUTE, false, null, List.of(), 3, 1, 365,
                            "Durée de validité, dans la limite du plafond de l'instance.")),
            block("request-signature", "Demander une signature",
                    "Demande la signature d'un document PDF ; à faire suivre de l'attente du message signatureCompleted.",
                    BlockCategory.SIGNATURE, BlockElement.SERVICE_TASK, BlockMarker.delegate("signatureRequestTask"),
                    new BlockParameter("signatureDocument", "Document à signer", ParameterType.SOURCE_REF,
                            ParameterBinding.ATTRIBUTE, true, null, List.of(), 200, null, null,
                            "variable:<nom> (ex. generatedDocumentId) ou dossierField:<nom>."),
                    new BlockParameter("signatureMode", "Ordre", ParameterType.ENUM, ParameterBinding.ATTRIBUTE, false,
                            null, names(SignatureMode.values()), 20, null, null, "Parallèle par défaut."),
                    new BlockParameter("signatureDeadlineDays", "Échéance (jours)", ParameterType.INTEGER,
                            ParameterBinding.ATTRIBUTE, false, null, List.of(), 3, 1, 365,
                            "Dans la limite du plafond de l'instance."),
                    new BlockParameter("signers", "Signataires", ParameterType.SIGNERS,
                            ParameterBinding.SIGNER_ELEMENTS, true, null, List.of(), SHORT, null, null,
                            "Au moins un signataire requis ; 10 au plus.")));

    private BuildingBlockCatalog() {}

    public static List<BuildingBlock> all() {
        return BLOCKS;
    }

    public static Optional<BuildingBlock> byId(String id) {
        return BLOCKS.stream().filter(b -> b.id().equals(id)).findFirst();
    }

    /** Brique dont le marqueur est ce delegate (nom de bean, sans {@code ${}}). */
    public static Optional<BuildingBlock> forDelegate(String delegate) {
        return BLOCKS.stream().filter(b -> delegate != null && delegate.equals(b.marker().delegate())).findFirst();
    }

    private static BuildingBlock block(
            String id, String label, String description, BlockCategory category, BlockElement element,
            BlockMarker marker, BlockParameter... parameters) {
        return new BuildingBlock(id, label, description, category, element, marker, List.of(parameters));
    }

    private static BlockParameter text(String name, String label, int maxLength, String help) {
        return text(name, label, maxLength, help, false);
    }

    private static BlockParameter text(String name, String label, int maxLength, String help, boolean required) {
        return new BlockParameter(name, label, ParameterType.TEXT, ParameterBinding.ATTRIBUTE, required, null,
                List.of(), maxLength, null, null, help);
    }

    private static BlockParameter reference(
            String name, String label, boolean required, String valueSource, String help) {
        return new BlockParameter(name, label, ParameterType.REFERENCE, ParameterBinding.ATTRIBUTE, required,
                valueSource, List.of(), 100, null, null, help);
    }

    private static BlockParameter enumSingle(
            String name, String label, boolean required, List<String> values, String help) {
        return new BlockParameter(name, label, ParameterType.ENUM, ParameterBinding.ATTRIBUTE, required, null, values,
                40, null, null, help);
    }

    private static BlockParameter enumMulti(
            String name, String label, boolean required, List<String> values, String help) {
        return new BlockParameter(name, label, ParameterType.ENUM_MULTI, ParameterBinding.ATTRIBUTE, required, null,
                values, 80, null, null, help);
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
