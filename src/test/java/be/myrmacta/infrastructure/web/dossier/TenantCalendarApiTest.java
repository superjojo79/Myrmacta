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
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import org.springframework.mock.web.MockMultipartFile;
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

    private static String sha(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
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
                .andExpect(jsonPath("$.sha256").value(sha(content)))
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
