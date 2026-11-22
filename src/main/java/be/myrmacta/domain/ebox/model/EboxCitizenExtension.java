package be.myrmacta.domain.ebox.model;

/**
 * TODO §155 (D-EBOX-SEND-MODEL) — ce que le format citoyen ajoute au modèle commun (format entreprise). Aujourd'hui : l'invitation
 * à payer. Le format exact de l'eBox citoyen (BOSA) n'est pas public ; la liste s'allongera quand il sera connu.
 *
 * @param payment invitation à payer, facultative
 */
public record EboxCitizenExtension(EboxPayment payment) {}
