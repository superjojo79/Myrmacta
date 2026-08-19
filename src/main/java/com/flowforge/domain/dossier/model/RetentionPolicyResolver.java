package com.flowforge.domain.dossier.model;

import java.util.Objects;
import java.util.OptionalInt;

public final class RetentionPolicyResolver {

    public OptionalInt effectiveDays(DossierType type, TenantRetentionSettings settings) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(settings, "settings");
        if (type.retentionNever()) {
            return OptionalInt.empty();
        }
        if (type.retentionDays() != null) {
            return OptionalInt.of(type.retentionDays());
        }
        return OptionalInt.of(settings.defaultRetentionDays());
    }
}
