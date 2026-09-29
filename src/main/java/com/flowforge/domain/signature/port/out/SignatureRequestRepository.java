package com.flowforge.domain.signature.port.out;

import com.flowforge.domain.signature.model.SignatureRequest;
import com.flowforge.domain.signature.model.Signer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistance des demandes et de leurs signataires (TODO §72). Les mises à jour sont des
 * compare-and-set sur {@code version} : {@code false} = un autre appel est passé avant
 * (double soumission, callback rejoué, expiration concurrente).
 */
public interface SignatureRequestRepository {

    /**
     * Insère la demande et ses signataires dans une seule transaction.
     *
     * @throws SignatureConflictException si une demande {@code PENDING} existe déjà pour le même
     *     couple (process, activité) — index partiel, rejeu du delegate
     */
    void insert(SignatureRequest request, List<Signer> signers);

    Optional<SignatureRequest> findById(UUID id);

    /** Retrouve la demande d'un événement du fournisseur (référence propre au fournisseur). */
    Optional<SignatureRequest> findByProviderReference(String providerReference);

    /** Demande {@code PENDING} d'un couple (process, activité), s'il y en a une. */
    Optional<SignatureRequest> findPendingByProcessAndActivity(String processInstanceId, String activityId);

    List<SignatureRequest> findPendingByProcessInstance(String processInstanceId);

    List<SignatureRequest> findByDossierId(UUID dossierId);

    List<Signer> findSigners(UUID requestId);

    Optional<Signer> findSignerById(UUID signerId);

    Optional<Signer> findSignerByTokenHash(String tokenHash);

    /** Signataires internes d'un utilisateur dont la demande n'est pas terminée, du plus récent au plus ancien. */
    List<Signer> findOpenInternalSigners(String userId);

    /** Signataires externes {@code PENDING} sans invitation envoyée ni plafond de tentatives atteint. */
    List<Signer> findInvitable(int maxAttempts, int limit);

    List<SignatureRequest> findPendingWithDeadlineBefore(Instant now, int limit);

    /**
     * Demandes terminées dont le process n'a pas encore été repris (D-SIGNATURE-DISPATCH) et dont le
     * nombre de tentatives est inférieur à {@code maxAttempts}.
     */
    List<SignatureRequest> findTerminalNotDispatched(int maxAttempts, int limit);

    /**
     * Applique la demande et l'état complet de ses signataires de façon atomique.
     *
     * @return {@code false} (et rien n'est écrit) si la version d'un des éléments a changé
     */
    boolean update(SignatureRequest next, List<Signer> nextSigners);

    /** Mise à jour isolée d'un signataire (jeton, invitation) ; {@code false} si la version a changé. */
    boolean updateSigner(Signer next);

    /** Mise à jour isolée de la demande (répartiteur, référence fournisseur) ; idem. */
    boolean updateRequest(SignatureRequest next);

    int deleteAllByDossierId(UUID dossierId);
}
