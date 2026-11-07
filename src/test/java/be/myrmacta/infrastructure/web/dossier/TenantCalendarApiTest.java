package be.myrmacta.infrastructure.web.dossier;

import static org.hamcrest.Matchers.hasItem;
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

/** TODO §120 (D-CAL-ZONE, D-CAL-HOLIDAYS) — calendrier du tenant, réservé à TENANT_ADMIN. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(OidcClientTestConfiguration.class)
class TenantCalendarApiTest {

    private static final String TENANT_ID = "tenant_dev";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearCalendar() {
        jdbcTemplate.update("DELETE FROM tenant_calendar WHERE tenant_id = ?", TENANT_ID);
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
