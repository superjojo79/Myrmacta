package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierRelation;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DuplicateDossierRelationException;
import com.flowforge.domain.dossier.model.RelationType;
import com.flowforge.domain.dossier.port.out.DossierRelationRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DossierRelationPersistenceTest {

    private static final Instant T0 = Instant.parse("2026-08-15T08:00:00Z");

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private DossierRelationRepository relationRepository;

    @Test
    void saveAndFindByDossierIsBidirectionalAndMapsDuplicate() {
        Dossier source = dossierRepository.save(dossier("SRC"));
        Dossier target = dossierRepository.save(dossier("TGT"));
        DossierRelation saved = relationRepository.save(relation(source.id(), target.id(), null));

        assertThat(relationRepository.findByDossier("tenant_dev", source.id()))
                .extracting(DossierRelation::id)
                .contains(saved.id());
        assertThat(relationRepository.findByDossier("tenant_dev", target.id()))
                .extracting(DossierRelation::id)
                .contains(saved.id());

        assertThatThrownBy(() -> relationRepository.save(relation(source.id(), target.id(), null)))
                .isInstanceOf(DuplicateDossierRelationException.class);
    }

    @Test
    void findByIdempotencyKey_roundTripsServiceTaskKey() {
        Dossier source = dossierRepository.save(dossier("SRC2"));
        Dossier target = dossierRepository.save(dossier("TGT2"));
        DossierRelation saved = relationRepository.save(relation(source.id(), target.id(), "pi:act"));

        assertThat(relationRepository.findByIdempotencyKey("tenant_dev", source.id(), "pi:act"))
                .contains(saved);
        assertThat(relationRepository.findByIdempotencyKey("tenant_dev", source.id(), "other"))
                .isEmpty();
    }

    @Test
    void save_allowsMultipleManualRelationsWithNullIdempotencyKey() {
        Dossier source = dossierRepository.save(dossier("SRC3"));
        Dossier first = dossierRepository.save(dossier("TGT3A"));
        Dossier second = dossierRepository.save(dossier("TGT3B"));

        relationRepository.save(relation(source.id(), first.id(), null));
        relationRepository.save(relation(source.id(), second.id(), null));

        assertThat(relationRepository.findByDossier("tenant_dev", source.id())).hasSize(2);
    }

    private static DossierRelation relation(UUID sourceId, UUID targetId, String key) {
        return new DossierRelation(
                UUID.randomUUID(),
                sourceId,
                targetId,
                "tenant_dev",
                RelationType.LINKED,
                "u1",
                T0,
                key);
    }

    private static Dossier dossier(String suffix) {
        UUID id = UUID.randomUUID();
        return new Dossier(
                id,
                "tenant_dev",
                "DOS-REL-" + suffix + "-" + id.toString().substring(0, 8),
                "REL",
                "Relation " + suffix,
                DossierStatus.OUVERT,
                java.util.Map.of(),
                null,
                0,
                "creator",
                T0,
                "creator",
                T0,
                null,
                null);
    }
}
