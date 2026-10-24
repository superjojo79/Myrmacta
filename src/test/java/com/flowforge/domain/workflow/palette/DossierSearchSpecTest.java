package com.flowforge.domain.workflow.palette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.portal.model.PortalRecipientSource;
import org.junit.jupiter.api.Test;

/** TODO §102 (issue #184, lot B, R2) — forme du paramètre {@code searchCriteria} de la brique « Rechercher des dossiers ». */
class DossierSearchSpecTest {

    private static final String OWNER = "flowable:searchCriteria on s";

    @Test
    void readsOneToThreeEqualityLinesFromTheDossierOrAVariable() {
        var criteria = DossierSearchSpec.parse("email = dossierField:email\n  plaque=variable:plaqueSaisie  \n", OWNER);
        assertThat(criteria).containsExactly(
                new DossierSearchSpec.Criterion("email",
                        new PortalRecipientSource(PortalRecipientSource.Kind.DOSSIER_FIELD, "email")),
                new DossierSearchSpec.Criterion("plaque",
                        new PortalRecipientSource(PortalRecipientSource.Kind.PROCESS_VARIABLE, "plaqueSaisie")));
    }

    @Test
    void refusesMalformedCriteria() {
        for (String bad : new String[] {
                "", "   ", "email", "email = ", "email = \"x\"", "email = dossierField:", "email = variable:a.b",
                "1email = dossierField:email", "email = dossierField:email\nemail = variable:x",
                "a = variable:x\nb = variable:x\nc = variable:x\nd = variable:x",
                "email == dossierField:email", "email = ${dossierField}"}) {
            assertThatThrownBy(() -> DossierSearchSpec.parse(bad, OWNER))
                    .as(bad).isInstanceOf(InvalidBpmnException.class).hasMessageContaining(OWNER);
        }
    }
}
