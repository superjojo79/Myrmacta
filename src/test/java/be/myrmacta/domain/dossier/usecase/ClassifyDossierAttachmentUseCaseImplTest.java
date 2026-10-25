package be.myrmacta.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.AttachmentPieceTypes;
import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierConflictException;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierElementNotFoundException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.model.InvalidDossierElementException;
import be.myrmacta.domain.dossier.model.PieceType;
import be.myrmacta.domain.dossier.port.in.ClassifyDossierAttachmentUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §105 (D-PIECE-CLASSIFY) — reclassement d'une pièce jointe déjà déposée. */
class ClassifyDossierAttachmentUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    private final DossierRepository dossierRepository = mock(DossierRepository.class);
    private final DossierElementRepository elementRepository = mock(DossierElementRepository.class);
    private final DossierTypeRepository typeRepository = mock(DossierTypeRepository.class);
    private final DossierAccessPolicy accessPolicy = mock(DossierAccessPolicy.class);
    private final ClassifyDossierAttachmentUseCaseImpl useCase = new ClassifyDossierAttachmentUseCaseImpl("t",
            dossierRepository, elementRepository, accessPolicy, new AttachmentPieceTypes(typeRepository),
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final AccessContext.User agent = AccessContext.user("agent", Set.of("DOSSIER_EDITOR"));

    private Dossier dossier;

    @BeforeEach
    void setUp() {
        dossier = dossier(DossierStatus.OUVERT);
        when(dossierRepository.findById(dossier.id())).thenReturn(Optional.of(dossier));
        when(typeRepository.findByCode("t", "PERMIS")).thenReturn(Optional.of(new DossierType(UUID.randomUUID(), "t",
                "PERMIS", "Permis", "wf", true, false, List.of(), null, false, null, List.of(),
                List.of(new PieceType("preuve", "Preuve", false), new PieceType("identite", "Identité", true)),
                "u", "u")));
        when(elementRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void classifiesAnUnclassifiedAttachmentAndTracesWhoAndWhen() {
        DossierElement element = attachment(Map.of("filename", "a.pdf"));

        ClassifyDossierAttachmentUseCase.Result result = useCase.classify(dossier.id(), element.id(), " preuve ", agent);

        assertThat(result.previousPieceKey()).isNull();
        assertThat(result.element().metadata())
                .containsEntry("pieceKey", "preuve")
                .containsEntry("filename", "a.pdf")
                .containsEntry("pieceClassifiedBy", "agent")
                .containsEntry("pieceClassifiedAt", NOW.toString());
        assertThat(result.element().storageRef()).isEqualTo(element.storageRef());
    }

    @Test
    void changesThenRemovesTheType() {
        DossierElement element = attachment(Map.of("pieceKey", "preuve"));

        assertThat(useCase.classify(dossier.id(), element.id(), "identite", agent).previousPieceKey()).isEqualTo("preuve");
        ClassifyDossierAttachmentUseCase.Result removed = useCase.classify(dossier.id(), element.id(), null, agent);
        assertThat(removed.element().metadata()).doesNotContainKey("pieceKey");
        assertThat(removed.previousPieceKey()).isEqualTo("preuve");
    }

    @Test
    void refusesUnknownKeyNonAttachmentAndElementOfAnotherDossier() {
        DossierElement element = attachment(Map.of());
        assertThatThrownBy(() -> useCase.classify(dossier.id(), element.id(), "inconnu", agent))
                .isInstanceOf(InvalidDossierElementException.class);

        DossierElement note = new DossierElement(UUID.randomUUID(), dossier.id(), "t", ElementType.NOTE, "Note", null,
                Map.of("text", "x"), "u", NOW, null);
        when(elementRepository.findByIdAndTenantId(note.id(), "t")).thenReturn(Optional.of(note));
        assertThatThrownBy(() -> useCase.classify(dossier.id(), note.id(), "preuve", agent))
                .isInstanceOf(InvalidDossierElementException.class);

        DossierElement foreign = new DossierElement(UUID.randomUUID(), UUID.randomUUID(), "t", ElementType.ATTACHMENT,
                "Pièce", "t/ref", Map.of(), "u", NOW, null);
        when(elementRepository.findByIdAndTenantId(foreign.id(), "t")).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> useCase.classify(dossier.id(), foreign.id(), "preuve", agent))
                .isInstanceOf(DossierElementNotFoundException.class);
        verify(elementRepository, never()).save(any());
    }

    @Test
    void requiresEditAndAnOpenDossier() {
        DossierElement element = attachment(Map.of());
        doThrow(new AccessDeniedException("no EDIT")).when(accessPolicy).assertCanEdit(dossier, agent);
        assertThatThrownBy(() -> useCase.classify(dossier.id(), element.id(), "preuve", agent))
                .isInstanceOf(AccessDeniedException.class);

        Dossier closed = dossier(DossierStatus.CLOTURE);
        when(dossierRepository.findById(closed.id())).thenReturn(Optional.of(closed));
        assertThatThrownBy(() -> useCase.classify(closed.id(), element.id(), "preuve", agent))
                .isInstanceOf(DossierConflictException.class);
        verify(elementRepository, never()).save(any());
    }

    private DossierElement attachment(Map<String, Object> metadata) {
        DossierElement element = new DossierElement(UUID.randomUUID(), dossier.id(), "t", ElementType.ATTACHMENT,
                "Pièce", "t/ref", metadata, "u", NOW, null);
        when(elementRepository.findByIdAndTenantId(element.id(), "t")).thenReturn(Optional.of(element));
        return element;
    }

    private static Dossier dossier(DossierStatus status) {
        return new Dossier(UUID.randomUUID(), "t", "R-1", "PERMIS", "L", status, Map.of(), null, 0, "u", NOW, null,
                null, null, null, null);
    }
}
