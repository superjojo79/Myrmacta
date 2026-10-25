package be.myrmacta.domain.connector.port.out;

import be.myrmacta.domain.connector.model.SftpConnectionConfig;
import java.util.List;

/**
 * SFTP transport for {@code batch-import} (connecteurs-cible, lot B1) — kept as a narrow port so
 * {@code BatchImportConnector} can be tested against a fake, without a real SSH server
 * (D-SFTP-TEST = mocké uniquement, requirements).
 */
public interface SftpClientPort {

    /** A file matching the configured directory/pattern, with its size known without downloading it. */
    record RemoteFile(String path, long sizeBytes) {}

    /**
     * @throws be.myrmacta.domain.connector.model.SftpConnectionException on connection/auth failure
     */
    List<RemoteFile> listFiles(SftpConnectionConfig config, String remoteDirectory, String filenamePattern);

    /**
     * Downloads at most {@code maxBytes} — the implementation must never buffer more than that in
     * memory even if the server misreports a smaller size at {@link #listFiles}-time (found in
     * adversarial review, connecteurs-cible lot B1: trusting only the pre-download size check
     * allows an OOM via a server that lies about a file's size).
     *
     * @throws be.myrmacta.domain.connector.model.SftpConnectionException on connection/auth/read failure
     * @throws be.myrmacta.domain.connector.model.SftpFileTooLargeException if the file is larger
     *     than {@code maxBytes} once actually read, regardless of what {@link #listFiles} reported
     */
    byte[] download(SftpConnectionConfig config, String remotePath, long maxBytes);

    /**
     * Moves/renames the file into {@code archiveDirectory} once processed (D-BATCH-DEDUP,
     * requirements) — best-effort semantics are the adapter's concern, not this port's.
     *
     * @throws be.myrmacta.domain.connector.model.SftpConnectionException on connection/write failure
     */
    void archive(SftpConnectionConfig config, String remotePath, String archiveDirectory);
}
