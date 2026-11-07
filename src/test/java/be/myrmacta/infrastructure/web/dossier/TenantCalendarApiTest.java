package be.myrmacta.infrastructure.web.dossier;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.Arrays;
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
