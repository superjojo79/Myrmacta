package be.myrmacta.infrastructure.web.user;

import be.myrmacta.domain.user.model.KnownUser;
import be.myrmacta.domain.user.model.UserGroup;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

record CreateUserGroupRequest(String code, String label, String idpGroup) {}

record UpdateUserGroupRequest(String label, String idpGroup) {}

record UserGroupMemberResponse(String userId, String displayName) {}

record UserGroupResponse(String code, String label, String idpGroup, List<UserGroupMemberResponse> members) {

    /** Les membres sont nommés d'après l'annuaire ; un membre sorti de l'annuaire garde son identifiant. */
    static UserGroupResponse from(UserGroup group, Map<String, String> displayNames) {
        List<UserGroupMemberResponse> members = group.memberIds().stream()
                .map(id -> new UserGroupMemberResponse(id, displayNames.getOrDefault(id, id)))
                .sorted(Comparator.comparing(UserGroupMemberResponse::displayName).thenComparing(UserGroupMemberResponse::userId))
                .toList();
        return new UserGroupResponse(group.code(), group.label(), group.idpGroup(), members);
    }
}

record DirectoryUserResponse(String userId, String displayName, String email) {

    static DirectoryUserResponse from(KnownUser user) {
        return new DirectoryUserResponse(user.userId(), user.displayName(), user.email());
    }
}
