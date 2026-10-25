package be.myrmacta.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldCondition;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierSearchPort;
import be.myrmacta.domain.dossier.port.out.DossierSearchPort.FieldPredicate;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * TODO §102 (issue #184, lot B, R3, R4) — recherche des dossiers « voisins » sur PostgreSQL : même type, non clôturés, hors
 * dossier courant ; nombre et référence du plus ancien.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DossierOpenMatchesIT {

    private static final String TENANT = "tenant_dev";

    @Autowired private DossierTypeRepository typeRepository;
    @Autowired private DossierRepository dossierRepository;
    @Autowired private DossierSearchPort searchPort;

    private String typeCode;

    @BeforeEach
    void type() {
        typeCode = "OPM" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        typeRepository.save(DossierType.inherit(UUID.randomUUID(), TENANT, typeCode, "T", "p", true, false, List.of(
                new FieldDefinition("email", "E-mail", FieldType.STRING, false, null, null, null, false, null, null, null,
                        null, null, null, true, null, null, false),
                new FieldDefinition("rang", "Rang", FieldType.NUMBER, false, null, null, null, false, null, null, null,
                        null, null, null, true, null, null, false))));
    }

    private Dossier dossier(String type, DossierStatus status, Instant createdAt, Map<String, Object> fields) {
        return dossierRepository.save(new Dossier(UUID.randomUUID(), TENANT, type + "-" + UUID.randomUUID(), type, "d", status,
                fields, null, 0, "u", createdAt, "u", createdAt, null, null, null));
    }

    private static FieldPredicate email(String value) {
        return new FieldPredicate("email", DossierSearchPort.Kind.TEXT, FieldCondition.Op.EQ, List.of(value));
    }

    @Test
    void countsOpenDossiersOfTheSameTypeExceptTheCurrentOneAndNamesTheOldest() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        Dossier current = dossier(typeCode, DossierStatus.EN_COURS, t0.plusSeconds(50), Map.of("email", "a@x.fr"));
        Dossier oldest = dossier(typeCode, DossierStatus.SUSPENDU, t0.plusSeconds(10), Map.of("email", "a@x.fr"));
        dossier(typeCode, DossierStatus.DRAFT, t0.plusSeconds(20), Map.of("email", "a@x.fr"));
        dossier(typeCode, DossierStatus.CLOTURE, t0, Map.of("email", "a@x.fr")); // clôturé : jamais compté
        dossier(typeCode, DossierStatus.EN_COURS, t0, Map.of("email", "b@x.fr"));

        var summary = searchPort.findOpenMatches(TENANT, typeCode, List.of(email("a@x.fr")), current.id());

        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.firstReference()).isEqualTo(oldest.reference());
    }

    @Test
    void anotherTypeNeverMatchesAndNoMatchGivesZeroAndNoReference() {
        Dossier current = dossier(typeCode, DossierStatus.EN_COURS, Instant.now(), Map.of("email", "c@x.fr"));
        typeRepository.save(DossierType.inherit(UUID.randomUUID(), TENANT, typeCode + "B", "T", "p", true, true, List.of()));
        dossier(typeCode + "B", DossierStatus.EN_COURS, Instant.now(), Map.of("email", "c@x.fr"));

        var summary = searchPort.findOpenMatches(TENANT, typeCode, List.of(email("c@x.fr")), current.id());

        assertThat(summary.count()).isZero();
        assertThat(summary.firstReference()).isNull();
    }

    @Test
    void allCriteriaMustMatchAndNumbersCompareByValue() {
        Dossier current = dossier(typeCode, DossierStatus.EN_COURS, Instant.now(), Map.of("email", "d@x.fr", "rang", 2));
        dossier(typeCode, DossierStatus.EN_COURS, Instant.now(), Map.of("email", "d@x.fr", "rang", 2.0));
        dossier(typeCode, DossierStatus.EN_COURS, Instant.now(), Map.of("email", "d@x.fr", "rang", 3));

        var rang = new FieldPredicate("rang", DossierSearchPort.Kind.NUMBER, FieldCondition.Op.EQ,
                List.of(new BigDecimal("2")));
        assertThat(searchPort.findOpenMatches(TENANT, typeCode, List.of(email("d@x.fr"), rang), current.id()).count())
                .isEqualTo(1);
    }
}
