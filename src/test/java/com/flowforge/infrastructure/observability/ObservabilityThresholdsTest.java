package com.flowforge.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ObservabilityThresholdsTest {

    @Test
    void ageSeverity_usesSixtyAndThreeHundredSeconds() {
        assertThat(ObservabilityThresholds.ageSeverity(0)).isEqualTo("ok");
        assertThat(ObservabilityThresholds.ageSeverity(59)).isEqualTo("ok");
        assertThat(ObservabilityThresholds.ageSeverity(60)).isEqualTo("warn");
        assertThat(ObservabilityThresholds.ageSeverity(299)).isEqualTo("warn");
        assertThat(ObservabilityThresholds.ageSeverity(300)).isEqualTo("crit");
    }

    @Test
    void countSeverity_usesZeroWarnMaxAndCritMin() {
        assertThat(ObservabilityThresholds.countSeverity(0, 10, 5)).isEqualTo("ok");
        assertThat(ObservabilityThresholds.countSeverity(1, 10, 5)).isEqualTo("warn");
        assertThat(ObservabilityThresholds.countSeverity(9, 10, 5)).isEqualTo("warn");
        assertThat(ObservabilityThresholds.countSeverity(10, 10, 5)).isEqualTo("crit");
        assertThat(ObservabilityThresholds.countSeverity(0, 5, 1)).isEqualTo("ok");
        assertThat(ObservabilityThresholds.countSeverity(4, 5, 1)).isEqualTo("warn");
        assertThat(ObservabilityThresholds.countSeverity(5, 5, 1)).isEqualTo("crit");
    }

    @Test
    void pdfSeverity_treatsZeroCountAsOk() {
        assertThat(ObservabilityThresholds.pdfSeverity(0, null)).isEqualTo("ok");
        assertThat(ObservabilityThresholds.pdfSeverity(1, 1999.0)).isEqualTo("ok");
        assertThat(ObservabilityThresholds.pdfSeverity(1, 2000.0)).isEqualTo("warn");
        assertThat(ObservabilityThresholds.pdfSeverity(1, 4999.0)).isEqualTo("warn");
        assertThat(ObservabilityThresholds.pdfSeverity(1, 5000.0)).isEqualTo("crit");
    }

    @Test
    void sseSeverity_usesFiftyAndTwoHundred() {
        assertThat(ObservabilityThresholds.sseSeverity(49)).isEqualTo("ok");
        assertThat(ObservabilityThresholds.sseSeverity(50)).isEqualTo("warn");
        assertThat(ObservabilityThresholds.sseSeverity(199)).isEqualTo("warn");
        assertThat(ObservabilityThresholds.sseSeverity(200)).isEqualTo("crit");
    }
}
