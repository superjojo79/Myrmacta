package be.myrmacta.domain.workflow.model;

public class WorkflowProcessInstanceNotFoundException extends RuntimeException {
    public WorkflowProcessInstanceNotFoundException(String message) {
        super(message);
    }
}
