package com.flowforge.infrastructure.persistence.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.flowforge.domain.workflow.model.BpmnDraft;
import com.flowforge.domain.workflow.port.out.BpmnDraftRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class JpaBpmnDraftRepositoryAdapterTest {

    @Autowired
    private BpmnDraftRepository repository;

    @Test
    void save_thenFindByTenantIdAndProcessDefinitionKey_roundTripsCorrectly() {
        String key = "roundTrip" + System.nanoTime();
        BpmnDraft draft = new BpmnDraft(
                "tenant_dev", key, "<bpmn>content</bpmn>", Instant.parse("2026-08-24T10:00:00Z"), "alice");

        repository.save(draft);
        Optional<BpmnDraft> found = repository.findByTenantIdAndProcessDefinitionKey("tenant_dev", key);

        assertThat(found).contains(draft);
    }

    @Test
    void save_sameCompositeKeyTwice_overwritesExistingRecordWithoutDuplicating() {
        String key = "overwrite" + System.nanoTime();
        repository.save(new BpmnDraft("tenant_dev", key, "<bpmn>v1</bpmn>", Instant.parse("2026-08-24T10:00:00Z"), "alice"));

        BpmnDraft updated =
                new BpmnDraft("tenant_dev", key, "<bpmn>v2</bpmn>", Instant.parse("2026-08-24T11:00:00Z"), "bob");
        repository.save(updated);

        Optional<BpmnDraft> found = repository.findByTenantIdAndProcessDefinitionKey("tenant_dev", key);
        assertThat(found).contains(updated);
    }

    @Test
    void deleteByTenantIdAndProcessDefinitionKey_existingKey_removesIt() {
        String key = "delete" + System.nanoTime();
        repository.save(new BpmnDraft("tenant_dev", key, "<bpmn/>", Instant.parse("2026-08-24T10:00:00Z"), "alice"));

        repository.deleteByTenantIdAndProcessDefinitionKey("tenant_dev", key);

        assertThat(repository.findByTenantIdAndProcessDefinitionKey("tenant_dev", key)).isEmpty();
    }

    @Test
    void deleteByTenantIdAndProcessDefinitionKey_absentKey_isNoOp() {
        String key = "neverExisted" + System.nanoTime();

        assertThatCode(() -> repository.deleteByTenantIdAndProcessDefinitionKey("tenant_dev", key))
                .doesNotThrowAnyException();
    }

    @Test
    void twoTenantsWithSameProcessDefinitionKey_haveIndependentDrafts() {
        String key = "sharedKey" + System.nanoTime();
        BpmnDraft tenantA = new BpmnDraft("tenant_a", key, "<bpmn>a</bpmn>", Instant.parse("2026-08-24T10:00:00Z"), "alice");
        BpmnDraft tenantB = new BpmnDraft("tenant_b", key, "<bpmn>b</bpmn>", Instant.parse("2026-08-24T10:00:00Z"), "bob");

        repository.save(tenantA);
        repository.save(tenantB);

        assertThat(repository.findByTenantIdAndProcessDefinitionKey("tenant_a", key)).contains(tenantA);
        assertThat(repository.findByTenantIdAndProcessDefinitionKey("tenant_b", key)).contains(tenantB);
    }
}
