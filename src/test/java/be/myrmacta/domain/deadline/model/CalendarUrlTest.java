package be.myrmacta.domain.deadline.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** TODO §121 (D-CAL-ICS-URL) — l'URL d'une source est un secret : seul son hôte se montre. */
class CalendarUrlTest {

    @Test
    void theMaskedFormKeepsTheSchemeAndTheHostOnly() {
        CalendarUrl url = CalendarUrl.parse("https://calendar.example.org/ical/abc123-secret-token/basic.ics?key=s3cr3t#frag");

        assertThat(url.value()).isEqualTo("https://calendar.example.org/ical/abc123-secret-token/basic.ics?key=s3cr3t#frag");
        assertThat(url.masked()).isEqualTo("https://calendar.example.org/…");
        assertThat(url.masked()).doesNotContain("abc123", "s3cr3t", "basic.ics");
        assertThat(url.host()).isEqualTo("calendar.example.org");
    }

    @Test
    void aNonDefaultPortIsShown_andTheHostIsLowercased() {
        assertThat(CalendarUrl.parse("https://Calendar.Example.ORG:8443/x").masked()).isEqualTo("https://calendar.example.org:8443/…");
    }

    @Test
    void theUrlIsStripped() {
        assertThat(CalendarUrl.parse("  https://example.org/a.ics \n").value()).isEqualTo("https://example.org/a.ics");
    }

    @Test
    void aBlankMalformedOrHostlessUrlIsRefused() {
        for (String bad : new String[] {null, "", "   ", "pas une url", "https://", "https:///chemin", "//example.org/a.ics", "example.org/a.ics"}) {
            assertThatThrownBy(() -> CalendarUrl.parse(bad)).as(String.valueOf(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void onlyHttpAndHttpsSchemesAreAccepted_theNetworkGuardDecidesTheRest() {
        assertThat(CalendarUrl.parse("http://localhost:8080/a.ics").masked()).isEqualTo("http://localhost:8080/…");
        for (String bad : new String[] {"ftp://example.org/a.ics", "file:///etc/passwd", "javascript:alert(1)", "gopher://example.org/"}) {
            assertThatThrownBy(() -> CalendarUrl.parse(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void credentialsInTheUrlAreRefused() {
        assertThatThrownBy(() -> CalendarUrl.parse("https://user:pass@example.org/a.ics"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("pass");
        assertThatThrownBy(() -> CalendarUrl.parse("https://user@example.org/a.ics")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUrlOver2048CharactersIsRefused() {
        String longPath = "https://example.org/" + "a".repeat(2048);

        assertThatThrownBy(() -> CalendarUrl.parse(longPath)).isInstanceOf(IllegalArgumentException.class);
        assertThat(CalendarUrl.parse("https://example.org/" + "a".repeat(2048 - "https://example.org/".length())).value()).hasSize(2048);
    }

    @Test
    void anErrorMessageNeverEchoesTheUrl() {
        String secret = "https://example.org/ical/TOPSECRET-TOKEN/a.ics";
        for (String bad : new String[] {"ftp://example.org/TOPSECRET-TOKEN/a.ics", "https://u:TOPSECRET-TOKEN@example.org/a.ics",
                "https://example.org/ TOPSECRET-TOKEN"}) {
            assertThatThrownBy(() -> CalendarUrl.parse(bad)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining("TOPSECRET");
        }
        assertThat(CalendarUrl.parse(secret).toString()).doesNotContain("TOPSECRET");
    }
}
