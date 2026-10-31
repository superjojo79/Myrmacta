package be.myrmacta.domain.user.port.in;

import be.myrmacta.domain.user.model.KnownUser;
import be.myrmacta.domain.user.model.UserGroup;
import java.util.List;
import java.util.Set;

/** TODO §113 (D-GROUP-SOURCE) — administration des groupes et appartenance effective d'un utilisateur. */
public interface UserGroupsUseCase {

    List<UserGroup> list();

    UserGroup get(String code);

    UserGroup create(String code, String label, String idpGroup);

    UserGroup update(String code, String label, String idpGroup);

    void delete(String code);

    /** Sans effet si l'utilisateur est déjà membre ; refusé s'il ne s'est jamais connecté (annuaire, §92). */
    UserGroup addMember(String code, String userId);

    /** Sans effet s'il n'était pas membre. */
    UserGroup removeMember(String code, String userId);

    /** Annuaire local (§92) : utilisateurs qu'on peut ajouter à un groupe, triés par nom affiché. */
    List<KnownUser> directory();

    /** Codes des groupes dont l'utilisateur est membre, listé ou par un groupe de son jeton. */
    Set<String> groupsOf(String userId);
}
