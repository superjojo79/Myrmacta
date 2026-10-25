package be.myrmacta.domain.workflow.model;

/** TODO §94 — le job a encore des tentatives automatiques : seul un incident (dead letter) se rejoue (409). */
public class IncidentNotRetryableException extends RuntimeException {
    public IncidentNotRetryableException(String message) {
        super(message);
    }
}
