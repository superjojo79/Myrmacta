package be.myrmacta.domain.reporting.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.reporting.model.BiExportFile;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;

public interface ListBiExportsUseCase {

    PageResult<BiExportFile> list(String tenantId, AccessContext ctx, PageQuery query);
}
