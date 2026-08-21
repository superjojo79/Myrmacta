package com.flowforge.infrastructure.web;

public final class ListSorts {

    public static final String DOSSIER_TYPES = "code,asc";
    public static final String DOCUMENT_TEMPLATES = "updatedAt,desc";
    public static final String CONNECTORS = "name,asc";
    public static final String WORKFLOW_TYPE_CONFIGS = "workflowKey,asc";
    public static final String NOTIFICATION_WEBHOOKS = "name,asc";
    public static final String PURGE_OUTBOX = "createdAt,desc";

    public static final String DOSSIER_ELEMENTS = "addedAt,asc";
    public static final String MY_WORKFLOW_TASKS = "createdAt,desc";
    public static final String CONNECTOR_DEFINITIONS = "type,asc";
    public static final String DOSSIER_WORKFLOWS = "sequence,asc";
    public static final String WORKFLOW_LINK_HISTORY = "startedAt,asc";
    public static final String WORKFLOW_LINK_TASKS = "createdAt,asc";

    private ListSorts() {}
}
