package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.ExportDossiersQuery;
import com.flowforge.domain.dossier.model.FieldDefinition;
import java.util.List;
import java.util.stream.Stream;

public interface ExportDossiersUseCase {

    ExportResult export(String tenantId, ExportDossiersQuery query, AccessContext ctx);

    /** {@code dossiers} is already masked per {@code DossierFieldRedactor} for the given actor. */
    record ExportResult(List<FieldDefinition> fieldDefinitions, Stream<Dossier> dossiers) {}
}
