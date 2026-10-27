package be.myrmacta.domain.precheck.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** TODO §106 — bornes de la pré-vérification, testées à la limite (issue #243). */
class PrecheckLimitsTest {

    /** Les sept bornes dans l'ordre du record ; {@code values[index] = value}, toutes les autres à 1. */
    private static PrecheckLimits withOnly(int index, long value) {
        long[] v = {1, 1, 1, 1, 1, 1, 1};
        v[index] = value;
        return new PrecheckLimits(v[0], (int) v[1], (int) v[2], (int) v[3], (int) v[4], (int) v[5], (int) v[6]);
    }

    @ParameterizedTest(name = "borne n° {0}")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void eachLimitAcceptsOneAndRefusesZero(int index) {
        assertThat(withOnly(index, 1)).isEqualTo(new PrecheckLimits(1, 1, 1, 1, 1, 1, 1));
        assertThatThrownBy(() -> withOnly(index, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("precheck limits must all be positive");
    }

    @Test
    void onePieceMayFillTheWholeTextBudgetButNotExceedIt() {
        assertThat(new PrecheckLimits(1, 1, 1, 1, 500, 500, 1).maxCharsPerPiece()).isEqualTo(500);
        assertThatThrownBy(() -> new PrecheckLimits(1, 1, 1, 1, 501, 500, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxCharsPerPiece must not exceed maxCharsTotal");
    }

    @Test
    void theDefaultsAreValid() {
        assertThat(PrecheckLimits.defaults().maxCharsPerPiece()).isLessThanOrEqualTo(PrecheckLimits.defaults().maxCharsTotal());
    }
}
