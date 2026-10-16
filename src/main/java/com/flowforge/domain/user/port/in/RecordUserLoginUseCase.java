package com.flowforge.domain.user.port.in;

import java.util.Set;

public interface RecordUserLoginUseCase {

    /** Enregistre (ou rafraîchit) l'utilisateur qui vient de se connecter. */
    void record(String userId, String name, String email, Set<String> roles);
}
