package be.myrmacta.infrastructure.web.dossier;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.mock.web.MockMultipartFile;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** TODO §120 (D-CAL-ZONE) et §121 (D-CAL-ENTRY, D-CAL-API, D-CAL-BE-SOURCE) — calendrier du tenant, réservé à TENANT_ADMIN. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class TenantCalendarApiTest {

    private static final String TENANT_ID = "tenant_dev";
    private static final String OTHER_TENANT = "tenant_autre";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearCalendar() {
        jdbcTemplate.update("DELETE FROM tenant_calendar WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT);
        jdbcTemplate.update("DELETE FROM tenant_calendar_source WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT);
        jdbcTemplate.update("DELETE FROM tenant_calendar_entry WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT);
    }

    @Test
    void getCalendar_absent_returnsUtcDefaults() throws Exception {
        mockMvc.perform(get("/api/tenant/calendar").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneId").value("UTC"))
                .andExpect(jsonPath("$.weekend").value("SATURDAY_SUNDAY"))
                .andExpect(jsonPath("$.holidays").isEmpty());
    }

    @Test
    void getCalendar_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/tenant/calendar").with(user("mgr-cal", "DOSSIER_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void putCalendar_unknownZone_returns400() throws Exception {
        mockMvc.perform(put("/api/tenant/calendar")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zoneId":"Europe/Nullepart","weekend":"SATURDAY_SUNDAY","holidays":[]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putAndBelgianFill_roundTrip_isIdempotent() throws Exception {
        mockMvc.perform(put("/api/tenant/calendar")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zoneId":"Europe/Brussels","weekend":"SUNDAY_ONLY","holidays":["2026-11-11"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneId").value("Europe/Brussels"))
                .andExpect(jsonPath("$.weekend").value("SUNDAY_ONLY"))
                .andExpect(jsonPath("$.holidays[0]").value("2026-11-11"));

        mockMvc.perform(post("/api/tenant/calendar/belgian-federal-holidays").with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holidays", hasItem("2026-04-06")))
                .andExpect(jsonPath("$.holidays", hasItem("2026-05-14")))
                .andExpect(jsonPath("$.holidays", hasItem("2026-11-11")))
                .andExpect(jsonPath("$.holidays.length()").value(20));

        mockMvc.perform(post("/api/tenant/calendar/belgian-federal-holidays").with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holidays.length()").value(20));
    }

    @Test
    void getCalendar_absent_hasNoEntriesNorSources() throws Exception {
        mockMvc.perform(get("/api/tenant/calendar").with(admin()))
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    @Test
    void putEntries_roundTrip_keepsPeriodsNaturesAndLabels() throws Exception {
        mockMvc.perform(put("/api/tenant/calendar")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zoneId":"Europe/Brussels","weekend":"SATURDAY_SUNDAY","entries":[
                                  {"start":"2026-12-24","end":"2026-12-31","nature":"CLOSED","label":"Congé collectif"},
                                  {"start":"2026-11-14","nature":"OPEN","label":"Samedi travaillé"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].start").value("2026-11-14"))
                .andExpect(jsonPath("$.entries[0].end").value("2026-11-14"))
                .andExpect(jsonPath("$.entries[0].nature").value("OPEN"))
                .andExpect(jsonPath("$.entries[1].end").value("2026-12-31"))
                .andExpect(jsonPath("$.entries[1].label").value("Congé collectif"))
                .andExpect(jsonPath("$.holidays.length()").value(8))
                .andExpect(jsonPath("$.updatedBy").value("admin-cal"));

        mockMvc.perform(get("/api/tenant/calendar").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[1].label").value("Congé collectif"));
    }

    @Test
    void putBothEntriesAndHolidays_returns400() throws Exception {
        mockMvc.perform(put("/api/tenant/calendar")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","entries":[],"holidays":["2026-11-11"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putInvalidEntries_return400() throws Exception {
        String[] bodies = {
                """
                {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","entries":[{"start":"2026-12-31","end":"2026-12-24","nature":"CLOSED"}]}
                """,
                """
                {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","entries":[{"start":"2026-01-01","end":"2027-12-31","nature":"CLOSED"}]}
                """,
                """
                {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","entries":[{"start":"2026-12-24","nature":"MAYBE"}]}
                """,
                """
                {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","entries":[{"start":"pas une date","nature":"CLOSED"}]}
                """,
                """
                {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","entries":[{"start":"2026-12-24","nature":"CLOSED","label":"%s"}]}
                """.formatted("x".repeat(121))
        };
        for (String body : bodies) {
            mockMvc.perform(put("/api/tenant/calendar").with(admin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void putCalendar_nonAdmin_returns403_andNothingIsWritten() throws Exception {
        mockMvc.perform(put("/api/tenant/calendar")
                        .with(user("mgr-cal", "DOSSIER_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","entries":[]}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tenant/calendar/belgian-federal-holidays").with(user("mgr-cal", "DOSSIER_MANAGER")).with(csrf()))
                .andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(
                jdbcTemplate.queryForObject("select count(*) from tenant_calendar where tenant_id = ?", Integer.class, TENANT_ID)).isZero();
    }

    @Test
    void belgianSource_listsLabeledDays_andAForcedOpenManualDayBeatsIt() throws Exception {
        mockMvc.perform(put("/api/tenant/calendar").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zoneId":"Europe/Brussels","weekend":"SATURDAY_SUNDAY","entries":[
                                  {"start":"2026-11-11","nature":"OPEN","label":"On travaille"}]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/tenant/calendar/belgian-federal-holidays").with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(1))
                .andExpect(jsonPath("$.sources[0].kind").value("BE_FEDERAL"))
                .andExpect(jsonPath("$.sources[0].name").value("Fériés fédéraux belges"))
                .andExpect(jsonPath("$.sources[0].entryCount").value(20))
                .andExpect(jsonPath("$.sources[0].maskedUrl").doesNotExist())
                .andExpect(jsonPath("$.sources[0].entries[?(@.start == '2026-12-25')].label", hasItem("Noël")))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.holidays", not(hasItem("2026-11-11"))))
                .andExpect(jsonPath("$.holidays.length()").value(19));
    }

    @Test
    void reloadingTheBelgianSource_keepsTheManualEntries_andOneSource() throws Exception {
        mockMvc.perform(put("/api/tenant/calendar").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","entries":[{"start":"2026-12-24","nature":"CLOSED"}]}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tenant/calendar/belgian-federal-holidays").with(admin()).with(csrf())).andExpect(status().isOk());

        mockMvc.perform(post("/api/tenant/calendar/belgian-federal-holidays").with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(1))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].start").value("2026-12-24"));
        mockMvc.perform(put("/api/tenant/calendar").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","entries":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(1));
    }

    @Test
    void anotherTenantsRowsAreNeverReturned() throws Exception {
        java.util.UUID source = java.util.UUID.randomUUID();
        jdbcTemplate.update("insert into tenant_calendar_source (id, tenant_id, name, kind) values (?, ?, 'Étrangère', 'ICS_FILE')",
                source, OTHER_TENANT);
        jdbcTemplate.update("""
                insert into tenant_calendar_entry (id, tenant_id, source_id, start_date, end_date, nature)
                values (?, ?, ?, '2026-12-01', '2026-12-01', 'CLOSED'), (?, ?, null, '2026-12-02', '2026-12-02', 'CLOSED')
                """, java.util.UUID.randomUUID(), OTHER_TENANT, source, java.util.UUID.randomUUID(), OTHER_TENANT);

        mockMvc.perform(put("/api/tenant/calendar").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","entries":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.holidays").isEmpty());

        org.assertj.core.api.Assertions.assertThat(
                jdbcTemplate.queryForObject("select count(*) from tenant_calendar_entry where tenant_id = ?", Integer.class, OTHER_TENANT))
                .isEqualTo(2);
    }

    // ------------------------------------------------------------------ sources ICS par fichier (lot 2)

    private static byte[] ics(String... events) {
        return ("BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//Test//FR\n" + String.join("", events) + "END:VCALENDAR\n")
                .replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String allDay(String uid, String date, String summary) {
        return "BEGIN:VEVENT\nUID:" + uid + "\nDTSTAMP:20260101T000000Z\nDTSTART;VALUE=DATE:" + date + "\nSUMMARY:" + summary + "\nEND:VEVENT\n";
    }

    private static final String TIMED = "BEGIN:VEVENT\nUID:t\nDTSTAMP:20260101T000000Z\nDTSTART:20261105T090000Z\nDTEND:20261105T100000Z\nSUMMARY:Réunion\nEND:VEVENT\n";

    /**
     * L'empreinte d'un contenu, telle qu'un client la reçoit : par un aperçu (elle décrit les périodes lues, pas les
     * octets du fichier). Sans effet : l'aperçu n'écrit rien.
     */
    private String sha(byte[] content) throws Exception {
        String body = mockMvc.perform(multipart("/api/tenant/calendar/sources/preview").file(file(content))
                        .param("name", "sonde-" + java.util.UUID.randomUUID()).with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.sha256");
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "ecoles.ics", "text/calendar", content);
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private java.util.UUID createSource(String name, byte[] content) throws Exception {
        String body = mockMvc.perform(multipart("/api/tenant/calendar/sources").file(file(content)).param("name", name)
                        .param("expectedSha256", sha(content)).with(admin()).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> ids = com.jayway.jsonpath.JsonPath.read(body, "$.sources[?(@.name == '" + name + "')].id");
        return java.util.UUID.fromString(ids.getFirst());
    }

    @Test
    void previewNewSource_listsAddedDaysIgnoredEventsAndTheSha_andWritesNothing() throws Exception {
        byte[] content = ics(allDay("a", "20261225", "Noël"), allDay("b", "20261231", "Saint-Sylvestre"), TIMED);

        mockMvc.perform(multipart("/api/tenant/calendar/sources/preview").file(file(content)).param("name", "Écoles")
                        .with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Écoles"))
                .andExpect(jsonPath("$.kind").value("ICS_FILE"))
                .andExpect(jsonPath("$.sha256").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.periodCount").value(2))
                .andExpect(jsonPath("$.added.length()").value(2))
                .andExpect(jsonPath("$.added[0].start").value("2026-12-25"))
                .andExpect(jsonPath("$.added[0].end").value("2026-12-25"))
                .andExpect(jsonPath("$.removed").isEmpty())
                .andExpect(jsonPath("$.masked").isEmpty())
                .andExpect(jsonPath("$.ignored.TIMED").value(1));

        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_source where tenant_id = ?", TENANT_ID)).isZero();
        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_entry where tenant_id = ?", TENANT_ID)).isZero();
    }

    @Test
    void createSource_withThePreviewSha_returns201_andPersistsTheSourceAndItsDays() throws Exception {
        byte[] content = ics(allDay("a", "20261225", "Noël"));

        mockMvc.perform(multipart("/api/tenant/calendar/sources").file(file(content)).param("name", "Écoles")
                        .param("expectedSha256", sha(content)).with(admin()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sources.length()").value(1))
                .andExpect(jsonPath("$.sources[0].name").value("Écoles"))
                .andExpect(jsonPath("$.sources[0].kind").value("ICS_FILE"))
                .andExpect(jsonPath("$.sources[0].loadedBy").value("admin-cal"))
                .andExpect(jsonPath("$.sources[0].entries[0].label").value("Noël"))
                .andExpect(jsonPath("$.sources[0].maskedUrl").doesNotExist())
                .andExpect(jsonPath("$.holidays", hasItem("2026-12-25")));

        mockMvc.perform(get("/api/tenant/calendar").with(admin()))
                .andExpect(jsonPath("$.sources[0].entryCount").value(1));
        org.assertj.core.api.Assertions.assertThat(
                jdbcTemplate.queryForObject("select content_sha256 from tenant_calendar_source where tenant_id = ?", String.class, TENANT_ID))
                .isEqualTo(sha(content));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("select url_ciphertext from tenant_calendar_source where tenant_id = ?",
                String.class, TENANT_ID)).isNull();
    }

    @Test
    void createSource_withAStaleSha_returns409_andWritesNothing() throws Exception {
        byte[] content = ics(allDay("a", "20261225", "Noël"));

        mockMvc.perform(multipart("/api/tenant/calendar/sources").file(file(content)).param("name", "Écoles")
                        .param("expectedSha256", sha(ics(allDay("a", "20261226", "Autre")))).with(admin()).with(csrf()))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_source where tenant_id = ?", TENANT_ID)).isZero();
    }

    @Test
    void createSource_withAnExistingNameIgnoringCase_returns409() throws Exception {
        byte[] content = ics(allDay("a", "20261225", "Noël"));
        createSource("Écoles", content);

        mockMvc.perform(multipart("/api/tenant/calendar/sources").file(file(content)).param("name", "ÉCOLES")
                        .param("expectedSha256", sha(content)).with(admin()).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(multipart("/api/tenant/calendar/sources/preview").file(file(content)).param("name", "écoles")
                        .with(admin()).with(csrf()))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_source where tenant_id = ?", TENANT_ID)).isEqualTo(1);
    }

    @Test
    void invalidInputs_return400() throws Exception {
        byte[] good = ics(allDay("a", "20261225", "Noël"));
        byte[] notIcs = "ceci n'est pas un calendrier".getBytes(StandardCharsets.UTF_8);
        byte[] tooBig = new byte[1024 * 1024 + 1];

        mockMvc.perform(multipart("/api/tenant/calendar/sources/preview").file(file(notIcs)).param("name", "X").with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/tenant/calendar/sources/preview").file(file(tooBig)).param("name", "X").with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/tenant/calendar/sources/preview").file(file(new byte[0])).param("name", "X").with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/tenant/calendar/sources/preview").file(file(good)).param("name", "   ").with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/tenant/calendar/sources/preview").param("name", "X").with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/tenant/calendar/sources").file(file(good)).param("name", "X").param("expectedSha256", " ")
                        .with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/tenant/calendar/sources").file(file(good)).param("name", "X").with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());

        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_source where tenant_id = ?", TENANT_ID)).isZero();
    }

    @Test
    void reloadSource_replacesOnlyItsDays_keepingManualEntriesAndOtherSources() throws Exception {
        mockMvc.perform(put("/api/tenant/calendar").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","entries":[{"start":"2026-12-24","nature":"CLOSED","label":"Pont"}]}
                        """)).andExpect(status().isOk());
        mockMvc.perform(post("/api/tenant/calendar/belgian-federal-holidays").with(admin()).with(csrf())).andExpect(status().isOk());
        java.util.UUID id = createSource("Écoles", ics(allDay("a", "20261225", "Noël"), allDay("b", "20261226", "Lendemain")));
        byte[] next = ics(allDay("b", "20261226", "Lendemain"), allDay("c", "20270104", "Rentrée"));

        mockMvc.perform(multipart("/api/tenant/calendar/sources/{id}/preview", id).file(file(next)).with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Écoles"))
                .andExpect(jsonPath("$.added[0].start").value("2027-01-04"))
                .andExpect(jsonPath("$.removed[0].start").value("2026-12-25"));
        mockMvc.perform(multipart("/api/tenant/calendar/sources/{id}/reload", id).file(file(next)).param("expectedSha256", sha(next))
                        .with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.sources[?(@.name == 'Écoles')].entryCount", hasItem(2)))
                .andExpect(jsonPath("$.sources[?(@.name == 'Écoles')].entries[0].start", hasItem("2026-12-26")))
                .andExpect(jsonPath("$.sources[?(@.kind == 'BE_FEDERAL')].entryCount", hasItem(20)))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].label").value("Pont"));

        org.assertj.core.api.Assertions.assertThat(
                jdbcTemplate.queryForObject("select content_sha256 from tenant_calendar_source where id = ?", String.class, id)).isEqualTo(sha(next));
    }

    @Test
    void reloadSource_withAStaleSha_returns409_andKeepsTheDays() throws Exception {
        byte[] first = ics(allDay("a", "20261225", "Noël"));
        java.util.UUID id = createSource("Écoles", first);
        byte[] other = ics(allDay("z", "20270104", "Rentrée"));

        mockMvc.perform(multipart("/api/tenant/calendar/sources/{id}/reload", id).file(file(other)).param("expectedSha256", sha(first))
                        .with(admin()).with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/tenant/calendar").with(admin()))
                .andExpect(jsonPath("$.sources[0].entries[0].start").value("2026-12-25"));
    }

    @Test
    void reloadingTheBelgianSourceFromAFile_returns400() throws Exception {
        String body = mockMvc.perform(post("/api/tenant/calendar/belgian-federal-holidays").with(admin()).with(csrf()))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.sources[0].id");
        byte[] content = ics(allDay("a", "20261225", "Noël"));

        mockMvc.perform(multipart("/api/tenant/calendar/sources/{id}/preview", id).file(file(content)).with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSource_returns204_andRemovesItsDays_butNotTheManualOnes() throws Exception {
        mockMvc.perform(put("/api/tenant/calendar").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"zoneId":"UTC","weekend":"SATURDAY_SUNDAY","entries":[{"start":"2026-12-24","nature":"CLOSED"}]}
                        """)).andExpect(status().isOk());
        java.util.UUID id = createSource("Écoles", ics(allDay("a", "20261225", "Noël")));

        mockMvc.perform(delete("/api/tenant/calendar/sources/{id}", id).with(admin()).with(csrf())).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tenant/calendar").with(admin()))
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.entries.length()").value(1));
        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_entry where source_id = ?", id)).isZero();
        mockMvc.perform(delete("/api/tenant/calendar/sources/{id}", id).with(admin()).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void anotherTenantsSourceIsNotFound_forPreviewReloadAndDelete_andStaysIntact() throws Exception {
        java.util.UUID foreign = java.util.UUID.randomUUID();
        jdbcTemplate.update("insert into tenant_calendar_source (id, tenant_id, name, kind) values (?, ?, 'Étrangère', 'ICS_FILE')", foreign, OTHER_TENANT);
        jdbcTemplate.update("""
                insert into tenant_calendar_entry (id, tenant_id, source_id, start_date, end_date, nature)
                values (?, ?, ?, '2026-12-01', '2026-12-01', 'CLOSED')
                """, java.util.UUID.randomUUID(), OTHER_TENANT, foreign);
        byte[] content = ics(allDay("a", "20261225", "Noël"));

        mockMvc.perform(multipart("/api/tenant/calendar/sources/{id}/preview", foreign).file(file(content)).with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/tenant/calendar/sources/{id}/reload", foreign).file(file(content)).param("expectedSha256", sha(content))
                        .with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tenant/calendar/sources/{id}", foreign).with(admin()).with(csrf())).andExpect(status().isNotFound());

        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_source where id = ?", foreign)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_entry where source_id = ?", foreign)).isEqualTo(1);
    }

    @Test
    void theSourceEndpoints_requireTheAdminRole_andACsrfToken() throws Exception {
        byte[] content = ics(allDay("a", "20261225", "Noël"));
        java.util.UUID id = java.util.UUID.randomUUID();
        RequestPostProcessor manager = user("mgr-cal", "DOSSIER_MANAGER");

        mockMvc.perform(multipart("/api/tenant/calendar/sources/preview").file(file(content)).param("name", "X").with(manager).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/tenant/calendar/sources").file(file(content)).param("name", "X").param("expectedSha256", sha(content))
                        .with(manager).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/tenant/calendar/sources/{id}/preview", id).file(file(content)).with(manager).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/tenant/calendar/sources/{id}/reload", id).file(file(content)).param("expectedSha256", sha(content))
                        .with(manager).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tenant/calendar/sources/{id}", id).with(manager).with(csrf())).andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/tenant/calendar/sources").file(file(content)).param("name", "X").param("expectedSha256", sha(content))
                        .with(admin()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tenant/calendar/sources/{id}", id).with(admin())).andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_source where tenant_id = ?", TENANT_ID)).isZero();
    }

    // ------------------------------------------------------------------ sources ICS par URL (lot 3)

    private static final String TOKEN = "TOPSECRET-TOKEN";
    private HttpServer feed;
    private final AtomicReference<byte[]> feedBody = new AtomicReference<>();
    private final AtomicInteger feedStatus = new AtomicInteger(200);
    private final AtomicInteger feedHits = new AtomicInteger();

    @AfterEach
    void stopFeed() {
        if (feed != null) {
            feed.stop(0);
        }
    }

    /** Un « agenda en ligne » local dont on change le contenu et le statut entre deux appels. */
    private String startFeed(byte[] content) throws Exception {
        feedBody.set(content);
        feed = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        feed.createContext("/", exchange -> {
            feedHits.incrementAndGet();
            byte[] body = feedBody.get();
            int status = feedStatus.get();
            exchange.sendResponseHeaders(status, status == 200 ? body.length : -1);
            if (status == 200) {
                try (java.io.OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        feed.start();
        return "http://127.0.0.1:" + feed.getAddress().getPort() + "/ical/" + TOKEN + "/basic.ics?key=" + TOKEN;
    }

    private static String urlBody(String name, String url, String sha) {
        return "{\"name\":" + (name == null ? "null" : "\"" + name + "\"") + ",\"url\":" + (url == null ? "null" : "\"" + url + "\"")
                + ",\"expectedSha256\":" + (sha == null ? "null" : "\"" + sha + "\"") + "}";
    }

    private java.util.UUID createUrlSource(String name, String url, byte[] content) throws Exception {
        String body = mockMvc.perform(post("/api/tenant/calendar/sources").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(urlBody(name, url, sha(content))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> ids = com.jayway.jsonpath.JsonPath.read(body, "$.sources[?(@.name == '" + name + "')].id");
        return java.util.UUID.fromString(ids.getFirst());
    }

    @Test
    void previewUrlSource_downloadsAndSummarises_withoutWritingOrEchoingTheUrl() throws Exception {
        byte[] content = ics(allDay("a", "20261225", "Noël"), TIMED);
        String url = startFeed(content);

        String response = mockMvc.perform(post("/api/tenant/calendar/sources/preview").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(urlBody("Agenda", url, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("ICS_URL"))
                .andExpect(jsonPath("$.sha256").value(sha(content)))
                .andExpect(jsonPath("$.added[0].start").value("2026-12-25"))
                .andExpect(jsonPath("$.ignored.TIMED").value(1))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response).doesNotContain(TOKEN);
        org.assertj.core.api.Assertions.assertThat(feedHits.get()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_source where tenant_id = ?", TENANT_ID)).isZero();
    }

    @Test
    void createUrlSource_storesTheUrlEncrypted_andOnlyEverReturnsTheMaskedForm() throws Exception {
        byte[] content = ics(allDay("a", "20261225", "Noël"));
        String url = startFeed(content);
        String masked = "http://127.0.0.1:" + feed.getAddress().getPort() + "/…";

        String created = mockMvc.perform(post("/api/tenant/calendar/sources").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(urlBody("Agenda", url, sha(content))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sources[0].kind").value("ICS_URL"))
                .andExpect(jsonPath("$.sources[0].maskedUrl").value(masked))
                .andExpect(jsonPath("$.sources[0].entries[0].label").value("Noël"))
                .andReturn().getResponse().getContentAsString();
        String listed = mockMvc.perform(get("/api/tenant/calendar").with(admin()))
                .andExpect(jsonPath("$.sources[0].maskedUrl").value(masked))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(created).doesNotContain(TOKEN, "basic.ics");
        org.assertj.core.api.Assertions.assertThat(listed).doesNotContain(TOKEN, "basic.ics");
        String stored = jdbcTemplate.queryForObject("select url_ciphertext from tenant_calendar_source where tenant_id = ?", String.class, TENANT_ID);
        org.assertj.core.api.Assertions.assertThat(stored).startsWith("enc:v1:").doesNotContain(TOKEN, "127.0.0.1", "basic.ics");
        org.assertj.core.api.Assertions.assertThat(
                jdbcTemplate.queryForObject("select masked_url from tenant_calendar_source where tenant_id = ?", String.class, TENANT_ID))
                .isEqualTo(masked);
    }

    @Test
    void createUrlSource_withAStaleShaOrATakenName_returns409_andStoresNothing() throws Exception {
        byte[] content = ics(allDay("a", "20261225", "Noël"));
        String url = startFeed(content);

        mockMvc.perform(post("/api/tenant/calendar/sources").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody("Agenda", url, sha(ics(allDay("z", "20270101", "Autre"))))))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_source where tenant_id = ?", TENANT_ID)).isZero();

        createUrlSource("Agenda", url, content);
        mockMvc.perform(post("/api/tenant/calendar/sources").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody("AGENDA", url, sha(content))))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_source where tenant_id = ?", TENANT_ID)).isEqualTo(1);
    }

    @Test
    void anUnsafeOrMalformedUrl_returns400_withoutEchoingIt_andNothingIsCalled() throws Exception {
        for (String bad : new String[] {"https://10.0.0.5/" + TOKEN, "https://169.254.169.254/latest/meta-data/" + TOKEN, "ftp://example.org/" + TOKEN,
                "file:///etc/passwd", "https://user:" + TOKEN + "@example.org/a.ics", "pas une url " + TOKEN.toLowerCase().replace("-", "")}) {
            String response = mockMvc.perform(post("/api/tenant/calendar/sources/preview").with(admin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(urlBody("Agenda", bad.replace(" ", "%20"), null)))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat(response).as(bad).doesNotContain(TOKEN).doesNotContain(TOKEN.toLowerCase().replace("-", ""));
        }
        mockMvc.perform(post("/api/tenant/calendar/sources").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody("Agenda", null, "x")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/tenant/calendar/sources").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody("Agenda", "https://example.org/a.ics", " ")))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_source where tenant_id = ?", TENANT_ID)).isZero();
    }

    @Test
    void reloadUrlSource_withoutAnUrl_readsTheStoredOne_andReplacesOnlyItsDays() throws Exception {
        mockMvc.perform(post("/api/tenant/calendar/belgian-federal-holidays").with(admin()).with(csrf())).andExpect(status().isOk());
        String url = startFeed(ics(allDay("a", "20261225", "Noël")));
        java.util.UUID id = createUrlSource("Agenda", url, feedBody.get());
        byte[] next = ics(allDay("b", "20270104", "Rentrée"));
        feedBody.set(next);

        mockMvc.perform(post("/api/tenant/calendar/sources/{id}/preview", id).with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sha256").value(sha(next)))
                .andExpect(jsonPath("$.added[0].start").value("2027-01-04"))
                .andExpect(jsonPath("$.removed[0].start").value("2026-12-25"));
        mockMvc.perform(post("/api/tenant/calendar/sources/{id}/reload", id).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody(null, null, sha(next))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.sources[?(@.name == 'Agenda')].entries[0].start", hasItem("2027-01-04")))
                .andExpect(jsonPath("$.sources[?(@.kind == 'BE_FEDERAL')].entryCount", hasItem(20)));

        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_entry where source_id = ?", id)).isEqualTo(1);
    }

    @Test
    void reloadUrlSource_withAStaleSha_returns409_andKeepsTheDays() throws Exception {
        String url = startFeed(ics(allDay("a", "20261225", "Noël")));
        java.util.UUID id = createUrlSource("Agenda", url, feedBody.get());
        String previewed = sha(feedBody.get());
        feedBody.set(ics(allDay("b", "20270104", "Rentrée")));

        mockMvc.perform(post("/api/tenant/calendar/sources/{id}/reload", id).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody(null, null, previewed)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/tenant/calendar").with(admin()))
                .andExpect(jsonPath("$.sources[0].entries[0].start").value("2026-12-25"));
    }

    @Test
    void aFailedReload_returns400_keepsTheDays_andShowsTheErrorWithoutTheUrl_untilTheNextSuccess() throws Exception {
        String url = startFeed(ics(allDay("a", "20261225", "Noël")));
        java.util.UUID id = createUrlSource("Agenda", url, feedBody.get());
        feedStatus.set(503);

        String error = mockMvc.perform(post("/api/tenant/calendar/sources/{id}/reload", id).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(urlBody(null, null, "abc")))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(error).contains("503").doesNotContain(TOKEN);

        String listed = mockMvc.perform(get("/api/tenant/calendar").with(admin()))
                .andExpect(jsonPath("$.sources[0].entries[0].start").value("2026-12-25"))
                .andExpect(jsonPath("$.sources[0].lastError").value(org.hamcrest.Matchers.containsString("503")))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(listed).doesNotContain(TOKEN);

        feedStatus.set(200);
        byte[] next = ics(allDay("b", "20270104", "Rentrée"));
        feedBody.set(next);
        mockMvc.perform(post("/api/tenant/calendar/sources/{id}/reload", id).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody(null, null, sha(next))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].lastError").doesNotExist());
    }

    @Test
    void reloadUrlSource_withANewUrl_replacesTheStoredOne() throws Exception {
        String url = startFeed(ics(allDay("a", "20261225", "Noël")));
        java.util.UUID id = createUrlSource("Agenda", url, feedBody.get());
        String before = jdbcTemplate.queryForObject("select url_ciphertext from tenant_calendar_source where id = ?", String.class, id);
        byte[] next = ics(allDay("b", "20270104", "Rentrée"));
        feedBody.set(next);
        String moved = url.replace("basic.ics", "other.ics");

        mockMvc.perform(post("/api/tenant/calendar/sources/{id}/reload", id).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody(null, moved, sha(next))))
                .andExpect(status().isOk());

        String after = jdbcTemplate.queryForObject("select url_ciphertext from tenant_calendar_source where id = ?", String.class, id);
        org.assertj.core.api.Assertions.assertThat(after).startsWith("enc:v1:").isNotEqualTo(before);
    }

    @Test
    void theKindsDoNotMix_overHttp() throws Exception {
        String url = startFeed(ics(allDay("a", "20261225", "Noël")));
        java.util.UUID urlId = createUrlSource("Agenda", url, feedBody.get());
        java.util.UUID fileId = createSource("Fichier", ics(allDay("f", "20261226", "Fichier")));

        mockMvc.perform(post("/api/tenant/calendar/sources/{id}/preview", fileId).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody(null, url, null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/tenant/calendar/sources/{id}/preview", urlId).file(file(ics(allDay("z", "20270101", "Z"))))
                        .with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherTenantsSourceIsNotFound_forUrlPreviewAndReload() throws Exception {
        java.util.UUID foreign = java.util.UUID.randomUUID();
        jdbcTemplate.update("insert into tenant_calendar_source (id, tenant_id, name, kind, masked_url) values (?, ?, 'Étrangère', 'ICS_URL', 'https://x.example/…')",
                foreign, OTHER_TENANT);
        String url = startFeed(ics(allDay("a", "20261225", "Noël")));

        mockMvc.perform(post("/api/tenant/calendar/sources/{id}/preview", foreign).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody(null, url, null)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/tenant/calendar/sources/{id}/reload", foreign).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody(null, url, "abc")))
                .andExpect(status().isNotFound());

        org.assertj.core.api.Assertions.assertThat(feedHits.get()).isZero();
    }

    @Test
    void theUrlEndpoints_requireTheAdminRole_andACsrfToken() throws Exception {
        String url = startFeed(ics(allDay("a", "20261225", "Noël")));
        java.util.UUID id = java.util.UUID.randomUUID();
        RequestPostProcessor manager = user("mgr-cal", "DOSSIER_MANAGER");

        mockMvc.perform(post("/api/tenant/calendar/sources/preview").with(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody("X", url, null))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tenant/calendar/sources").with(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody("X", url, "abc"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tenant/calendar/sources/{id}/preview", id).with(manager).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tenant/calendar/sources/{id}/reload", id).with(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody(null, null, "abc"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tenant/calendar/sources").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody("X", url, "abc"))).andExpect(status().isForbidden());

        org.assertj.core.api.Assertions.assertThat(feedHits.get()).isZero();
        org.assertj.core.api.Assertions.assertThat(count("select count(*) from tenant_calendar_source where tenant_id = ?", TENANT_ID)).isZero();
    }

    @Test
    void theShaDescribesThePeriodsRead_notTheBytes_soAnAgendaThatRegeneratesItsStampKeepsIt() throws Exception {
        byte[] first = ics(allDay("a", "20261225", "Noël"));
        byte[] regenerated = new String(first, StandardCharsets.UTF_8).replace("DTSTAMP:20260101T000000Z", "DTSTAMP:20261107T101010Z")
                .replace("PRODID:-//Test//FR", "PRODID:-//Regenere " + System.nanoTime() + "//FR").getBytes(StandardCharsets.UTF_8);

        org.assertj.core.api.Assertions.assertThat(sha(regenerated)).isEqualTo(sha(first));
        org.assertj.core.api.Assertions.assertThat(sha(ics(allDay("a", "20261226", "Noël")))).isNotEqualTo(sha(first));
    }

    @Test
    void anUrlFeedThatRegeneratesItsStampAtEveryDownload_canStillBeCreated() throws Exception {
        byte[] content = ics(allDay("a", "20261225", "Noël"));
        String url = startFeed(content);
        String previewed = sha(content);
        feedBody.set(new String(content, StandardCharsets.UTF_8).replace("DTSTAMP:20260101T000000Z", "DTSTAMP:20261107T101010Z")
                .getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/api/tenant/calendar/sources").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(urlBody("Agenda", url, previewed)))
                .andExpect(status().isCreated());
    }

    private static RequestPostProcessor admin() {
        return user("admin-cal", "TENANT_ADMIN");
    }

    private static RequestPostProcessor user(String userId, String... roles) {
        return oidcLogin()
                .idToken(token -> token
                        .subject(userId)
                        .claim("tenant_id", TENANT_ID)
                        .claim("email", userId + "@example.com")
                        .claim("name", userId))
                .authorities(Arrays.stream(roles)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new));
    }
}
