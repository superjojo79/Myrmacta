package be.myrmacta.infrastructure.web.user;

import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §113 (D-GROUP-REASSIGN) — codes et libellés des groupes, pour choisir à qui réaffecter une tâche. Lisible par tout
 * utilisateur connecté : ni membres, ni groupe d'IdP repris (réservés à l'administration).
 */
@RestController
@RequestMapping("/api/user-group-options")
@PreAuthorize("isAuthenticated()")
public class UserGroupOptionsController {

    private final UserGroupsUseCase groups;

    public UserGroupOptionsController(UserGroupsUseCase groups) {
        this.groups = groups;
    }

    @GetMapping
    public List<UserGroupOptionResponse> listUserGroupOptions() {
        return groups.list().stream().map(group -> new UserGroupOptionResponse(group.code(), group.label())).toList();
    }

    record UserGroupOptionResponse(String code, String label) {}
}
