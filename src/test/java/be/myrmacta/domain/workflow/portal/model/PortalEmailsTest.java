package be.myrmacta.domain.workflow.portal.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PortalEmailsTest {

    @Test
    void normalize_acceptsPlausibleAddresses() {
        assertThat(PortalEmails.normalize(" alice.martin+dossier@exemple.fr ")).contains("alice.martin+dossier@exemple.fr");
    }

    @Test
    void normalize_rejectsInvalidOrNonString() {
        assertThat(PortalEmails.normalize(null)).isEmpty();
        assertThat(PortalEmails.normalize(42)).isEmpty();
        assertThat(PortalEmails.normalize("")).isEmpty();
        assertThat(PortalEmails.normalize("pas-une-adresse")).isEmpty();
        assertThat(PortalEmails.normalize("a@b")).isEmpty();
        assertThat(PortalEmails.normalize("${connectorServiceTask}@x.org")).isEmpty();
        assertThat(PortalEmails.normalize("a@x.org\r\nBcc: evil@x.org")).isEmpty();
    }

    @Test
    void mask_neverRevealsTheLocalPart() {
        assertThat(PortalEmails.mask("alice@example.org")).isEqualTo("a***@example.org");
        assertThat(PortalEmails.mask(null)).isEqualTo("<none>");
        assertThat(PortalEmails.mask("weird")).isEqualTo("***");
    }
}
