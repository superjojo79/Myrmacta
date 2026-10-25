package be.myrmacta.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DossierElementViewTest {

    @Test
    void from_producedDocWithStorageRef_isDownloadable() {
        assertThat(view(ElementType.PRODUCED_DOC, "tenant_dev/x.pdf").downloadable()).isTrue();
    }

    @Test
    void from_attachmentWithStorageRef_isDownloadable() {
        assertThat(view(ElementType.ATTACHMENT, "tenant_dev/att-1").downloadable()).isTrue();
    }

    @Test
    void from_informationWithoutStorageRef_isNotDownloadable() {
        assertThat(view(ElementType.INFORMATION, null).downloadable()).isFalse();
    }

    @Test
    void from_noteWithoutStorageRef_isNotDownloadable() {
        assertThat(view(ElementType.NOTE, null).downloadable()).isFalse();
    }

    @Test
    void from_linkWithoutStorageRef_isNotDownloadable() {
        assertThat(view(ElementType.LINK, null).downloadable()).isFalse();
    }

    private static DossierElementView view(ElementType type, String storageRef) {
        DossierElement element = new DossierElement(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "tenant_dev",
                type,
                "label",
                storageRef,
                Map.of(),
                "user",
                Instant.parse("2026-08-16T10:00:00Z"),
                null);
        return DossierElementView.from(element);
    }
}
