package be.myrmacta.domain.dossier.port.out;

/**
 * D-AV: pluggable antivirus scan point for dossier attachment uploads. V1 ships only a no-op
 * adapter (no real AV engine in the fixed stack) — the contract exists so a future scanner
 * (e.g. ClamAV) can be wired in without changing the caller.
 */
public interface AttachmentScanPort {

    AttachmentScanResult scan(byte[] content, String declaredContentType);
}
