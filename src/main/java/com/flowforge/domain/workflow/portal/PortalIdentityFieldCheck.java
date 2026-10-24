package com.flowforge.domain.workflow.portal;

import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.workflow.portal.model.PortalIdentityField;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §103 (T2, D-BEAUTH-NRN-FIELD) — le champ d'identité d'une tâche à authentification forte doit être déclaré dans
 * le type du dossier, de type {@code STRING} et {@code sensitive} : c'est le masquage des champs sensibles qui protège
 * le NRN une fois écrit (N1). Vérifié à l'émission du lien, le type ayant pu changer depuis le déploiement.
 */
@FunctionalInterface
public interface PortalIdentityFieldCheck {

    boolean isUsable(UUID dossierId, PortalIdentityField field);

    /** Aucun champ n'est utilisable : une tâche à authentification forte n'est jamais envoyée. */
    PortalIdentityFieldCheck NONE = (dossierId, field) -> false;

    static PortalIdentityFieldCheck of(String tenantId, DossierRepository dossiers, DossierTypeRepository types) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dossiers, "dossiers");
        Objects.requireNonNull(types, "types");
        return (dossierId, field) -> dossiers.findById(dossierId)
                .flatMap(dossier -> types.findByCode(tenantId, dossier.dossierTypeCode()))
                .flatMap(type -> type.fieldDefinitions().stream()
                        .filter(definition -> definition.name().equals(field.fieldName()))
                        .findFirst())
                .filter(PortalIdentityFieldCheck::holdsAnIdentity)
                .isPresent();
    }

    private static boolean holdsAnIdentity(FieldDefinition definition) {
        return definition.type() == FieldType.STRING && definition.isSensitive();
    }
}
