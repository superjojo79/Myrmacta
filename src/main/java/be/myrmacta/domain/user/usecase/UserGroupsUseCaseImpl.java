package be.myrmacta.domain.user.usecase;

import be.myrmacta.domain.user.model.KnownUser;
import be.myrmacta.domain.user.model.UnknownUserException;
import be.myrmacta.domain.user.model.UserGroup;
import be.myrmacta.domain.user.model.UserGroupConflictException;
import be.myrmacta.domain.user.model.UserGroupNotFoundException;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.user.port.out.KnownUserRepository;
import be.myrmacta.domain.user.port.out.UserGroupRepository;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class UserGroupsUseCaseImpl implements UserGroupsUseCase {

    private final String tenantId;
    private final UserGroupRepository groups;
    private final KnownUserRepository users;

    public UserGroupsUseCaseImpl(String tenantId, UserGroupRepository groups, KnownUserRepository users) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.users = Objects.requireNonNull(users, "users");
    }

    @Override
    public List<UserGroup> list() {
        return groups.findAll(tenantId);
    }

    @Override
    public UserGroup get(String code) {
        return groups.findByCode(tenantId, code).orElseThrow(() -> new UserGroupNotFoundException(code));
    }

    @Override
    public UserGroup create(String code, String label, String idpGroup) {
        UserGroup group = new UserGroup(tenantId, code, label, idpGroup, Set.of());
        if (groups.findByCode(tenantId, group.code()).isPresent()) {
            throw new UserGroupConflictException(group.code());
        }
        groups.save(group);
        return group;
    }

    @Override
    public UserGroup update(String code, String label, String idpGroup) {
        UserGroup current = get(code);
        UserGroup updated = new UserGroup(tenantId, current.code(), label, idpGroup, current.memberIds());
        groups.save(updated);
        return updated;
    }

    @Override
    public void delete(String code) {
        if (!groups.delete(tenantId, code)) {
            throw new UserGroupNotFoundException(code);
        }
    }

    @Override
    public UserGroup addMember(String code, String userId) {
        UserGroup current = get(code);
        if (userId == null || users.findById(tenantId, userId).isEmpty()) {
            throw new UnknownUserException(String.valueOf(userId));
        }
        if (current.memberIds().contains(userId)) {
            return current;
        }
        Set<String> members = new HashSet<>(current.memberIds());
        members.add(userId);
        UserGroup updated = current.withMembers(members);
        groups.save(updated);
        return updated;
    }

    @Override
    public UserGroup removeMember(String code, String userId) {
        UserGroup current = get(code);
        if (!current.memberIds().contains(userId)) {
            return current;
        }
        Set<String> members = new HashSet<>(current.memberIds());
        members.remove(userId);
        UserGroup updated = current.withMembers(members);
        groups.save(updated);
        return updated;
    }

    @Override
    public List<KnownUser> directory() {
        return users.findAll(tenantId);
    }

    @Override
    public Set<String> groupsOf(String userId) {
        if (userId == null || userId.isBlank()) {
            return Set.of();
        }
        Set<String> idpGroups = users.findById(tenantId, userId).map(KnownUser::idpGroups).orElse(Set.of());
        Set<String> result = new LinkedHashSet<>();
        for (UserGroup group : groups.findAll(tenantId)) {
            if (group.includes(userId, idpGroups)) {
                result.add(group.code());
            }
        }
        return Set.copyOf(result);
    }
}
