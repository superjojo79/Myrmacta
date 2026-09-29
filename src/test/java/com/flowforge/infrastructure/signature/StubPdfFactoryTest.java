package com.flowforge.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** S-STUB-2, S-STUB-9, S-STUB-10 (TODO §72, D-SIGNATURE-PDF / D-SIGNATURE-PROOF). */
class StubPdfFactoryTest {

    private final StubPdfFactory factory = new StubPdfFactory();

    @Test
    void watermarkAddsTheSimulationMarkOnEveryPageAndKeepsTheOriginalContent() {
        byte[] source = TestPdfs.pdf(3, "Contrat de test");

        byte[] marked = factory.watermark(source);

        assertThat(TestPdfs.pageCount(marked)).isEqualTo(3);
        String text = TestPdfs.text(marked);
        assertThat(text).contains("Contrat de test page 1", "Contrat de test page 3");
        assertThat(text.split(StubPdfFactory.MARK, -1).length - 1).isGreaterThanOrEqualTo(3);
        assertThat(TestPdfs.text(source)).doesNotContain("SIMULATION");
    }

    @Test
    void watermarkRejectsNonPdfContent() {
        assertThatThrownBy(() -> factory.watermark("pas un pdf".getBytes()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void proofContainsDigestSignersTimestampsAndTheMark() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-09-30T10:00:00Z");
        Instant done = Instant.parse("2026-09-30T11:00:00Z");

        byte[] proof = factory.proof(new StubPdfFactory.ProofData(
                id, "storage/doc.pdf", "ab".repeat(32), "stub", "SEQUENTIAL", created, done,
                List.of(
                        new StubPdfFactory.ProofSigner("Alice (a***@example.org)", "EXTERNAL", true, "SIGNED", done),
                        new StubPdfFactory.ProofSigner("Bob (bob)", "INTERNAL", false, "DECLINED", done))));

        String text = TestPdfs.text(proof);
        assertThat(text).contains(StubPdfFactory.MARK.toUpperCase(java.util.Locale.ROOT).substring(0, 10));
        assertThat(text).contains(id.toString(), "ab".repeat(32), "SEQUENTIAL", "2026-09-30T11:00:00Z");
        assertThat(text).contains("Alice (a***@example.org)", "requis", "facultatif", "DECLINED");
        assertThat(text).contains("aucune valeur probante");
    }

    @Test
    void watermarkedPdfCarriesTheMarkInItsMetadataAndForbidsModification() throws Exception {
        // revue adversariale n°14 : l'incrustation seule est retirable
        byte[] marked = factory.watermark(TestPdfs.pdf(1, "Contrat"));

        org.openpdf.text.pdf.PdfReader reader = new org.openpdf.text.pdf.PdfReader(marked);
        assertThat(reader.getInfo().get("Subject")).contains("SIMULATION");
        assertThat(reader.isEncrypted()).isTrue();
        assertThat(reader.getPermissions() & org.openpdf.text.pdf.PdfWriter.ALLOW_MODIFY_CONTENTS).isZero();
        reader.close();
    }
}
