package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DossierSpecificationsTest {

    @Test
    void escapeLikePrefixEscapesMetacharacters() {
        assertThat(DossierSpecifications.escapeLikePrefix("dos%fac_1\\x"))
                .isEqualTo("dos\\%fac\\_1\\\\x");
    }
}
