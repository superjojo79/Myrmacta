package be.myrmacta.domain.ebox.port.out;

import java.time.Instant;

/**
 * TODO §155 (D-EBOX-SEND-REGISTERED) — réservation d'une clé d'idempotence de recommandé, unique par tenant. Registered Mail 1.1
 * n'a pas d'en-tête {@code Idempotency-Key} : sans réservation, deux tentatives concurrentes, ou une reprise après une issue
 * incertaine, pourraient déposer deux recommandés. Une seule tentative envoie ; toute autre doit retrouver la preuve.
 */
public interface EboxSendReservations {

    /** Vrai si la clé vient d'être réservée par cet appel ; faux si une tentative l'a déjà prise. Validé tout de suite. */
    boolean reserve(String tenantId, String idempotencyKey, Instant at);

    /** Libère la clé : la tentative n'a certainement rien déposé. */
    void release(String tenantId, String idempotencyKey);
}
