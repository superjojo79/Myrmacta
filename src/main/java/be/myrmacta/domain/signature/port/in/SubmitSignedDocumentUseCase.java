package be.myrmacta.domain.signature.port.in;

import java.util.UUID;

/**
 * Un signataire apporte le PDF qu'il a signé hors de Myrmacta (TODO §138, D-EID-UPLOAD). Externe : identifié
 * par son jeton ; interne : par son identité authentifiée. L'accès suit les règles du clic « Signer »
 * ({@link DecideSignatureUseCase}) et son refus uniforme ({@code PortalAccessDeniedException}) : demande
 * close ou échue, signataire déjà décidé ou pas à son tour.
 *
 * <p>Le PDF est vérifié par le fournisseur, hors transaction ; accepté, il devient le document de travail
 * de la demande (D-EID-CHAIN) et décide {@code SIGN} pour le signataire. Le résultat est celui d'une
 * décision.
 *
 * <p>Refus : {@code SignedUploadRejectedException} (motif restituable au signataire),
 * {@code SignedUploadTooLargeException} (taille).
 */
public interface SubmitSignedDocumentUseCase {

    DecideSignatureUseCase.Result submitByToken(String token, Upload upload);

    DecideSignatureUseCase.Result submitInternal(String userId, UUID signerId, Upload upload);

    /**
     * Chemin interne de la signature dans la page (TODO §138, D-EID-BRIDGE) : le PDF a été assemblé par le serveur, il
     * suit le chemin du téléversement sans la garde {@code upload-enabled}. Jamais exposé à un signataire.
     */
    DecideSignatureUseCase.Result submitAssembledByToken(String token, Upload upload);

    DecideSignatureUseCase.Result submitAssembledInternal(String userId, UUID signerId, Upload upload);

    /**
     * Fichier téléversé, lu à la demande : l'accès et la taille sont contrôlés avant que le contenu soit lu en
     * mémoire.
     */
    interface Upload {

        /** Taille déclarée du fichier, en octets. */
        long size();

        /** Contenu du fichier. */
        byte[] read();

        static Upload of(byte[] content) {
            return new Upload() {
                @Override
                public long size() {
                    return content.length;
                }

                @Override
                public byte[] read() {
                    return content;
                }
            };
        }
    }
}
