package be.myrmacta.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExportDossiersQueryTest {

    @Test
    void constructor_rejectsNullDossierTypeCode() {
        assertThatThrownBy(() -> new ExportDossiersQuery(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejectsBlankDossierTypeCode() {
        assertThatThrownBy(() -> new ExportDossiersQuery("   ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_treatsBlankReferencePrefixAsAbsent() {
        ExportDossiersQuery query = new ExportDossiersQuery("FAC", null, "   ");

        assertThat(query.referencePrefix()).isNull();
    }

    @Test
    void constructor_trimsReferencePrefix() {
        ExportDossiersQuery query = new ExportDossiersQuery("FAC", null, "  DOS-  ");

        assertThat(query.referencePrefix()).isEqualTo("DOS-");
    }
}
