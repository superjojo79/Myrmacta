package com.flowforge.infrastructure.web;

public final class AdminListSorts {

    public static final String DOSSIER_TYPES = "code,asc";
    public static final String DOCUMENT_TEMPLATES = "updatedAt,desc";
    public static final String CONNECTORS = "name,asc";
    public static final String WORKFLOW_TYPE_CONFIGS = "workflowKey,asc";
    public static final String NOTIFICATION_WEBHOOKS = "name,asc";
    public static final String PURGE_OUTBOX = "createdAt,desc";

    private AdminListSorts() {}
}
