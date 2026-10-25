package be.myrmacta.domain.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AttachmentContentSnifferTest {

    @Test
    void matchesDeclaredType_acceptsValidPdfSignature() {
        byte[] content = bytes(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34);

        assertThat(AttachmentContentSniffer.matchesDeclaredType(content, "application/pdf")).isTrue();
    }

    @Test
    void matchesDeclaredType_acceptsValidPngSignature() {
        byte[] content = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

        assertThat(AttachmentContentSniffer.matchesDeclaredType(content, "image/png")).isTrue();
    }

    @Test
    void matchesDeclaredType_acceptsValidJpegSignature() {
        byte[] content = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10);

        assertThat(AttachmentContentSniffer.matchesDeclaredType(content, "image/jpeg")).isTrue();
    }

    @Test
    void matchesDeclaredType_acceptsValidOoxmlZipSignatureForDocx() {
        byte[] content = bytes(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00);

        assertThat(AttachmentContentSniffer.matchesDeclaredType(
                        content,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .isTrue();
    }

    @Test
    void matchesDeclaredType_rejectsPngBytesDeclaredAsPdf() {
        byte[] content = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

        assertThat(AttachmentContentSniffer.matchesDeclaredType(content, "application/pdf")).isFalse();
    }

    @Test
    void matchesDeclaredType_rejectsBinaryContentDeclaredAsPlainText() {
        byte[] content = bytes(0x00, 0x01, 0x02, 0x03, 0xFF, 0xFE);

        assertThat(AttachmentContentSniffer.matchesDeclaredType(content, "text/plain")).isFalse();
    }

    @Test
    void matchesDeclaredType_acceptsLegitimateTextDeclaredAsPlainText() {
        byte[] content = "Hello, this is a legitimate text attachment.".getBytes(StandardCharsets.UTF_8);

        assertThat(AttachmentContentSniffer.matchesDeclaredType(content, "text/plain")).isTrue();
    }

    @Test
    void matchesDeclaredType_rejectsEmptyContentWithoutThrowing() {
        assertThat(AttachmentContentSniffer.matchesDeclaredType(new byte[0], "application/pdf")).isFalse();
    }

    @Test
    void matchesDeclaredType_rejectsEmptyContentDeclaredAsPlainText() {
        assertThat(AttachmentContentSniffer.matchesDeclaredType(new byte[0], "text/plain")).isFalse();
    }

    @Test
    void matchesDeclaredType_recognizedTypeWithNullContent_returnsFalse() {
        assertThat(AttachmentContentSniffer.matchesDeclaredType(null, "application/pdf")).isFalse();
    }

    @Test
    void matchesDeclaredType_passesThroughUnrecognizedDeclaredType() {
        byte[] content = bytes(0x01, 0x02, 0x03);

        assertThat(AttachmentContentSniffer.matchesDeclaredType(content, "application/x-custom-unlisted"))
                .isTrue();
    }

    private static byte[] bytes(int... unsignedBytes) {
        byte[] result = new byte[unsignedBytes.length];
        for (int i = 0; i < unsignedBytes.length; i++) {
            result[i] = (byte) unsignedBytes[i];
        }
        return result;
    }
}
