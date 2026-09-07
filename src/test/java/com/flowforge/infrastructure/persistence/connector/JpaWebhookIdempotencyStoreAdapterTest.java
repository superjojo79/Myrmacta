package com.flowforge.infrastructure.persistence.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.WebhookIdempotencyInFlightException;
import com.flowforge.infrastructure.config.ConnectorProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Couverture de branches manquante trouvée en clôturant fiabilisation-tests-plateforme
 * (D-COVERAGE, seuil BRANCH relevé) : {@link JpaWebhookIdempotencyStoreAdapter} n'avait aucun
 * test — mock du repository plutôt que DB réelle, cohérent avec un adaptateur package-privé.
 */
class JpaWebhookIdempotencyStoreAdapterTest {

    private static final UUID INSTANCE_ID = UUID.randomUUID();
    private static final String IDEM_KEY = "idem-1";

    private final SpringDataWebhookIdempotencyRepository repository = mock(SpringDataWebhookIdempotencyRepository.class);
    private final ConnectorProperties properties =
            new ConnectorProperties(
                    null, new ConnectorProperties.Webhook(1_048_576L, 120, Duration.ofHours(24), 0), null, null);
    private final JpaWebhookIdempotencyStoreAdapter adapter =
            new JpaWebhookIdempotencyStoreAdapter(repository, properties);

    @Test
    void beginOrGetCompleted_noExistingRow_claimsAndReturnsEmpty() {
        when(repository.findByInstanceIdAndIdemKey(INSTANCE_ID, IDEM_KEY)).thenReturn(Optional.empty());

        Optional<Integer> result = adapter.beginOrGetCompleted(INSTANCE_ID, IDEM_KEY);

        assertThat(result).isEmpty();
        verify(repository).saveAndFlush(any());
    }

    @Test
    void beginOrGetCompleted_concurrentClaimRacesPrimaryKey_reReadsAndInterprets() {
        WebhookIdempotencyJpaEntity racedRow = entity(Instant.now(), 200);
        when(repository.findByInstanceIdAndIdemKey(INSTANCE_ID, IDEM_KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(racedRow));
        doThrow(new DataIntegrityViolationException("pk violation")).when(repository).saveAndFlush(any());

        Optional<Integer> result = adapter.beginOrGetCompleted(INSTANCE_ID, IDEM_KEY);

        assertThat(result).contains(200);
    }

    @Test
    void beginOrGetCompleted_existingFreshCompletedRow_returnsStatus() {
        WebhookIdempotencyJpaEntity completed = entity(Instant.now(), 201);
        when(repository.findByInstanceIdAndIdemKey(INSTANCE_ID, IDEM_KEY)).thenReturn(Optional.of(completed));

        Optional<Integer> result = adapter.beginOrGetCompleted(INSTANCE_ID, IDEM_KEY);

        assertThat(result).contains(201);
    }

    @Test
    void beginOrGetCompleted_existingFreshInFlightRow_throwsInFlightException() {
        WebhookIdempotencyJpaEntity inFlight = entity(Instant.now(), JpaWebhookIdempotencyStoreAdapter.IN_FLIGHT);
        when(repository.findByInstanceIdAndIdemKey(INSTANCE_ID, IDEM_KEY)).thenReturn(Optional.of(inFlight));

        assertThatThrownBy(() -> adapter.beginOrGetCompleted(INSTANCE_ID, IDEM_KEY))
                .isInstanceOf(WebhookIdempotencyInFlightException.class);
    }

    @Test
    void beginOrGetCompleted_existingExpiredRow_deletesAndReclaimsAsEmpty() {
        WebhookIdempotencyJpaEntity expired = entity(Instant.now().minus(Duration.ofHours(25)), 200);
        when(repository.findByInstanceIdAndIdemKey(INSTANCE_ID, IDEM_KEY)).thenReturn(Optional.of(expired));

        Optional<Integer> result = adapter.beginOrGetCompleted(INSTANCE_ID, IDEM_KEY);

        assertThat(result).isEmpty();
        verify(repository).delete(expired);
        verify(repository).saveAndFlush(any());
    }

    @Test
    void complete_claimedKey_updatesResponseStatus() {
        WebhookIdempotencyJpaEntity row = entity(Instant.now(), JpaWebhookIdempotencyStoreAdapter.IN_FLIGHT);
        when(repository.findByInstanceIdAndIdemKey(INSTANCE_ID, IDEM_KEY)).thenReturn(Optional.of(row));

        adapter.complete(INSTANCE_ID, IDEM_KEY, 204);

        assertThat(row.responseStatus()).isEqualTo(204);
        verify(repository).saveAndFlush(row);
    }

    @Test
    void complete_unclaimedKey_throwsIllegalState() {
        when(repository.findByInstanceIdAndIdemKey(INSTANCE_ID, IDEM_KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.complete(INSTANCE_ID, IDEM_KEY, 204))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findResponseStatus_freshCompletedRow_returnsStatus() {
        WebhookIdempotencyJpaEntity completed = entity(Instant.now(), 200);
        when(repository.findByInstanceIdAndIdemKey(INSTANCE_ID, IDEM_KEY)).thenReturn(Optional.of(completed));

        assertThat(adapter.findResponseStatus(INSTANCE_ID, IDEM_KEY)).contains(200);
    }

    @Test
    void findResponseStatus_expiredRow_returnsEmpty() {
        WebhookIdempotencyJpaEntity expired = entity(Instant.now().minus(Duration.ofHours(25)), 200);
        when(repository.findByInstanceIdAndIdemKey(INSTANCE_ID, IDEM_KEY)).thenReturn(Optional.of(expired));

        assertThat(adapter.findResponseStatus(INSTANCE_ID, IDEM_KEY)).isEmpty();
    }

    @Test
    void findResponseStatus_inFlightRow_returnsEmpty() {
        WebhookIdempotencyJpaEntity inFlight = entity(Instant.now(), JpaWebhookIdempotencyStoreAdapter.IN_FLIGHT);
        when(repository.findByInstanceIdAndIdemKey(INSTANCE_ID, IDEM_KEY)).thenReturn(Optional.of(inFlight));

        assertThat(adapter.findResponseStatus(INSTANCE_ID, IDEM_KEY)).isEmpty();
    }

    @Test
    void findResponseStatus_noRow_returnsEmpty() {
        when(repository.findByInstanceIdAndIdemKey(INSTANCE_ID, IDEM_KEY)).thenReturn(Optional.empty());

        assertThat(adapter.findResponseStatus(INSTANCE_ID, IDEM_KEY)).isEmpty();
    }

    private static WebhookIdempotencyJpaEntity entity(Instant createdAt, int responseStatus) {
        return new WebhookIdempotencyJpaEntity(INSTANCE_ID, IDEM_KEY, responseStatus, createdAt);
    }
}
