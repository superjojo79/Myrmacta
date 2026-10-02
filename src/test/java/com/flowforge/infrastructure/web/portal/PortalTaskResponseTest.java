package com.flowforge.infrastructure.web.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import org.junit.jupiter.api.Test;

/** TODO §85 (LC4a) — le portail externe reçoit la mise en page (section, largeur, aide) mais jamais plus que les champs. */
class PortalTaskResponseTest {

    @Test
    void theLayoutOfAFieldReachesThePortal() {
        var def = new FieldDefinition("a", "A", FieldType.STRING, true, null, null, "Contact", false, null, null, null, null,
                null, null, false, "Votre nom", "HALF");
        var field = PortalTaskResponse.Field.from(def);
        assertThat(field.group()).isEqualTo("Contact");
        assertThat(field.helpText()).isEqualTo("Votre nom");
        assertThat(field.width()).isEqualTo("HALF");
    }
}
