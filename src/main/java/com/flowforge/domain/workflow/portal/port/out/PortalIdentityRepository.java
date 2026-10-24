package com.flowforge.domain.workflow.portal.port.out;

import com.flowforge.domain.workflow.portal.model.IdentityVerification;
import com.flowforge.domain.workflow.portal.model.PortalIdentityAttempt;
import com.flowforge.domain.workflow.portal.model.PortalLinkIdentity;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** TODO §103 (D-BEAUTH-STORE) — tentatives et identités vérifiées du portail. */
public interface PortalIdentityRepository {

    void insertAttempt(PortalIdentityAttempt attempt);

    /**
     * Consomme la tentative (usage unique, compare-and-set) : vide si elle est inconnue, déjà consommée ou expirée. Une
     * tentative consommée ne peut plus servir, même si la suite échoue.
     */
    Optional<PortalIdentityAttempt> consumeAttempt(String stateHash, Instant now);

    /** Remplace l'identité du lien (une nouvelle authentification annule la précédente). */
    void saveIdentity(PortalLinkIdentity identity);

    Optional<PortalLinkIdentity> findByProofHash(String proofHash);

    Optional<PortalLinkIdentity> findByLinkId(UUID linkId);

    /** Soumission : efface la preuve, le NRN chiffré et l'expiration ; la trace reste. Faux si déjà soumise. */
    boolean markSubmitted(UUID linkId, Instant now);

    /** Supprime une identité non soumise (lien révoqué, renvoyé, expiré) ; la trace d'une soumission reste. */
    void deleteUnsubmitted(UUID linkId);

    /**
     * Nettoyage périodique : tentatives échues, identités non soumises échues ou dont le lien n'est plus actif. Renvoie
     * le nombre de lignes supprimées.
     */
    int purgeExpired(Instant now);

    /** Traces des identités des liens donnés (vue instructeur), sans NRN. */
    Map<UUID, IdentityVerification> tracesByLinkId(Collection<UUID> linkIds);
}
