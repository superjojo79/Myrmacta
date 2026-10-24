package com.flowforge.domain.workflow.portal.model;

/** Raison d'un statut {@code NOT_SENT} ou {@code REVOKED} (affichée à l'instructeur). */
public enum PortalLinkStatusReason {
    NO_ADDRESS,
    INVALID_ADDRESS,
    SCHEMA_MISSING,
    SEND_FAILED,
    /** TODO §103 (T2) — champ d'identité absent du type, ou non {@code STRING} ou non sensible. */
    INVALID_IDENTITY_FIELD,
    TASK_GONE,
    RESENT,
    REVOKED_BY_USER
}
