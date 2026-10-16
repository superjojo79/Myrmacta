package com.flowforge.domain.user.port.out;

import com.flowforge.domain.user.model.KnownUser;
import java.util.List;
import java.util.Optional;

public interface KnownUserRepository {

    /** Insère ou met à jour l'utilisateur ({@code tenantId}, {@code userId}). */
    void upsert(KnownUser user);

    Optional<KnownUser> findById(String tenantId, String userId);

    /** Tous les utilisateurs connus du tenant, triés par nom affiché. */
    List<KnownUser> findAll(String tenantId);
}
