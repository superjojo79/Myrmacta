package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.dossier.port.out.AttachmentScanPort;
import be.myrmacta.infrastructure.dossier.ClamAvAttachmentScanAdapter;
import be.myrmacta.infrastructure.dossier.NoOpAttachmentScanAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Sélection conditionnelle du bean {@link AttachmentScanPort} (D-AV-CLAMAV-CONFIG-SELECTOR),
 * testée par instanciation directe des méthodes {@code @Bean} concernées — même technique que
 * {@code StorageConfigTest} pour {@code app.storage.type}, sans contexte Spring complet.
 */
class DossierConfigTest {

    private final DossierConfig config = new DossierConfig();

    @Test
    void noOpAttachmentScanPort_returnsNoOpAdapter() {
        AttachmentScanPort port = config.noOpAttachmentScanPort();

        assertThat(port).isInstanceOf(NoOpAttachmentScanAdapter.class);
    }

    @Test
    void clamAvAttachmentScanPort_returnsClamAvAdapter() {
        AttachmentScanProperties properties = new AttachmentScanProperties(
                "clamav",
                new AttachmentScanProperties.ClamAv(
                        "clamav.internal", 3310, Duration.ofSeconds(5), Duration.ofSeconds(5)));

        AttachmentScanPort port = config.clamAvAttachmentScanPort(properties, new SimpleMeterRegistry());

        assertThat(port).isInstanceOf(ClamAvAttachmentScanAdapter.class);
    }
}
