package com.flowforge.infrastructure.storage;

import com.flowforge.domain.document.model.DocumentStorageException;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import java.util.Objects;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3StorageAdapter implements DocumentStoragePort {

    private final S3Client s3Client;
    private final String bucket;

    public S3StorageAdapter(S3Client s3Client, String bucket) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
        this.bucket = Objects.requireNonNull(bucket, "bucket");
    }

    @Override
    public String store(String tenantId, String contentType, byte[] content) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(content, "content");
        String storageRef = tenantId + "/" + UUID.randomUUID() + ".pdf";
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(storageRef)
                            .contentType(contentType != null ? contentType : "application/pdf")
                            .build(),
                    RequestBody.fromBytes(content));
            return storageRef;
        } catch (S3Exception ex) {
            throw new DocumentStorageException("Failed to store document: " + storageRef, ex);
        }
    }

    @Override
    public byte[] retrieve(String tenantId, String storageRef) {
        validateRef(tenantId, storageRef);
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(storageRef)
                            .build())
                    .asByteArray();
        } catch (NoSuchKeyException ex) {
            throw new DocumentStorageException("Document not found: " + storageRef, ex);
        } catch (S3Exception ex) {
            throw new DocumentStorageException("Failed to retrieve document: " + storageRef, ex);
        }
    }

    @Override
    public boolean exists(String tenantId, String storageRef) {
        try {
            validateRef(tenantId, storageRef);
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(storageRef).build());
            return true;
        } catch (NoSuchKeyException | DocumentStorageException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw new DocumentStorageException("Failed to check document: " + storageRef, ex);
        }
    }

    private static void validateRef(String tenantId, String storageRef) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(storageRef, "storageRef");
        if (!storageRef.startsWith(tenantId + "/") || storageRef.contains("..")) {
            throw new DocumentStorageException("Invalid storageRef: " + storageRef);
        }
    }
}
