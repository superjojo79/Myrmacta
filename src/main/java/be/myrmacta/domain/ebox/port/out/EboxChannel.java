package be.myrmacta.domain.ebox.port.out;

import be.myrmacta.domain.ebox.model.EboxConsent;
import be.myrmacta.domain.ebox.model.EboxOutgoingMessage;
import be.myrmacta.domain.ebox.model.EboxPublication;
import be.myrmacta.domain.ebox.model.EboxRecipient;
import be.myrmacta.domain.ebox.model.EboxRoute;
import be.myrmacta.domain.ebox.model.EboxSenderAccount;
import java.util.Optional;

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
     * inconnue rend {@link EboxPublication.Status#UNCERTAIN}. Contrat : toute exception levée signifie que rien n'a été publié
     * (une issue inconnue après l'envoi est rendue, jamais levée).
     */
    EboxPublication publish(String tenantId, EboxSenderAccount sender, EboxOutgoingMessage message, String idempotencyKey);

    /**
     * TODO §155 (D-EBOX-SEND-REGISTERED) — preuve de dépôt d'un recommandé déjà émis : par identifiant rendu si
     * {@code providerMessageId} est connu, sinon par référence client (la clé d'idempotence). Rend une publication
     * {@link EboxPublication.Status#DUPLICATE} qui porte la preuve, ou rien si l'eBox n'en connaît pas.
     */
    Optional<EboxPublication> registeredProof(String tenantId, EboxSenderAccount sender, String idempotencyKey,
            String providerMessageId);
}
