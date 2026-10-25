package be.myrmacta.domain.workflow.model;

public class DecisionEvaluationException extends RuntimeException {

    public DecisionEvaluationException(String message) {
        super(message);
    }

    public DecisionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
