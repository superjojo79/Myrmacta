package be.myrmacta.domain.workflow.model;

public class WorkflowTypeConfigNotFoundException extends RuntimeException {
    public WorkflowTypeConfigNotFoundException(String message) {
        super(message);
    }
}
