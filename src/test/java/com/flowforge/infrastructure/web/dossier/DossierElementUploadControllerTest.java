package com.flowforge.infrastructure.web.dossier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierConflictException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.infrastructure.config.AttachmentUploadProperties;
import com.flowforge.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

/**
 * Pure unit test (mocked collaborators) for the store-vs-ACL ordering invariant: MockMvc-based
 * tests (DossierElementUploadApiTest) already cover the 403 outcome, but can't cheaply assert
 * that storage was never touched — the real DocumentStoragePort bean has no "list what got
 * written" API without inspecting the filesystem. Mocking it here verifies the invariant directly.
 */
@ExtendWith(MockitoExtension.class)
class DossierElementUploadControllerTest {

    @Mock
    DocumentStoragePort documentStoragePort;

    @Mock
    AddDossierElementUseCase addDossierElementUseCase;

    @Mock
    TenantProperties tenantProperties;

    @Mock
    AccessContextFactory accessContextFactory;

    @Mock
    DossierAccessGuard accessGuard;

    @Mock
    Authentication authentication;

    DossierElementUploadController controller;

    @BeforeEach
    void setUp() {
        controller = new DossierElementUploadController(
                documentStoragePort,
                addDossierElementUseCase,
                tenantProperties,
                accessContextFactory,
                new AttachmentUploadProperties(List.of("application/pdf")),
                accessGuard);
    }

    @Test
    void upload_deniedByAccessGuard_neverWritesToStorage() {
        UUID dossierId = UUID.randomUUID();
        AccessContext.User actor = AccessContext.user("u1", java.util.Set.of("DOSSIER_VIEWER"));
        when(accessContextFactory.user(authentication)).thenReturn(actor);
        doThrow(new AccessDeniedException("denied")).when(accessGuard).requireEditable(dossierId, actor);
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> controller.upload(dossierId, file, "label", authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(documentStoragePort, never()).store(anyString(), anyString(), any());
        verify(addDossierElementUseCase, never()).add(any(), any());
    }

    @Test
    void upload_dossierClotureOrArchive_neverWritesToStorage() {
        UUID dossierId = UUID.randomUUID();
        AccessContext.User actor = AccessContext.user("u1", java.util.Set.of("DOSSIER_MANAGER"));
        when(accessContextFactory.user(authentication)).thenReturn(actor);
        when(accessGuard.requireEditable(dossierId, actor)).thenReturn(closedDossier(dossierId));
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> controller.upload(dossierId, file, "label", authentication))
                .isInstanceOf(DossierConflictException.class);

        verify(documentStoragePort, never()).store(anyString(), anyString(), any());
        verify(addDossierElementUseCase, never()).add(any(), any());
    }

    private static Dossier closedDossier(UUID id) {
        Instant now = Instant.now();
        return new Dossier(
                id,
                "tenant1",
                "DOS-000001",
                "TYPE",
                "label",
                DossierStatus.CLOTURE,
                java.util.Map.of(),
                null,
                0L,
                "creator",
                now,
                "creator",
                now,
                "creator",
                now);
    }
}
