package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierRelation;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.model.PurgeOutboxOrigin;
import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.model.PurgeOutboxStatus;
import com.flowforge.domain.dossier.model.RelationType;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRelationRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class PurgeGraphPersistenceIT {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    @Autowired
    private DossierRepository dossierRepository;

    @Autowired
    private DossierElementRepository elementRepository;

    @Autowired
    private DossierRelationRepository relationRepository;

    @Autowired
    private DossierWorkflowLinkRepository linkRepository;

    @Autowired
    private PurgeOutboxRepository purgeOutboxRepository;

    @Test
    void claimIsAtomicAndGraphDeletesLeaveEmpty() {
        Dossier dossier = dossierRepository.save(dossier("SRC"));
        Dossier other = dossierRepository.save(dossier("TGT"));
        elementRepository.save(new DossierElement(
                UUID.randomUUID(),
                dossier.id(),
                TENANT,
                ElementType.NOTE,
                "note",
                null,
                Map.of(),
                "user",
                NOW,
                null));
        relationRepository.save(new DossierRelation(
                UUID.randomUUID(),
                dossier.id(),
                other.id(),
                TENANT,
                RelationType.LINKED,
                "user",
                NOW,
                null));
        linkRepository.save(new DossierWorkflowLink(
                UUID.randomUUID(),
                dossier.id(),
                TENANT,
                "pi-purge",
                "wf",
                1,
                "ACTIVE",
                "start",
                NOW,
                null,
                null,
                "user"));
        PurgeOutboxRecord record = PurgeOutboxRecord.pending(
                UUID.randomUUID(),
                dossier.id(),
                "DOSSIER_PURGE_REQUESTED",
                PurgeOutboxOrigin.ADMIN,
                "{}",
                NOW);
        purgeOutboxRepository.insert(record);

        assertThat(purgeOutboxRepository.claim(record.id())).isTrue();
        assertThat(purgeOutboxRepository.claim(record.id())).isFalse();

        elementRepository.deleteAllByDossierId(dossier.id());
        relationRepository.deleteRelationsTouching(dossier.id());
        linkRepository.deleteAllByDossierId(dossier.id());
        dossierRepository.deleteById(dossier.id());
        purgeOutboxRepository.markCompleted(record.id());

        assertThat(elementRepository.findByDossierIdOrderByAddedAtAsc(dossier.id())).isEmpty();
        assertThat(relationRepository.findByDossier(TENANT, dossier.id())).isEmpty();
        assertThat(linkRepository.findByDossierIdOrderBySequence(dossier.id())).isEmpty();
        assertThat(dossierRepository.findById(dossier.id())).isEmpty();
        assertThat(purgeOutboxRepository.list(PurgeOutboxStatus.COMPLETED, 50)).isNotEmpty();
        assertThat(purgeOutboxRepository.existsByDossierId(dossier.id())).isTrue();
    }

    private static Dossier dossier(String suffix) {
        UUID id = UUID.randomUUID();
        return new Dossier(
                id,
                TENANT,
                "DOS-" + suffix + "-" + id.toString().substring(0, 8),
                "CLAIM",
                "Graph " + suffix,
                DossierStatus.ARCHIVE,
                Map.of(),
                null,
                0,
                "creator",
                NOW,
                "user",
                NOW,
                "user",
                NOW,
                NOW);
    }
}
