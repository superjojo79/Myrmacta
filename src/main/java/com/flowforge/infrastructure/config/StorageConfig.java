package com.flowforge.infrastructure.config;

import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.infrastructure.storage.FilesystemStorageAdapter;
import com.flowforge.infrastructure.storage.S3StorageAdapter;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "filesystem", matchIfMissing = true)
    DocumentStoragePort filesystemDocumentStoragePort(StorageProperties properties) {
        return new FilesystemStorageAdapter(properties.filesystem().root());
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    S3Client s3Client(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.s3();
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(s3.region() == null || s3.region().isBlank() ? "us-east-1" : s3.region()));
        if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        if (s3.accessKeyId() != null
                && !s3.accessKeyId().isBlank()
                && s3.secretAccessKey() != null
                && !s3.secretAccessKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3.accessKeyId(), s3.secretAccessKey())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    DocumentStoragePort s3DocumentStoragePort(S3Client s3Client, StorageProperties properties) {
        String bucket = properties.s3().bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("app.storage.s3.bucket is required when app.storage.type=s3");
        }
        return new S3StorageAdapter(s3Client, bucket);
    }
}
