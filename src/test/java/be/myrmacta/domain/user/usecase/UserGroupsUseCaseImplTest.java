package be.myrmacta.domain.user.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.user.model.KnownUser;
import be.myrmacta.domain.user.model.UnknownUserException;
import be.myrmacta.domain.user.model.UserGroup;
import be.myrmacta.domain.user.model.UserGroupConflictException;
import be.myrmacta.domain.user.model.UserGroupNotFoundException;
import be.myrmacta.domain.user.port.out.KnownUserRepository;
import be.myrmacta.domain.user.port.out.UserGroupRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §114 (D-GROUP-SOURCE) — administration des groupes et appartenance effective, locale ou par l'IdP. */
class UserGroupsUseCaseImplTest {

    private static final String TENANT = "tenant_dev";

    private final Map<String, KnownUser> users = new HashMap<>();
    private final Map<String, UserGroup> groups = new HashMap<>();
    private UserGroupsUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        KnownUserRepository userRepository = new KnownUserRepository() {
            @Override
            public void upsert(KnownUser user) {
                users.put(user.userId(), user);
            }

            @Override
            public Optional<KnownUser> findById(String tenantId, String userId) {
                return Optional.ofNullable(users.get(userId));
            }

            @Override
            public List<KnownUser> findAll(String tenantId) {
                return List.copyOf(users.values());
            }
        };
        UserGroupRepository groupRepository = new UserGroupRepository() {
            @Override
            public List<UserGroup> findAll(String tenantId) {
                List<UserGroup> all = new ArrayList<>(groups.values());
                all.sort(Comparator.comparing(UserGroup::code));
                return all;
            }

            @Override
            public Optional<UserGroup> findByCode(String tenantId, String code) {
                return Optional.ofNullable(groups.get(code));
            }

            @Override
            public void save(UserGroup group) {
                groups.put(group.code(), group);
            }

            @Override
            public boolean delete(String tenantId, String code) {
                return groups.remove(code) != null;
            }
        };
        useCase = new UserGroupsUseCaseImpl(TENANT, groupRepository, userRepository);
        known("alice", Set.of());
        known("bob", Set.of("/services/compta"));
    }

    private void known(String userId, Set<String> idpGroups) {
        users.put(userId, new KnownUser(TENANT, userId, userId, null, Set.of("DOSSIER_MANAGER"), idpGroups,
                Instant.parse("2026-10-31T08:00:00Z")));
    }

    @Test
    void aGroupIsCreatedListedUpdatedAndDeleted() {
        useCase.create("COMPTABILITE", "Compta", null);
        useCase.update("COMPTABILITE", "Comptabilité", "/services/compta");

        assertThat(useCase.list()).singleElement().satisfies(group -> {
            assertThat(group.label()).isEqualTo("Comptabilité");
            assertThat(group.idpGroup()).isEqualTo("/services/compta");
        });
        useCase.delete("COMPTABILITE");
        assertThat(useCase.list()).isEmpty();
    }

    @Test
    void aSecondGroupWithTheSameCodeIsAConflict() {
        useCase.create("RH", "Ressources humaines", null);

        assertThatThrownBy(() -> useCase.create("RH", "Autre", null)).isInstanceOf(UserGroupConflictException.class);
    }

    @Test
    void anUnknownGroupIsNotFound() {
        assertThatThrownBy(() -> useCase.get("RH")).isInstanceOf(UserGroupNotFoundException.class);
        assertThatThrownBy(() -> useCase.update("RH", "x", null)).isInstanceOf(UserGroupNotFoundException.class);
        assertThatThrownBy(() -> useCase.delete("RH")).isInstanceOf(UserGroupNotFoundException.class);
        assertThatThrownBy(() -> useCase.addMember("RH", "alice")).isInstanceOf(UserGroupNotFoundException.class);
    }

    @Test
    void onlyAKnownUserCanBeAdded_andAddingOrRemovingTwiceChangesNothing() {
        useCase.create("RH", "Ressources humaines", null);

        assertThatThrownBy(() -> useCase.addMember("RH", "typo")).isInstanceOf(UnknownUserException.class);
        useCase.addMember("RH", "alice");
        useCase.addMember("RH", "alice");
        assertThat(useCase.get("RH").memberIds()).containsExactly("alice");
        useCase.removeMember("RH", "alice");
        useCase.removeMember("RH", "alice");
        assertThat(useCase.get("RH").memberIds()).isEmpty();
    }

    @Test
    void theEffectiveGroupsOfAUserMixLocalMembershipAndTheIdpClaim() {
        useCase.create("RH", "Ressources humaines", null);
        useCase.create("COMPTABILITE", "Comptabilité", "/services/compta");
        useCase.addMember("RH", "alice");
        useCase.addMember("RH", "bob");

        assertThat(useCase.groupsOf("alice")).containsExactly("RH");
        assertThat(useCase.groupsOf("bob")).containsExactlyInAnyOrder("RH", "COMPTABILITE");
        assertThat(useCase.groupsOf("stranger")).isEmpty();
    }
}
