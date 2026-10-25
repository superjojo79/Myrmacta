package be.myrmacta.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierFieldValidationException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.model.FieldValidationError;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TODO §102 (issue #184) — garantie en base de l'unicité (D-UNIQUE-STORE) : toute écriture par {@link DossierRepository}
 * tient {@code dossier_unique_value} à jour, y compris sans pré-contrôle et sous écritures concurrentes (U5, U8, U9).
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DossierUniqueValueIT {

    private static final String TENANT = "tenant_dev";

    @Autowired private DossierTypeRepository typeRepository;
    @Autowired private DossierRepository dossierRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    private String typeCode;

    @BeforeEach
    void uniqueType() {
        typeCode = "UNQ" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        typeRepository.save(DossierType.inherit(UUID.randomUUID(), TENANT, typeCode, "Unique", "p", true, false, List.of(
                new FieldDefinition("plaque", "Plaque", FieldType.STRING, false, null, null, null, false, null, null,
                        null, null, null, null, true, null, null, true),
                new FieldDefinition("note", "Note", FieldType.STRING, false, null, null, null))));
    }

    private Dossier dossier(String plaque) {
        Instant now = Instant.now();
        return new Dossier(UUID.randomUUID(), TENANT, typeCode + "-" + UUID.randomUUID(), typeCode, "d",
                DossierStatus.EN_COURS, plaque == null ? Map.of() : Map.of("plaque", plaque), null, 0, "u", now, "u", now,
                null, null, null);
    }

    private int rows() {
        return jdbcTemplate.queryForObject(
                "select count(*) from dossier_unique_value where dossier_type_code = ?", Integer.class, typeCode);
    }

    @Test
    void aSecondOpenDossierWithTheSameValueIsRefusedAndNotWritten_U8() {
        Dossier first = dossierRepository.save(dossier("BE-1ABC123"));
        Dossier second = dossier("BE-1ABC123");

        assertThatThrownBy(() -> dossierRepository.save(second))
                .isInstanceOfSatisfying(DossierFieldValidationException.class, ex -> assertThat(ex.errors())
                        .extracting(FieldValidationError::field, FieldValidationError::code, FieldValidationError::reference)
                        .containsExactly(org.assertj.core.groups.Tuple.tuple("plaque", "UNIQUE", first.reference())));
        assertThat(dossierRepository.findById(second.id())).isEmpty();
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void savingAgainOrChangingItsOwnValueIsAllowed() {
        Dossier first = dossierRepository.save(dossier("BE-1"));
        Dossier again = dossierRepository.save(first);
        dossierRepository.save(new Dossier(again.id(), TENANT, again.reference(), typeCode, "d", DossierStatus.EN_COURS,
                Map.of("plaque", "BE-2"), null, again.version(), "u", again.createdAt(), "u", Instant.now(), null, null,
                null));
        assertThat(jdbcTemplate.queryForList(
                "select field_value from dossier_unique_value where dossier_type_code = ?", String.class, typeCode))
                .containsExactly("BE-2");
        dossierRepository.save(dossier("BE-1")); // l'ancienne valeur est libérée
    }

    @Test
    void closingADossierReleasesItsValue_U5() {
        Dossier first = dossierRepository.save(dossier("BE-9"));
        dossierRepository.save(first.withStatus(DossierStatus.CLOTURE, "u", Instant.now()));
        assertThat(rows()).isZero();
        dossierRepository.save(dossier("BE-9"));
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void emptyValuesNeverConflict_U7() {
        dossierRepository.save(dossier(null));
        dossierRepository.save(dossier(""));
        dossierRepository.save(dossier(null));
        assertThat(rows()).isZero();
    }

    @Test
    void deletingADossierDeletesItsValues() {
        Dossier first = dossierRepository.save(dossier("BE-5"));
        dossierRepository.deleteById(first.id());
        assertThat(rows()).isZero();
    }

    @Test
    void ofTwoConcurrentWritesOfTheSameValueOnlyOneSucceeds_U9() throws Exception {
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(tx -> {
            dossierRepository.save(dossier("BE-RACE"));
            firstWritten.countDown();
            await(releaseFirst);
        }));
        assertThat(firstWritten.await(30, TimeUnit.SECONDS)).isTrue();

        // Le second insère pendant que le premier n'a pas encore validé : PostgreSQL le fait attendre sur la clé.
        CompletableFuture<Dossier> second = CompletableFuture.supplyAsync(() -> dossierRepository.save(dossier("BE-RACE")));
        Thread.sleep(500);
        assertThat(second).isNotDone();
        releaseFirst.countDown();
        first.get(30, TimeUnit.SECONDS);

        assertThatThrownBy(() -> second.get(30, TimeUnit.SECONDS))
                .hasCauseInstanceOf(DossierFieldValidationException.class);
        assertThat(rows()).isEqualTo(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
