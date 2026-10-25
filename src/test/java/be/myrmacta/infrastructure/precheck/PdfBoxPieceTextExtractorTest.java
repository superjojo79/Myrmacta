package be.myrmacta.infrastructure.precheck;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.precheck.model.PieceText;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/** TODO §106 (lot A, P6, H3) — texte des pièces, borné ; jamais d'exception pour une pièce illisible. */
class PdfBoxPieceTextExtractorTest {

    private final PdfBoxPieceTextExtractor extractor = new PdfBoxPieceTextExtractor();

    @Test
    void readsTheTextLayerOfAPdf() throws IOException {
        PieceText text = extractor.extract(pdf("Facture Sibelga", "Rue de la Loi 1, 1030 Schaerbeek"),
                "application/pdf", 30, 30_000);

        assertThat(text.status()).isEqualTo(PieceText.Status.READABLE);
        assertThat(text.text()).contains("Facture Sibelga").contains("1030 Schaerbeek");
        assertThat(text.pagesRead()).isEqualTo(2);
        assertThat(text.truncated()).isFalse();
    }

    @Test
    void boundsPagesAndCharacters() throws IOException {
        PieceText pages = extractor.extract(pdf("page un", "page deux", "page trois"), "application/pdf", 2, 30_000);
        assertThat(pages.text()).contains("page deux").doesNotContain("page trois");
        assertThat(pages.pagesRead()).isEqualTo(2);
        assertThat(pages.truncated()).isTrue();

        PieceText chars = extractor.extract(pdf("abcdefghijklmnopqrstuvwxyz"), "application/pdf", 30, 10);
        assertThat(chars.text()).hasSize(10);
        assertThat(chars.truncated()).isTrue();
    }

    @Test
    void aPdfWithoutTextLayerIsAScanAndIsNeverOcred() throws IOException {
        assertThat(extractor.extract(pdf(), "application/pdf", 30, 30_000).status())
                .isEqualTo(PieceText.Status.NO_TEXT);
        assertThat(extractor.extract(new byte[] {(byte) 0x89, 'P', 'N', 'G'}, "image/png", 30, 30_000).status())
                .isEqualTo(PieceText.Status.NO_TEXT);
    }

    @Test
    void encryptedCorruptAndUnsupportedPiecesAreReportedNotThrown() throws IOException {
        assertThat(extractor.extract(encryptedPdf(), "application/pdf", 30, 30_000).status())
                .isEqualTo(PieceText.Status.ENCRYPTED);
        assertThat(extractor.extract("%PDF-1.7 pas vraiment".getBytes(StandardCharsets.US_ASCII), "application/pdf", 30,
                30_000).status()).isEqualTo(PieceText.Status.UNREADABLE);
        assertThat(extractor.extract(new byte[] {1, 2, 3}, "application/zip", 30, 30_000).status())
                .isEqualTo(PieceText.Status.UNSUPPORTED);
        assertThat(extractor.extract(new byte[0], "application/pdf", 30, 30_000).status())
                .isEqualTo(PieceText.Status.UNREADABLE);
    }

    @Test
    void plainTextIsReadAsUtf8WithoutControlCharacters() {
        PieceText text = extractor.extract("Ligne 1\u0007\n\n\n\nLigne   2".getBytes(StandardCharsets.UTF_8),
                "text/plain; charset=utf-8", 30, 30_000);
        assertThat(text.text()).isEqualTo("Ligne 1\n\nLigne 2");
        assertThat(extractor.extract(new byte[] {(byte) 0xC3, (byte) 0x28}, "text/plain", 30, 30_000).status())
                .isEqualTo(PieceText.Status.UNREADABLE);
    }

    @Test
    void truncationNeverSplitsASurrogatePair() {
        PieceText text = PdfBoxPieceTextExtractor.bounded("ab😀cd", 1, false, 3);
        assertThat(text.text()).isEqualTo("ab");
        assertThat(text.truncated()).isTrue();
    }

    private static byte[] pdf(String... pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (pages.length == 0) {
                document.addPage(new PDPage());
            }
            for (String line : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 700);
                    stream.showText(line);
                    stream.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] encryptedPdf() throws IOException {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf("secret"));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "user", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(out);
            return out.toByteArray();
        }
    }
}
