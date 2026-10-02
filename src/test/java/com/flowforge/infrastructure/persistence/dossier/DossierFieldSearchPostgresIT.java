package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldCondition;
import com.flowforge.domain.dossier.model.FieldCondition.Op;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.model.InvalidDossierSearchException;
import com.flowforge.domain.dossier.model.SearchDossiersQuery;
import com.flowforge.domain.dossier.port.in.SearchDossiersUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import com.flowforge.support.PostgresTestContainerSupport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * TODO §81 (D2) — recherche sur champs dynamiques sur un vrai PostgreSQL (jsonb, opérateur {@code @>}, index GIN). Ignoré sans
 * Docker ni {@code FLOWFORGE_TEST_PG_URL} ; exécuté en CI (Testcontainers) et en local avec un serveur externe.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
@EnabledIf("postgresAvailable")
class DossierFieldSearchPostgresIT {

    private static final String TENANT = "tenant_dev";
    private static final String SCHEMA = "d2_search_" + UUID.randomUUID().toString().replace("-", "");

    static boolean postgresAvailable() {
        return PostgresTestContainerSupport.isDockerAvailable();
    }

    @BeforeAll
    static void schema() {
        PostgresTestContainerSupport.createSchema(SCHEMA);
    }

    @org.junit.jupiter.api.AfterAll
    static void dropSchema() {
        if (postgresAvailable()) {
            PostgresTestContainerSupport.dropSchema(SCHEMA);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (!postgresAvailable()) {
            return;
        }
        PostgresTestContainerSupport.createSchema(SCHEMA);
        String url = PostgresTestContainerSupport.jdbcUrl();
        registry.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", PostgresTestContainerSupport::adminUsername);
        registry.add("spring.datasource.password", PostgresTestContainerSupport::adminPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Flowable détecte ses tables par les métadonnées JDBC, tous schémas confondus : on lui donne le schéma du test.
        registry.add("flowable.database-schema", () -> SCHEMA);
        registry.add("flowable.database-schema-update", () -> "true");
    }

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private DossierTypeRepository typeRepository;

    @Autowired
    private SearchDossiersUseCase search;

    @Autowired
    private JdbcTemplate jdbc;

    private static FieldDefinition searchable(String name, FieldType type, List<String> options) {
        return new FieldDefinition(name, name, type, false, null, options, null, false, null, null, null, null, null, null, true);
    }

    private String seed(String typeCode) {
        typeRepository.save(new DossierType(UUID.randomUUID(), TENANT, typeCode, typeCode, "wf", true, true,
                List.of(searchable("contrat", FieldType.STRING, null),
                        searchable("montant", FieldType.NUMBER, null),
                        searchable("echeance", FieldType.DATE, null),
                        searchable("urgent", FieldType.BOOLEAN, null),
                        searchable("categorie", FieldType.SELECT, List.of("A", "B", "C")),
                        new FieldDefinition("note", "note", FieldType.STRING, false, null, null, null, false),
                        new FieldDefinition("iban", "iban", FieldType.STRING, false, null, null, null, true)),
                null, false, null, "t", "t"));
        save(typeCode, "D1", Map.of("contrat", "C-42", "montant", 5000, "echeance", "2026-03-01", "urgent", true, "categorie", "A", "note", "x"));
        save(typeCode, "D2", Map.of("contrat", "C-43", "montant", 12000.5, "echeance", "2026-06-15", "urgent", false, "categorie", "B"));
        save(typeCode, "D3", Map.of("contrat", "c-420", "montant", 99, "echeance", "2026-12-31", "categorie", "C", "iban", "FR76SECRET"));
        save(typeCode, "D4", Map.of("note", "rien"));
        return typeCode;
    }

    private void save(String type, String label, Map<String, Object> fields) {
        Instant now = Instant.parse("2099-01-01T00:00:00Z");
        dossierRepository.save(new Dossier(UUID.randomUUID(), TENANT, "REF-" + type + "-" + label, type, label,
                DossierStatus.OUVERT, fields, null, 0, "u", now, "u", now, null, null, null));
    }

    private Set<String> labels(String type, FieldCondition... conditions) {
        return search.search(TENANT, SearchDossiersQuery.of(null, type, null, 0, 50, List.of(conditions)), AccessContext.system("d2-it"))
                .content().stream().map(i -> i.label()).collect(Collectors.toSet());
    }

    private static FieldCondition c(String field, Op op, String... values) {
        return new FieldCondition(field, op, List.of(values));
    }

    @Test
    void equalityInRangesPrefixAndCombination() {
        String type = seed("D2A" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        assertThat(labels(type, c("contrat", Op.EQ, "C-42"))).containsExactly("D1");
        assertThat(labels(type, c("contrat", Op.IN, "C-42", "C-43"))).containsExactlyInAnyOrder("D1", "D2");
        assertThat(labels(type, c("contrat", Op.PREFIX, "c-4"))).containsExactlyInAnyOrder("D1", "D2", "D3");
        assertThat(labels(type, c("montant", Op.EQ, "5000.0"))).containsExactly("D1");
        assertThat(labels(type, c("montant", Op.GTE, "5000"))).containsExactlyInAnyOrder("D1", "D2");
        assertThat(labels(type, c("montant", Op.GTE, "100"), c("montant", Op.LTE, "6000"))).containsExactly("D1");
        assertThat(labels(type, c("echeance", Op.GTE, "2026-06-01"), c("echeance", Op.LTE, "2026-12-30"))).containsExactly("D2");
        assertThat(labels(type, c("urgent", Op.EQ, "true"))).containsExactly("D1");
        assertThat(labels(type, c("categorie", Op.IN, "B", "C"))).containsExactlyInAnyOrder("D2", "D3");
        assertThat(labels(type, c("contrat", Op.PREFIX, "c-4"), c("categorie", Op.EQ, "C"))).containsExactly("D3");
    }

    @Test
    void userInputIsNeverInterpretedAsSqlOrLikePattern() {
        String type = seed("D2B" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        assertThat(labels(type, c("contrat", Op.EQ, "C-42' OR '1'='1"))).isEmpty();
        assertThat(labels(type, c("contrat", Op.PREFIX, "%"))).isEmpty();
        assertThat(labels(type, c("contrat", Op.PREFIX, "_-42"))).isEmpty();
        assertThat(labels(type, c("contrat", Op.EQ, "\"}, \"x\":{"))).isEmpty();
    }

    @Test
    void aDateStoredAsAnArrayOrAnIntegerNeverMatchesATextRange_reviewD2() {
        String type = seed("D2D" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        save(type, "ARR", Map.of("echeance", List.of(2026, 6, 15)));
        save(type, "INT", Map.of("echeance", 20260615));
        assertThat(labels(type, c("echeance", Op.GTE, "2026-06-01"))).containsExactlyInAnyOrder("D2", "D3");
    }

    @Test
    void refusesWhatIsNotSearchableOrWellFormed() {
        String type = seed("D2C" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        assertThatThrownBy(() -> labels(type, c("note", Op.EQ, "x"))).isInstanceOf(InvalidDossierSearchException.class).hasMessageContaining("not searchable");
        assertThatThrownBy(() -> labels(type, c("iban", Op.EQ, "FR76SECRET"))).isInstanceOf(InvalidDossierSearchException.class);
        assertThatThrownBy(() -> labels(type, c("inconnu", Op.EQ, "x"))).isInstanceOf(InvalidDossierSearchException.class).hasMessageContaining("not defined");
        assertThatThrownBy(() -> labels(type, c("montant", Op.PREFIX, "5"))).isInstanceOf(InvalidDossierSearchException.class).hasMessageContaining("not supported");
        assertThatThrownBy(() -> labels(type, c("montant", Op.GTE, "abc"))).isInstanceOf(InvalidDossierSearchException.class);
        assertThatThrownBy(() -> labels(type, c("echeance", Op.GTE, "2026-13-45"))).isInstanceOf(InvalidDossierSearchException.class);
        assertThatThrownBy(() -> labels(type, c("categorie", Op.EQ, "Z"))).isInstanceOf(InvalidDossierSearchException.class);
        assertThatThrownBy(() -> labels(type, c("urgent", Op.EQ, "peut-etre"))).isInstanceOf(InvalidDossierSearchException.class);
        // sans type de dossier : pas de cadrage
        assertThatThrownBy(() -> search.search(TENANT,
                        SearchDossiersQuery.of(null, null, null, 0, 20, List.of(c("contrat", Op.EQ, "C-42"))), AccessContext.system("d2-it")))
                .isInstanceOf(InvalidDossierSearchException.class).hasMessageContaining("dossierTypeCode");
        // plus de 5 critères
        List<FieldCondition> six = List.of(c("contrat", Op.EQ, "a"), c("contrat", Op.EQ, "b"), c("contrat", Op.EQ, "c"),
                c("contrat", Op.EQ, "d"), c("contrat", Op.EQ, "e"), c("contrat", Op.EQ, "f"));
        assertThatThrownBy(() -> SearchDossiersQuery.of(null, type, null, 0, 20, six)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theColumnIsJsonbWithAGinIndexThatServesContainment() {
        assertThat(jdbc.queryForObject(
                        "select data_type from information_schema.columns where table_schema = ? and table_name = 'dossier' and column_name = 'fields'",
                        String.class, SCHEMA))
                .isEqualTo("jsonb");
        assertThat(jdbc.queryForObject(
                        "select indexdef from pg_indexes where schemaname = ? and indexname = 'idx_dossier_fields_gin'", String.class, SCHEMA))
                .contains("USING gin").contains("jsonb_path_ops");
        // le plan choisi quand le planificateur n'a pas d'alternative séquentielle : l'index GIN sert bien l'opérateur @>
        String plan = jdbc.execute((java.sql.Connection con) -> {
            try (var st = con.createStatement()) {
                st.execute("set enable_seqscan = off");
                var rs = st.executeQuery("explain select id from dossier where fields @> cast('{\"contrat\":\"C-42\"}' as jsonb)");
                StringBuilder sb = new StringBuilder();
                while (rs.next()) {
                    sb.append(rs.getString(1)).append('\n');
                }
                return sb.toString();
            }
        });
        assertThat(plan).contains("idx_dossier_fields_gin");
    }
}
