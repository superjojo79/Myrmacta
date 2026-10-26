package be.myrmacta.domain.user.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import be.myrmacta.domain.user.model.KnownUser;
import be.myrmacta.domain.user.port.out.KnownUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecordUserLoginUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-10-13T08:00:00Z");

    private KnownUserRepository repository;
    private RecordUserLoginUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        repository = mock(KnownUserRepository.class);
        useCase = new RecordUserLoginUseCaseImpl(TENANT, repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void record_upsertsUserForInstanceTenantWithLoginTimestamp() {
        useCase.record("sub-1", "Demo User", "demo@myrmacta.local", Set.of("TENANT_ADMIN"));

        KnownUser saved = captureUpsert();
        assertThat(saved.tenantId()).isEqualTo(TENANT);
        assertThat(saved.userId()).isEqualTo("sub-1");
        assertThat(saved.displayName()).isEqualTo("Demo User");
        assertThat(saved.email()).isEqualTo("demo@myrmacta.local");
        assertThat(saved.roles()).containsExactly("TENANT_ADMIN");
        assertThat(saved.lastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void record_fallsBackToEmailThenUserIdWhenNameIsBlank() {
        useCase.record("sub-1", " ", "demo@myrmacta.local", Set.of());
        assertThat(captureUpsert().displayName()).isEqualTo("demo@myrmacta.local");
    }

    @Test
    void record_fallsBackToUserIdWhenNameAndEmailAreMissing() {
        useCase.record("sub-1", null, null, null);

        KnownUser saved = captureUpsert();
        assertThat(saved.displayName()).isEqualTo("sub-1");
        assertThat(saved.email()).isNull();
        assertThat(saved.roles()).isEmpty();
    }

    @Test
    void record_rejectsBlankUserId() {
        assertThatThrownBy(() -> useCase.record(" ", "Demo", null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    private KnownUser captureUpsert() {
        ArgumentCaptor<KnownUser> captor = ArgumentCaptor.forClass(KnownUser.class);
        verify(repository).upsert(captor.capture());
        return captor.getValue();
    }
}
