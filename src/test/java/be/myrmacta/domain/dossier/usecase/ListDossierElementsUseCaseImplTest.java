package be.myrmacta.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierElementView;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListDossierElementsUseCaseImplTest {

    private static final UUID DOSSIER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final AccessContext.User ACTOR = AccessContext.user("user-1", Set.of("DOSSIER_MANAGER"));

    @Mock
    private DossierRepository dossierRepository;
    @Mock
    private DossierElementRepository elementRepository;
    @Mock
    private DossierAccessPolicy accessPolicy;

    @Test
    void list_mapsViewsWithoutStorageRefAndMarksDownloadable() {
        when(dossierRepository.findById(DOSSIER_ID)).thenReturn(Optional.of(dossier()));
        UUID elementId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        PageQuery query = PageQuery.defaults();
        DossierElement element = new DossierElement(
                elementId,
                DOSSIER_ID,
                "tenant_dev",
                ElementType.PRODUCED_DOC,
                "Rapport",
                "tenant_dev/docs/1",
                Map.of("k", "v"),
                "user-1",
                Instant.parse("2026-08-12T10:00:00Z"),
                "step-1");
        when(elementRepository.pageByDossierId(DOSSIER_ID, query))
                .thenReturn(PageResult.of(List.of(element), 0, 20, 1));

        var useCase = new ListDossierElementsUseCaseImpl(dossierRepository, elementRepository, accessPolicy);
        PageResult<DossierElementView> page = useCase.list(DOSSIER_ID, ACTOR, query);

        verify(accessPolicy).assertCanView(dossier(), ACTOR);
        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
        DossierElementView view = page.content().getFirst();
        assertThat(view.id()).isEqualTo(elementId);
        assertThat(view.downloadable()).isTrue();
        assertThat(view.metadata()).containsEntry("k", "v");
        assertThat(view.toString()).doesNotContain("tenant_dev/docs/1");
    }

    @Test
    void list_unknownDossier_throws() {
        when(dossierRepository.findById(DOSSIER_ID)).thenReturn(Optional.empty());
        var useCase = new ListDossierElementsUseCaseImpl(dossierRepository, elementRepository, accessPolicy);
        assertThatThrownBy(() -> useCase.list(DOSSIER_ID, ACTOR, PageQuery.defaults()))
                .isInstanceOf(DossierNotFoundException.class);
    }

    private static Dossier dossier() {
        return new Dossier(
                DOSSIER_ID,
                "tenant_dev",
                "DOS-1",
                "SINISTRE",
                "Label",
                DossierStatus.EN_COURS,
                Map.of(),
                null,
                0L,
                "user-1",
                Instant.parse("2026-08-01T00:00:00Z"),
                "user-1",
                Instant.parse("2026-08-01T00:00:00Z"),
                null,
                null,
                null);
    }
}
