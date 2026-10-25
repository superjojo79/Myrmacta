package be.myrmacta.infrastructure.persistence.user;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.user.model.KnownUser;
import be.myrmacta.domain.user.port.out.KnownUserRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** D-ASSIGN-DIR — l'annuaire local se met à jour à chaque connexion, sans doublon. */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class JpaKnownUserRepositoryAdapterIT {

    @Autowired
    private KnownUserRepository repository;

    @Test
    void upsert_insertsThenRefreshesTheSameUserWithoutDuplicating() {
        String tenant = "tenant-" + UUID.randomUUID();
        Instant first = Instant.parse("2026-10-13T08:00:00Z");
        Instant second = Instant.parse("2026-10-13T09:00:00Z");

        repository.upsert(new KnownUser(tenant, "sub-1", "Old Name", null, Set.of("DOSSIER_VIEWER"), first));
        repository.upsert(new KnownUser(
                tenant, "sub-1", "New Name", "new@example.org", Set.of("DOSSIER_MANAGER", "TENANT_ADMIN"), second));

        assertThat(repository.findAll(tenant)).singleElement().satisfies(user -> {
            assertThat(user.displayName()).isEqualTo("New Name");
            assertThat(user.email()).isEqualTo("new@example.org");
            assertThat(user.roles()).containsExactlyInAnyOrder("DOSSIER_MANAGER", "TENANT_ADMIN");
            assertThat(user.lastLoginAt()).isEqualTo(second);
        });
    }

    @Test
    void findAll_isScopedToTenantAndSortedByDisplayName() {
        String tenant = "tenant-" + UUID.randomUUID();
        Instant now = Instant.parse("2026-10-13T08:00:00Z");
        repository.upsert(new KnownUser(tenant, "sub-z", "Zoé", null, Set.of(), now));
        repository.upsert(new KnownUser(tenant, "sub-a", "Alice", null, Set.of(), now));
        repository.upsert(new KnownUser("other-" + UUID.randomUUID(), "sub-b", "Bob", null, Set.of(), now));

        assertThat(repository.findAll(tenant)).extracting(KnownUser::userId).containsExactly("sub-a", "sub-z");
        assertThat(repository.findById(tenant, "sub-a")).isPresent();
        assertThat(repository.findById(tenant, "sub-b")).isEmpty();
    }
}
