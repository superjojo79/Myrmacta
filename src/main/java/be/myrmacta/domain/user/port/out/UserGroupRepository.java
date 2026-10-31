package be.myrmacta.domain.user.port.out;

import be.myrmacta.domain.user.model.UserGroup;
import java.util.List;
import java.util.Optional;

/** TODO §114 — groupes d'utilisateurs du tenant, avec leurs membres listés. */
public interface UserGroupRepository {

    /** Tous les groupes du tenant, triés par code. */
    List<UserGroup> findAll(String tenantId);

    Optional<UserGroup> findByCode(String tenantId, String code);

    /** Insère ou remplace le groupe et la liste de ses membres. */
    void save(UserGroup group);

    /** {@code false} si le groupe n'existait pas. */
    boolean delete(String tenantId, String code);
}
