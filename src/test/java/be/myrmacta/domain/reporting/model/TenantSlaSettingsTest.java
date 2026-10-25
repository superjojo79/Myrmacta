package be.myrmacta.domain.reporting.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantSlaSettingsTest {

    @Test
    void constructor_rejectsPercentOutsideRange() {
        Instant now = Instant.parse("2026-03-15T00:00:00Z");
        assertThatThrownBy(() -> new TenantSlaSettings("t", 0, now, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantSlaSettings("t", 101, now, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultComplianceTargetIs95() {
        assertThat(TenantSlaSettings.DEFAULT_COMPLIANCE_TARGET_PERCENT).isEqualTo(95);
        TenantSlaSettings settings =
                new TenantSlaSettings("t", 1, Instant.parse("2026-03-15T00:00:00Z"), "admin");
        assertThat(settings.complianceTargetPercent()).isEqualTo(1);
    }
}
