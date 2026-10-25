package be.myrmacta.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierFieldValidationException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.DossierTypeSchemaIncompatibleException;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.model.UniqueDuplicate;
import be.myrmacta.domain.dossier.port.in.UpdateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierTypeUseCase.UpdateDossierTypeCommand;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * TODO §102 (issue #184) — activer, modifier ou retirer {@code unique} sur un type qui a déjà des dossiers : refus en
 * présence de doublons ouverts (U10), reconstruction des valeurs (U11), champ calculé ajouté après coup, et course entre la
 * mise à jour du type et un enregistrement de dossier (D-UNIQUE-TYPE-LOCK).
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DossierUniqueTypeIT {

    private static final String TENANT = "tenant_dev";
    private static final AccessContext.User ADMIN = AccessContext.user("admin", Set.of("TENANT_ADMIN"));

    @Autowired private DossierTypeRepository typeRepository;
    @Autowired private DossierRepository dossierRepository;
    @Autowired private UpdateDossierTypeUseCase updateType;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    private String typeCode;

    @BeforeEach
    void plainType() {
        typeCode = "UNT" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        typeRepository.save(DossierType.inherit(UUID.randomUUID(), TENANT, typeCode, "T", "p", true, false,
                List.of(plaque(false))));
    }

    private static FieldDefinition plaque(boolean unique) {
        return new FieldDefinition("plaque", "Plaque", FieldType.STRING, false, null, null, null, false, null, null, null,
                null, null, null, true, null, null, unique);
    }

    private void update(List<FieldDefinition> definitions) {
        updateType.update(new UpdateDossierTypeCommand(TENANT, typeCode, "T", "p", true, false, definitions, null, false,
                null, List.of()), ADMIN);
    }

    private Dossier dossier(String plaque, DossierStatus status) {
        Instant now = Instant.now();
        return dossierRepository.save(new Dossier(UUID.randomUUID(), TENANT, typeCode + "-" + UUID.randomUUID(), typeCode,
                "d", status, Map.of("plaque", plaque), null, 0, "u", now, "u", now, null, null, null));
    }

    private List<String> values() {
        return jdbcTemplate.queryForList("select field_value from dossier_unique_value where dossier_type_code = ? "
                + "order by field_value", String.class, typeCode);
    }

    @Test
    void enablingUniqueWithOpenDuplicatesIsRefusedWithTheValuesAndReferences_U10() {
        Dossier a = dossier("BE-1", DossierStatus.EN_COURS);
        Dossier b = dossier("BE-1", DossierStatus.DRAFT);
        dossier("BE-1", DossierStatus.CLOTURE); // un dossier clôturé ne compte pas
        dossier("BE-2", DossierStatus.EN_COURS);

        assertThatThrownBy(() -> update(List.of(plaque(true))))
                .isInstanceOfSatisfying(DossierTypeSchemaIncompatibleException.class, ex -> {
                    assertThat(ex.duplicates()).containsExactly(new UniqueDuplicate("plaque", "BE-1",
                            List.of(a.reference(), b.reference()).stream().sorted().toList()));
                    assertThat(ex.incompatibleCount()).isEqualTo(2);
                });
        assertThat(typeRepository.findByCode(TENANT, typeCode).orElseThrow().fieldDefinitions().getFirst().isUnique())
                .isFalse();
        assertThat(values()).isEmpty();
    }

    @Test
    void enablingUniqueWithoutDuplicatesProtectsTheExistingDossiersAtOnce_U10_U11() {
        Dossier a = dossier("BE-1", DossierStatus.EN_COURS);
        dossier("BE-1", DossierStatus.CLOTURE);
        dossier("BE-2", DossierStatus.SUSPENDU);

        update(List.of(plaque(true)));

        assertThat(values()).containsExactly("BE-1", "BE-2");
        assertThatThrownBy(() -> dossier("BE-1", DossierStatus.DRAFT))
                .isInstanceOfSatisfying(DossierFieldValidationException.class,
                        ex -> assertThat(ex.errors().getFirst().reference()).isEqualTo(a.reference()));

        update(List.of(plaque(false))); // retirer `unique` lève la contrainte (U11)
        assertThat(values()).isEmpty();
        dossier("BE-1", DossierStatus.DRAFT);
    }

    @Test
    void aComputedUniqueFieldAddedLaterIsRecomputedForExistingDossiersAndSurvivesAStatusChange() {
        Dossier a = dossier("be-7", DossierStatus.OUVERT);
        var cle = new FieldDefinition("cle", "Clé", FieldType.STRING, false, null, null, null, false, null, null, null,
                "$uppercase(dossier.fields.plaque)", null, null, false, null, null, true);

        update(List.of(plaque(false), cle));
        assertThat(values()).containsExactly("BE-7");

        // Les valeurs stockées de `a` n'ont pas `cle` : un changement de statut ne doit pas effacer sa clé.
        dossierRepository.save(dossierRepository.findById(a.id()).orElseThrow()
                .withStatus(DossierStatus.EN_COURS, "u", Instant.now()));
        assertThat(values()).containsExactly("BE-7");
        assertThatThrownBy(() -> dossier("Be-7", DossierStatus.DRAFT)).isInstanceOf(DossierFieldValidationException.class);
    }

    @Test
    void aDossierWrittenWhileUniqueIsBeingEnabledCannotSlipThrough_typeLock() throws Exception {
        dossier("BE-RACE", DossierStatus.EN_COURS);
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // Un second dossier BE-RACE est écrit mais pas encore validé quand l'administrateur active `unique` : le contrôle
        // de compatibilité ne le voit pas, la reconstruction (après le verrou exclusif) le voit.
        CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(tx -> {
            dossier("BE-RACE", DossierStatus.EN_COURS);
            written.countDown();
            await(release);
        }));
        assertThat(written.await(30, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> admin = CompletableFuture.runAsync(() -> update(List.of(plaque(true))));
        Thread.sleep(500);
        assertThat(admin).isNotDone(); // attend le verrou exclusif du type
        release.countDown();
        writer.get(30, TimeUnit.SECONDS);

        assertThatThrownBy(() -> admin.get(30, TimeUnit.SECONDS))
                .hasCauseInstanceOf(DossierTypeSchemaIncompatibleException.class);
        assertThat(typeRepository.findByCode(TENANT, typeCode).orElseThrow().fieldDefinitions().getFirst().isUnique())
                .isFalse();
        assertThat(values()).isEmpty();
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
