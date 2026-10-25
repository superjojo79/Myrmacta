package be.myrmacta.infrastructure.storage;

import be.myrmacta.domain.document.model.DocumentStorageException;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public class FilesystemStorageAdapter implements DocumentStoragePort {

    private final Path root;

    public FilesystemStorageAdapter(String rootDirectory) {
        this.root = Path.of(Objects.requireNonNull(rootDirectory, "rootDirectory")).toAbsolutePath().normalize();
    }

    @Override
    public String store(String tenantId, String contentType, byte[] content) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(content, "content");
        String storageRef = tenantId + "/" + UUID.randomUUID() + ".pdf";
        Path target = resolveSafe(tenantId, storageRef);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(tmp, content);
            Files.move(tmp, target);
            return storageRef;
        } catch (IOException ex) {
            throw new DocumentStorageException("Failed to store document: " + storageRef, ex);
        }
    }

    @Override
    public byte[] retrieve(String tenantId, String storageRef) {
        Path target = resolveSafe(tenantId, storageRef);
        if (!Files.isRegularFile(target)) {
            throw new DocumentStorageException("Document not found: " + storageRef);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new DocumentStorageException("Failed to retrieve document: " + storageRef, ex);
        }
    }

    @Override
    public boolean exists(String tenantId, String storageRef) {
        try {
            Path target = resolveSafe(tenantId, storageRef);
            return Files.isRegularFile(target);
        } catch (DocumentStorageException ex) {
            return false;
        }
    }

    @Override
    public void delete(String tenantId, String storageRef) {
        Path target = resolveSafe(tenantId, storageRef);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new DocumentStorageException("Failed to delete document: " + storageRef, ex);
        }
    }

    private Path resolveSafe(String tenantId, String storageRef) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(storageRef, "storageRef");
        if (!storageRef.startsWith(tenantId + "/")) {
            throw new DocumentStorageException("storageRef tenant mismatch: " + storageRef);
        }
        if (storageRef.contains("..")) {
            throw new DocumentStorageException("Invalid storageRef: " + storageRef);
        }
        Path target = root.resolve(storageRef).normalize();
        if (!target.startsWith(root)) {
            throw new DocumentStorageException("Path escapes storage root: " + storageRef);
        }
        return target;
    }
}
