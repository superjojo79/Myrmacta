package be.myrmacta.domain.connector.model;

import java.util.Objects;

/**
 * Connection details for {@code batch-import} (connecteurs-cible, lot B1) — built from an
 * instance's own {@code config()} map, one level of trust below the SSHJ adapter that actually
 * opens the connection.
 *
 * @param authType {@code "PASSWORD"} or {@code "PRIVATE_KEY"} — the adapter authenticates using
 *     exactly this, never by inferring the method from which of {@code password}/{@code
 *     privateKeyPem} happens to be non-null (found in adversarial review, connecteurs-cible lot
 *     B1: a stale leftover value in the field not currently selected — e.g. after switching auth
 *     type without clearing the old one — would otherwise silently take effect).
 * @param hostKeyFingerprint expected SSH host key fingerprint (e.g. {@code SHA256:...}, OpenSSH
 *     format) — checked by the adapter on every connection (found writing the real SSHJ adapter,
 *     T1.10: SSHJ requires an explicit {@code HostKeyVerifier}, silently accepting any host key
 *     would be a MITM risk on an outbound connection admins configure once and rarely revisit).
 */
public record SftpConnectionConfig(
        String host,
        int port,
        String username,
        String password,
        String privateKeyPem,
        String authType,
        String hostKeyFingerprint) {

    public SftpConnectionConfig {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(authType, "authType");
        Objects.requireNonNull(hostKeyFingerprint, "hostKeyFingerprint");
    }

    /**
     * Masks {@code password}/{@code privateKeyPem} — no caller logs this record today (checked),
     * but it carries live SSH credentials, so a future debug/error log capturing the whole object
     * rather than only its safe fields should not leak them (defense in depth, adversarial review
     * connecteurs-cible lot B1).
     */
    @Override
    public String toString() {
        return "SftpConnectionConfig[host=%s, port=%d, username=%s, password=%s, privateKeyPem=%s, authType=%s, hostKeyFingerprint=%s]"
                .formatted(
                        host,
                        port,
                        username,
                        password == null ? "null" : "****",
                        privateKeyPem == null ? "null" : "****",
                        authType,
                        hostKeyFingerprint);
    }
}
