package be.myrmacta.domain.workflow.model;

public class UnknownDecisionVersionException extends RuntimeException {

    public UnknownDecisionVersionException(String message) {
        super(message);
    }
}
