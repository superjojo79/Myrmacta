package be.myrmacta.domain.workflow.model;

public class UnknownDecisionKeyException extends RuntimeException {
    public UnknownDecisionKeyException(String message) {
        super(message);
    }
}
