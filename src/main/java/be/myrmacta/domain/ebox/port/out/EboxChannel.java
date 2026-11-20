package be.myrmacta.domain.ebox.port.out;

import be.myrmacta.domain.ebox.model.EboxConsent;
import be.myrmacta.domain.ebox.model.EboxOutgoingMessage;
import be.myrmacta.domain.ebox.model.EboxPublication;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxRoute;
import be.myrmacta.domain.ebox.model.EboxSenderAccount;

/**
 * TODO §155 (D-EBOX-SEND-CHANNELS) — une voie d'envoi eBox : entreprise (eBox Enterprise, ONSS/Smals) ou citoyen (eBox citoyen,
 * BOSA). Une seule implémentation par voie.
 */
public interface EboxChannel {

    EboxRoute route();

    /**
     * État du consentement (citoyen) ou de la préférence eBox (entreprise) du destinataire, constaté maintenant.
     *
     * @throws RuntimeException si le service ne répond pas : l'envoi s'arrête, rien n'est décidé sans état connu
     */
    EboxConsent consent(String tenantId, EboxSenderAccount sender, EboxRecipient recipient);

    /**
     * Publie le message. Un refus certain lève {@link be.myrmacta.domain.ebox.model.EboxPublicationRejectedException} ; une issue
     * inconnue rend {@link EboxPublication.Status#UNCERTAIN}.
     */
    EboxPublication publish(String tenantId, EboxSenderAccount sender, EboxOutgoingMessage message, String idempotencyKey);
}
