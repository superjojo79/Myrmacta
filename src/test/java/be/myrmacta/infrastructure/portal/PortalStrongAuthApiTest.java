package be.myrmacta.infrastructure.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.infrastructure.web.portal.FixedClockPortalRateLimiterConfig;
import be.myrmacta.support.InMemoryOidcProvider;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §104 (issue #210, lot B) — authentification forte d'un tiers de bout en bout : vrai moteur, vraie base, vraie
 * chaîne portail, client OIDC direct contre un fournisseur en mémoire ({@code private_key_jwt}, PKCE, ID token signé).
 * Vue réduite (T3b), preuve d'identité (D-BEAUTH-PROOF), premier enregistrement du NRN (T6), refus d'une autre personne
 * (T5), trace pour l'instructeur (T8), et le NRN absent des journaux, des variables de process et de l'outbox (N2).
 */
@AutoConfigureMockMvc
@Import(FixedClockPortalRateLimiterConfig.class)
@TestPropertySource(properties = {
        "app.portal.strong-auth.enabled=true",
        "app.portal.strong-auth.providers.fas.client-id=" + InMemoryOidcProvider.CLIENT_ID,
        "app.portal.strong-auth.providers.fas.claims.nrn=egovNRN",
        "app.portal.strong-auth.providers.fas.claims.given-name=givenName",
        "app.portal.strong-auth.providers.fas.claims.family-name=surname",
        "app.portal.strong-auth.providers.fas.claims.level=loa",
        "app.portal.strong-auth.providers.fas.levels.HIGH=high",
        "app.portal.strong-auth.providers.fas.levels.SUBSTANTIAL=substantial"})
@ExtendWith(OutputCaptureExtension.class)
class PortalStrongAuthApiTest extends AbstractPortalEngineTest {

    private static final String NRN = "85073003328";
    private static final String OTHER_NRN = "90011512462";
    private static final String TYPE = "IDENTITE_SMOKE";
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();
    private static final InMemoryOidcProvider PROVIDER = new InMemoryOidcProvider();

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry registry) throws Exception {
        Path dir = Files.createTempDirectory("portal-strong-auth");
        Path key = PROVIDER.writeInstanceKey(dir);
        registry.add("app.portal.strong-auth.providers.fas.issuer-uri", PROVIDER::issuer);
        registry.add("app.portal.strong-auth.keys.signing-key-file", key::toString);
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.close();
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DossierTypeRepository typeRepository;

    private UUID dossierId;
    private String token;

    @BeforeEach
    void identityTask() {
        ensureSchema();
        if (typeRepository.findByCode("tenant_dev", TYPE).isEmpty()) {
            typeRepository.save(DossierType.inherit(UUID.randomUUID(), "tenant_dev", TYPE, "Identité", "p", true, false,
                    List.of(new FieldDefinition("contactEmail", "E-mail", FieldType.STRING, false, null, null, null),
                            new FieldDefinition("nrnDemandeur", "NRN du demandeur", FieldType.STRING, false, null, null,
                                    null, true, null, null, null, null, null, null, false, null, null, false))));
        }
    }

    private void startTask(String expectedNrn) {
        String recipient = "demandeur-" + System.nanoTime() + "@example.org";
        Map<String, Object> fields = new HashMap<>(Map.of("contactEmail", recipient));
        if (expectedNrn != null) {
            fields.put("nrnDemandeur", expectedNrn);
        }
        Instant now = Instant.now();
        Dossier dossier = dossierRepository.save(new Dossier(UUID.randomUUID(), "tenant_dev", "REF-" + System.nanoTime(),
                TYPE, "Dossier identité", be.myrmacta.domain.dossier.model.DossierStatus.OUVERT, fields, null, 0L, "test",
                now, null, null, null, null, null));
        dossierId = dossier.id();
        Map<String, Object> variables = new HashMap<>();
        variables.put("dossierId", dossierId.toString());
        runtimeService.startProcessInstanceByKey("portalIdentitySmoke", variables);
        token = sendAndCaptureToken(recipient);
    }

    private static RequestPostProcessor ip() {
        int n = IP_SEQUENCE.getAndIncrement();
        String addr = "10.30." + (n / 250 % 250) + "." + (1 + n % 250);
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }

    /** Démarre l'authentification, simule le fournisseur et suit le retour ; renvoie l'en-tête Location. */
    private String authenticateAs(String nrn) throws Exception {
        MvcResult started = mockMvc.perform(post("/api/portal/identity/start").with(ip())
                        .header("X-Portal-Token", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"fas\"}"))
                .andExpect(status().isOk()).andReturn();
        URI authorization = URI.create(objectMapper.readTree(started.getResponse().getContentAsString())
                .get("authorizationUrl").asString());
        assertThat(authorization.toString()).startsWith(PROVIDER.issuer() + "/auth?");
        Map<String, String> back = PROVIDER.authorize(authorization, "code-" + UUID.randomUUID(),
                Map.of("egovNRN", nrn, "givenName", "Alice", "surname", "Martin", "loa", "high"));
        return mockMvc.perform(get("/api/portal/identity/callback").with(ip())
                        .param("state", back.get("state")).param("code", back.get("code")))
                .andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
    }

    @Test
    void theTokenAloneShowsTheReducedViewAndAllowsNoAction_T3b() throws Exception {
        startTask(null);

        MvcResult view = mockMvc.perform(get("/api/portal/task").with(ip()).header("X-Portal-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskName").value("Avis du demandeur"))
                .andExpect(jsonPath("$.identity.verified").value(false))
                .andExpect(jsonPath("$.identity.providers[0].id").value("fas"))
                .andReturn();
        JsonNode body = objectMapper.readTree(view.getResponse().getContentAsString());
        assertThat(body.get("instructions").isNull()).isTrue();
        assertThat(body.get("form").get("fields")).isEmpty();
        assertThat(view.getResponse().getContentAsString()).doesNotContain("Merci de confirmer");

        mockMvc.perform(post("/api/portal/task/submit").with(ip()).header("X-Portal-Token", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":{\"disponible\":true}}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/portal/task/attachments").file(new MockMultipartFile("file", "a.pdf",
                        "application/pdf", "%PDF-1.7\n".getBytes())).with(ip()).header("X-Portal-Token", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void aFirstAuthenticationWritesTheNumberAndLeavesOnlyATrace_T6_T8_N2(CapturedOutput output) throws Exception {
        startTask(null);

        String location = authenticateAs(NRN);
        assertThat(location).startsWith("https://ff.example.org/tenant-a/portal/#identity:");
        String proof = location.substring(location.indexOf("#identity:") + "#identity:".length());

        mockMvc.perform(get("/api/portal/task").with(ip()).header("X-Portal-Identity", proof))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructions").value("Merci de confirmer votre demande."))
                .andExpect(jsonPath("$.identity.verified").value(true))
                .andExpect(jsonPath("$.identity.name").value("Alice Martin"));
        mockMvc.perform(post("/api/portal/task/submit").with(ip()).header("X-Portal-Identity", proof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":{\"disponible\":true}}"))
                .andExpect(status().isNoContent());

        assertThat(dossierRepository.findById(dossierId).orElseThrow().fields()).containsEntry("nrnDemandeur", NRN);
        assertThat(jdbcTemplate.queryForList("select nrn_enc, proof_hash, match_result from portal_link_identity "
                + "where portal_link_id = ?", link().id())).singleElement().satisfies(row -> {
                    assertThat(row.get("nrn_enc")).isNull();
                    assertThat(row.get("proof_hash")).isNull();
                    assertThat(row.get("match_result")).isEqualTo("PREMIER_ENREGISTREMENT");
                });
        mockMvc.perform(get("/api/portal/task").with(ip()).header("X-Portal-Identity", proof))
                .andExpect(status().isNotFound());

        String admin = mockMvc.perform(get("/api/dossiers/{id}/portal-links", dossierId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links[0].identity.result").value("PREMIER_ENREGISTREMENT"))
                .andExpect(jsonPath("$.links[0].identity.level").value("HIGH"))
                .andExpect(jsonPath("$.links[0].identity.familyName").value("Martin"))
                .andReturn().getResponse().getContentAsString();
        assertThat(admin).doesNotContain(NRN);

        // N2 — le NRN n'est que dans le champ sensible du dossier.
        assertThat(output.getAll()).doesNotContain(NRN).doesNotContain(proof);
        assertThat(jdbcTemplate.queryForObject("select count(*) from act_hi_varinst where text_ like ? or text2_ like ?",
                Integer.class, "%" + NRN + "%", "%" + NRN + "%")).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from outbox_event where cast(payload as text) like ?",
                Integer.class, "%" + NRN + "%")).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from notification_outbox where cast(payload_json as text) like ?",
                Integer.class, "%" + NRN + "%")).isZero();
    }

    @Test
    void anotherPersonIsRefusedNeutrallyAndNothingIsKept_T5_T10() throws Exception {
        startTask(NRN);

        String location = authenticateAs(OTHER_NRN);

        assertThat(location).isEqualTo("https://ff.example.org/tenant-a/portal/#identity-refused");
        assertThat(jdbcTemplate.queryForObject("select count(*) from portal_link_identity where portal_link_id = ?",
                Integer.class, link().id())).isZero();
    }

    @Test
    void theExpectedPersonSubmitsWithoutRewritingTheField_T5() throws Exception {
        startTask("85.07.30-033.28");

        String location = authenticateAs(NRN);
        String proof = location.substring(location.indexOf("#identity:") + "#identity:".length());
        mockMvc.perform(post("/api/portal/task/submit").with(ip()).header("X-Portal-Identity", proof)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":{\"disponible\":true}}"))
                .andExpect(status().isNoContent());

        assertThat(dossierRepository.findById(dossierId).orElseThrow().fields())
                .containsEntry("nrnDemandeur", "85.07.30-033.28");
    }

    @Test
    void aReplayedCallbackIsRefused() throws Exception {
        startTask(null);
        MvcResult started = mockMvc.perform(post("/api/portal/identity/start").with(ip())
                        .header("X-Portal-Token", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"fas\"}"))
                .andReturn();
        URI authorization = URI.create(objectMapper.readTree(started.getResponse().getContentAsString())
                .get("authorizationUrl").asString());
        Map<String, String> back = PROVIDER.authorize(authorization, "code-replay",
                Map.of("egovNRN", NRN, "loa", "high"));

        mockMvc.perform(get("/api/portal/identity/callback").with(ip()).param("state", back.get("state"))
                .param("code", back.get("code"))).andExpect(status().isFound());
        String replay = mockMvc.perform(get("/api/portal/identity/callback").with(ip()).param("state", back.get("state"))
                .param("code", back.get("code"))).andReturn().getResponse().getHeader("Location");

        assertThat(replay).endsWith("#identity-refused");
    }

    /** T11 — la JWKS publiée par l'instance, publique et sans partie privée. */
    @Test
    void theInstancePublishesItsPublicKeys() throws Exception {
        String jwks = mockMvc.perform(get("/api/portal/identity/jwks").with(ip()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(jwks).get("keys")).hasSize(1);
        assertThat(jwks).doesNotContain("\"d\"");
    }

    private be.myrmacta.domain.workflow.portal.model.PortalLink link() {
        return portalLinkRepository.findByDossierId(dossierId).getFirst();
    }

    private static RequestPostProcessor admin() {
        return oidcLogin()
                .idToken(t -> t.subject("portal-admin").claim("tenant_id", "tenant_dev").claim("name", "Admin"))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
    }
}
