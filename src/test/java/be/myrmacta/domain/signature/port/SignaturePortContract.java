package be.myrmacta.domain.signature.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignerKind;
import be.myrmacta.domain.signature.port.out.SignaturePort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Suite de tests de contrat de {@link SignaturePort} (S-CONTRACT-1, D-SIGNATURE-CONTRACT-CHECK),
 * réutilisable par tout futur adaptateur : chaque adaptateur l'étend et fournit
 * {@link #port()} et {@link #completeWith}, l'étape propre au fournisseur qui amène une demande
 * soumise à une issue donnée.
 */
public abstract class SignaturePortContract {

    protected abstract SignaturePort port();

    /** Amène la demande soumise (référence donnée) à l'issue demandée, comme le ferait le fournisseur. */
    protected abstract void completeWith(String providerReference, UUID requestId, SignatureOutcome outcome);

    /**
     * Point d'extension pour un adaptateur dont l'état vit ailleurs que chez un fournisseur externe
     * (le stub relit la demande FlowForge) : appelé après chaque {@code submit}.
     */
    protected void onSubmitted(UUID requestId, SignaturePort.SignatureSubmission submission) {}

    private SignaturePort.SignatureSubmission submit(UUID requestId) {
        SignaturePort.SignatureSubmission submission = port().submit(command(requestId));
        onSubmitted(requestId, submission);
        return submission;
    }

    /** Contenu soumis ; un adaptateur qui relit réellement le PDF (stub) le redéfinit. */
    protected byte[] document() {
        return "%PDF-fake".getBytes();
    }

    protected final SignaturePort.SubmitCommand command(UUID requestId) {
        byte[] doc = document();
        return new SignaturePort.SubmitCommand(
                requestId,
                "contrat.pdf",
                "application/pdf",
                doc,
                "a".repeat(64),
                List.of(
                        new SignaturePort.SubmitSigner(UUID.randomUUID(), 0, SignerKind.EXTERNAL, true, "Alice",
                                "alice@example.org"),
                        new SignaturePort.SubmitSigner(UUID.randomUUID(), 1, SignerKind.EXTERNAL, false, "Bob",
                                "bob@example.org")),
                SignatureMode.PARALLEL,
                "Merci de signer",
                Instant.parse("2026-10-30T10:00:00Z"),
                null);
    }

    @Test
    void codeAndCapabilitiesAreDeclared() {
        assertThat(port().code()).isNotBlank();
        assertThat(port().capabilities()).isNotNull();
        assertThat(port().capabilities().assuranceLevels()).isNotNull();
    }

    @Test
    void submitReturnsAReferenceAndACoherentSimulationFlag() {
        SignaturePort.SignatureSubmission submission = submit(UUID.randomUUID());

        assertThat(submission.providerReference()).isNotBlank();
        assertThat(submission.signingUrls()).isNotNull();
        if (!port().capabilities().hostedSigningUrl()) {
            assertThat(submission.signingUrls()).isEmpty();
        }
    }

    @Test
    void aFreshRequestHasNoOutcomeYet() {
        SignaturePort.SignatureSubmission submission = submit(UUID.randomUUID());

        assertThat(port().status(submission.providerReference()).outcome()).isEmpty();
    }

    @Test
    void aSignedRequestReportsItsOutcomeAndYieldsArtifacts() {
        UUID id = UUID.randomUUID();
        SignaturePort.SignatureSubmission submission = submit(id);
        completeWith(submission.providerReference(), id, SignatureOutcome.SIGNED);

        assertThat(port().status(submission.providerReference()).outcome()).contains(SignatureOutcome.SIGNED);
        SignaturePort.SignedArtifacts artifacts = port().fetchArtifacts(submission.providerReference());
        assertThat(artifacts.signedDocument()).isNotEmpty();
        assertThat(artifacts.signedContentType()).isNotBlank();
        if (port().capabilities().proofProvided()) {
            assertThat(artifacts.proof()).isNotEmpty();
            assertThat(artifacts.proofContentType()).isNotBlank();
        }
        assertThat(artifacts.simulated()).isEqualTo(submission.simulated());
    }

    @Test
    void aDeclinedRequestReportsItsOutcomeAndHasNoArtifacts() {
        UUID id = UUID.randomUUID();
        SignaturePort.SignatureSubmission submission = submit(id);
        completeWith(submission.providerReference(), id, SignatureOutcome.DECLINED);

        assertThat(port().status(submission.providerReference()).outcome()).contains(SignatureOutcome.DECLINED);
        assertThatThrownBy(() -> port().fetchArtifacts(submission.providerReference()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelIsBestEffortAndIdempotent() {
        SignaturePort.SignatureSubmission submission = submit(UUID.randomUUID());

        assertThatCode(() -> {
                    port().cancel(submission.providerReference());
                    port().cancel(submission.providerReference());
                })
                .doesNotThrowAnyException();
    }

    @Test
    void callbackParsingIsSupportedOrExplicitlyUnsupported() {
        try {
            SignaturePort.SignatureCallbackEvent event = port().parseCallback(Map.of());
            assertThat(event).isNotNull();
        } catch (UnsupportedOperationException expected) {
            // un adaptateur sans callback (stub) le déclare ainsi
        } catch (RuntimeException other) {
            // un adaptateur avec callback rejette un corps vide de façon explicite
            assertThat(other).isNotInstanceOf(NullPointerException.class);
        }
    }
}
