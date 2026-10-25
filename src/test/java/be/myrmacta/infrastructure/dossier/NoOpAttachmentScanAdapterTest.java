package be.myrmacta.infrastructure.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.dossier.port.out.AttachmentScanResult;
import org.junit.jupiter.api.Test;

class NoOpAttachmentScanAdapterTest {

    @Test
    void scan_alwaysReturnsClean() {
        NoOpAttachmentScanAdapter adapter = new NoOpAttachmentScanAdapter();

        AttachmentScanResult result = adapter.scan(new byte[] {1, 2, 3}, "application/pdf");

        assertThat(result.clean()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void scan_isCleanEvenForEmptyContent() {
        NoOpAttachmentScanAdapter adapter = new NoOpAttachmentScanAdapter();

        assertThat(adapter.scan(new byte[0], "text/plain").clean()).isTrue();
    }
}
