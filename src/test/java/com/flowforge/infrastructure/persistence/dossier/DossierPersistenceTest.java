package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.out.DossierReferenceGenerator;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.support.OidcClientTestConfiguration;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DossierPersistenceTest {

    @Autowired
    private DossierTypeRepository dossierTypeRepository;

    @Autowired
    private DossierReferenceGenerator referenceGenerator;

    @Autowired
    private DossierRepository dossierRepository;

    @Test
    void savesAndLoadsDossierTypeWithFieldDefinitions() {
        var type = DossierType.inherit(
                UUID.randomUUID(),
                "tenant_dev",
                "SINISTRE",
                "Sinistre",
                "sinistre-process",
                true,
                false,
                List.of(
                        new FieldDefinition(
                                "victim",
                                "Victim",
                                FieldType.STRING,
                                true,
                                null,
                                List.of(),
                                "identity",
                                true),
                        new FieldDefinition(
                                "severity",
                                "Severity",
                                FieldType.SELECT,
                                false,
                                "LOW",
                                List.of("LOW", "HIGH"),
                                "details")));

        dossierTypeRepository.save(type);

        assertThat(dossierTypeRepository.findByCode("tenant_dev", "SINISTRE"))
                .contains(type);
        assertThat(dossierTypeRepository.findByCode("tenant_dev", "SINISTRE"))
                .get()
                .extracting(DossierType::fieldDefinitions)
                .asList()
                .first()
                .extracting("sensitive")
                .isEqualTo(true);
        assertThat(dossierTypeRepository.findAll("tenant_dev"))
                .contains(type);
    }

    @Test
    void page_filtersAllowedCodesOrdersByCodeAndOffsetsWithoutHoles() {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String codeC = "ZZC" + token;
        String codeA = "ZZA" + token;
        String codeB = "ZZB" + token;
        dossierTypeRepository.save(type(codeC, "C " + token));
        dossierTypeRepository.save(type(codeA, "A " + token));
        dossierTypeRepository.save(type(codeB, "B " + token));

        Set<String> allowed = Set.of(codeA, codeB, codeC);
        var page0 = dossierTypeRepository.page("tenant_dev", allowed, PageQuery.of(0, 2));
        assertThat(page0.totalElements()).isEqualTo(3);
        assertThat(page0.totalPages()).isEqualTo(2);
        assertThat(page0.content()).extracting(DossierType::code).containsExactly(codeA, codeB);

        var page1 = dossierTypeRepository.page("tenant_dev", allowed, PageQuery.of(1, 2));
        assertThat(page1.content()).extracting(DossierType::code).containsExactly(codeC);
        assertThat(page1.totalElements()).isEqualTo(3);

        var emptyAcl = dossierTypeRepository.page("tenant_dev", Set.of(), PageQuery.of(0, 20));
        assertThat(emptyAcl.content()).isEmpty();
        assertThat(emptyAcl.totalElements()).isZero();

        var pastEnd = dossierTypeRepository.page("tenant_dev", allowed, PageQuery.of(9, 20));
        assertThat(pastEnd.content()).isEmpty();
        assertThat(pastEnd.totalElements()).isEqualTo(3);
    }

    private static DossierType type(String code, String label) {
        return DossierType.inherit(
                UUID.randomUUID(),
                "tenant_dev",
                code,
                label,
                "sinistre-process",
                true,
                false,
                List.of());
    }

    @Test
    void generatesSequentialReferencesForCurrentUtcYear() {
        String first = referenceGenerator.generate("tenant_dev", "SINISTRE");
        String second = referenceGenerator.generate("tenant_dev", "SINISTRE");

        assertThat(first).matches("DOS-SINISTRE-\\d{4}-000001");
        assertThat(second).isEqualTo(first.substring(0, first.length() - 6) + "000002");
    }

    @Test
    void savingDossierTwiceWithStaleVersionThrowsOptimisticLockException() {
        Dossier original = dossier(0, DossierStatus.DRAFT);
        dossierRepository.save(original);
        Dossier persisted = dossierRepository.findById(original.id()).orElseThrow();

        dossierRepository.save(persisted.withStatus(DossierStatus.OUVERT, "editor-1", Instant.parse("2026-07-31T12:05:00Z")));

        assertThatThrownBy(() -> dossierRepository.save(
                        persisted.withStatus(DossierStatus.CLOTURE, "editor-2", Instant.parse("2026-07-31T12:10:00Z"))))
                .isInstanceOfAny(ObjectOptimisticLockingFailureException.class, OptimisticLockException.class);
    }

    @Test
    void findClosedForRetention_isScopedToTenantAndOrdersByClosedAtAsc() {
        // §37 : findClosedForRetention prend désormais tenantId explicitement (aligné sur
        // findByReference) — ce test prouve l'isolation, même si en déploiement réel une seule
        // instance ne voit jamais qu'un schéma/tenant (search_path, 02-multi-tenant.md).
        String otherTenant = "tenant-other-" + UUID.randomUUID();
        Instant closedEarlier = Instant.parse("2026-08-01T00:00:00Z");
        Instant closedLater = Instant.parse("2026-08-10T00:00:00Z");

        Dossier tenantDevLater = closedDossier("tenant_dev", closedLater);
        Dossier tenantDevEarlier = closedDossier("tenant_dev", closedEarlier);
        Dossier otherTenantDossier = closedDossier(otherTenant, closedEarlier);
        dossierRepository.save(tenantDevLater);
        dossierRepository.save(tenantDevEarlier);
        dossierRepository.save(otherTenantDossier);

        List<Dossier> candidates = dossierRepository.findClosedForRetention("tenant_dev", 100);

        assertThat(candidates)
                .extracting(Dossier::id)
                .contains(tenantDevEarlier.id(), tenantDevLater.id())
                .doesNotContain(otherTenantDossier.id());
        assertThat(candidates).extracting(Dossier::tenantId).containsOnly("tenant_dev");
        int earlierIndex = candidates.indexOf(candidates.stream()
                .filter(d -> d.id().equals(tenantDevEarlier.id())).findFirst().orElseThrow());
        int laterIndex = candidates.indexOf(candidates.stream()
                .filter(d -> d.id().equals(tenantDevLater.id())).findFirst().orElseThrow());
        assertThat(earlierIndex).isLessThan(laterIndex);
    }

    @Test
    void findByDossierTypeCode_isScopedToTenantAndType() {
        String type = "SCHEMA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String otherTenant = "tenant-other-" + UUID.randomUUID();
        Dossier matching = typedDossier("tenant_dev", type);
        Dossier otherType = typedDossier("tenant_dev", "OTHER-" + type);
        Dossier otherTenantDossier = typedDossier(otherTenant, type);
        dossierRepository.save(matching);
        dossierRepository.save(otherType);
        dossierRepository.save(otherTenantDossier);

        List<Dossier> found = dossierRepository.findByDossierTypeCode("tenant_dev", type);

        assertThat(found).extracting(Dossier::id).containsExactly(matching.id());
    }

    private static Dossier typedDossier(String tenantId, String dossierTypeCode) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T12:00:00Z");
        return new Dossier(
                id,
                tenantId,
                "DOS-SCHEMA-" + id.toString().replace("-", "").substring(0, 12),
                dossierTypeCode,
                "Schema scan dossier",
                DossierStatus.OUVERT,
                Map.of(),
                null,
                0,
                "creator",
                now,
                "creator",
                now,
                null,
                null,
                null);
    }

    private static Dossier closedDossier(String tenantId, Instant closedAt) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T12:00:00Z");
        return new Dossier(
                id,
                tenantId,
                "DOS-RETENTION-" + id.toString().replace("-", "").substring(0, 12),
                "STALE",
                "Retention scan dossier",
                DossierStatus.CLOTURE,
                Map.of(),
                null,
                0,
                "creator",
                now,
                "creator",
                now,
                "creator",
                closedAt,
                null);
    }

    private static Dossier dossier(long version, DossierStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-31T12:00:00Z");
        return new Dossier(
                id,
                "tenant_dev",
                "DOS-STALE-" + id.toString().replace("-", "").substring(0, 12),
                "STALE",
                "Stale dossier",
                status,
                Map.of("priority", "NORMAL"),
                null,
                version,
                "creator",
                now,
                "creator",
                now,
                null,
                null,
                null);
    }
}
