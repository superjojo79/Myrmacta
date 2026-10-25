package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Issue #183 — D-NOTIF-ATTACH-MAX, revue adversariale L1 et I4. */
class NotificationEmailPropertiesTest {

    @Test
    void defaultStaysUnderTheUsualSmtpLimitOnceBase64Encoded() {
        long max = new NotificationEmailProperties(null).attachmentMaxBytes();
        assertThat(max).isEqualTo(7L * 1024 * 1024);
        assertThat((max + 2) / 3 * 4).isLessThan(10_240_000L);
    }

    @Test
    void aZeroOrNegativeCeilingIsAConfigurationError() {
        assertThat(new NotificationEmailProperties(1024L).attachmentMaxBytes()).isEqualTo(1024L);
        assertThatThrownBy(() -> new NotificationEmailProperties(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationEmailProperties(-1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
