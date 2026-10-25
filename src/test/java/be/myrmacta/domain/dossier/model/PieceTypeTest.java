package be.myrmacta.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TODO §106 (D-PIECE-TYPES) — types de pièces déclarés sur un type de dossier. */
class PieceTypeTest {

    @Test
    void acceptsValidKeyAndStripsLabel() {
        PieceType type = new PieceType("preuveDomicile", "  Preuve de domicile ", true);

        assertThat(type.label()).isEqualTo("Preuve de domicile");
        assertThat(type.sensitive()).isTrue();
    }

    @Test
    void rejectsInvalidKeys() {
        for (String key : List.of("", "Preuve", "1piece", "preuve-domicile", "preuve domicile", "a".repeat(61))) {
            assertThatThrownBy(() -> new PieceType(key, "Libellé", false))
                    .as(key)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(PieceType.isValidKey("a".repeat(60))).isTrue();
    }

    @Test
    void rejectsBlankOrTooLongLabel() {
        assertThatThrownBy(() -> new PieceType("preuve", "  ", false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PieceType("preuve", "x".repeat(101), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dossierTypeRejectsDuplicateKeysAndMoreThanThirty() {
        List<PieceType> duplicated = List.of(new PieceType("preuve", "A", false), new PieceType("preuve", "B", false));
        assertThatThrownBy(() -> type(duplicated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate pieceType key: preuve");

        List<PieceType> many = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            many.add(new PieceType("piece" + i, "Pièce " + i, false));
        }
        assertThatThrownBy(() -> type(many)).isInstanceOf(IllegalArgumentException.class);
        assertThat(type(many.subList(0, 30)).pieceTypes()).hasSize(30);
    }

    @Test
    void dossierTypeFindsDeclaredPieceTypeAndKeepsItOnLegacyUpdate() {
        DossierType type = type(List.of(new PieceType("preuve", "Preuve", false)));

        assertThat(type.pieceType("preuve")).map(PieceType::label).contains("Preuve");
        assertThat(type.pieceType("autre")).isEmpty();
        DossierType updated = type.withUpdate("Nouveau", "wf", true, false, List.of(), null, false, null, List.of(), "u2");
        assertThat(updated.pieceTypes()).isEqualTo(type.pieceTypes());
        assertThat(DossierType.inherit(UUID.randomUUID(), "t", "C", "L", "wf", true, false, List.of()).pieceTypes())
                .isEmpty();
    }

    private static DossierType type(List<PieceType> pieceTypes) {
        return new DossierType(UUID.randomUUID(), "t", "C", "L", "wf", true, false, List.of(), null, false, null,
                List.of(), pieceTypes, "u", "u");
    }
}
