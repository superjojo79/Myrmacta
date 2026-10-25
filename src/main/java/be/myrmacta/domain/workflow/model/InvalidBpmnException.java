package be.myrmacta.domain.workflow.model;

public class InvalidBpmnException extends RuntimeException {
    public InvalidBpmnException(String message) {
        super(message);
    }

    public InvalidBpmnException(String message, Throwable cause) {
        super(message, cause);
    }
}
