package com.flowforge.domain.document.port.out;

import com.flowforge.domain.document.model.DocumentStorageException;

public interface DocumentStoragePort {

    /**
     * Stores bytes and returns an opaque storageRef (tenant-prefixed).
     *
     * @throws DocumentStorageException on I/O failure
     */
    String store(String tenantId, String contentType, byte[] content) throws DocumentStorageException;

    /**
     * @throws DocumentStorageException when missing or I/O failure
     */
    byte[] retrieve(String tenantId, String storageRef) throws DocumentStorageException;

    boolean exists(String tenantId, String storageRef);

    /**
     * Deletes the object at {@code storageRef}. Missing object is success (idempotent).
     *
     * @throws DocumentStorageException on invalid ref (tenant mismatch / path traversal) or I/O failure
     */
    void delete(String tenantId, String storageRef) throws DocumentStorageException;
}
