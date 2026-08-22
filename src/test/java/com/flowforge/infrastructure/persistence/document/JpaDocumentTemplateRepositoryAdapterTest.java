package com.flowforge.infrastructure.persistence.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateStatus;
import com.flowforge.domain.document.port.out.DocumentTemplateRepository;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Couverture de branches manquante trouvée en clôturant fiabilisation-tests-plateforme
 * (D-COVERAGE, seuil BRANCH relevé) : {@code listByTenantId}/{@code pageByTenantId} (filtre
 * {@code templateKey} nul/vide vs présent) et {@code save} (chemin update, entité déjà
 * existante) n'étaient exercés par aucun test réel — {@link DocumentTemplateUseCasesTest} mocke
 * entièrement le port.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class JpaDocumentTemplateRepositoryAdapterTest {

    @Autowired
    private DocumentTemplateRepository repository;

    @Test
    void listByTenantId_blankTemplateKey_returnsAllForTenant() {
        String key = "list-" + UUID.randomUUID();
        DocumentTemplate saved = repository.save(template(key));

        assertThat(repository.listByTenantId("tenant_dev", "  "))
                .extracting(DocumentTemplate::id)
                .contains(saved.id());
    }

    @Test
    void listByTenantId_specificTemplateKey_filtersToThatKey() {
        String key = "list-" + UUID.randomUUID();
        DocumentTemplate saved = repository.save(template(key));

        assertThat(repository.listByTenantId("tenant_dev", key))
                .extracting(DocumentTemplate::id)
                .containsExactly(saved.id());
    }

    @Test
    void pageByTenantId_nullTemplateKey_returnsAllForTenant() {
        String key = "page-" + UUID.randomUUID();
        DocumentTemplate saved = repository.save(template(key));

        assertThat(repository.pageByTenantId("tenant_dev", null, com.flowforge.domain.shared.PageQuery.of(0, 100))
                        .content())
                .extracting(DocumentTemplate::id)
                .contains(saved.id());
    }

    @Test
    void pageByTenantId_specificTemplateKey_filtersToThatKey() {
        String key = "page-" + UUID.randomUUID();
        DocumentTemplate saved = repository.save(template(key));

        assertThat(repository.pageByTenantId("tenant_dev", key, com.flowforge.domain.shared.PageQuery.of(0, 100))
                        .content())
                .extracting(DocumentTemplate::id)
                .containsExactly(saved.id());
    }

    @Test
    void save_existingEntity_updatesInPlaceRatherThanInserting() {
        String key = "update-" + UUID.randomUUID();
        DocumentTemplate saved = repository.save(template(key));

        DocumentTemplate updated = repository.save(new DocumentTemplate(
                saved.id(),
                saved.templateKey(),
                saved.variantKey(),
                saved.locale(),
                "Updated label",
                saved.version(),
                saved.status(),
                saved.htmlContent(),
                saved.tenantId(),
                saved.createdAt(),
                Instant.now(),
                "updater"));

        assertThat(updated.id()).isEqualTo(saved.id());
        assertThat(repository.findByIdAndTenantId(saved.id(), "tenant_dev")).get()
                .extracting(DocumentTemplate::label)
                .isEqualTo("Updated label");
    }

    private static DocumentTemplate template(String templateKey) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-22T00:00:00Z");
        return new DocumentTemplate(
                id, templateKey, null, null, "Label", 1, TemplateStatus.ACTIVE,
                "<html></html>", "tenant_dev", now, now, "creator");
    }
}
