package com.flowforge.infrastructure.persistence.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.in.ActivateDocumentTemplateUseCase;
import com.flowforge.domain.document.port.in.CreateDocumentTemplateUseCase;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DocumentTemplatePersistenceIT {

    @Autowired
    private DocumentTemplateRepository repository;

    @Autowired
    private CreateDocumentTemplateUseCase createUseCase;

    @Autowired
    private ActivateDocumentTemplateUseCase activateUseCase;

    @Test
    void activate_archivesPreviousActive_includingNullDefaults() {
        String key = "act-" + UUID.randomUUID();
        var first = createUseCase.create(new CreateDocumentTemplateUseCase.CreateCommand(
                key, null, null, "v1", "<p th:text=\"${title}\">x</p>", "test"));
        activateUseCase.activate(first.id(), "tenant_dev", "test");

        var second = createUseCase.create(new CreateDocumentTemplateUseCase.CreateCommand(
                key, null, null, "v2", "<p th:text=\"${title}\">y</p>", "test"));
        var activated = activateUseCase.activate(second.id(), "tenant_dev", "test");

        assertThat(activated.status()).isEqualTo(TemplateStatus.ACTIVE);
        assertThat(activated.version()).isEqualTo(2);
        assertThat(repository.findByIdAndTenantId(first.id(), "tenant_dev"))
                .get()
                .extracting(DocumentTemplate::status)
                .isEqualTo(TemplateStatus.ARCHIVED);
        assertThat(repository.findActiveExact("tenant_dev", key, null, null))
                .get()
                .extracting(DocumentTemplate::id)
                .isEqualTo(second.id());
    }

    @Test
    void findBestActive_prefersExactThenFallbacks() {
        String key = "resolve-" + UUID.randomUUID();
        saveActive(key, "premium", "nl", 1);
        saveActive(key, "premium", null, 2);
        saveActive(key, null, "nl", 3);
        saveActive(key, null, null, 4);

        assertThat(repository.findBestActive("tenant_dev", key, "premium", "nl"))
                .get()
                .extracting(DocumentTemplate::locale, DocumentTemplate::variantKey)
                .containsExactly("nl", "premium");

        assertThat(repository.findBestActive("tenant_dev", key, "premium", "de"))
                .get()
                .extracting(DocumentTemplate::variantKey, DocumentTemplate::locale)
                .containsExactly("premium", null);

        assertThat(repository.findBestActive("tenant_dev", key, "gold", "nl"))
                .get()
                .extracting(DocumentTemplate::variantKey, DocumentTemplate::locale)
                .containsExactly(null, "nl");

        assertThat(repository.findBestActive("tenant_dev", key, "gold", "de"))
                .get()
                .extracting(DocumentTemplate::variantKey, DocumentTemplate::locale)
                .containsExactly(null, null);
    }

    @Test
    void findBestActive_ignoresDraftAndArchived() {
        String key = "ignore-" + UUID.randomUUID();
        save(key, null, null, 1, TemplateStatus.DRAFT);
        save(key, null, null, 2, TemplateStatus.ARCHIVED);
        assertThat(repository.findBestActive("tenant_dev", key, null, null)).isEmpty();
    }

    @Test
    void nextVersion_incrementsPerCombination() {
        String key = "ver-" + UUID.randomUUID();
        save(key, "std", "fr", 1, TemplateStatus.DRAFT);
        save(key, "std", "fr", 2, TemplateStatus.ARCHIVED);
        assertThat(repository.nextVersion("tenant_dev", key, "std", "fr")).isEqualTo(3);
        assertThat(repository.nextVersion("tenant_dev", key, null, null)).isEqualTo(1);
    }

    @Test
    void pageByTenantId_filtersByKeyOrdersUpdatedAtDescAndOffsets() {
        String key = "page-" + UUID.randomUUID();
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-06-01T00:00:00Z");
        Instant t3 = Instant.parse("2026-12-01T00:00:00Z");
        UUID oldest = saveAt(key, 1, t1);
        UUID middle = saveAt(key, 2, t2);
        UUID newest = saveAt(key, 3, t3);

        var page0 = repository.pageByTenantId("tenant_dev", key, PageQuery.of(0, 1));
        assertThat(page0.totalElements()).isEqualTo(3);
        assertThat(page0.totalPages()).isEqualTo(3);
        assertThat(page0.content()).extracting(DocumentTemplate::id).containsExactly(newest);

        var page1 = repository.pageByTenantId("tenant_dev", key, PageQuery.of(1, 1));
        assertThat(page1.content()).extracting(DocumentTemplate::id).containsExactly(middle);
        assertThat(page1.totalElements()).isEqualTo(3);

        var page2 = repository.pageByTenantId("tenant_dev", key, PageQuery.of(2, 1));
        assertThat(page2.content()).extracting(DocumentTemplate::id).containsExactly(oldest);

        var pastEnd = repository.pageByTenantId("tenant_dev", key, PageQuery.of(5, 1));
        assertThat(pastEnd.content()).isEmpty();
        assertThat(pastEnd.totalElements()).isEqualTo(3);
    }

    @Test
    void findActiveByTemplateKeysIn_returnsOnlyActiveMatchingKeys() {
        String keyA = "docgen-a-" + UUID.randomUUID();
        String keyB = "docgen-b-" + UUID.randomUUID();
        String keyC = "docgen-c-" + UUID.randomUUID();
        saveActive(keyA, null, null, 1);
        save(keyB, null, null, 1, TemplateStatus.DRAFT);
        saveActive(keyC, null, null, 1);

        var result = repository.findActiveByTemplateKeysIn("tenant_dev", java.util.List.of(keyA, keyB));

        assertThat(result).extracting(DocumentTemplate::templateKey).containsExactly(keyA);
    }

    @Test
    void findActiveByTemplateKeysIn_emptyKeys_returnsEmpty() {
        assertThat(repository.findActiveByTemplateKeysIn("tenant_dev", java.util.List.of())).isEmpty();
    }

    private UUID saveAt(String key, int version, Instant updatedAt) {
        UUID id = UUID.randomUUID();
        repository.save(new DocumentTemplate(
                id,
                key,
                "std",
                "fr",
                "label",
                version,
                TemplateStatus.DRAFT,
                "<p>ok</p>",
                "tenant_dev",
                updatedAt,
                updatedAt,
                "test"));
        return id;
    }

    private void saveActive(String key, String variant, String locale, int version) {
        save(key, variant, locale, version, TemplateStatus.ACTIVE);
    }

    private void save(String key, String variant, String locale, int version, TemplateStatus status) {
        Instant now = Instant.now();
        repository.save(new DocumentTemplate(
                UUID.randomUUID(),
                key,
                variant,
                locale,
                "label",
                version,
                status,
                "<p>ok</p>",
                "tenant_dev",
                now,
                now,
                "test"));
    }
}
