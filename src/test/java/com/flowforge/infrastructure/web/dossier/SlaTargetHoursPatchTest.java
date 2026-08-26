package com.flowforge.infrastructure.web.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SlaTargetHoursPatchTest {

    @Test
    void omitted_keepsExisting() {
        assertThat(SlaTargetHoursPatch.resolve(48, null, null)).isEqualTo(48);
        assertThat(SlaTargetHoursPatch.resolve(48, null, false)).isEqualTo(48);
    }

    @Test
    void requestedHours_replacesExisting() {
        assertThat(SlaTargetHoursPatch.resolve(48, 72, null)).isEqualTo(72);
    }

    @Test
    void clearFlag_setsNull() {
        assertThat(SlaTargetHoursPatch.resolve(48, null, true)).isNull();
    }

    @Test
    void clearFlagWithHours_isRejected() {
        assertThatThrownBy(() -> SlaTargetHoursPatch.resolve(48, 24, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clearSlaTargetHours");
    }
}
