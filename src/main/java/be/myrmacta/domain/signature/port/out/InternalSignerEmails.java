package be.myrmacta.domain.signature.port.out;

import java.util.Optional;

/**
 * Adresse d'un signataire interne (TODO §109, D-SIGNATURE-SIGNER-EMAIL). L'annuaire ne sert que
 * lorsqu'un fournisseur hébergé exige une adresse ; le stub n'appelle pas ce port.
 */
@FunctionalInterface
public interface InternalSignerEmails {

    Optional<String> emailOf(String userId);
}
