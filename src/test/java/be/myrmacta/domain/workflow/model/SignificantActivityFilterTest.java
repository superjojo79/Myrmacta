package be.myrmacta.domain.workflow.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SignificantActivityFilterTest {

    @ParameterizedTest
    @CsvSource({
            "userTask, Smoke Task",
            "serviceTask, Update workflow link status",
            "startEvent, Start",
            "endEvent, End",
            "intermediateCatchEvent, Attente signal",
            "businessRuleTask, Éligibilité"
    })
    void keepsNamedBusinessActivities(String type, String name) {
        assertThat(SignificantActivityFilter.isSignificant(type, name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "sequenceFlow",
            "exclusiveGateway",
            "inclusiveGateway",
            "parallelGateway",
            "eventBasedGateway",
            "complexGateway"
    })
    void hidesTechnicalTypesEvenWhenNamed(String type) {
        assertThat(SignificantActivityFilter.isSignificant(type, "Branche A")).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void hidesActivitiesWithoutAName(String name) {
        assertThat(SignificantActivityFilter.isSignificant("userTask", name)).isFalse();
        assertThat(SignificantActivityFilter.isSignificant("startEvent", name)).isFalse();
    }

    @Test
    void hidesUnnamedSequenceFlowTypicalOfDesignerIds() {
        assertThat(SignificantActivityFilter.isSignificant("sequenceFlow", null)).isFalse();
    }
}
