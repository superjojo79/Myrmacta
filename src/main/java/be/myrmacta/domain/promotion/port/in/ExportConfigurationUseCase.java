package be.myrmacta.domain.promotion.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.promotion.model.ConfigurationBundle;

/** TODO §83 (D4) — export de la configuration du tenant ; réservé à {@code TENANT_ADMIN}. Jamais de secret. */
public interface ExportConfigurationUseCase {

    ConfigurationBundle export(AccessContext.User actor, String sourceLabel);
}
