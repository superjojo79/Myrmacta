package com.flowforge.domain.notification.port.in;

import com.flowforge.domain.notification.model.EmailAttachment;
import java.util.UUID;

/**
 * Issue #183 — vérifie qu'une référence de stockage désigne un document joignable à un e-mail du dossier (D-NOTIF-ATTACH-PRODUCED-DOC)
 * et construit la pièce jointe.
 */
public interface ResolveEmailAttachmentUseCase {

    /**
     * @param dossierId dossier du process courant
     * @param storageRef valeur résolue de la source (variable ou champ du dossier)
     * @param requestedFileName nom demandé par le concepteur ({@code null} : libellé de l'élément)
     * @param origin libellé de la source pour les messages d'erreur (jamais la valeur)
     * @throws com.flowforge.domain.notification.model.NotificationValidationException si le document n'est pas un PDF
     *     produit et rattaché à ce dossier, s'il est vide, illisible ou trop gros
     */
    EmailAttachment resolve(UUID dossierId, String storageRef, String requestedFileName, String origin);
}
