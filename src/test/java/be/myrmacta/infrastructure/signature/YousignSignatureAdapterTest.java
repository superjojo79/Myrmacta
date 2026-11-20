package be.myrmacta.infrastructure.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignerKind;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import be.myrmacta.domain.signature.port.out.SignaturePort.SignatureSubmission;
import be.myrmacta.domain.signature.port.out.SignaturePort.SubmitCommand;
import be.myrmacta.domain.signature.port.out.SignaturePort.SubmitSigner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/** Cérémonie Yousign : ancre, activation, lien non conservé (TODO §109). */
class YousignSignatureAdapterTest {

    private final FakeYousign client = new FakeYousign();
    private final YousignSignatureAdapter adapter = new YousignSignatureAdapter(client);

    @Test
    void submitCreatesUploadsAddsSignersAndActivatesWithoutStoringTheLink() throws IOException {
        client.anchors = 2;
        byte[] pdf = pdf("{{s1|signature|85|37}}", "{{s2|signature|85|37}}");
        SignatureSubmission submission = adapter.submit(command(SignatureMode.SEQUENTIAL, pdf, "Ada Lovelace", "Bob"));

        assertThat(client.calls).containsExactly("create:true", "upload", "signer:Ada:Lovelace", "signer:Bob:-", "activate");
        assertThat(submission.providerReference()).isEqualTo("yousign:" + FakeYousign.REQUEST_ID);
        assertThat(submission.simulated()).isFalse();
        assertThat(submission.signingUrls()).isEmpty();
        assertThat(adapter.capabilities().hostedSigningUrl()).isTrue();
        assertThat(adapter.capabilities().optionalSigners()).isFalse();
    }

    @Test
    void missingAnchorCancelsTheDraftAndFails() {
        client.anchors = 0;

        assertThatThrownBy(() -> adapter.submit(command(SignatureMode.PARALLEL, "Ada Lovelace")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("anchor");
        assertThat(client.calls).contains("delete").doesNotContain("activate");
    }

    /** Deux fois l'ancre du rang 1 : Yousign compte 2, mais le rang 2 n'a pas la sienne. */
    @Test
    void duplicateAnchorOnTheFirstRankCancelsTheDraft() throws IOException {
        client.anchors = 2;
        byte[] pdf = pdf("{{s1|signature|85|37}}", "{{s1|signature|85|37}}");

        assertThatThrownBy(() -> adapter.submit(command(SignatureMode.PARALLEL, pdf, "Ada Lovelace", "Bob")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("anchor");
        assertThat(client.calls).contains("delete").doesNotContain("activate");
    }

    /** #133 — un dépôt refusé après la création supprime le brouillon et laisse remonter l'erreur d'origine. */
    @Test
    void uploadFailureDeletesTheDraftAndRethrowsTheOriginalError() {
        client.failOn = "upload";

        assertThatThrownBy(() -> adapter.submit(command(SignatureMode.PARALLEL, "Ada Lovelace")))
                .isSameAs(client.failure);
        assertThat(client.calls).containsExactly("create:false", "upload", "delete");
    }

    @Test
    void signerFailureDeletesTheDraftAndRethrowsTheOriginalError() throws IOException {
        client.anchors = 2;
        client.failOn = "signer:Bob:-";
        byte[] pdf = pdf("{{s1|signature|85|37}}", "{{s2|signature|85|37}}");

        assertThatThrownBy(() -> adapter.submit(command(SignatureMode.PARALLEL, pdf, "Ada Lovelace", "Bob")))
                .isSameAs(client.failure);
        assertThat(client.calls).endsWith("signer:Bob:-", "delete").doesNotContain("activate");
    }

    @Test
    void activationFailureDeletesTheDraftAndRethrowsTheOriginalError() throws IOException {
        client.failOn = "activate";
        byte[] pdf = pdf("{{s1|signature|85|37}}");

        assertThatThrownBy(() -> adapter.submit(command(SignatureMode.PARALLEL, pdf, "Ada Lovelace")))
                .isSameAs(client.failure);
        assertThat(client.calls).endsWith("activate", "delete");
    }

    /**
     * #133 — l'activation a pu aboutir chez Yousign malgré l'erreur (délai dépassé) : la demande n'est plus un brouillon
     * et refuse la suppression ; elle est alors annulée. Un nettoyage qui échoue lui-même ne masque pas l'erreur.
     */
    @Test
    void failedDeletionFallsBackToCancel_andAFailingCleanupNeverHidesTheOriginalError() throws IOException {
        client.failOn = "activate";
        client.deleteFails = true;
        client.cancelFails = true;
        byte[] pdf = pdf("{{s1|signature|85|37}}");

        assertThatThrownBy(() -> adapter.submit(command(SignatureMode.PARALLEL, pdf, "Ada Lovelace")))
                .isSameAs(client.failure);
        assertThat(client.calls).endsWith("activate", "delete", "cancel");
    }

    @Test
    void creationFailureHasNoDraftToClean() {
        client.failOn = "create:false";

        assertThatThrownBy(() -> adapter.submit(command(SignatureMode.PARALLEL, "Ada Lovelace")))
                .isSameAs(client.failure);
        assertThat(client.calls).containsExactly("create:false");
    }

    /** #132 — chaque signataire est créé dans la langue du tenant ({@code app.i18n.default-locale}). */
    @Test
    void signersAreCreatedInTheTenantLanguage() throws IOException {
        client.anchors = 2;
        YousignSignatureAdapter english = new YousignSignatureAdapter(client, Locale.ENGLISH);
        byte[] pdf = pdf("{{s1|signature|85|37}}", "{{s2|signature|85|37}}");

        english.submit(command(SignatureMode.PARALLEL, pdf, "Ada Lovelace", "Bob"));

        assertThat(client.locales).containsExactly(Locale.ENGLISH, Locale.ENGLISH);
    }

    @Test
    void doneEventBecomesSigned() {
        Map<String, Object> payload = Map.of(
                "event_name", "signature_request.done",
                "data", Map.of("signature_request", Map.of("id", FakeYousign.REQUEST_ID.toString())));

        assertThat(adapter.parseCallback(payload).outcome()).isEqualTo(SignatureOutcome.SIGNED);
    }

    @Test
    void onlySandboxAndProductionHostsAreAccepted() {
        assertThat(YousignHttpClient.baseFor("sandbox")).isEqualTo(YousignHttpClient.SANDBOX);
        assertThat(YousignHttpClient.baseFor("production")).isEqualTo(YousignHttpClient.PRODUCTION);
        assertThatThrownBy(() -> YousignHttpClient.baseFor("https://evil.example"))
                .isInstanceOf(YousignCallException.class);
    }

    private static SubmitCommand command(SignatureMode mode, String... names) {
        return command(mode, new byte[] {'%'}, names);
    }

    private static SubmitCommand command(SignatureMode mode, byte[] pdf, String... names) {
        List<SubmitSigner> signers = new ArrayList<>();
        int position = 0;
        for (String name : names) {
            signers.add(new SubmitSigner(UUID.randomUUID(), position++, SignerKind.EXTERNAL, true, name, "a@example.org"));
        }
        return new SubmitCommand(UUID.randomUUID(), "Contrat", "application/pdf", pdf, "digest",
                signers, mode, null, Instant.parse("2026-11-11T00:00:00Z"), null);
    }

    private static byte[] pdf(String... lines) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 700);
                for (String line : lines) {
                    stream.showText(line);
                    stream.newLineAtOffset(0, -20);
                }
                stream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static final class FakeYousign implements YousignClient {
        static final UUID REQUEST_ID = UUID.fromString("0ef334d2-7f36-4643-8a2a-4283b4a3e4a4");
        final List<String> calls = new ArrayList<>();
        final List<Locale> locales = new ArrayList<>();
        final YousignCallException failure = new YousignCallException("Yousign answered HTTP 400");
        int anchors = 1;
        String failOn;
        boolean deleteFails;
        boolean cancelFails;

        private void record(String call) {
            calls.add(call);
            if (call.equals(failOn)) {
                throw failure;
            }
        }

        @Override
        public UUID createSignatureRequest(String name, boolean orderedSigners, String externalId, String expirationDate) {
            record("create:" + orderedSigners);
            return REQUEST_ID;
        }

        @Override
        public Upload uploadSignablePdf(UUID requestId, String filename, byte[] pdf) {
            record("upload");
            return new Upload(UUID.randomUUID(), anchors);
        }

        @Override
        public UUID addSigner(UUID requestId, String firstName, String lastName, String email, Locale locale) {
            locales.add(locale);
            record("signer:" + firstName + ":" + lastName);
            return UUID.randomUUID();
        }

        @Override
        public void activate(UUID requestId) {
            record("activate");
        }

        @Override
        public Optional<String> signatureLink(UUID requestId, int signerPosition) {
            return Optional.of("https://yousign.app/signatures/abc");
        }

        @Override
        public void cancel(UUID requestId) {
            calls.add("cancel");
            if (cancelFails) {
                throw new YousignCallException("Yousign answered HTTP 400");
            }
        }

        @Override
        public void deleteDraft(UUID requestId) {
            calls.add("delete");
            if (deleteFails) {
                throw new YousignCallException("Yousign answered HTTP 405");
            }
        }

        @Override
        public byte[] downloadSignedPdf(UUID requestId) {
            return new byte[] {1};
        }

        @Override
        public byte[] downloadAuditTrail(UUID requestId) {
            return new byte[] {2};
        }
    }
}
