package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierConflictException;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.model.InvalidDossierElementException;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddDossierElementUseCaseImplTest {

    @Mock
    DossierRepository dossierRepository;

    @Mock
    DossierElementRepository elementRepository;

    @Mock
    DossierAccessPolicy accessPolicy;

    AddDossierElementUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddDossierElementUseCaseImpl(
                "tenant_dev", dossierRepository, elementRepository, accessPolicy);
    }

    @Test
    void add_producedDoc_persistsWithMetadata() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId)));
        when(elementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DossierElement saved = useCase.add(
                new AddDossierElementUseCase.AddCommand(
                        dossierId,
                        ElementType.PRODUCED_DOC,
                        "Document généré — step",
                        "tenant_dev/x.pdf",
                        Map.of("templateKey", "contrat", "contentType", "application/pdf"),
                        "system",
                        "task1"),
                AccessContext.system("test"));

        ArgumentCaptor<DossierElement> captor = ArgumentCaptor.forClass(DossierElement.class);
        verify(elementRepository).save(captor.capture());
        verify(accessPolicy).assertCanEdit(any(Dossier.class), any(AccessContext.class));
        assertThat(captor.getValue().type()).isEqualTo(ElementType.PRODUCED_DOC);
        assertThat(captor.getValue().metadata()).containsEntry("templateKey", "contrat");
        assertThat(saved.storageRef()).isEqualTo("tenant_dev/x.pdf");
    }

    @Test
    void add_unknownDossier_throws() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.add(
                        new AddDossierElementUseCase.AddCommand(
                                dossierId,
                                ElementType.PRODUCED_DOC,
                                "label",
                                "tenant_dev/x.pdf",
                                Map.of(),
                                "system",
                                null),
                        AccessContext.system("test")))
                .isInstanceOf(DossierNotFoundException.class);
        verify(elementRepository, never()).save(any());
    }

    @Test
    void add_blankStorageRef_throws() {
        assertThatThrownBy(() -> useCase.add(
                        new AddDossierElementUseCase.AddCommand(
                                UUID.randomUUID(),
                                ElementType.PRODUCED_DOC,
                                "label",
                                " ",
                                Map.of(),
                                "system",
                                null),
                        AccessContext.system("test")))
                .isInstanceOf(InvalidDossierElementException.class);
    }

    @Test
    void add_storageRefWithoutTenantPrefix_throws() {
        assertThatThrownBy(() -> useCase.add(
                        new AddDossierElementUseCase.AddCommand(
                                UUID.randomUUID(),
                                ElementType.PRODUCED_DOC,
                                "label",
                                "other/x.pdf",
                                Map.of(),
                                "system",
                                null),
                        AccessContext.system("test")))
                .isInstanceOf(InvalidDossierElementException.class)
                .hasMessageContaining("tenant-scoped");
    }

    @Test
    void add_attachment_persistsWithStorageRef() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId)));
        when(elementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DossierElement saved = useCase.add(
                new AddDossierElementUseCase.AddCommand(
                        dossierId,
                        ElementType.ATTACHMENT,
                        "Justificatif",
                        "tenant_dev/att-1",
                        Map.of("filename", "justificatif.pdf", "contentType", "application/pdf"),
                        "user",
                        null),
                AccessContext.user("u1", Set.of("DOSSIER_MANAGER")));

        assertThat(saved.type()).isEqualTo(ElementType.ATTACHMENT);
        assertThat(saved.storageRef()).isEqualTo("tenant_dev/att-1");
    }

    @Test
    void add_attachmentWithoutStorageRef_throws() {
        UUID dossierId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.add(
                        new AddDossierElementUseCase.AddCommand(
                                dossierId, ElementType.ATTACHMENT, "label", null, Map.of(), "user", null),
                        AccessContext.user("u1", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(InvalidDossierElementException.class);
        verify(elementRepository, never()).save(any());
    }

    @Test
    void add_information_persistsWithNullStorageRefAndNoLabelRestriction() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId)));
        when(elementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DossierElement saved = useCase.add(
                new AddDossierElementUseCase.AddCommand(
                        dossierId,
                        ElementType.INFORMATION,
                        "Vérification externe",
                        null,
                        Map.of("statut", "confirmé"),
                        "user",
                        null),
                AccessContext.user("u1", Set.of("DOSSIER_MANAGER")));

        assertThat(saved.storageRef()).isNull();
        assertThat(saved.metadata()).containsEntry("statut", "confirmé");
    }

    @Test
    void add_informationWithEmptyMetadata_throws() {
        UUID dossierId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.add(
                        new AddDossierElementUseCase.AddCommand(
                                dossierId, ElementType.INFORMATION, "label", null, Map.of(), "user", null),
                        AccessContext.user("u1", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(InvalidDossierElementException.class);
    }

    @Test
    void add_note_persistsTextInMetadata() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId)));
        when(elementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DossierElement saved = useCase.add(
                new AddDossierElementUseCase.AddCommand(
                        dossierId,
                        ElementType.NOTE,
                        "Rappel",
                        null,
                        Map.of("text", "À relancer le 20/08"),
                        "user",
                        null),
                AccessContext.user("u1", Set.of("DOSSIER_MANAGER")));

        assertThat(saved.metadata()).containsEntry("text", "À relancer le 20/08");
    }

    @Test
    void add_noteWithBlankText_throws() {
        UUID dossierId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.add(
                        new AddDossierElementUseCase.AddCommand(
                                dossierId,
                                ElementType.NOTE,
                                "label",
                                null,
                                Map.of("text", "  "),
                                "user",
                                null),
                        AccessContext.user("u1", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(InvalidDossierElementException.class);
    }

    @Test
    void add_link_persistsUrlInMetadata() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId)));
        when(elementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DossierElement saved = useCase.add(
                new AddDossierElementUseCase.AddCommand(
                        dossierId,
                        ElementType.LINK,
                        "Ticket externe",
                        null,
                        Map.of("url", "https://example.com/ticket/1", "description", "Suivi"),
                        "user",
                        null),
                AccessContext.user("u1", Set.of("DOSSIER_MANAGER")));

        assertThat(saved.metadata()).containsEntry("url", "https://example.com/ticket/1");
    }

    @Test
    void add_linkWithMalformedUrl_throws() {
        UUID dossierId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.add(
                        new AddDossierElementUseCase.AddCommand(
                                dossierId,
                                ElementType.LINK,
                                "label",
                                null,
                                Map.of("url", "not a url"),
                                "user",
                                null),
                        AccessContext.user("u1", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(InvalidDossierElementException.class);
    }

    @Test
    void add_linkWithJavascriptScheme_throws() {
        // ElementsList.vue renders LINK.metadata.url as a clickable <a href> — a non-http(s)
        // scheme like javascript: would let a stored LINK execute in a viewer's session on click.
        UUID dossierId = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.add(
                        new AddDossierElementUseCase.AddCommand(
                                dossierId,
                                ElementType.LINK,
                                "label",
                                null,
                                Map.of("url", "javascript:alert(1)"),
                                "user",
                                null),
                        AccessContext.user("u1", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(InvalidDossierElementException.class);
    }

    @Test
    void add_humanElementOnClosedDossier_throwsConflict() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId, DossierStatus.CLOTURE)));

        assertThatThrownBy(() -> useCase.add(
                        new AddDossierElementUseCase.AddCommand(
                                dossierId,
                                ElementType.NOTE,
                                "label",
                                null,
                                Map.of("text", "x"),
                                "user",
                                null),
                        AccessContext.user("u1", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(DossierConflictException.class);
        verify(elementRepository, never()).save(any());
    }

    @Test
    void add_humanElementOnArchivedDossier_throwsConflict() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId, DossierStatus.ARCHIVE)));

        assertThatThrownBy(() -> useCase.add(
                        new AddDossierElementUseCase.AddCommand(
                                dossierId,
                                ElementType.ATTACHMENT,
                                "label",
                                "tenant_dev/x",
                                Map.of(),
                                "user",
                                null),
                        AccessContext.user("u1", Set.of("DOSSIER_MANAGER"))))
                .isInstanceOf(DossierConflictException.class);
    }

    @Test
    void add_producedDocOnClosedDossier_stillAllowed() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId, DossierStatus.CLOTURE)));
        when(elementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DossierElement saved = useCase.add(
                new AddDossierElementUseCase.AddCommand(
                        dossierId,
                        ElementType.PRODUCED_DOC,
                        "label",
                        "tenant_dev/x.pdf",
                        Map.of(),
                        "system",
                        "task1"),
                AccessContext.system("test"));

        assertThat(saved.type()).isEqualTo(ElementType.PRODUCED_DOC);
    }

    @Test
    void add_userWithoutAcl_denied() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossier(dossierId);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        doThrow(new AccessDeniedException("denied"))
                .when(accessPolicy)
                .assertCanEdit(any(Dossier.class), any(AccessContext.class));

        assertThatThrownBy(() -> useCase.add(
                        new AddDossierElementUseCase.AddCommand(
                                dossierId,
                                ElementType.PRODUCED_DOC,
                                "label",
                                "tenant_dev/x.pdf",
                                Map.of(),
                                "user",
                                null),
                        new AccessContext.User("u1", Set.of("ROLE_DOSSIER_MANAGER"))))
                .isInstanceOf(AccessDeniedException.class);
        verify(elementRepository, never()).save(any());
    }

    private static Dossier dossier(UUID id) {
        return dossier(id, DossierStatus.OUVERT);
    }

    private static Dossier dossier(UUID id, DossierStatus status) {
        Instant now = Instant.parse("2026-08-03T12:00:00Z");
        return new Dossier(
                id,
                "tenant_dev",
                "REF-1",
                "SINISTRE",
                "label",
                status,
                Map.of(),
                null,
                0L,
                "user",
                now,
                null,
                null,
                null,
                null,
                null);
    }
}
