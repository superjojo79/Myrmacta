package com.flowforge.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.document.model.DocumentStorageException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemStorageAdapterTest {

    @TempDir
    Path root;

    private FilesystemStorageAdapter storage;

    @BeforeEach
    void setUp() {
        storage = new FilesystemStorageAdapter(root.toString());
    }

    @Test
    void storeRetrieveExists_roundTrip() {
        byte[] pdf = "%PDF-demo".getBytes(StandardCharsets.UTF_8);
        String ref = storage.store("tenant_dev", "application/pdf", pdf);
        assertThat(ref).startsWith("tenant_dev/");
        assertThat(ref).endsWith(".pdf");
        assertThat(storage.exists("tenant_dev", ref)).isTrue();
        assertThat(storage.retrieve("tenant_dev", ref)).isEqualTo(pdf);
    }

    @Test
    void exists_falseWhenMissing() {
        assertThat(storage.exists("tenant_dev", "tenant_dev/missing.pdf")).isFalse();
    }

    @Test
    void pathTraversal_rejected() {
        assertThatThrownBy(() -> storage.retrieve("tenant_dev", "tenant_dev/../secret.pdf"))
                .isInstanceOf(DocumentStorageException.class);
        assertThat(storage.exists("tenant_dev", "../../etc/passwd")).isFalse();
    }

    @Test
    void retrieve_missing_throws() {
        assertThatThrownBy(() -> storage.retrieve("tenant_dev", "tenant_dev/nope.pdf"))
                .isInstanceOf(DocumentStorageException.class);
    }
}
