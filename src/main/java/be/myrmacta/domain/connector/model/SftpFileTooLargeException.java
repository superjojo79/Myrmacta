package be.myrmacta.domain.connector.model;

/**
 * A file exceeded the configured {@code maxFileBytes} while it was actually being downloaded —
 * distinct from {@link SftpConnectionException} so {@code BatchImportConnector} can skip just this
 * one file (same isolation as the pre-download size check against {@code listFiles} metadata)
 * instead of aborting the whole poll like a real connection failure would. Found in adversarial
 * review (connecteurs-cible, lot B1): the pre-download check alone trusts the size a server
 * reports at {@code listFiles} time, which a misbehaving or lying server can understate.
 */
public class SftpFileTooLargeException extends RuntimeException {

    public SftpFileTooLargeException(String message) {
        super(message);
    }
}
