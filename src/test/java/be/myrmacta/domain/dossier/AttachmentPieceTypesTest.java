package be.myrmacta.domain.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.model.InvalidDossierElementException;
import be.myrmacta.domain.dossier.model.PieceType;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §106 (D-PIECE-CLASSIFY, D-PIECE-PORTAL). */
class AttachmentPieceTypesTest {

    private final DossierTypeRepository typeRepository = mock(DossierTypeRepository.class);
    private final AttachmentPieceTypes pieceTypes = new AttachmentPieceTypes(typeRepository);
    private final Dossier dossier = new Dossier(UUID.randomUUID(), "t", "R-1", "PERMIS", "L", DossierStatus.OUVERT,
            Map.of(), null, 0, "u", Instant.now(), null, null, null, null, null);

    @BeforeEach
    void declareTypes() {
        DossierType type = new DossierType(UUID.randomUUID(), "t", "PERMIS", "Permis", "wf", true, false, List.of(),
                null, false, null, List.of(), List.of(new PieceType("preuve", "Preuve", false),
                        new PieceType("identite", "Identité", true)), "u", "u");
        when(typeRepository.findByCode("t", "PERMIS")).thenReturn(Optional.of(type));
    }

    @Test
    void attachmentWithoutPieceKeyIsUnclassified() {
        assertThatCode(() -> pieceTypes.requireValid(dossier, ElementType.ATTACHMENT, Map.of("filename", "a.pdf")))
                .doesNotThrowAnyException();
        assertThatCode(() -> pieceTypes.requireValid(dossier, ElementType.NOTE, null)).doesNotThrowAnyException();
    }

    @Test
    void acceptsDeclaredKeyOnAttachmentOnly() {
        assertThatCode(() -> pieceTypes.requireValid(dossier, ElementType.ATTACHMENT, Map.of("pieceKey", "preuve")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> pieceTypes.requireValid(dossier, ElementType.NOTE, Map.of("pieceKey", "preuve")))
                .isInstanceOf(InvalidDossierElementException.class);
        assertThatThrownBy(() -> pieceTypes.requireValid(dossier, ElementType.ATTACHMENT, Map.of("pieceKey", 3)))
                .isInstanceOf(InvalidDossierElementException.class);
    }

    @Test
    void rejectsAForgedClassificationTrace() {
        assertThatThrownBy(() -> pieceTypes.requireValid(dossier, ElementType.ATTACHMENT,
                        Map.of("pieceKey", "preuve", "pieceClassifiedBy", "quelquun")))
                .isInstanceOf(InvalidDossierElementException.class);
        assertThatThrownBy(() -> pieceTypes.requireValid(dossier, ElementType.NOTE,
                        Map.of("text", "x", "pieceClassifiedAt", "2026-01-01T00:00:00Z")))
                .isInstanceOf(InvalidDossierElementException.class);
    }

    @Test
    void rejectsUnknownOrMalformedKey() {
        assertThatThrownBy(() -> pieceTypes.require(dossier, "inconnu"))
                .isInstanceOf(InvalidDossierElementException.class)
                .hasMessageContaining("unknown pieceKey");
        assertThatThrownBy(() -> pieceTypes.require(dossier, "../x"))
                .isInstanceOf(InvalidDossierElementException.class)
                .hasMessageContaining("invalid pieceKey");
    }

    @Test
    void elementWhoseTypeWasRemovedBecomesUnclassified() {
        DossierElement classified = element(Map.of("pieceKey", "preuve"));
        DossierElement orphan = element(Map.of("pieceKey", "retire"));

        assertThat(pieceTypes.of(dossier, classified)).map(PieceType::key).contains("preuve");
        assertThat(pieceTypes.of(dossier, orphan)).isEmpty();
        assertThat(pieceTypes.of(dossier, element(Map.of()))).isEmpty();
    }

    @Test
    void offeredKeepsBpmnOrderAndDropsUndeclaredKeys() {
        assertThat(pieceTypes.offered(dossier, List.of("identite", "inconnu", "preuve")))
                .extracting(PieceType::key)
                .containsExactly("identite", "preuve");
        assertThat(pieceTypes.offered(dossier, List.of())).isEmpty();
    }

    private DossierElement element(Map<String, Object> metadata) {
        return new DossierElement(UUID.randomUUID(), dossier.id(), "t", ElementType.ATTACHMENT, "Pièce", "t/ref",
                metadata, "u", Instant.now(), null);
    }
}
