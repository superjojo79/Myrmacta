package com.flowforge.infrastructure.persistence.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.connector.port.out.WebhookIdempotencyStore;
import com.flowforge.support.OidcClientTestConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class ConnectorPersistenceIT {

    @Autowired
    private ConnectorInstanceRepository instances;

    @Autowired
    private WebhookIdempotencyStore idempotencyStore;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void save_encryptsPasswordFieldsAtRest_andDecryptsOnLoad() {
        UUID id = UUID.randomUUID();
        var saved = instances.save(new ConnectorInstanceData(
                id,
                "tenant_dev",
                "Claims " + id,
                "http-rest",
                true,
                Map.of(
                        "webhookAuthType", "HMAC_SHA256",
                        "webhookSecret", "plain-secret")));

        assertThat(saved.config().get("webhookSecret")).isEqualTo("plain-secret");

        String rawConfig = jdbc.queryForObject(
                "SELECT cast(config as varchar) FROM connector_instance WHERE id = ?",
                String.class,
                id);
        assertThat(rawConfig).contains("enc:v1:");
        assertThat(rawConfig).doesNotContain("plain-secret");

        assertThat(instances.findByIdAndTenantId(id, "tenant_dev"))
                .get()
                .extracting(data -> data.config().get("webhookSecret"))
                .isEqualTo("plain-secret");
    }

    @Test
    void softDelete_hidesFromFindAndList_butIncludingDeletedStillFinds() {
        UUID id = UUID.randomUUID();
        instances.save(new ConnectorInstanceData(
                id, "tenant_dev", "ToDelete " + id, "http-rest", true, Map.of()));

        instances.softDelete(id, "tenant_dev");

        assertThat(instances.findByIdAndTenantId(id, "tenant_dev")).isEmpty();
        assertThat(instances.listByTenantId("tenant_dev"))
                .noneMatch(data -> data.id().equals(id));
        assertThat(instances.findByIdAndTenantIdIncludingDeleted(id, "tenant_dev")).isPresent();
    }

    @Test
    void webhookIdempotency_storesAndReplaysWithinTtl() {
        UUID instanceId = UUID.randomUUID();
        idempotencyStore.store(instanceId, "delivery-1", 202);
        assertThat(idempotencyStore.findResponseStatus(instanceId, "delivery-1")).contains(202);
        assertThat(idempotencyStore.findResponseStatus(instanceId, "missing")).isEmpty();
    }
}
