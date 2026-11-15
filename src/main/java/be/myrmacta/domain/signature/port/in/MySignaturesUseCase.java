package be.myrmacta.domain.signature.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * « Mes signatures » : les demandes adressées à un utilisateur Myrmacta, qu'il lit et signe depuis la
 * SPA authentifiée (TODO §72, US-10, D-SIGNATURE-INTERNAL-UX, A4). Un signataire interne ne voit et
 * ne signe que les demandes qui lui sont adressées, à son tour, et seulement s'il a le droit de lecture
 * du dossier ; tout autre cas est traité comme une ressource inexistante.
 */
public interface MySignaturesUseCase {

    List<Item> list(AccessContext.User user);

    /**
     * Lien chez le fournisseur, demandé au clic du signataire à son tour. {@code null} si le lien
     * n'est pas prêt ou si le fournisseur échoue : l'écran d'attente reste affiché.
     */
    String signingUrl(AccessContext.User user, UUID signerId);

    GetSignatureUseCase.DocumentContent document(AccessContext.User user, UUID signerId);

    DecideSignatureUseCase.Result decide(
            AccessContext.User user, UUID signerId, DecideSignatureUseCase.Choice choice, String reason);

    /**
     * Téléverse le PDF que le signataire a signé hors de Myrmacta (TODO §138, D-EID-UPLOAD) : même accès que
     * {@link #decide}, même résultat qu'une décision {@code SIGN}.
     *
     * @throws be.myrmacta.domain.signature.model.SignedUploadRejectedException fichier refusé
     */
    DecideSignatureUseCase.Result submitSigned(
            AccessContext.User user, UUID signerId, SubmitSignedDocumentUseCase.Upload upload);

    /** Signature dans la page (TODO §138, D-EID-BRIDGE) : même accès que {@link #decide}, aucune écriture. */
    CardSignatureUseCase.Prepared prepareCardSignature(
            AccessContext.User user, UUID signerId, CardSignatureUseCase.CardInput input);

    /** Fin de la signature dans la page : même résultat qu'une décision {@code SIGN}. */
    DecideSignatureUseCase.Result completeCardSignature(
            AccessContext.User user, UUID signerId, CardSignatureUseCase.Completion completion);

    /**
     * @param cardBridges ponts autorisés pour signer dans la page, vide si non offerte
     * @param signedUpload le signataire signe hors de Myrmacta et téléverse le PDF signé (TODO §138)
     * @param myTurn faux tant que le signataire est {@code WAITING} (mode séquentiel)
     * @param signingUrl toujours nulle dans la liste : le lien se demande au clic ({@link #signingUrl})
     */
    record Item(
            UUID signerId,
            UUID requestId,
            UUID dossierId,
            String dossierReference,
            String documentLabel,
            boolean required,
            boolean myTurn,
            Instant deadlineAt,
            boolean simulated,
            boolean hosted,
            boolean signedUpload,
            List<String> cardBridges,
            String signingUrl) {}
}
