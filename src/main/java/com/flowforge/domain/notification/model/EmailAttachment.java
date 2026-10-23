package com.flowforge.domain.notification.model;

import java.util.Objects;

/**
 * Issue #183 — pièce jointe d'un e-mail, portée par <b>référence</b> jusqu'à l'outbox (D-NOTIF-ATTACH-REF-IN-OUTBOX) : le
 * contenu est relu dans le stockage du tenant au moment de l'envoi. Construite seulement après les contrôles de
 * {@code ResolveEmailAttachmentUseCase} (document produit du dossier courant, PDF, taille — D-NOTIF-ATTACH-PRODUCED-DOC).
 */
public record EmailAttachment(String storageRef, String fileName, String contentType) {

    public EmailAttachment {
        requireText(storageRef, "storageRef");
        requireText(fileName, "fileName");
        requireText(contentType, "contentType");
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
