package com.flowforge.infrastructure.web.reporting;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.reporting.model.BiExportFile;
import com.flowforge.domain.reporting.model.OperationalCounts;
import com.flowforge.domain.reporting.port.in.DownloadBiExportUseCase;
import com.flowforge.domain.reporting.port.in.GetOperationalCountsUseCase;
import com.flowforge.domain.reporting.port.in.ListBiExportsUseCase;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.web.PageResponse;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

record OperationalCountsResponse(long deadLetterJobCount, long unassignedOpenTaskCount) {
    static OperationalCountsResponse from(OperationalCounts counts) {
        return new OperationalCountsResponse(counts.deadLetterJobCount(), counts.unassignedOpenTaskCount());
    }
}

record BiExportFileResponse(
        UUID id, String kind, String dossierTypeCode, String fileName, Instant createdAt, long sizeBytes) {
    static BiExportFileResponse from(BiExportFile file) {
        return new BiExportFileResponse(
                file.id(),
                file.kind().name(),
                file.dossierTypeCode(),
                file.fileName(),
                file.createdAt(),
                file.sizeBytes());
    }
}

@RestController
public class OperationalReportController {

    private final TenantProperties tenantProperties;
    private final GetOperationalCountsUseCase getOperationalCountsUseCase;
    private final ListBiExportsUseCase listBiExportsUseCase;
    private final DownloadBiExportUseCase downloadBiExportUseCase;
    private final AccessContextFactory accessContextFactory;

    public OperationalReportController(
            TenantProperties tenantProperties,
            GetOperationalCountsUseCase getOperationalCountsUseCase,
            ListBiExportsUseCase listBiExportsUseCase,
            DownloadBiExportUseCase downloadBiExportUseCase,
            AccessContextFactory accessContextFactory) {
        this.tenantProperties = tenantProperties;
        this.getOperationalCountsUseCase = getOperationalCountsUseCase;
        this.listBiExportsUseCase = listBiExportsUseCase;
        this.downloadBiExportUseCase = downloadBiExportUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping("/api/reports/operations")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN','DOSSIER_MANAGER')")
    public OperationalCountsResponse operations(Authentication authentication) {
        AccessContext ctx = accessContextFactory.user(authentication);
        return OperationalCountsResponse.from(
                getOperationalCountsUseCase.get(tenantProperties.id(), ctx));
    }

    @GetMapping("/api/reports/bi-exports")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN','DOSSIER_MANAGER')")
    public PageResponse<BiExportFileResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        AccessContext ctx = accessContextFactory.user(authentication);
        return PageResponse.from(listBiExportsUseCase
                .list(tenantProperties.id(), ctx, PageQuery.of(page, size))
                .map(BiExportFileResponse::from));
    }

    @GetMapping(path = "/api/reports/bi-exports/{id}", produces = "text/csv")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN','DOSSIER_MANAGER')")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, Authentication authentication) {
        AccessContext ctx = accessContextFactory.user(authentication);
        DownloadBiExportUseCase.Download download =
                downloadBiExportUseCase.download(tenantProperties.id(), id, ctx);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(download.content());
    }
}
