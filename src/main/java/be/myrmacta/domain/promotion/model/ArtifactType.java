package be.myrmacta.domain.promotion.model;

import java.util.List;

/**
 * TODO §83 (D4) — artefacts de configuration d'un bundle.
 *
 * <p>TODO §117 (D-PROMO-FORMAT2) — le rang de déclaration des constantes est figé : l'empreinte d'un bundle déjà journalisé en
 * dépend ({@link ConfigurationBundle#sha256()}). Les nouveaux artefacts s'ajoutent à la fin, et l'ordre d'application
 * (dépendances) est {@link #applyRank()}.
 */
public enum ArtifactType {
    DOSSIER_TYPE("dossier-types", ".json", 1),
    TASK_QUESTION_SCHEMA("task-question-schemas", ".json", 1),
    CONNECTOR_INSTANCE("connectors", ".json", 1),
    DMN("dmn", ".dmn", 1),
    BPMN("bpmn", ".bpmn20.xml", 1),
    WORKFLOW_CONFIG("workflow-configs", ".json", 1),
    /** TODO §117 (D-PROMO-GROUP) — groupe d'utilisateurs, sans ses membres. */
    USER_GROUP("user-groups", ".json", 2);

    /** Ordre d'application : une dépendance passe avant ce qui la référence. */
    private static final List<ArtifactType> APPLY_ORDER =
            List.of(USER_GROUP, DOSSIER_TYPE, TASK_QUESTION_SCHEMA, CONNECTOR_INSTANCE, DMN, BPMN, WORKFLOW_CONFIG);

    private final String directory;
    private final String extension;
    private final int sinceFormatVersion;

    ArtifactType(String directory, String extension, int sinceFormatVersion) {
        this.directory = directory;
        this.extension = extension;
        this.sinceFormatVersion = sinceFormatVersion;
    }

    public String directory() {
        return directory;
    }

    public String extension() {
        return extension;
    }

    /** Premier format de bundle qui connaît cet artefact : un bundle d'un format antérieur qui le porte est refusé. */
    public int sinceFormatVersion() {
        return sinceFormatVersion;
    }

    /** Rang dans l'ordre d'application (plan, application, export). */
    public int applyRank() {
        return APPLY_ORDER.indexOf(this);
    }

    /** Chemin du fichier dans l'archive : {@code <répertoire>/<clé><extension>}. */
    public String path(String key) {
        return directory + "/" + key + extension;
    }
}
