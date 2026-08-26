package com.flowforge.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetentionPolicyResolverTest {

    private static final String TENANT = "tenant_dev";
    private final RetentionPolicyResolver resolver = new RetentionPolicyResolver();

    @Test
    void effectiveDays_typeInheritsTenantDefault() {
        DossierType type = DossierType.inherit(
                UUID.randomUUID(), TENANT, "CLAIM", "Claim", "wf-claim", true, false, List.of());
        TenantRetentionSettings settings = settings(30);

        assertThat(resolver.effectiveDays(type, settings)).hasValue(30);
    }

    @Test
    void effectiveDays_typeOverrideWinsOverTenantDefault() {
        DossierType type = new DossierType(
                UUID.randomUUID(), TENANT, "LEGAL", "Legal", "wf-legal", true, false, List.of(), 365, false, null);
        TenantRetentionSettings settings = settings(30);

        assertThat(resolver.effectiveDays(type, settings)).hasValue(365);
    }

    @Test
    void effectiveDays_retentionNeverReturnsEmpty() {
        DossierType type = new DossierType(
                UUID.randomUUID(), TENANT, "LOG", "Log", "wf-log", true, false, List.of(), null, true, null);
        TenantRetentionSettings settings = settings(30);

        assertThat(resolver.effectiveDays(type, settings)).isEmpty();
    }

    @Test
    void tenantSettings_rejectsNonPositiveDefaultAtConstruction() {
        assertThatThrownBy(() -> new TenantRetentionSettings(TENANT, 0, Instant.now(), "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantRetentionSettings(TENANT, -1, Instant.now(), "admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TenantRetentionSettings settings(int defaultRetentionDays) {
        return new TenantRetentionSettings(TENANT, defaultRetentionDays, Instant.now(), "admin");
    }
}
