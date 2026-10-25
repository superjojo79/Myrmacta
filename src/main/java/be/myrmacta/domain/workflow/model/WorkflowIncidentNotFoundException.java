package be.myrmacta.domain.workflow.model;

/** TODO §94 — job en échec inconnu, d'un autre tenant ou d'une autre exécution que celle visée (404). */
public class WorkflowIncidentNotFoundException extends RuntimeException {
    public WorkflowIncidentNotFoundException(String message) {
        super(message);
    }
}
