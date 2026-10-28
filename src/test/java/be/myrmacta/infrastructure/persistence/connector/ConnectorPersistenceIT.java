package be.myrmacta.infrastructure.persistence.connector;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.connector.port.out.WebhookIdempotencyStore;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.List;
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
    void save_encryptsSecretTextareaFieldAtRest_andDecryptsOnLoad() {
        // connecteurs-cible (lot B1), IT-SECRET-FULL-ROUNDTRIP (T0.6) : ConfigFieldType.
        // SECRET_TEXTAREA (sftpPrivateKey) doit être traité comme sensible de bout en bout, pas
        // seulement PASSWORD — même round-trip que webhookSecret ci-dessus, sur un vrai contexte
        // Spring (ConnectorRegistry réel avec BatchImportConnector auto-enregistré).
        UUID id = UUID.randomUUID();
        String privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nabc123\n-----END OPENSSH PRIVATE KEY-----";
        var saved = instances.save(new ConnectorInstanceData(
                id,
                "tenant_dev",
                "Batch " + id,
                "batch-import",
                true,
                Map.ofEntries(
                        Map.entry("sftpHost", "sftp.example.com"),
                        Map.entry("sftpUsername", "myrmacta"),
                        Map.entry("sftpAuthType", "PRIVATE_KEY"),
                        Map.entry("sftpPrivateKey", privateKey),
                        Map.entry("sftpHostKeyFingerprint", "SHA256:abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"),
                        Map.entry("remoteDirectory", "/in"),
                        Map.entry("filenamePattern", "*.csv"),
                        Map.entry("archiveDirectory", "/archive"),
                        Map.entry("cronExpression", "0 0 * * * *"),
                        Map.entry("processKey", "importProcess"),
                        Map.entry("dossierTypeCode", "IMPORT"))));

        assertThat(saved.config().get("sftpPrivateKey")).isEqualTo(privateKey);

        String rawConfig = jdbc.queryForObject(
                "SELECT cast(config as varchar) FROM connector_instance WHERE id = ?",
                String.class,
                id);
        assertThat(rawConfig).contains("enc:v1:");
        assertThat(rawConfig).doesNotContain("BEGIN OPENSSH PRIVATE KEY");

        assertThat(instances.findByIdAndTenantId(id, "tenant_dev"))
                .get()
                .extracting(data -> data.config().get("sftpPrivateKey"))
                .isEqualTo(privateKey);
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
    void webhookIdempotency_claimsCompletesAndReplaysWithinTtl() {
        UUID instanceId = UUID.randomUUID();
        assertThat(idempotencyStore.beginOrGetCompleted(instanceId, "delivery-1")).isEmpty();
        idempotencyStore.complete(instanceId, "delivery-1", 202);
        assertThat(idempotencyStore.findResponseStatus(instanceId, "delivery-1")).contains(202);
        assertThat(idempotencyStore.beginOrGetCompleted(instanceId, "delivery-1")).contains(202);
        assertThat(idempotencyStore.findResponseStatus(instanceId, "missing")).isEmpty();
    }

    @Test
    void pageByTenantId_ordersByNameExcludesDeletedAndKeepsTotalsPastEnd() {
        String token = UUID.randomUUID().toString().substring(0, 8);
        UUID deletedId = UUID.randomUUID();
        instances.save(new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "zzz-page-" + token, "http-rest", true, Map.of()));
        instances.save(new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "aaa-page-" + token, "http-rest", true, Map.of()));
        instances.save(new ConnectorInstanceData(
                UUID.randomUUID(), "tenant_dev", "mmm-page-" + token, "http-rest", true, Map.of()));
        instances.save(new ConnectorInstanceData(
                deletedId, "tenant_dev", "del-page-" + token, "http-rest", true, Map.of()));
        instances.softDelete(deletedId, "tenant_dev");

        var page0 = instances.pageByTenantId("tenant_dev", PageQuery.of(0, 100));
        List<String> names = page0.content().stream()
                .map(ConnectorInstanceData::name)
                .filter(name -> name.endsWith(token))
                .toList();
        assertThat(names).containsExactly("aaa-page-" + token, "mmm-page-" + token, "zzz-page-" + token);
        assertThat(page0.content()).noneMatch(data -> data.id().equals(deletedId));
        // L'ordre des autres lignes du tenant suit la collation PostgreSQL, pas String.compareTo.

        var pastEnd = instances.pageByTenantId("tenant_dev", PageQuery.of(9_999, 20));
        assertThat(pastEnd.content()).isEmpty();
        assertThat(pastEnd.totalElements()).isEqualTo(page0.totalElements());
        assertThat(pastEnd.size()).isEqualTo(20);
    }
}
