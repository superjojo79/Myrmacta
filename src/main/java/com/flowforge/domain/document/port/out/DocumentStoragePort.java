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
}
