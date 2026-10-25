package be.myrmacta.domain.workflow.portal.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class PortalRecipientSourceTest {

    @Test
    void parse_dossierFieldAndVariable() {
        assertThat(PortalRecipientSource.parse("dossierField:contactEmail"))
                .contains(new PortalRecipientSource(PortalRecipientSource.Kind.DOSSIER_FIELD, "contactEmail"));
        assertThat(PortalRecipientSource.parse(" variable:email_tiers "))
                .contains(new PortalRecipientSource(PortalRecipientSource.Kind.PROCESS_VARIABLE, "email_tiers"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "email", "field:x", "dossierField:", "variable:a.b", "dossierField:${x}",
            "variable:a b", "dossierField:1abc", "Variable:x"})
    void parse_rejectsInvalidForms(String raw) {
        assertThat(PortalRecipientSource.parse(raw)).isEmpty();
    }
}
