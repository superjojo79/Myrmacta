package be.myrmacta.domain.signature.port.out;

import be.myrmacta.domain.signature.model.SignatureMode;
import be.myrmacta.domain.signature.model.SignatureOutcome;
import be.myrmacta.domain.signature.model.SignerKind;
import be.myrmacta.domain.signature.model.SignerStatus;
import be.myrmacta.domain.signature.model.SignedUploadVerdict;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Contrat d'un fournisseur de signature (TODO §72, D-SIGNATURE-ABSTRACTION), indépendant de tout
 * fournisseur. Un seul adaptateur est actif, choisi par {@code app.signature.provider}. Le domaine
 * s'adapte aux {@link SignatureProviderCapabilities} au lieu de supposer un fournisseur.
 *
 * <p>Forme <strong>gelée en M0</strong> (D-SIGNATURE-CONTRACT-CHECK) : voir le tableau de
 * correspondance avec les API publiques dans le design (§ « Revue documentaire du contrat »).
 */
public interface SignaturePort {

    /** Code de l'adaptateur (« stub » ou code du futur fournisseur), stocké sur la demande. */
    String code();

    SignatureProviderCapabilities capabilities();

    /** Crée la demande côté fournisseur. Ne doit jamais exposer un jeton Myrmacta au fournisseur. */
    SignatureSubmission submit(SubmitCommand command);

    /** Issue connue du fournisseur, si la demande est terminée. */
    SignatureProviderStatus status(String providerReference);

    /**
     * Interprète le corps JSON déjà décodé d'un callback du fournisseur (l'authenticité — HMAC
     * horodaté, idempotence, plafonds — est vérifiée en amont par le contrôleur générique
     * {@code /api/callbacks/{instanceId}}). Le stub n'utilise pas les callbacks : il applique ses
     * issues en interne.
     *
     * @throws UnsupportedOperationException si l'adaptateur ne reçoit pas de callback
     * @throws IllegalArgumentException si le corps n'est pas un événement valide de ce fournisseur
     */
    default SignatureCallbackEvent parseCallback(Object payload) {
        throw new UnsupportedOperationException("provider " + code() + " does not receive callbacks");
    }

    /** Annulation au mieux : ne doit jamais bloquer l'appelant. */
    void cancel(String providerReference);

    /** Document signé et preuve, une fois la demande {@code SIGNED}. */
    SignedArtifacts fetchArtifacts(String providerReference);

    /**
     * URL de signature à l'instant de l'appel (TODO §109, D-SIGNATURE-LINK). Vide si le signataire
     * n'est pas encore notifié, ou si l'adaptateur signe dans Myrmacta. {@code signerPosition} est
     * le rang Myrmacta, à partir de 0.
     */
    default Optional<String> signingUrl(String providerReference, int signerPosition) {
        return Optional.empty();
    }

    /**
     * Vérifie le PDF qu'un signataire a signé hors de Myrmacta puis téléversé (TODO §138,
     * D-EID-UPLOAD). Aucune écriture : l'appel peut joindre le réseau (révocation) et se fait hors
     * transaction. Un fichier refusé donne un verdict, pas une exception.
     *
     * @throws UnsupportedOperationException si {@code capabilities().signedUpload()} est faux
     */
    default SignedUploadVerdict verifySignedUpload(SignedUploadCheck check) {
        throw new UnsupportedOperationException("provider " + code() + " does not take signed uploads");
    }

    /**
     * @param baseDocument le PDF remis au signataire : la source, suivie des signatures déjà acceptées
     * @param signedDocument le PDF téléversé
     * @param usedCertificateFingerprints SHA-256 (hexadécimal minuscule) des certificats qui ont déjà
     *     signé pour un autre signataire de la demande
     */
    record SignedUploadCheck(byte[] baseDocument, byte[] signedDocument, Set<String> usedCertificateFingerprints) {
        public SignedUploadCheck {
            usedCertificateFingerprints =
                    usedCertificateFingerprints == null ? Set.of() : Set.copyOf(usedCertificateFingerprints);
        }
    }

    /**
     * @param requestId corrélation Myrmacta de la demande
     * @param document contenu à signer (borné par {@code app.signature.max-document-bytes})
     */
    record SubmitCommand(
            UUID requestId,
            String documentName,
            String documentContentType,
            byte[] document,
            String documentDigest,
            List<SubmitSigner> signers,
            SignatureMode mode,
            String message,
            Instant deadlineAt,
            String requestedAssuranceLevel) {}

    record SubmitSigner(
            UUID signerId, int position, SignerKind kind, boolean required, String displayName, String email) {}

    /**
     * {@code simulated} vrai pour le stub ; {@code assuranceLevel} informatif ; {@code signingUrls}
     * (signataire → URL chez le fournisseur) renseigné seulement si {@code hostedSigningUrl}.
     */
    record SignatureSubmission(
            String providerReference, boolean simulated, String assuranceLevel, Map<UUID, String> signingUrls) {
        public SignatureSubmission {
            signingUrls = signingUrls == null ? Map.of() : Map.copyOf(signingUrls);
        }
    }

    /** Événement de callback normalisé : issue de la demande et, si connues, décisions par signataire. */
    record SignatureCallbackEvent(
            String providerReference, SignatureOutcome outcome, Map<UUID, SignerStatus> signerDecisions) {
        public SignatureCallbackEvent {
            signerDecisions = signerDecisions == null ? Map.of() : Map.copyOf(signerDecisions);
        }
    }

    record SignatureProviderStatus(Optional<SignatureOutcome> outcome) {}

    record SignedArtifacts(
            byte[] signedDocument, String signedContentType, byte[] proof, String proofContentType,
            boolean simulated) {}

    /**
     * @param orderedSigning le fournisseur sait imposer un ordre
     * @param optionalSigners le fournisseur sait distinguer requis et facultatifs
     * @param hostedSigningUrl le signataire signe chez le fournisseur (redirection)
     * @param proofProvided le fournisseur fournit une preuve
     * @param signedUpload le signataire signe hors de Myrmacta et téléverse le PDF signé, que
     *     l'adaptateur vérifie (TODO §138, D-EID-UPLOAD)
     */
    record SignatureProviderCapabilities(
            boolean orderedSigning,
            boolean optionalSigners,
            boolean hostedSigningUrl,
            boolean proofProvided,
            boolean signedUpload,
            Set<String> assuranceLevels) {
        public SignatureProviderCapabilities {
            assuranceLevels = assuranceLevels == null ? Set.of() : Set.copyOf(assuranceLevels);
        }

        /** Fournisseur sans téléversement : la forme d'avant le TODO §138. */
        public SignatureProviderCapabilities(
                boolean orderedSigning,
                boolean optionalSigners,
                boolean hostedSigningUrl,
                boolean proofProvided,
                Set<String> assuranceLevels) {
            this(orderedSigning, optionalSigners, hostedSigningUrl, proofProvided, false, assuranceLevels);
        }

        /**
         * Le document signé et la preuve sont produits par le répartiteur, hors de la décision du
         * signataire : téléchargement chez un fournisseur hébergé, ou horodatage d'un téléversement.
         */
        public boolean artifactsDeferred() {
            return hostedSigningUrl || signedUpload;
        }
    }
}
