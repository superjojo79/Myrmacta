package be.myrmacta.domain.workflow.palette;

import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.notification.model.NotificationChannel;
import be.myrmacta.domain.signature.model.SignatureMode;
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
    public static final int VERSION = 6;

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
                            "Instance de connecteur (rôle service)."),
                    // TODO §112 (D-ORCH-REQUEST) — requête construite dans le BPMN, jamais par une variable saisie.
                    text("connectorRequest", "Requête", 2000,
                            "Expression métier (JSONata borné) qui produit la requête, ex. {'method': 'PUT', 'path': "
                                    + "'/users/{id}/groups/{g}', 'pathParams': {'id': variables.compteIam, 'g': 'devs'}}. "
                                    + "Racine : dossier, connectorResult (+ json), variables, now. Prime sur la variable "
                                    + "connectorInput ; connecteur obligatoire. dossier.fields et variables sont modifiables "
                                    + "par les utilisateurs : ne pas en tirer la cible d'un PUT ou d'un DELETE (facultatif).")),
            block("notify", "Notifier",
                    "Envoie une notification par e-mail, webhook ou navigateur.",
                    BlockCategory.INTEGRATION, BlockElement.SERVICE_TASK, BlockMarker.delegate("notificationDispatchTask"),
                    enumMulti("notificationChannels", "Canaux", true, names(NotificationChannel.values()),
                            "Un ou plusieurs canaux."),
                    text("notificationTo", "Destinataire e-mail", 254,
                            "Adresse littérale, dossierField:<champ> (lu dans le dossier) ou variable:<nom>."),
                    text("notificationSubject", "Objet", SHORT, "Objet du message."),
                    text("notificationBody", "Message", LONG, "Texte littéral du message."),
                    text("notificationUserId", "Utilisateur", 100, "Identifiant de l'utilisateur à notifier (navigateur)."),
                    reference("notificationWebhookConfigId", "Webhook", false, "notificationWebhooks",
                            "Configuration de webhook sortant."),
                    // Issue #183 — lus dans le BPMN déployé seulement (D-NOTIF-ATTACH-LITERAL).
                    new BlockParameter("notificationAttachment", "Pièce jointe (e-mail)", ParameterType.SOURCE_REF,
                            ParameterBinding.ATTRIBUTE, false, null, List.of(), SHORT, null, null,
                            "variable:<nom> (ex. generatedDocumentId) ou dossierField:<nom> : PDF produit et joint au "
                                    + "dossier courant (facultatif)."),
                    text("notificationAttachmentName", "Nom du fichier joint", 100,
                            "Ex. autorisation.pdf ; par défaut le libellé du document (facultatif).")),
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
            // TODO §113 (D-MAILROOM-ROUTE) — lit la décision de la table de routage (variables routage*).
            block("route-mail", "Router le courrier",
                    "Après la table de routage : ouvre le dossier métier lié et y démarre son process pour le service désigné, "
                            + "ou envoie le courrier à la file de tri.",
                    BlockCategory.DOSSIER, BlockElement.SERVICE_TASK, BlockMarker.delegate("routeMailTask"),
                    text("groupeTri", "Groupe de la file de tri", 64,
                            "Code du groupe qui trie à la main ce que la table ne reconnaît pas (ex. COURRIER).", true)),
            // TODO §113 (D-MAILROOM-THREAD) — en tête du tri : une réponse à un e-mail connu rejoint le dossier de son fil.
            block("attach-to-thread", "Rattacher au fil",
                    "En tête du tri : une réponse à un e-mail connu rejoint le dossier de son fil (lien, pièces, avertissement, "
                            + "message courrierRecu) ; sinon le courrier poursuit vers le routage.",
                    BlockCategory.DOSSIER, BlockElement.SERVICE_TASK, BlockMarker.delegate("attachToThreadTask")),
            // TODO §115 (D-DEADLINE-TIMER) — écrit echeance, echeanceRappel et echeanceDepassee, que portent les minuteurs.
            block("compute-deadline", "Calculer une échéance",
                    "Calcule l'échéance depuis un champ date du dossier ou un délai, et pose les variables echeanceRappel et "
                            + "echeanceDepassee qu'un minuteur peut porter (${echeanceDepassee}).",
                    BlockCategory.DOSSIER, BlockElement.SERVICE_TASK, BlockMarker.delegate("computeDeadlineTask"),
                    text("champEcheance", "Champ échéance", 64,
                            "Champ date du dossier qui porte l'échéance (ex. echeance). Vide : le délai seul.", false),
                    new BlockParameter("delaiJours", "Délai (jours)", ParameterType.INTEGER, ParameterBinding.ATTRIBUTE, false,
                            null, List.of(), 4, 0, 3650,
                            "Jours depuis aujourd'hui, utilisés si le champ est absent ou vide. Ouvrables si « Délai en jours ouvrables » vaut OUVRABLE."),
                    new BlockParameter("rappelJours", "Rappel (jours avant)", ParameterType.INTEGER, ParameterBinding.ATTRIBUTE,
                            false, null, List.of(), 3, 0, 365,
                            "echeanceRappel tombe ce nombre de jours avant l'échéance (0 = le jour même), ouvrables si le rappel l'est."),
                    enumSingle("delaiOuvrable", "Délai en jours ouvrables", false, List.of("CALENDAIRE", "OUVRABLE"),
                            "OUVRABLE saute le week-end du tenant et ses jours fériés. Absent : calendaire."),
                    enumSingle("rappelOuvrable", "Rappel en jours ouvrables", false, List.of("CALENDAIRE", "OUVRABLE"),
                            "OUVRABLE recule en jours ouvrables. Le minuteur dépassé reste le lendemain calendaire.")),
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
            // TODO §102 (issue #184, lot B) — dossiers ouverts du même type qui partagent des valeurs avec le dossier courant.
            block("search-dossiers", "Rechercher des dossiers",
                    "Compte les dossiers en cours du même type qui ont les mêmes valeurs que des champs choisis (hors "
                            + "dossier courant), et donne la référence du plus ancien ; une passerelle route ensuite.",
                    BlockCategory.DOSSIER, BlockElement.SERVICE_TASK, BlockMarker.delegate("dossierSearchTask"),
                    text("searchCriteria", "Critères", 1000,
                            "Une ligne par critère : champ = dossierField:<champ> ou champ = variable:<nom>. Ex. email = "
                                    + "dossierField:email. Champs déclarés cherchables ; 3 critères au plus.", true),
                    text("searchCountResult", "Variable du nombre trouvé", 60,
                            "Nom de la variable qui reçoit le nombre de dossiers trouvés (0 si aucun).", true),
                    text("searchFirstReferenceResult", "Variable de la référence", 60,
                            "Nom de la variable qui reçoit la référence du plus ancien dossier trouvé (vide si aucun).",
                            true)),
            // TODO §106 (issue #211, lot B) — pré-vérification IA avant l'instruction ; toujours asynchrone.
            block("ai-precheck", "Pré-vérification IA",
                    "Contrôle la complétude du dossier, tire des constats de variables posées avant et pose des questions "
                            + "fermées à un modèle de langage sur les champs et pièces désignés ; l'agent accepte ou écarte "
                            + "chaque constat. Toujours asynchrone.",
                    BlockCategory.DOSSIER, BlockElement.SERVICE_TASK, BlockMarker.asyncDelegate("aiPrecheckTask"),
                    text("precheckPieces", "Pièces", 2000,
                            "Une ligne par type de pièce : clé : obligatoire, lire. Ex. preuveDomicile : obligatoire, lire. "
                                    + "« lire » envoie le texte au modèle (10 types au plus, jamais un type sensible)."),
                    text("precheckFields", "Champs transmis", 1000,
                            "Champs envoyés au modèle, séparés par des virgules (30 au plus, jamais un champ sensible). Ex. "
                                    + "nom, prenom, commune."),
                    text("precheckQuestions", "Questions fermées", 8000,
                            "Une ligne par question : clé = question. Ex. domicileAuNom = La preuve de domicile est-elle au "
                                    + "nom du demandeur ? (20 au plus)."),
                    text("precheckRules", "Règles", 4000,
                            "Une ligne par règle : variable:<nom> <condition> => MANQUANT|INCOHERENCE|A_VERIFIER : message. "
                                    + "Conditions : est vrai, est faux, est vide, n'est pas vide, > N, < N, = N, = \"texte\"."),
                    text("precheckResult", "Variable de résultat", 60,
                            "Facultatif : variable qui reçoit les comptes déterministes (status, missing, inconsistent).")),
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
                            "Durée de validité, dans la limite du plafond de l'instance."),
                    // TODO §106 (D-PIECE-PORTAL) — types de pièces que le tiers choisit au dépôt.
                    text("portalPieceTypes", "Types de pièces demandés", 700,
                            "Clés de types de pièces du type de dossier, séparées par des virgules (10 au plus). Ex. "
                                    + "preuveDomicile, certificatImmatriculation. Sans valeur : pièces non classées.",
                            false)),
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
