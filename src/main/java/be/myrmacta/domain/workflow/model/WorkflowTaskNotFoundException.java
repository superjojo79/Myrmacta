package be.myrmacta.domain.workflow.model;

public class WorkflowTaskNotFoundException extends RuntimeException {
    public WorkflowTaskNotFoundException(String message) {
        super(message);
    }
}
