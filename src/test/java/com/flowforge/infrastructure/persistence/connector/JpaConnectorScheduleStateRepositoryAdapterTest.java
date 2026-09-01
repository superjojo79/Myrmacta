package com.flowforge.infrastructure.persistence.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.connector.port.out.ConnectorScheduleStateRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "app.connector.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@Import(OidcClientTestConfiguration.class)
class JpaConnectorScheduleStateRepositoryAdapterTest {

    @Autowired
    private ConnectorInstanceRepository connectorInstanceRepository;

    @Autowired
    private ConnectorScheduleStateRepository repository;

    @Test
    void findLastTriggeredAt_beforeAnyUpsert_isEmpty() {
        UUID instanceId = seedConnectorInstance();

        assertThat(repository.findLastTriggeredAt(instanceId)).isEmpty();
    }

    @Test
    void upsert_thenFind_roundTripsCorrectly() {
        UUID instanceId = seedConnectorInstance();
        Instant triggeredAt = Instant.parse("2026-09-01T12:00:00Z");

        repository.upsert(instanceId, triggeredAt);

        assertThat(repository.findLastTriggeredAt(instanceId)).contains(triggeredAt);
    }

    @Test
    void upsert_calledTwice_overwritesWithoutDuplicating() {
        UUID instanceId = seedConnectorInstance();
        repository.upsert(instanceId, Instant.parse("2026-09-01T12:00:00Z"));

        Instant secondFiring = Instant.parse("2026-09-01T12:01:00Z");
        repository.upsert(instanceId, secondFiring);

        assertThat(repository.findLastTriggeredAt(instanceId)).contains(secondFiring);
    }

    private UUID seedConnectorInstance() {
        UUID id = UUID.randomUUID();
        connectorInstanceRepository.save(new ConnectorInstanceData(
                id, "tenant_dev", "Scheduler smoke " + id, "scheduler", true, Map.of()));
        return id;
    }
}
