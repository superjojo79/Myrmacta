package com.flowforge.infrastructure.persistence.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.in.ActivateDocumentTemplateUseCase;
import com.flowforge.domain.document.port.in.CreateDocumentTemplateUseCase;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
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
