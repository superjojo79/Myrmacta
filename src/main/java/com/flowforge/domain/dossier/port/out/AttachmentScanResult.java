package com.flowforge.domain.dossier.port.out;

public record AttachmentScanResult(boolean clean, String reason) {

    public static AttachmentScanResult ofClean() {
        return new AttachmentScanResult(true, null);
    }

    public static AttachmentScanResult ofInfected(String reason) {
        return new AttachmentScanResult(false, reason);
    }
}
