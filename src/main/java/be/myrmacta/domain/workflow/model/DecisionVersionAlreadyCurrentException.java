package be.myrmacta.domain.workflow.model;

public class DecisionVersionAlreadyCurrentException extends RuntimeException {

    public DecisionVersionAlreadyCurrentException(String message) {
        super(message);
    }
}
