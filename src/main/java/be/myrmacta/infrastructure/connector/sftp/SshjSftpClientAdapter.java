package be.myrmacta.infrastructure.connector.sftp;

import be.myrmacta.domain.connector.model.SftpConnectionConfig;
import be.myrmacta.domain.connector.model.SftpConnectionException;
import be.myrmacta.domain.connector.model.SftpFileTooLargeException;
import be.myrmacta.domain.connector.port.out.SftpClientPort;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile;
import org.springframework.stereotype.Component;

/**
 * Real SFTP transport for {@code batch-import} (connecteurs-cible, lot B1) — SSHJ, verified
 * against the actual library API (javap on the resolved 0.39.0 jar, not assumed from memory,
 * following the same discipline as {@code connecteur-callback} Phase 3 verifying the real
 * Flowable API). One SSH connection per port operation (list/download/archive) — simplest correct
 * behavior for a cron-triggered job firing at most every few minutes; connection reuse across a
 * single {@code onEvent} call is a possible follow-up, not required for correctness.
 */
@Component
public class SshjSftpClientAdapter implements SftpClientPort {

    private static final int TIMEOUT_MS = 10_000;

    @Override
    public List<RemoteFile> listFiles(SftpConnectionConfig config, String remoteDirectory, String filenamePattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + filenamePattern);
        try (SSHClient ssh = connect(config);
                SFTPClient sftp = ssh.newSFTPClient()) {
            List<RemoteFile> files = new ArrayList<>();
            for (RemoteResourceInfo info : sftp.ls(remoteDirectory)) {
                if (info.isRegularFile() && matcher.matches(Path.of(info.getName()))) {
                    files.add(new RemoteFile(info.getPath(), info.getAttributes().getSize()));
                }
            }
            return files;
        } catch (IOException ex) {
            throw new SftpConnectionException(
                    "Failed to list " + remoteDirectory + " on " + config.host(), ex);
        }
    }

    @Override
    public byte[] download(SftpConnectionConfig config, String remotePath, long maxBytes) {
        try (SSHClient ssh = connect(config);
                SFTPClient sftp = ssh.newSFTPClient();
                net.schmizz.sshj.sftp.RemoteFile remoteFile = sftp.open(remotePath);
                InputStream in = remoteFile.new RemoteFileInputStream()) {
            return readBounded(in, maxBytes, remotePath);
        } catch (IOException ex) {
            throw new SftpConnectionException("Failed to download " + remotePath + " from " + config.host(), ex);
        }
    }

    /**
     * Reads at most {@code maxBytes + 1} bytes so a file larger than the bound is detected without
     * ever buffering the whole thing — same pattern as {@code HttpRestConnector.readBounded}. A
     * server's {@code listFiles} size is metadata, not a guarantee: {@code readAllBytes()} would
     * trust it blindly and could be driven to buffer an unbounded amount in memory.
     */
    static byte[] readBounded(InputStream in, long maxBytes, String remotePath) throws IOException {
        int limit = Math.toIntExact(Math.min(maxBytes, Integer.MAX_VALUE - 1) + 1);
        byte[] bytes = in.readNBytes(limit);
        if (bytes.length > maxBytes) {
            throw new SftpFileTooLargeException(
                    "File " + remotePath + " exceeds maxFileBytes while downloading "
                            + "(the server reported a smaller size when listing)");
        }
        return bytes;
    }

    @Override
    public void archive(SftpConnectionConfig config, String remotePath, String archiveDirectory) {
        try (SSHClient ssh = connect(config);
                SFTPClient sftp = ssh.newSFTPClient()) {
            sftp.mkdirs(archiveDirectory);
            String filename = remotePath.substring(remotePath.lastIndexOf('/') + 1);
            String target = archiveDirectory.endsWith("/")
                    ? archiveDirectory + filename
                    : archiveDirectory + "/" + filename;
            sftp.rename(remotePath, target);
        } catch (IOException ex) {
            throw new SftpConnectionException("Failed to archive " + remotePath + " on " + config.host(), ex);
        }
    }

    /**
     * Never accepts a host key blindly (found while writing this adapter, D-SFTP-HOSTKEY,
     * requirements) — {@code SSHClient.addHostKeyVerifier(String)} matches the configured
     * fingerprint against the one presented by the server, failing the connection on mismatch.
     */
    private static SSHClient connect(SftpConnectionConfig config) {
        SSHClient client = new SSHClient();
        client.addHostKeyVerifier(config.hostKeyFingerprint());
        client.setConnectTimeout(TIMEOUT_MS);
        client.setTimeout(TIMEOUT_MS);
        try {
            client.connect(config.host(), config.port());
            if ("PRIVATE_KEY".equals(config.authType())) {
                OpenSSHKeyFile keyFile = new OpenSSHKeyFile();
                keyFile.init(config.privateKeyPem(), null, null);
                client.authPublickey(config.username(), keyFile);
            } else {
                client.authPassword(config.username(), config.password());
            }
            return client;
        } catch (IOException ex) {
            try {
                client.close();
            } catch (IOException closeFailure) {
                ex.addSuppressed(closeFailure);
            }
            throw new SftpConnectionException(
                    "Failed to connect/authenticate to " + config.host() + ":" + config.port(), ex);
        }
    }
}
