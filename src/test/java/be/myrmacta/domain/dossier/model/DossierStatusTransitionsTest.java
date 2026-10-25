package be.myrmacta.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DossierStatusTransitionsTest {

    @ParameterizedTest
    @CsvSource({
        "DRAFT,OUVERT",
        "OUVERT,EN_COURS",
        "OUVERT,CLOTURE",
        "EN_COURS,SUSPENDU",
        "EN_COURS,CLOTURE",
        "SUSPENDU,EN_COURS",
        "SUSPENDU,CLOTURE",
        "CLOTURE,ARCHIVE"
    })
    void allowedTransitions(DossierStatus from, DossierStatus to) {
        assertThat(DossierStatusTransitions.canTransition(from, to)).isTrue();
        DossierStatusTransitions.assertCanTransition(from, to);
    }

    @Test
    void illegalTransition_throws() {
        assertThat(DossierStatusTransitions.canTransition(DossierStatus.DRAFT, DossierStatus.ARCHIVE))
                .isFalse();
        assertThatThrownBy(() ->
                        DossierStatusTransitions.assertCanTransition(
                                DossierStatus.DRAFT, DossierStatus.ARCHIVE))
                .isInstanceOf(IllegalDossierStatusTransitionException.class);
    }

    @Test
    void archive_isTerminal() {
        assertThat(DossierStatusTransitions.canTransition(DossierStatus.ARCHIVE, DossierStatus.OUVERT))
                .isFalse();
    }
}
