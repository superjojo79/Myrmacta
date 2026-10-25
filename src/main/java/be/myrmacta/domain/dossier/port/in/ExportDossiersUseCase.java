package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.ExportDossiersQuery;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import java.util.List;
import java.util.stream.Stream;

public interface ExportDossiersUseCase {

    ExportResult export(String tenantId, ExportDossiersQuery query, AccessContext ctx);

    /** {@code dossiers} is already masked per {@code DossierFieldRedactor} for the given actor. */
    record ExportResult(List<FieldDefinition> fieldDefinitions, Stream<Dossier> dossiers) {}
}
