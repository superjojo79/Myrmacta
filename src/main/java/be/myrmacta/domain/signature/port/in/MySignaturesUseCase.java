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

    GetSignatureUseCase.DocumentContent document(AccessContext.User user, UUID signerId);

    DecideSignatureUseCase.Result decide(
            AccessContext.User user, UUID signerId, DecideSignatureUseCase.Choice choice, String reason);

    /**
     * @param myTurn faux tant que le signataire est {@code WAITING} (mode séquentiel)
     * @param signingUrl adresse chez le fournisseur, seulement à son tour ; nulle en attente du lien
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
            String signingUrl) {}
}
