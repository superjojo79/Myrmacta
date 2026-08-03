package com.flowforge.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3StorageAdapterTest {

    @Test
    void storeRetrieveExists_useBucketAndKey() {
        S3Client client = mock(S3Client.class);
        S3StorageAdapter storage = new S3StorageAdapter(client, "flowforge-docs");
        byte[] pdf = "%PDF".getBytes(StandardCharsets.UTF_8);

        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), pdf));
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        String ref = storage.store("tenant_dev", "application/pdf", pdf);
        assertThat(ref).startsWith("tenant_dev/").endsWith(".pdf");

        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(putCaptor.capture(), any(RequestBody.class));
        assertThat(putCaptor.getValue().bucket()).isEqualTo("flowforge-docs");
        assertThat(putCaptor.getValue().key()).isEqualTo(ref);
        assertThat(putCaptor.getValue().contentType()).isEqualTo("application/pdf");

        assertThat(storage.exists("tenant_dev", ref)).isTrue();
        assertThat(storage.retrieve("tenant_dev", ref)).isEqualTo(pdf);
    }

    @Test
    void exists_falseOnNoSuchKey() {
        S3Client client = mock(S3Client.class);
        S3StorageAdapter storage = new S3StorageAdapter(client, "bucket");
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());
        assertThat(storage.exists("tenant_dev", "tenant_dev/x.pdf")).isFalse();
    }
}
