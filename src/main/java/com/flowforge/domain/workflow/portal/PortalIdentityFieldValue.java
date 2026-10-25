package com.flowforge.domain.workflow.portal;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.workflow.portal.model.PortalIdentityField;

/**
 * TODO §104 (T5, T6) — seule lecture du dossier par l'authentification forte : la valeur du champ d'identité, comparée
 * au NRN vérifié, jamais renvoyée au tiers ni journalisée (liste blanche ArchUnit de {@code Dossier.fields()}).
 */
public final class PortalIdentityFieldValue {

    private PortalIdentityFieldValue() {
    }

    public static Object of(Dossier dossier, PortalIdentityField field) {
        return dossier.fields() == null ? null : dossier.fields().get(field.fieldName());
    }
}
