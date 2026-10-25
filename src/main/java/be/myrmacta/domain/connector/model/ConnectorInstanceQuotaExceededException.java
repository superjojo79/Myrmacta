package be.myrmacta.domain.connector.model;

/**
 * A tenant tried to enable more instances of one connector type than
 * {@code ManageConnectorInstanceUseCaseImpl}'s configured per-type quota allows (found in
 * adversarial review, connecteurs-cible lot B1: an enabled {@code kafka} instance runs a real
 * consumer thread/connection for the lifetime of the JVM, so an unbounded number of them can
 * exhaust resources shared by the whole tenant — not just the connector).
 */
public class ConnectorInstanceQuotaExceededException extends RuntimeException {

    public ConnectorInstanceQuotaExceededException(String message) {
        super(message);
    }
}
