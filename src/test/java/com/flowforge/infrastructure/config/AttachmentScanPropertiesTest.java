package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AttachmentScanPropertiesTest {

    @Test
    void defaults_selectNoopAndFillClamavDefaults() {
        var props = new AttachmentScanProperties(null, null);

        assertThat(props.type()).isEqualTo("noop");
        assertThat(props.clamav().host()).isEqualTo("localhost");
        assertThat(props.clamav().port()).isEqualTo(3310);
        assertThat(props.clamav().connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.clamav().readTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void blankType_fallsBackToNoop() {
        var props = new AttachmentScanProperties(" ", null);

        assertThat(props.type()).isEqualTo("noop");
    }

    @Test
    void explicitType_isPreserved() {
        var props = new AttachmentScanProperties("clamav", null);

        assertThat(props.type()).isEqualTo("clamav");
    }

    @Test
    void clamavPartialOverride_fillsMissingFieldsWithDefault() {
        var props = new AttachmentScanProperties(
                "clamav", new AttachmentScanProperties.ClamAv("clamav.internal", 0, null, null));

        assertThat(props.clamav().host()).isEqualTo("clamav.internal");
        assertThat(props.clamav().port()).isEqualTo(3310);
        assertThat(props.clamav().connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.clamav().readTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void clamavCustomValues_arePreserved() {
        var clamav = new AttachmentScanProperties.ClamAv(
                "clamav.internal", 4310, Duration.ofSeconds(2), Duration.ofSeconds(3));

        assertThat(clamav.host()).isEqualTo("clamav.internal");
        assertThat(clamav.port()).isEqualTo(4310);
        assertThat(clamav.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(clamav.readTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void clamavBlankHost_fallsBackToLocalhost() {
        var clamav = new AttachmentScanProperties.ClamAv(" ", 3310, null, null);

        assertThat(clamav.host()).isEqualTo("localhost");
    }

    @Test
    void clamavNonPositivePort_fallsBackToDefault() {
        var clamav = new AttachmentScanProperties.ClamAv("localhost", -1, null, null);

        assertThat(clamav.port()).isEqualTo(3310);
    }
}
