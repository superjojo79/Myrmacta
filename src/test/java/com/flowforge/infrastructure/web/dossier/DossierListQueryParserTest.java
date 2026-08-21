package com.flowforge.infrastructure.web.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.SearchDossiersQuery;
import org.junit.jupiter.api.Test;

class DossierListQueryParserTest {

    @Test
    void defaultsPageAndSize() {
        SearchDossiersQuery query = DossierListQueryParser.parse(null, null, null, null, null, null);
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.status()).isNull();
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> DossierListQueryParser.parse(-1, 20, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
    }

    @Test
    void rejectsPageAboveMax() {
        assertThatThrownBy(() -> DossierListQueryParser.parse(
                        SearchDossiersQuery.MAX_PAGE + 1, 20, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
    }

    @Test
    void acceptsMaxPage() {
        SearchDossiersQuery query =
                DossierListQueryParser.parse(SearchDossiersQuery.MAX_PAGE, 20, null, null, null, null);
        assertThat(query.page()).isEqualTo(SearchDossiersQuery.MAX_PAGE);
    }

    @Test
    void rejectsSizeOutOfBounds() {
        assertThatThrownBy(() -> DossierListQueryParser.parse(0, 0, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DossierListQueryParser.parse(0, 101, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsBoundarySizes() {
        assertThat(DossierListQueryParser.parse(0, 1, null, null, null, null).size()).isEqualTo(1);
        assertThat(DossierListQueryParser.parse(0, 100, null, null, null, null).size()).isEqualTo(100);
    }

    @Test
    void rejectsUnsupportedSort() {
        assertThatThrownBy(() -> DossierListQueryParser.parse(0, 20, "reference,asc", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sort");
    }

    @Test
    void acceptsUpdatedAtDescSort() {
        SearchDossiersQuery query = DossierListQueryParser.parse(0, 20, "updatedAt,desc", "OUVERT", "FAC", "DOS");
        assertThat(query.status()).isEqualTo(DossierStatus.OUVERT);
        assertThat(query.dossierTypeCode()).isEqualTo("FAC");
        assertThat(query.referencePrefix()).isEqualTo("DOS");
    }

    @Test
    void rejectsInvalidStatus() {
        assertThatThrownBy(() -> DossierListQueryParser.parse(0, 20, null, "NOPE", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    void parseStatus_blankNonNullStatus_treatedAsAbsent() {
        assertThat(DossierListQueryParser.parseStatus("   ")).isNull();
    }
}
