package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.model.InvalidDossierElementException;
import com.flowforge.domain.dossier.port.in.AddDossierElementUseCase;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddDossierElementUseCaseImplTest {

    @Mock
    DossierRepository dossierRepository;

    @Mock
    DossierElementRepository elementRepository;

    AddDossierElementUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddDossierElementUseCaseImpl("tenant_dev", dossierRepository, elementRepository);
    }

    @Test
    void add_producedDoc_persistsWithMetadata() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId)));
        when(elementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DossierElement saved = useCase.add(
                new AddDossierElementUseCase.AddCommand(
                        dossierId,
                        ElementType.PRODUCED_DOC,
                        "Document généré — step",
                        "tenant_dev/x.pdf",
                        Map.of("templateKey", "contrat", "contentType", "application/pdf"),
                        "system",
                        "task1"),
                AccessContext.system("test"));

        ArgumentCaptor<DossierElement> captor = ArgumentCaptor.forClass(DossierElement.class);
        verify(elementRepository).save(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(ElementType.PRODUCED_DOC);
        assertThat(captor.getValue().metadata()).containsEntry("templateKey", "contrat");
        assertThat(saved.storageRef()).isEqualTo("tenant_dev/x.pdf");
    }

    @Test
    void add_unknownDossier_throws() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.add(
                        new AddDossierElementUseCase.AddCommand(
                                dossierId,
                                ElementType.PRODUCED_DOC,
                                "label",
                                "tenant_dev/x.pdf",
                                Map.of(),
                                "system",
                                null),
                        AccessContext.system("test")))
                .isInstanceOf(DossierNotFoundException.class);
    }

    @Test
    void add_blankStorageRef_throws() {
        assertThatThrownBy(() -> useCase.add(
                        new AddDossierElementUseCase.AddCommand(
                                UUID.randomUUID(),
                                ElementType.PRODUCED_DOC,
                                "label",
                                " ",
                                Map.of(),
                                "system",
                                null),
                        AccessContext.system("test")))
                .isInstanceOf(InvalidDossierElementException.class);
    }

    private static Dossier dossier(UUID id) {
        Instant now = Instant.parse("2026-08-03T12:00:00Z");
        return new Dossier(
                id,
                "tenant_dev",
                "REF-1",
                "SINISTRE",
                "label",
                DossierStatus.OUVERT,
                Map.of(),
                null,
                0L,
                "user",
                now,
                null,
                null,
                null,
                null);
    }
}
