package be.myrmacta.infrastructure.signature.eid;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.signature.model.SignedUploadEvidence;
import be.myrmacta.infrastructure.signature.TestPdfs;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TODO §138 (D-EID-EVIDENCE, D-EID-QUALIFIED, D-EID-NRN) — preuve de signature du fournisseur eid. */
class EidProofPdfFactoryTest {

    private static final Instant T = Instant.parse("2026-09-30T10:00:00Z");

    @Test
    void listsTheEvidencePerSignerTheDigestsAndTheQualificationBasisWithoutAnySimulationMark() {
        UUID id = UUID.randomUUID();
        SignedUploadEvidence evidence = new SignedUploadEvidence("Alice Martin", "CN=Citizen CA", "0A1B", "ab".repeat(32),
                T.minusSeconds(60), T, "PAdES-BASELINE-B", "RSA_SHA256", true, false, "CRL", T.minusSeconds(120));

        byte[] pdf = new EidProofPdfFactory().proof(new EidProofPdfFactory.ProofData(id, "eid", "SEQUENTIAL", T, T,
                "11".repeat(32), "22".repeat(32), List.of(
                        new EidProofPdfFactory.ProofSigner("Alice (a***@example.org)", "EXTERNAL", true, "SIGNED", T, 1,
                                evidence),
                        new EidProofPdfFactory.ProofSigner("Bob", "INTERNAL", false, "REVOKED", null, null, null))));

        String text = TestPdfs.text(pdf);
        assertThat(text).contains(id.toString(), "Alice Martin", "CN=Citizen CA", "0A1B", "ab".repeat(16),
                "PAdES-BASELINE-B", "RSA_SHA256", "CRL", "Bob", "REVOKED", "Base de la qualification");
        assertThat(text).contains("11".repeat(16), "22".repeat(16));
        assertThat(text).doesNotContainIgnoringCase("simulation").doesNotContain("registre national");
    }

    @Test
    void textFromACertificateCannotInjectControlCharacters() {
        SignedUploadEvidence evidence = new SignedUploadEvidence("Eve\u0000\u0007Mallory", "CA", "1", "cd".repeat(32), null,
                T, null, null, true, true, null, null);

        byte[] pdf = new EidProofPdfFactory().proof(new EidProofPdfFactory.ProofData(UUID.randomUUID(), "eid", "PARALLEL",
                T, T, "11".repeat(32), "22".repeat(32),
                List.of(new EidProofPdfFactory.ProofSigner("Eve", "EXTERNAL", true, "SIGNED", T, 1, evidence))));

        assertThat(TestPdfs.text(pdf)).contains("Eve  Mallory");
    }
}
