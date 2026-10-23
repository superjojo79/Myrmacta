package com.flowforge.infrastructure.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.flowforge.FlowForgeApplication;
import com.flowforge.support.EmbeddedPostgresSupport;
import com.flowforge.support.OidcClientTestConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * TODO §95 (P10, risque 3 de la revue du 22 octobre) : deux instances FlowForge réelles (tenants A et B) partagent
 * une même base PostgreSQL, provisionnée par {@code scripts/provision-tenant.sh} — le SQL de production, pas une
 * copie. Complète {@link TenantRoleIsolationIT} (rôles créés à la main) et {@link TenantSchemaIsolationIT} (un seul
 * contexte) : ici chaque instance migre son propre schéma avec son propre rôle, et ni la base ni l'API ne laissent
 * l'une atteindre l'autre.
 */
class TenantInstancesIsolationIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private static final String TENANT_A = "iso_a_" + SUFFIX;
    private static final String TENANT_B = "iso_b_" + SUFFIX;
    private static final String PASSWORD = "iso-pass-" + SUFFIX;

    private static String databaseUrl;
    private static ConfigurableApplicationContext instanceA;
    private static ConfigurableApplicationContext instanceB;

    @BeforeAll
    static void provisionTwoTenantsAndStartTheirInstances() throws Exception {
        databaseUrl = EmbeddedPostgresSupport.newApplicationDatabase().jdbcUrl();
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute(provisioningSql(TENANT_A));
            statement.execute(provisioningSql(TENANT_B));
        }
        instanceA = start(TENANT_A);
        instanceB = start(TENANT_B);
    }

    @AfterAll
    static void stopInstancesAndDropRoles() {
        for (ConfigurableApplicationContext instance : new ConfigurableApplicationContext[] {instanceA, instanceB}) {
            if (instance != null) {
                instance.close();
            }
        }
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            for (String tenant : List.of(TENANT_A, TENANT_B)) {
                statement.execute("DROP SCHEMA IF EXISTS " + tenant + " CASCADE");
                statement.execute("DROP OWNED BY flowforge_" + tenant);
                statement.execute("DROP ROLE IF EXISTS flowforge_" + tenant);
            }
        } catch (SQLException ignored) {
            // nettoyage au mieux : la base est propre à ce test
        }
    }

    @Test
    void eachInstanceMigratesItsOwnSchemaWithItsOwnRole() {
        Map.of(TENANT_A, instanceA, TENANT_B, instanceB).forEach((tenant, instance) -> {
            JdbcTemplate jdbc = instance.getBean(JdbcTemplate.class);

            assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("flowforge_" + tenant);
            assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(tenant);
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'dossier'",
                            Integer.class, tenant))
                    .isEqualTo(1);
        });
    }

    @Test
    void theRoleOfTenantA_cannotReadTheSchemaOfTenantB() throws SQLException {
        try (Connection asA = DriverManager.getConnection(databaseUrl, "flowforge_" + TENANT_A, PASSWORD);
                Statement statement = asA.createStatement()) {
            assertThatThrownBy(() -> statement.executeQuery("SELECT COUNT(*) FROM " + TENANT_B + ".dossier"))
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo("42501"); // insufficient_privilege
        }
    }

    @Test
    void dataWrittenByInstanceA_isInvisibleToInstanceB() {
        JdbcTemplate jdbcA = instanceA.getBean(JdbcTemplate.class);
        JdbcTemplate jdbcB = instanceB.getBean(JdbcTemplate.class);
        jdbcA.execute("CREATE TABLE isolation_probe (label text)");
        jdbcA.update("INSERT INTO isolation_probe VALUES ('écrit par A')");

        assertThat(jdbcB.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'isolation_probe'",
                        Integer.class))
                .as("B ne voit même pas la table de A")
                .isZero();
    }

    @Test
    void aMachineTokenForTenantB_isRefusedByInstanceA() throws Exception {
        MockMvc mvcA = MockMvcBuilders.webAppContextSetup((WebApplicationContext) instanceA)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        assertThat(callMcp(mvcA, TENANT_B)).as("jeton tenant_id=B sur l'instance A").isEqualTo(403);
        assertThat(callMcp(mvcA, TENANT_A)).as("jeton tenant_id=A sur l'instance A").isEqualTo(200);
    }

    private static int callMcp(MockMvc mvc, String tenantClaim) throws Exception {
        return mvc.perform(post("/api/m2m/mcp")
                        .with(jwt().jwt(b -> b.claim("sub", "agent").claim("tenant_id", tenantClaim)
                                        .claim("roles", List.of("API_SERVICE")))
                                .authorities(new SimpleGrantedAuthority("API_SERVICE")))
                        .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
                                "capabilities":{},"clientInfo":{"name":"isolation-test","version":"0.1.0"}}}"""))
                .andReturn().getResponse().getStatus();
    }

    private static ConfigurableApplicationContext start(String tenant) {
        SpringApplication application = new SpringApplication(FlowForgeApplication.class, OidcClientTestConfiguration.class);
        return application.run(
                "--test.embedded-postgres.enabled=false",
                "--spring.datasource.url=" + databaseUrl,
                "--spring.datasource.username=flowforge_" + tenant,
                "--spring.datasource.password=" + PASSWORD,
                "--app.tenant.id=" + tenant,
                "--server.port=0");
    }

    /** Le SQL que l'exploitant applique en production (docs/guides/DEPLOIEMENT.md §4). */
    private static String provisioningSql(String tenant) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("bash", Path.of("scripts", "provision-tenant.sh").toString(), tenant)
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        builder.environment().put("TENANT_DB_PASSWORD", PASSWORD);
        Process process = builder.start();
        String sql = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as("scripts/provision-tenant.sh " + tenant).isZero();
        return sql;
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(
                databaseUrl, EmbeddedPostgresSupport.adminUsername(), EmbeddedPostgresSupport.adminPassword());
    }
}
