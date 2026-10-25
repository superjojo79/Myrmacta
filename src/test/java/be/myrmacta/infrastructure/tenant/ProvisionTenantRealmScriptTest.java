package be.myrmacta.infrastructure.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TODO §104 (issue #210, D-BEAUTH-PROVISION) — structure du realm par tenant généré par
 * {@code scripts/provision-tenant.sh --realm-per-tenant}. La preuve sur un vrai Keycloak est
 * {@code scripts/keycloak-realm-smoke.sh} (D-BEAUTH-KC-SMOKE) ; ce test fige ce que le fichier doit contenir et ne pas
 * contenir.
 */
class ProvisionTenantRealmScriptTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path dir;

    private record Run(int exit, String stderr) {}

    private Run provision(String... options) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("bash", Path.of("scripts", "provision-tenant.sh").toString(),
                "tenant_b", "--instance-dir", dir.toString(), "--port", "8082"));
        command.addAll(List.of(options));
        ProcessBuilder builder = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().putAll(Map.of(
                "TENANT_DB_PASSWORD", "pw-de-test",
                "KEYCLOAK_URL", "https://auth.example.be",
                "PUBLIC_BASE_URL", "https://flowforge.example.be"));
        Process process = builder.start();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Run(process.waitFor(), stderr);
    }

    private JsonNode realm() throws IOException {
        return JSON.readTree(dir.resolve("tenant_b.realm.json").toFile());
    }

    private static List<JsonNode> list(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }

    private static JsonNode byField(JsonNode array, String field, String value) {
        return list(array).stream().filter(n -> value.equals(n.path(field).asText())).findFirst().orElseThrow(
                () -> new AssertionError(field + "=" + value + " absent"));
    }

    @Test
    void withoutTheOptionNothingChanges() throws Exception {
        Run run = provision();
        assertThat(run.exit()).as(run.stderr()).isZero();
        assertThat(dir.resolve("tenant_b.realm.json")).doesNotExist();
        assertThat(Files.readString(dir.resolve("tenant_b.keycloak.sh"))).contains("flowforge-m2m-tenant_b")
                .doesNotContain("realm.json");
    }

    @Test
    void theRealmOfTheTenantCarriesTheBffClientRolesAndProfile() throws Exception {
        Run run = provision("--realm-per-tenant", "--idp", "fas", "--idp", "itsme");
        assertThat(run.exit()).as(run.stderr()).isZero();
        JsonNode realm = realm();

        assertThat(realm.path("realm").asText()).isEqualTo("flowforge-tenant_b");
        assertThat(realm.path("sslRequired").asText()).isEqualTo("external");
        assertThat(realm.path("registrationAllowed").asBoolean(true)).isFalse();

        JsonNode bff = byField(realm.path("clients"), "clientId", "mon-app");
        assertThat(bff.path("publicClient").asBoolean(true)).isFalse();
        assertThat(bff.has("secret")).as("aucun secret dans le fichier").isFalse();
        assertThat(list(bff.path("redirectUris")).stream().map(JsonNode::asText))
                .containsExactly("https://flowforge.example.be/tenant_b/*");
        assertThat(list(bff.path("protocolMappers")).stream().map(m -> m.path("config").path("claim.name").asText()))
                .contains("realm_access.roles", "tenant_id");
        assertThat(bff.has("authenticationFlowBindingOverrides")).as("mode both : flux standard").isFalse();

        assertThat(list(realm.path("roles").path("realm")).stream().map(r -> r.path("name").asText()))
                .contains("TENANT_ADMIN", "WORKFLOW_ADMIN", "DOSSIER_MANAGER", "DOSSIER_VIEWER");
        // A2 : un compte créé par un fournisseur ne reçoit aucun rôle FlowForge.
        assertThat(realm.path("defaultRole").path("composites").path("realm").toString())
                .doesNotContain("TENANT_ADMIN", "WORKFLOW_ADMIN", "DOSSIER_MANAGER", "DOSSIER_VIEWER");

        JsonNode profile = JSON.readTree(realm.path("components").path("org.keycloak.userprofile.UserProfileProvider")
                .get(0).path("config").path("kc.user.profile.config").get(0).asText());
        JsonNode tenantAttribute = byField(profile.path("attributes"), "name", "tenant_id");
        assertThat(tenantAttribute.path("permissions").path("edit").toString()).isEqualTo("[\"admin\"]");
        assertThat(tenantAttribute.path("permissions").path("view").toString()).isEqualTo("[\"admin\"]");
        assertThat(byField(profile.path("attributes"), "name", "email").has("required"))
                .as("FAS peut ne pas fournir d'e-mail").isFalse();
    }

    /** A2, A5 : les fournisseurs ne posent ni NRN ni tenant_id sur le compte d'un agent ; aucun secret dans le fichier. */
    @Test
    void theProvidersMapOnlyTheNameAndNeverTheNrnNorTheTenant() throws Exception {
        Run run = provision("--realm-per-tenant", "--idp", "fas", "--idp", "itsme");
        assertThat(run.exit()).as(run.stderr()).isZero();
        JsonNode realm = realm();

        assertThat(list(realm.path("identityProviders")).stream().map(p -> p.path("alias").asText()))
                .containsExactly("fas", "itsme");
        JsonNode itsme = byField(realm.path("identityProviders"), "alias", "itsme");
        assertThat(itsme.path("config").path("clientAuthMethod").asText()).isEqualTo("private_key_jwt");
        assertThat(itsme.path("config").path("pkceEnabled").asText()).isEqualTo("true");
        for (JsonNode provider : list(realm.path("identityProviders"))) {
            assertThat(provider.path("config").has("clientSecret")).isFalse();
            assertThat(provider.path("firstBrokerLoginFlowAlias").asText()).isEqualTo("first broker login");
        }
        List<String> attributes = list(realm.path("identityProviderMappers")).stream()
                .map(m -> m.path("config").path("user.attribute").asText()).toList();
        assertThat(attributes).isNotEmpty().allMatch(a -> List.of("firstName", "lastName", "email").contains(a));
        assertThat(realm.toString()).doesNotContainIgnoringCase("nrn").doesNotContain("NationalNumber");
    }

    /** D-BEAUTH-IDP-ONLY : flux sans mot de passe sur le client BFF, sauf pour le rôle « secours ». */
    @Test
    void idpOnlyBindsADedicatedFlowToTheBffClientWithAPasswordPathForRescueAccountsOnly() throws Exception {
        Run run = provision("--realm-per-tenant", "--idp", "fas", "--password-login", "idp-only");
        assertThat(run.exit()).as(run.stderr()).isZero();
        JsonNode realm = realm();

        assertThat(realm.path("resetPasswordAllowed").asBoolean(true)).isFalse();
        assertThat(list(realm.path("roles").path("realm")).stream().map(r -> r.path("name").asText())).contains("secours");
        JsonNode flow = byField(realm.path("authenticationFlows"), "alias", "browser-fournisseurs");
        JsonNode bff = byField(realm.path("clients"), "clientId", "mon-app");
        assertThat(bff.path("authenticationFlowBindingOverrides").path("browser").asText()).isEqualTo(flow.path("id").asText());
        assertThat(list(flow.path("authenticationExecutions")).stream().map(e -> e.path("authenticator").asText()))
                .contains("auth-cookie", "identity-provider-redirector");

        String flows = realm.path("authenticationFlows").toString();
        assertThat(flows).contains("auth-username-form", "conditional-user-role", "deny-access-authenticator",
                "auth-password-form", "auth-otp-form");
        assertThat(list(realm.path("authenticatorConfig")).stream().map(c -> c.path("config").toString()))
                .anySatisfy(c -> assertThat(c).contains("\"condUserRole\":\"secours\"", "\"negate\":\"true\""))
                .anySatisfy(c -> assertThat(c).contains("\"condUserRole\":\"secours\"", "\"negate\":\"false\""));
        assertThat(Files.readString(dir.resolve("tenant_b.keycloak.sh"))).contains("SECOURS_PASSWORD", "CONFIGURE_TOTP");
    }

    @Test
    void refusesAnUnknownProviderAnUnknownModeAndMissingUrls() throws Exception {
        assertThat(provision("--realm-per-tenant", "--idp", "google").exit()).isNotZero();
        assertThat(provision("--realm-per-tenant", "--password-login", "jamais").exit()).isNotZero();
        assertThat(provision("--idp", "fas").exit()).as("--idp sans --realm-per-tenant").isNotZero();
        assertThat(dir.resolve("tenant_b.realm.json")).doesNotExist();
    }
}
