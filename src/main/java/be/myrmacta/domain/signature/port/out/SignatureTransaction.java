package be.myrmacta.domain.signature.port.out;

import java.util.function.Supplier;

/**
 * Frontière de transaction demandée par le domaine (TODO §138) : le téléversement d'un PDF signé vérifie
 * hors transaction (la révocation peut joindre le réseau), puis enregistre en une transaction courte.
 * L'infrastructure fournit la transaction ; un test exécute l'action en ligne.
 */
@FunctionalInterface
public interface SignatureTransaction {

    /** Exécute l'action dans une transaction : une exception l'annule et remonte. */
    <T> T execute(Supplier<T> action);
}
