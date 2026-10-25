package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.model.UniqueDuplicate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * TODO §102 — corps documenté du 409 « schéma incompatible ». La liste {@code duplicates} cite au plus 20 valeurs ;
 * {@code duplicateCount} est le total. Chaque valeur cite au plus 20 références et garde le total dans
 * {@code referenceCount}.
 */
@Schema(description = "409: the new schema is incompatible with existing dossiers")
public record SchemaIncompatibleProblem(
        String title,
        int status,
        String detail,
        int incompatibleCount,
        List<String> sampleReferences,
        List<UniqueDuplicate> duplicates,
        int duplicateCount) {}
