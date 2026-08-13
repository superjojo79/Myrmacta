package com.flowforge.domain.dossier.port.out;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DossierSearchPortFilterTest {

    @Test
    void restrictedRejectsEmptyTypeCodes() {
        assertThatThrownBy(() -> DossierSearchPort.Filter.restricted(null, Set.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty");
    }
}
