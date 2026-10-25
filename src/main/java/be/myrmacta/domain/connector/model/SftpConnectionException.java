package be.myrmacta.domain.connector.model;

/**
 * SFTP connection/authentication failure ({@code batch-import}, lot B1) — propagated out of
 * {@code BatchImportConnector.onEvent(...)} unhandled; the caller ({@code
 * BatchImportConnectorTriggerJob}) decides whether to continue with other instances of the same
 * poll (D-BI-SFTP-CONN-FAILURE, requirements).
 */
public class SftpConnectionException extends RuntimeException {

    public SftpConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
