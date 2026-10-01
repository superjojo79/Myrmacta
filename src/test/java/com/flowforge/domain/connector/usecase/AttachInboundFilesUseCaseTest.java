package com.flowforge.domain.connector.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.InboundAttachment;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.AttachmentScanPort;
import com.flowforge.domain.dossier.port.out.AttachmentScanResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** TODO §87 (D3c) — pièces jointes d'un e-mail : mêmes contrôles que l'upload interne ; un refus n'arrête pas les autres. */
class AttachInboundFilesUseCaseTest {

    private static final byte[] PDF = "%PDF-1.7 test".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PNG = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};

    private AttachmentScanPort scan;
    private DocumentStoragePort storage;
    private AddDossierElementUseCase add;
    private AttachInboundFilesUseCaseImpl useCase;
    private final UUID dossier = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scan = mock(AttachmentScanPort.class);
        when(scan.scan(any(), any())).thenReturn(AttachmentScanResult.ofClean());
        storage = mock(DocumentStoragePort.class);
        when(storage.store(any(), any(), any())).thenReturn("ref-1");
        add = mock(AddDossierElementUseCase.class);
        useCase = new AttachInboundFilesUseCaseImpl("t1", Set.of("application/pdf", "image/png")::contains, 3, 1000, scan, storage, add);
    }

    @Test
    void aCleanAllowedFileBecomesAnAttachmentElementAddedBySystem() {
        var report = useCase.attach(dossier, "connector:abc", List.of(new InboundAttachment("devis.pdf", "application/pdf", PDF)));

        assertThat(report.attached()).isEqualTo(1);
        assertThat(report.rejections()).isEmpty();
        ArgumentCaptor<AddDossierElementUseCase.AddCommand> cmd = ArgumentCaptor.forClass(AddDossierElementUseCase.AddCommand.class);
        verify(add).add(cmd.capture(), any(AccessContext.System.class));
        assertThat(cmd.getValue().storageRef()).isEqualTo("ref-1");
        assertThat(cmd.getValue().addedBy()).isEqualTo("connector:abc");
        assertThat(cmd.getValue().metadata()).containsEntry("filename", "devis.pdf").containsEntry("contentType", "application/pdf");
    }

    @Test
    void aRefusedFileIsSkippedWithItsReason_theOthersStillAttach() {
        when(scan.scan(any(), any())).thenAnswer(inv -> new String((byte[]) inv.getArgument(0), StandardCharsets.UTF_8).contains("EVIL")
                ? AttachmentScanResult.ofInfected("Eicar") : AttachmentScanResult.ofClean());
        var evil = "%PDF-EVIL".getBytes(StandardCharsets.UTF_8);
        var report = useCase.attach(dossier, "c", List.of(
                new InboundAttachment("ok.pdf", "application/pdf", PDF),
                new InboundAttachment("virus.pdf", "application/pdf", evil),
                new InboundAttachment("run.exe", "application/x-msdownload", PDF),
                new InboundAttachment("fake.pdf", "application/pdf", PNG)));

        // maxFiles = 3 : le quatrième est refusé pour le nombre, les trois premiers pour leur propre raison
        assertThat(report.attached()).isEqualTo(1);
        assertThat(report.rejections()).hasSize(3);
        assertThat(report.rejections().get(0)).contains("virus.pdf").contains("antivirus");
        assertThat(report.rejections().get(1)).contains("run.exe").contains("not allowed");
        assertThat(report.rejections().get(2)).contains("too many");
        verify(storage).store(any(), any(), any()); // un seul fichier stocké : jamais un fichier refusé
    }

    @Test
    void contentThatDoesNotMatchItsDeclaredTypeEmptyAndOversizedFilesAreRefused() {
        var report = useCase.attach(dossier, "c", List.of(
                new InboundAttachment("fake.pdf", "application/pdf", PNG),
                new InboundAttachment("empty.pdf", "application/pdf", new byte[0]),
                new InboundAttachment("big.pdf", "application/pdf", new byte[1001])));
        assertThat(report.attached()).isZero();
        assertThat(report.rejections()).hasSize(3);
        assertThat(report.rejections().get(0)).contains("does not match");
        assertThat(report.rejections().get(1)).contains("empty");
        assertThat(report.rejections().get(2)).contains("exceeds");
        verify(storage, never()).store(any(), any(), any());
        verify(add, never()).add(any(), any());
    }
}
