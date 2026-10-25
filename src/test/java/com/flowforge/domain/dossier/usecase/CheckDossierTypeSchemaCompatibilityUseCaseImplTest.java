package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierFieldValidator;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.in.CheckDossierTypeSchemaCompatibilityUseCase.Query;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckDossierTypeSchemaCompatibilityUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String TYPE = "SINISTRE";

    @Mock
    private DossierRepository dossierRepository;

    private CheckDossierTypeSchemaCompatibilityUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new CheckDossierTypeSchemaCompatibilityUseCaseImpl(dossierRepository, new DossierFieldValidator());
    }

    @Test
    void compatibleFields_returnsZeroIncompatibleCount() {
        when(dossierRepository.findByDossierTypeCode(TENANT, TYPE))
                .thenReturn(List.of(dossier("REF-1", Map.of("amount", 10))));

        var result = useCase.check(new Query(
                TENANT, TYPE, List.of(numberField("amount", false)), false));

        assertThat(result.incompatibleCount()).isZero();
        assertThat(result.compatible()).isTrue();
        assertThat(result.sampleReferences()).isEmpty();
    }

    @Test
    void newRequiredFieldMissingOnExistingDossiers_countsIncompatible() {
        when(dossierRepository.findByDossierTypeCode(TENANT, TYPE))
                .thenReturn(List.of(dossier("REF-1", Map.of()), dossier("REF-2", Map.of("amount", 5))));

        var result = useCase.check(new Query(
                TENANT, TYPE, List.of(numberField("amount", true)), false));

        assertThat(result.incompatibleCount()).isEqualTo(1);
        assertThat(result.compatible()).isFalse();
        assertThat(result.sampleReferences()).containsExactly("REF-1");
    }

    @Test
    void sampleReferences_capsAtFive() {
        List<Dossier> dossiers = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> dossier("REF-" + i, Map.of()))
                .toList();
        when(dossierRepository.findByDossierTypeCode(TENANT, TYPE)).thenReturn(dossiers);

        var result = useCase.check(new Query(
                TENANT, TYPE, List.of(numberField("amount", true)), false));

        assertThat(result.incompatibleCount()).isEqualTo(8);
        assertThat(result.sampleReferences()).hasSize(5);
    }

    @Test
    void duplicateHolderCountStaysCompleteWhenReferencesAreSampled() {
        List<Dossier> dossiers = java.util.stream.IntStream.range(0, 25)
                .mapToObj(i -> dossier("R" + i, Map.of("plaque", "A")))
                .toList();
        when(dossierRepository.findByDossierTypeCode(TENANT, TYPE)).thenReturn(dossiers);
        var plaque = new FieldDefinition("plaque", "Plaque", FieldType.STRING, false, null, null, null, false, null,
                null, null, null, null, null, false, null, null, true);

        var result = useCase.check(new Query(TENANT, TYPE, List.of(plaque), false));

        assertThat(result.incompatibleCount()).isEqualTo(25);
        assertThat(result.duplicates()).singleElement().satisfies(duplicate -> {
            assertThat(duplicate.references()).hasSize(20);
            assertThat(duplicate.referenceCount()).isEqualTo(25);
        });
    }

    @Test
    void duplicateValuesAreCappedAndTheTotalIsKept() {
        List<Dossier> dossiers = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) {
            dossiers.add(dossier("A" + i, Map.of("plaque", "V" + i)));
            dossiers.add(dossier("B" + i, Map.of("plaque", "V" + i)));
        }
        when(dossierRepository.findByDossierTypeCode(TENANT, TYPE)).thenReturn(dossiers);
        var plaque = new FieldDefinition("plaque", "Plaque", FieldType.STRING, false, null, null, null, false, null,
                null, null, null, null, null, false, null, null, true);

        var result = useCase.check(new Query(TENANT, TYPE, List.of(plaque), false));

        assertThat(result.duplicates()).hasSize(20);
        assertThat(result.duplicateCount()).isEqualTo(21);
    }

    private static FieldDefinition numberField(String name, boolean required) {
        return new FieldDefinition(name, name, FieldType.NUMBER, required, null, List.of(), null, false);
    }

    private static Dossier dossier(String reference, Map<String, Object> fields) {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new Dossier(
                UUID.randomUUID(),
                TENANT,
                reference,
                TYPE,
                "Label",
                DossierStatus.OUVERT,
                fields,
                null,
                0L,
                "creator",
                now,
                "creator",
                now,
                null,
                null,
                null);
    }
}
