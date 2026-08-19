package com.flowforge.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DossierTypeTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String TENANT = "tenant_dev";

    @Test
    void constructor_acceptsAlphanumericCode() {
        DossierType type =
                DossierType.inherit(ID, TENANT, "CLAIM-1", "Claim", "wf-claim", true, false, List.of());

        assertThat(type.code()).isEqualTo("CLAIM-1");
    }

    @Test
    void constructor_rejectsCodeWithControlCharacter() {
        assertThatThrownBy(() -> DossierType.inherit(
                        ID, TENANT, "CLAIM\nCODE", "Claim", "wf-claim", true, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsCodeWithSpace() {
        assertThatThrownBy(() -> DossierType.inherit(
                        ID, TENANT, "CLAIM CODE", "Claim", "wf-claim", true, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsBlankCode() {
        assertThatThrownBy(
                        () -> DossierType.inherit(ID, TENANT, "", "Claim", "wf-claim", true, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
