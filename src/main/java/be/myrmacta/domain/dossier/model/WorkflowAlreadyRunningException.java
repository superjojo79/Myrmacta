package be.myrmacta.domain.dossier.model;

import java.util.UUID;

public class WorkflowAlreadyRunningException extends RuntimeException {

    private final UUID dossierId;

    public WorkflowAlreadyRunningException(UUID dossierId) {
        super("A workflow is already active for dossier " + dossierId);
        this.dossierId = dossierId;
    }

    public WorkflowAlreadyRunningException(UUID dossierId, Throwable cause) {
        super("A workflow is already active for dossier " + dossierId, cause);
        this.dossierId = dossierId;
    }

    public UUID dossierId() {
        return dossierId;
    }
}
