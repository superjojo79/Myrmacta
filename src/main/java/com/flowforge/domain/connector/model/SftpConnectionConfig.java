package com.flowforge.domain.connector.model;

import java.util.Objects;

/**
 * Connection details for {@code batch-import} (connecteurs-cible, lot B1) — built from an
 * instance's own {@code config()} map, one level of trust below the SSHJ adapter that actually
 * opens the connection.
 *
 * @param hostKeyFingerprint expected SSH host key fingerprint (e.g. {@code SHA256:...}, OpenSSH
 *     format) — checked by the adapter on every connection (found writing the real SSHJ adapter,
 *     T1.10: SSHJ requires an explicit {@code HostKeyVerifier}, silently accepting any host key
 *     would be a MITM risk on an outbound connection admins configure once and rarely revisit).
 */
public record SftpConnectionConfig(
        String host, int port, String username, String password, String privateKeyPem, String hostKeyFingerprint) {

    public SftpConnectionConfig {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(hostKeyFingerprint, "hostKeyFingerprint");
    }
}
