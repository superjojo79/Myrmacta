package com.flowforge.domain.dossier;

import java.util.Map;
import java.util.function.Predicate;

/**
 * D-AV: complements the declarative MIME allowlist ({@code AttachmentUploadProperties}) with a
 * check of the file's actual bytes, so a malicious file renamed with an allowed
 * {@code Content-Type} is caught before it reaches storage. Only the content types known to the
 * default allowlist have a signature here; any other declared type is passed through unchecked —
 * this sniffer narrows the allowlist, it does not replace it, so an operator-configured type this
 * class doesn't recognize yet is not silently rejected.
 */
public final class AttachmentContentSniffer {

    private static final Map<String, Predicate<byte[]>> SIGNATURES = Map.of(
            "application/pdf", AttachmentContentSniffer::isPdf,
            "image/png", AttachmentContentSniffer::isPng,
            "image/jpeg", AttachmentContentSniffer::isJpeg,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", AttachmentContentSniffer::isZip,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", AttachmentContentSniffer::isZip,
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", AttachmentContentSniffer::isZip,
            "text/plain", AttachmentContentSniffer::looksLikeText);

    private AttachmentContentSniffer() {
    }

    public static boolean matchesDeclaredType(byte[] content, String declaredContentType) {
        Predicate<byte[]> signature = SIGNATURES.get(declaredContentType);
        if (signature == null) {
            return true;
        }
        return content != null && signature.test(content);
    }

    private static boolean isPdf(byte[] content) {
        return startsWith(content, 0x25, 0x50, 0x44, 0x46, 0x2D);
    }

    private static boolean isPng(byte[] content) {
        return startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    }

    private static boolean isJpeg(byte[] content) {
        return startsWith(content, 0xFF, 0xD8, 0xFF);
    }

    private static boolean isZip(byte[] content) {
        return startsWith(content, 0x50, 0x4B, 0x03, 0x04);
    }

    /** No hard binary signature for text — reject a control-byte-laden file renamed to .txt. */
    private static boolean looksLikeText(byte[] content) {
        if (content.length == 0) {
            return false;
        }
        int limit = Math.min(content.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (content[i] == 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] content, int... expectedUnsignedBytes) {
        if (content.length < expectedUnsignedBytes.length) {
            return false;
        }
        for (int i = 0; i < expectedUnsignedBytes.length; i++) {
            if ((content[i] & 0xFF) != expectedUnsignedBytes[i]) {
                return false;
            }
        }
        return true;
    }
}
