package be.myrmacta.domain.reporting.port.out;

import be.myrmacta.domain.reporting.model.BiExportFile;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BiExportFileRepository {

    BiExportFile save(BiExportFile file);

    /**
     * @param viewableTypeCodes {@code null} = every file; empty = SLA files only; otherwise SLA
     *     plus dossier CSV whose type code is in the set
     */
    PageResult<BiExportFile> page(String tenantId, Set<String> viewableTypeCodes, PageQuery query);

    Optional<BiExportFile> findById(String tenantId, UUID id);

    List<BiExportFile> findCreatedBefore(String tenantId, Instant cutoff);

    void delete(UUID id);
}
