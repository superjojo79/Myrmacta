package be.myrmacta.infrastructure.web.user;

import be.myrmacta.domain.user.model.KnownUser;
import be.myrmacta.domain.user.model.UserGroup;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** TODO §114 (issue #190, D-GROUP-SOURCE) — administration des groupes d'utilisateurs, réservée à l'administrateur du tenant. */
@RestController
@RequestMapping("/api/user-groups")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class UserGroupAdminController {

    private final UserGroupsUseCase groups;

    public UserGroupAdminController(UserGroupsUseCase groups) {
        this.groups = groups;
    }

    @GetMapping
    public List<UserGroupResponse> listUserGroups() {
        Map<String, String> names = displayNames();
        return groups.list().stream().map(group -> UserGroupResponse.from(group, names)).toList();
    }

    @PostMapping
    public ResponseEntity<UserGroupResponse> createUserGroup(@RequestBody CreateUserGroupRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        UserGroup created = groups.create(request.code(), request.label(), request.idpGroup());
        return ResponseEntity.created(URI.create("/api/user-groups/" + created.code())).body(UserGroupResponse.from(created, Map.of()));
    }

    /** Utilisateurs de l'annuaire local, à proposer comme membres. */
    @GetMapping("/directory")
    public List<DirectoryUserResponse> userDirectory() {
        return groups.directory().stream().map(DirectoryUserResponse::from).toList();
    }

    @GetMapping("/{code}")
    public UserGroupResponse getUserGroup(@PathVariable String code) {
        return UserGroupResponse.from(groups.get(code), displayNames());
    }

    @PutMapping("/{code}")
    public UserGroupResponse updateUserGroup(@PathVariable String code, @RequestBody UpdateUserGroupRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        return UserGroupResponse.from(groups.update(code, request.label(), request.idpGroup()), displayNames());
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteUserGroup(@PathVariable String code) {
        groups.delete(code);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{code}/members/{userId}")
    public UserGroupResponse addUserGroupMember(@PathVariable String code, @PathVariable String userId) {
        return UserGroupResponse.from(groups.addMember(code, userId), displayNames());
    }

    @DeleteMapping("/{code}/members/{userId}")
    public UserGroupResponse removeUserGroupMember(@PathVariable String code, @PathVariable String userId) {
        return UserGroupResponse.from(groups.removeMember(code, userId), displayNames());
    }

    private Map<String, String> displayNames() {
        return groups.directory().stream()
                .collect(Collectors.toMap(KnownUser::userId, KnownUser::displayName, (first, second) -> first));
    }
}
