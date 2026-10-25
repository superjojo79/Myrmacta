package be.myrmacta.domain.reporting.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.DossierPermission;
import be.myrmacta.domain.dossier.model.DossierTypeAclEntry;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import be.myrmacta.domain.reporting.model.BiExportFile;
import be.myrmacta.domain.reporting.model.BiExportKind;
import be.myrmacta.domain.reporting.model.BiExportNotFoundException;
import be.myrmacta.domain.reporting.port.out.BiExportFileRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BiExportAccessUseCaseTest {

    private static final String TENANT = "tenant_dev";

    @Test
    void list_adminSeesEveryFile_managerIsFilteredByView() {
        BiExportFileRepository files = mock(BiExportFileRepository.class);
        DossierTypeAclRepository acl = mock(DossierTypeAclRepository.class);
        ListBiExportsUseCaseImpl useCase = new ListBiExportsUseCaseImpl(files, acl);
        when(files.page(eq(TENANT), any(), any())).thenReturn(PageResult.empty(0, 20));

        useCase.list(TENANT, AccessContext.user("admin", Set.of("TENANT_ADMIN")), PageQuery.defaults());
        verify(files).page(eq(TENANT), isNull(), eq(PageQuery.defaults()));

        when(acl.findBySubject(TENANT, "manager")).thenReturn(List.of(
                new DossierTypeAclEntry(TENANT, "CLAIM", "manager", DossierPermission.VIEW),
                new DossierTypeAclEntry(TENANT, "LOG", "manager", DossierPermission.EDIT)));
        useCase.list(TENANT, AccessContext.user("manager", Set.of("DOSSIER_MANAGER")), PageQuery.defaults());
        verify(files).page(eq(TENANT), eq(Set.of("CLAIM", "LOG")), eq(PageQuery.defaults()));
    }

    @Test
    void download_hiddenTypeIsNotFound_visibleFileReturnsBytes() {
        BiExportFileRepository files = mock(BiExportFileRepository.class);
        DossierTypeAclRepository acl = mock(DossierTypeAclRepository.class);
        DocumentStoragePort storage = mock(DocumentStoragePort.class);
        DownloadBiExportUseCaseImpl useCase = new DownloadBiExportUseCaseImpl(files, acl, storage);
        UUID id = UUID.randomUUID();
        BiExportFile hidden = file(id, BiExportKind.DOSSIERS, "SECRET");
        when(files.findById(TENANT, id)).thenReturn(Optional.of(hidden));
        when(acl.findBySubject(TENANT, "manager")).thenReturn(List.of(
                new DossierTypeAclEntry(TENANT, "CLAIM", "manager", DossierPermission.VIEW)));

        assertThatThrownBy(() -> useCase.download(
                        TENANT, id, AccessContext.user("manager", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(BiExportNotFoundException.class);

        BiExportFile sla = file(id, BiExportKind.SLA, null);
        when(files.findById(TENANT, id)).thenReturn(Optional.of(sla));
        when(storage.retrieve(TENANT, "ref")).thenReturn("ok".getBytes());
        assertThat(useCase.download(TENANT, id, AccessContext.user("manager", Set.of("DOSSIER_MANAGER")))
                        .content())
                .isEqualTo("ok".getBytes());

        assertThatThrownBy(() -> useCase.download(TENANT, id, AccessContext.user("viewer", Set.of("DOSSIER_VIEWER"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static BiExportFile file(UUID id, BiExportKind kind, String typeCode) {
        return new BiExportFile(id, TENANT, kind, typeCode, "ref", "file.csv", Instant.parse("2026-08-01T00:00:00Z"), 2);
    }
}
