package com.flowforge.infrastructure.persistence.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.flowforge.domain.workflow.model.DmnDraft;
import com.flowforge.domain.workflow.port.out.DmnDraftRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class JpaDmnDraftRepositoryAdapterTest {

    @Autowired
    private DmnDraftRepository repository;

    @Test
    void save_thenFindByTenantIdAndDecisionKey_roundTripsCorrectly() {
        String key = "roundTrip" + System.nanoTime();
        DmnDraft draft =
                new DmnDraft("tenant_dev", key, "<dmn>content</dmn>", Instant.parse("2026-09-11T10:00:00Z"), "alice");

        repository.save(draft);
        Optional<DmnDraft> found = repository.findByTenantIdAndDecisionKey("tenant_dev", key);

        assertThat(found).contains(draft);
    }

    @Test
    void save_sameCompositeKeyTwice_overwritesExistingRecordWithoutDuplicating() {
        String key = "overwrite" + System.nanoTime();
        repository.save(new DmnDraft("tenant_dev", key, "<dmn>v1</dmn>", Instant.parse("2026-09-11T10:00:00Z"), "alice"));

        DmnDraft updated =
                new DmnDraft("tenant_dev", key, "<dmn>v2</dmn>", Instant.parse("2026-09-11T11:00:00Z"), "bob");
        repository.save(updated);

        Optional<DmnDraft> found = repository.findByTenantIdAndDecisionKey("tenant_dev", key);
        assertThat(found).contains(updated);
    }

    @Test
    void deleteByTenantIdAndDecisionKey_existingKey_removesIt() {
        String key = "delete" + System.nanoTime();
        repository.save(new DmnDraft("tenant_dev", key, "<dmn/>", Instant.parse("2026-09-11T10:00:00Z"), "alice"));

        repository.deleteByTenantIdAndDecisionKey("tenant_dev", key);

        assertThat(repository.findByTenantIdAndDecisionKey("tenant_dev", key)).isEmpty();
    }

    @Test
    void deleteByTenantIdAndDecisionKey_absentKey_isNoOp() {
        String key = "neverExisted" + System.nanoTime();

        assertThatCode(() -> repository.deleteByTenantIdAndDecisionKey("tenant_dev", key))
                .doesNotThrowAnyException();
    }

    @Test
    void twoTenantsWithSameDecisionKey_haveIndependentDrafts() {
        String key = "sharedKey" + System.nanoTime();
        DmnDraft tenantA = new DmnDraft("tenant_a", key, "<dmn>a</dmn>", Instant.parse("2026-09-11T10:00:00Z"), "alice");
        DmnDraft tenantB = new DmnDraft("tenant_b", key, "<dmn>b</dmn>", Instant.parse("2026-09-11T10:00:00Z"), "bob");

        repository.save(tenantA);
        repository.save(tenantB);

        assertThat(repository.findByTenantIdAndDecisionKey("tenant_a", key)).contains(tenantA);
        assertThat(repository.findByTenantIdAndDecisionKey("tenant_b", key)).contains(tenantB);
    }
}
