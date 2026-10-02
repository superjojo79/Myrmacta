package com.flowforge.domain.promotion.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.promotion.model.ConfigurationBundle;

/** TODO §83 (D4) — export de la configuration du tenant ; réservé à {@code TENANT_ADMIN}. Jamais de secret. */
public interface ExportConfigurationUseCase {

    ConfigurationBundle export(AccessContext.User actor, String sourceLabel);
}
