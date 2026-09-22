package com.flowforge.domain.reporting.usecase;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.reporting.SlaReportAccess;
import com.flowforge.domain.reporting.model.BiExportFile;
import com.flowforge.domain.reporting.port.in.ListBiExportsUseCase;
import com.flowforge.domain.reporting.port.out.BiExportFileRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.Objects;
import java.util.Set;

public final class ListBiExportsUseCaseImpl implements ListBiExportsUseCase {

    private final BiExportFileRepository fileRepository;
    private final DossierTypeAclRepository aclRepository;

    public ListBiExportsUseCaseImpl(BiExportFileRepository fileRepository, DossierTypeAclRepository aclRepository) {
        this.fileRepository = Objects.requireNonNull(fileRepository, "fileRepository");
        this.aclRepository = Objects.requireNonNull(aclRepository, "aclRepository");
    }

    @Override
    public PageResult<BiExportFile> list(String tenantId, AccessContext ctx, PageQuery query) {
        Objects.requireNonNull(query, "query");
        Set<String> viewable = SlaReportAccess.allowedTypeCodes(aclRepository, tenantId, ctx);
        return fileRepository.page(tenantId, viewable, query);
    }
}
