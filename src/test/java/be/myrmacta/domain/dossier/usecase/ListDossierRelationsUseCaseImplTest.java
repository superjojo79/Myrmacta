package be.myrmacta.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierRelation;
import be.myrmacta.domain.dossier.model.DossierRelationView;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.RelationDirection;
import be.myrmacta.domain.dossier.model.RelationType;
import be.myrmacta.domain.dossier.port.out.DossierRelationRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListDossierRelationsUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant T0 = Instant.parse("2026-08-15T08:00:00Z");

    private DossierRepository dossierRepository;
    private DossierRelationRepository relationRepository;
    private DossierTypeRepository typeRepository;
    private DossierAccessPolicy accessPolicy;
    private ListDossierRelationsUseCaseImpl useCase;
    private AccessContext.User actor;
    private Dossier current;
    private Dossier related;

    @BeforeEach
    void setUp() {
        dossierRepository = mock(DossierRepository.class);
        relationRepository = mock(DossierRelationRepository.class);
        typeRepository = mock(DossierTypeRepository.class);
        accessPolicy = mock(DossierAccessPolicy.class);
        useCase = new ListDossierRelationsUseCaseImpl(
                dossierRepository, relationRepository, typeRepository, accessPolicy);
        actor = AccessContext.user("u1", Set.of("DOSSIER_MANAGER"));
        current = dossier("FAC", "Facture Acme");
        related = dossier("SIN", "Sinistre");
        when(dossierRepository.findById(current.id())).thenReturn(Optional.of(current));
        when(dossierRepository.findById(related.id())).thenReturn(Optional.of(related));
        when(typeRepository.findByCode(TENANT, "SIN"))
                .thenReturn(Optional.of(type("SIN", "Sinistre")));
        when(typeRepository.findByCode(TENANT, "FAC"))
                .thenReturn(Optional.of(type("FAC", "Facture")));
    }

    @Test
    void list_marksOutgoingWhenCurrentIsSource() {
        when(relationRepository.findByDossier(TENANT, current.id()))
                .thenReturn(List.of(relation(current.id(), related.id())));

        List<DossierRelationView> views = useCase.list(current.id(), actor);

        assertThat(views).hasSize(1);
        assertThat(views.getFirst().direction()).isEqualTo(RelationDirection.OUTGOING);
        assertThat(views.getFirst().relatedDossier().id()).isEqualTo(related.id());
        assertThat(views.getFirst().relatedDossier().dossierTypeLabel()).isEqualTo("Sinistre");
        assertThat(views.getFirst().relatedDossier().getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("fields");
    }

    @Test
    void list_marksIncomingWhenCurrentIsTarget() {
        when(relationRepository.findByDossier(TENANT, current.id()))
                .thenReturn(List.of(relation(related.id(), current.id())));

        List<DossierRelationView> views = useCase.list(current.id(), actor);

        assertThat(views).hasSize(1);
        assertThat(views.getFirst().direction()).isEqualTo(RelationDirection.INCOMING);
        assertThat(views.getFirst().relatedDossier().id()).isEqualTo(related.id());
    }

    @Test
    void list_omitsRelationWhenRelatedDossierIsNotViewable() {
        when(relationRepository.findByDossier(TENANT, current.id()))
                .thenReturn(List.of(relation(current.id(), related.id())));
        doThrow(new AccessDeniedException("Access denied for permission VIEW"))
                .when(accessPolicy)
                .assertCanView(related, actor);

        assertThat(useCase.list(current.id(), actor)).isEmpty();
    }

    @Test
    void list_adminBypassKeepsAllRelations() {
        AccessContext.User admin = AccessContext.user("admin", Set.of("TENANT_ADMIN"));
        when(relationRepository.findByDossier(TENANT, current.id()))
                .thenReturn(List.of(relation(current.id(), related.id())));

        assertThat(useCase.list(current.id(), admin)).hasSize(1);
    }

    @Test
    void list_omitsRelationWhenRelatedDossierIsMissing() {
        when(relationRepository.findByDossier(TENANT, current.id()))
                .thenReturn(List.of(relation(current.id(), related.id())));
        when(dossierRepository.findById(related.id())).thenReturn(Optional.empty());

        assertThat(useCase.list(current.id(), actor)).isEmpty();
    }

    @Test
    void list_emptyWhenNoRelations() {
        when(relationRepository.findByDossier(TENANT, current.id())).thenReturn(List.of());

        assertThat(useCase.list(current.id(), actor)).isEmpty();
    }

    @Test
    void list_rejectsUnknownDossier() {
        UUID missing = UUID.randomUUID();
        when(dossierRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.list(missing, actor)).isInstanceOf(DossierNotFoundException.class);
    }

    private DossierRelation relation(UUID sourceId, UUID targetId) {
        return new DossierRelation(
                UUID.randomUUID(),
                sourceId,
                targetId,
                TENANT,
                RelationType.PARENT_CHILD,
                "u1",
                T0,
                null);
    }

    private static Dossier dossier(String typeCode, String label) {
        UUID id = UUID.randomUUID();
        return new Dossier(
                id,
                TENANT,
                "DOS-" + id.toString().substring(0, 8),
                typeCode,
                label,
                DossierStatus.OUVERT,
                Map.of("secret", "value"),
                null,
                0,
                "u1",
                T0,
                "u1",
                T0,
                null,
                null,
                null);
    }

    private static DossierType type(String code, String label) {
        return DossierType.inherit(UUID.randomUUID(), TENANT, code, label, "wf", true, false, List.of());
    }
}
