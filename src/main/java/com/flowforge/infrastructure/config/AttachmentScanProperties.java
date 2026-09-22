package com.flowforge.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * D-AV-CLAMAV-CONFIG-SELECTOR: {@code type} selects the single active {@code
 * AttachmentScanPort} bean ({@code noop}/{@code clamav}), same idiom as {@link
 * StorageProperties#type()} rather than a boolean flag.
 */
@ConfigurationProperties(prefix = "app.dossier.attachment.scan")
public record AttachmentScanProperties(String type, ClamAv clamav) {

    public AttachmentScanProperties {
        if (type == null || type.isBlank()) {
            type = "noop";
        }
        if (clamav == null) {
            clamav = new ClamAv(null, 0, null, null);
        }
    }

    public record ClamAv(String host, int port, Duration connectTimeout, Duration readTimeout) {
        public ClamAv {
            if (host == null || host.isBlank()) {
                host = "localhost";
            }
            if (port <= 0) {
                port = 3310;
            }
            if (connectTimeout == null) {
                connectTimeout = Duration.ofSeconds(5);
            }
            if (readTimeout == null) {
                readTimeout = Duration.ofSeconds(5);
            }
        }
    }
}
