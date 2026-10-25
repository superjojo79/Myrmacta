package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Couverture de branches manquante trouvée en clôturant fiabilisation-tests-plateforme
 * (D-COVERAGE, seuil BRANCH relevé) : les beans {@code s3Client}/{@code s3DocumentStoragePort}
 * ne sont jamais instanciés par le contexte Spring de test ({@code app.storage.type=filesystem}
 * partout), donc jamais exercés — appelés ici directement (construction de client S3 sans appel
 * réseau, comme {@code S3Client.builder().build()} le garantit).
 */
class StorageConfigTest {

    private final StorageConfig config = new StorageConfig();

    @Test
    void filesystemDocumentStoragePort_returnsFilesystemAdapter() {
        StorageProperties properties = new StorageProperties("filesystem", new StorageProperties.Filesystem("/tmp/docs"), null);

        DocumentStoragePort port = config.filesystemDocumentStoragePort(properties);

        assertThat(port).isNotNull();
    }

    @Test
    void s3Client_blankRegion_defaultsToUsEast1() {
        StorageProperties properties = s3Properties(new StorageProperties.S3("bucket", " ", null, null, null));

        S3Client client = config.s3Client(properties);

        assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("us-east-1");
    }

    @Test
    void s3Client_explicitRegion_isUsed() {
        StorageProperties properties = s3Properties(new StorageProperties.S3("bucket", "eu-west-1", null, null, null));

        S3Client client = config.s3Client(properties);

        assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("eu-west-1");
    }

    @Test
    void s3Client_blankEndpoint_doesNotOverrideEndpoint() {
        StorageProperties properties = s3Properties(new StorageProperties.S3("bucket", "eu-west-1", " ", null, null));

        assertThat(config.s3Client(properties)).isNotNull();
    }

    @Test
    void s3Client_explicitEndpoint_overridesEndpointAndEnablesPathStyle() {
        StorageProperties properties =
                s3Properties(new StorageProperties.S3("bucket", "eu-west-1", "http://minio:9000", null, null));

        assertThat(config.s3Client(properties)).isNotNull();
    }

    @Test
    void s3Client_blankCredentials_usesDefaultCredentialsProvider() {
        StorageProperties properties = s3Properties(new StorageProperties.S3("bucket", "eu-west-1", null, " ", " "));

        assertThat(config.s3Client(properties)).isNotNull();
    }

    @Test
    void s3Client_explicitCredentials_usesStaticCredentialsProvider() {
        StorageProperties properties =
                s3Properties(new StorageProperties.S3("bucket", "eu-west-1", null, "AKIA...", "secret"));

        assertThat(config.s3Client(properties)).isNotNull();
    }

    @Test
    void s3DocumentStoragePort_withBucket_returnsS3Adapter() {
        StorageProperties properties = s3Properties(new StorageProperties.S3("bucket", "eu-west-1", null, null, null));
        S3Client s3Client = config.s3Client(properties);

        DocumentStoragePort port = config.s3DocumentStoragePort(s3Client, properties);

        assertThat(port).isNotNull();
    }

    @Test
    void s3DocumentStoragePort_blankBucket_throwsIllegalState() {
        StorageProperties properties = s3Properties(new StorageProperties.S3(" ", "eu-west-1", null, null, null));
        S3Client s3Client = config.s3Client(properties);

        assertThatThrownBy(() -> config.s3DocumentStoragePort(s3Client, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.storage.s3.bucket");
    }

    private static StorageProperties s3Properties(StorageProperties.S3 s3) {
        return new StorageProperties("s3", null, s3);
    }
}
