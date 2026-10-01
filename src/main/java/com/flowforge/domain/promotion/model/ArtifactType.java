package com.flowforge.domain.promotion.model;

/**
 * TODO §83 (D4) — artefacts de configuration d'un bundle. L'ordre de déclaration EST l'ordre d'application (dépendances) :
 * types de dossier → schémas de questions → connecteurs → DMN → BPMN → configurations de workflow.
 */
public enum ArtifactType {
    DOSSIER_TYPE("dossier-types", ".json"),
    TASK_QUESTION_SCHEMA("task-question-schemas", ".json"),
    CONNECTOR_INSTANCE("connectors", ".json"),
    DMN("dmn", ".dmn"),
    BPMN("bpmn", ".bpmn20.xml"),
    WORKFLOW_CONFIG("workflow-configs", ".json");

    private final String directory;
    private final String extension;

    ArtifactType(String directory, String extension) {
        this.directory = directory;
        this.extension = extension;
    }

    public String directory() {
        return directory;
    }

    public String extension() {
        return extension;
    }

    /** Chemin du fichier dans l'archive : {@code <répertoire>/<clé><extension>}. */
    public String path(String key) {
        return directory + "/" + key + extension;
    }
}
