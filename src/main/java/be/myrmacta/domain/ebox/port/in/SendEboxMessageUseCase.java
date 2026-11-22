package be.myrmacta.domain.ebox.port.in;

import be.myrmacta.domain.ebox.model.EboxSendOutcome;
import be.myrmacta.domain.ebox.model.SendEboxCommand;

/** TODO §155 — envoyer un document dans l'eBox d'un citoyen ou d'une entreprise, ou dire quelle voie de secours suivre. */
public interface SendEboxMessageUseCase {

    EboxSendOutcome send(SendEboxCommand command);
}
