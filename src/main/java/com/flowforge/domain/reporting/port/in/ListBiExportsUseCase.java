package com.flowforge.domain.reporting.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.reporting.model.BiExportFile;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;

public interface ListBiExportsUseCase {

    PageResult<BiExportFile> list(String tenantId, AccessContext ctx, PageQuery query);
}
