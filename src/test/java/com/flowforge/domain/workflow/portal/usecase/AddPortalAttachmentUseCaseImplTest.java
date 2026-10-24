package com.flowforge.domain.workflow.portal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.InvalidDossierElementException;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.AttachmentScanPort;
import com.flowforge.domain.dossier.port.out.AttachmentScanResult;
import com.flowforge.domain.workflow.portal.PortalAccessResolver;
import com.flowforge.domain.workflow.portal.model.PortalCredential;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.port.in.AddPortalAttachmentUseCase.AttachmentUpload;
import com.flowforge.domain.workflow.portal.port.out.PortalTaskLookupPort;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** TODO §68 — cas limites du dépôt portail (Phase 8). */
class AddPortalAttachmentUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final byte[] PDF = "%PDF-1.7".getBytes();
    private final PortalAccessResolver resolver = mock(PortalAccessResolver.class);
    private final AttachmentScanPort scan = mock(AttachmentScanPort.class);
    private final DocumentStoragePort storage = mock(DocumentStoragePort.class);
    private final AddDossierElementUseCase addElement = mock(AddDossierElementUseCase.class);
    private final AddPortalAttachmentUseCaseImpl useCase = new AddPortalAttachmentUseCaseImpl(
            "tenant_dev", resolver, "application/pdf"::equals, scan, storage, addElement);
    private PortalLink link;

    @BeforeEach
    void setUp() {
        UUID dossierId = UUID.randomUUID();
        link = PortalLink.pendingSend(UUID.randomUUID(), "tenant_dev", dossierId, "t", "pi", "avis", "a@b.org", 7, NOW,
                "x").activated("h", NOW);
        when(resolver.resolve(PortalCredential.of("tok", null))).thenReturn(access(DossierStatus.OUVERT));
        when(scan.scan(any(), anyString())).thenReturn(new AttachmentScanResult(true, null));
        when(storage.store(anyString(), anyString(), any())).thenReturn("tenant_dev/x.pdf");
    }

    private PortalAccessResolver.ResolvedAccess access(DossierStatus status) {
        Dossier dossier = new Dossier(link.dossierId(), "tenant_dev", "REF", "T", "L", status, Map.of(), null, 0L, "u",
                NOW, null, null, null, null, null);
        return new PortalAccessResolver.ResolvedAccess(link, new PortalTaskLookupPort.OpenTask("t", "pi", "T", null), dossier);
    }

    private AddDossierElementUseCase.AddCommand captured() {
        ArgumentCaptor<AddDossierElementUseCase.AddCommand> command = ArgumentCaptor.forClass(AddDossierElementUseCase.AddCommand.class);
        verify(addElement).add(command.capture(), any());
        return command.getValue();
    }

    @Test
    void blankLabel_defaultsToTheFilename_thenToAGenericLabel() {
        useCase.add("tok", new AttachmentUpload("  ", "devis.pdf", "application/pdf", PDF));
        assertThat(captured().label()).isEqualTo("devis.pdf");
    }

    @Test
    void noLabelNorFilename_usesAGenericLabel_andOverlongLabelsAreTruncated() {
        useCase.add("tok", new AttachmentUpload(null, null, "application/pdf", PDF));
        assertThat(captured().label()).isEqualTo("Pièce jointe");

        AddDossierElementUseCase second = mock(AddDossierElementUseCase.class);
        new AddPortalAttachmentUseCaseImpl("tenant_dev", resolver, "application/pdf"::equals, scan, storage, second)
                .add("tok", new AttachmentUpload("x".repeat(500), "f.pdf", "application/pdf", PDF));
        ArgumentCaptor<AddDossierElementUseCase.AddCommand> command = ArgumentCaptor.forClass(AddDossierElementUseCase.AddCommand.class);
        verify(second).add(command.capture(), any());
        assertThat(command.getValue().label()).hasSize(200);
    }

    @Test
    void emptyFile_isRejectedBeforeAnyStorage() {
        assertThatThrownBy(() -> useCase.add("tok", new AttachmentUpload("L", "f.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(InvalidDossierElementException.class);
        verify(storage, never()).store(anyString(), anyString(), any());
    }

    @Test
    void closedDossier_isRejectedBeforeAnyStorage() {
        when(resolver.resolve(PortalCredential.of("tok", null))).thenReturn(access(DossierStatus.CLOTURE));

        assertThatThrownBy(() -> useCase.add("tok", new AttachmentUpload("L", "f.pdf", "application/pdf", PDF)))
                .isInstanceOf(com.flowforge.domain.dossier.model.DossierConflictException.class);
        verify(storage, never()).store(anyString(), anyString(), any());
    }
}
