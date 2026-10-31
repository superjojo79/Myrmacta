package be.myrmacta.infrastructure.persistence.user;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.user.model.UserGroup;
import be.myrmacta.domain.user.port.out.UserGroupRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** TODO §114 — groupes et membres listés, par tenant, sans doublon. */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class JpaUserGroupRepositoryAdapterIT {

    @Autowired
    private UserGroupRepository repository;

    @Test
    void aGroupIsSavedWithItsMembersThenUpdatedThenDeleted() {
        String tenant = "tenant-" + UUID.randomUUID();
        repository.save(new UserGroup(tenant, "COMPTABILITE", "Compta", null, Set.of("alice", "bob")));
        repository.save(new UserGroup(tenant, "COMPTABILITE", "Comptabilité", "/services/compta", Set.of("bob", "carol")));

        assertThat(repository.findByCode(tenant, "COMPTABILITE")).hasValueSatisfying(group -> {
            assertThat(group.label()).isEqualTo("Comptabilité");
            assertThat(group.idpGroup()).isEqualTo("/services/compta");
            assertThat(group.memberIds()).containsExactlyInAnyOrder("bob", "carol");
        });
        assertThat(repository.delete(tenant, "COMPTABILITE")).isTrue();
        assertThat(repository.delete(tenant, "COMPTABILITE")).isFalse();
        assertThat(repository.findByCode(tenant, "COMPTABILITE")).isEmpty();
    }

    @Test
    void findAll_isScopedToTenantAndSortedByCode() {
        String tenant = "tenant-" + UUID.randomUUID();
        repository.save(new UserGroup(tenant, "RH", "Ressources humaines", null, Set.of()));
        repository.save(new UserGroup(tenant, "COMPTABILITE", "Comptabilité", null, Set.of()));
        repository.save(new UserGroup("other-" + UUID.randomUUID(), "JURIDIQUE", "Juridique", null, Set.of()));

        assertThat(repository.findAll(tenant)).extracting(UserGroup::code).containsExactly("COMPTABILITE", "RH");
    }
}
