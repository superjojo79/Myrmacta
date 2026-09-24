package com.flowforge.domain.workflow.portal.port.out;

/** Un lien vivant existe déjà pour cette tâche (index unique partiel, exigence H2). */
public class PortalLinkConflictException extends RuntimeException {

    public PortalLinkConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
