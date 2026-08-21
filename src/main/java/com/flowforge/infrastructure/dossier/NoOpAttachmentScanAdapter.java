package com.flowforge.infrastructure.dossier;

import com.flowforge.domain.dossier.port.out.AttachmentScanPort;
import com.flowforge.domain.dossier.port.out.AttachmentScanResult;

/**
 * D-AV: V1 has no antivirus engine in the fixed stack — always reports clean. Exists to give
 * {@link AttachmentScanPort} a default implementation ready to be swapped for a real scanner
 * (e.g. ClamAV) later without touching the caller.
 */
public class NoOpAttachmentScanAdapter implements AttachmentScanPort {

    @Override
    public AttachmentScanResult scan(byte[] content, String declaredContentType) {
        return AttachmentScanResult.ofClean();
    }
}
