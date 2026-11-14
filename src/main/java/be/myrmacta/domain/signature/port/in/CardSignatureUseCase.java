package be.myrmacta.domain.signature.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Signature dans la page, par un pont navigateur, la clé restant sur la carte (TODO §138, D-EID-BRIDGE) : en deux
 * temps. {@code prepare} rend le condensé que la carte signera (aucune écriture) ; {@code complete} assemble le PDF
 * avec la valeur rendue par la carte puis le fait suivre exactement le chemin du téléversement
 * ({@link SubmitSignedDocumentUseCase}). Le PIN ne passe jamais ici (D-EID-PIN).
 *
 * <p>Même accès et même refus uniforme que le téléversement. Refus : {@code InvalidCardSignatureInputException}
 * (entrée illisible ou hors bornes), {@code SignedUploadRejectedException} ({@code STALE_DOCUMENT} si le document
 * courant a changé), {@code SigningSessionExpiredException}.
 */
public interface CardSignatureUseCase {

    Prepared prepareByToken(String token, CardInput input);

    Prepared prepareInternal(String userId, UUID signerId, CardInput input);

    DecideSignatureUseCase.Result completeByToken(String token, Completion completion);

    DecideSignatureUseCase.Result completeInternal(String userId, UUID signerId, Completion completion);

    /**
     * @param certificate certificat de signature lu sur la carte, en DER
     * @param certificateChain autorités lues sur la carte, en DER ; facultatives
     */
    record CardInput(byte[] certificate, List<byte[]> certificateChain) {
        public CardInput {
            certificateChain = certificateChain == null ? List.of() : List.copyOf(certificateChain);
        }
    }

    /**
     * @param digest condensé à faire signer par la carte
     * @param hashFunction {@code SHA-256} ou {@code SHA-384}
     * @param signingTime heure fixée par le serveur, à rendre telle quelle à {@code complete}
     * @param documentDigest SHA-256 (hexadécimal) du document courant sur lequel porte le condensé
     */
    record Prepared(byte[] digest, String hashFunction, Instant signingTime, String documentDigest) {}

    record Completion(
            byte[] certificate,
            List<byte[]> certificateChain,
            Instant signingTime,
            String documentDigest,
            byte[] signature) {
        public Completion {
            certificateChain = certificateChain == null ? List.of() : List.copyOf(certificateChain);
        }
    }
}
