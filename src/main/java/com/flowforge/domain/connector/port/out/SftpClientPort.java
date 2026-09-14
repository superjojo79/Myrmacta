package com.flowforge.domain.connector.port.out;

import com.flowforge.domain.connector.model.SftpConnectionConfig;
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
     * @throws com.flowforge.domain.connector.model.SftpConnectionException on connection/auth failure
     */
    List<RemoteFile> listFiles(SftpConnectionConfig config, String remoteDirectory, String filenamePattern);

    /**
     * @throws com.flowforge.domain.connector.model.SftpConnectionException on connection/auth/read failure
     */
    byte[] download(SftpConnectionConfig config, String remotePath);

    /**
     * Moves/renames the file into {@code archiveDirectory} once processed (D-BATCH-DEDUP,
     * requirements) — best-effort semantics are the adapter's concern, not this port's.
     *
     * @throws com.flowforge.domain.connector.model.SftpConnectionException on connection/write failure
     */
    void archive(SftpConnectionConfig config, String remotePath, String archiveDirectory);
}
