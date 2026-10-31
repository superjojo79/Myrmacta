package be.myrmacta.domain.user.port.in;

import java.util.Set;

public interface RecordUserLoginUseCase {

    /** Enregistre (ou rafraîchit) l'utilisateur qui vient de se connecter. */
    default void record(String userId, String name, String email, Set<String> roles) {
        record(userId, name, email, roles, Set.of());
    }

    /** Idem, avec les groupes portés par le jeton (TODO §113, D-GROUP-SOURCE) : bornés, les valeurs illisibles sont écartées. */
    void record(String userId, String name, String email, Set<String> roles, Set<String> idpGroups);
}
