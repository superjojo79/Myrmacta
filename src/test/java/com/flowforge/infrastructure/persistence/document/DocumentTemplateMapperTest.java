package com.flowforge.infrastructure.persistence.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.document.model.DocumentTemplate;
import com.flowforge.domain.document.model.TemplateStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentTemplateMapperTest {

    @Test
    void roundTrip_preservesNullVariantAndLocale() {
        Instant now = Instant.parse("2026-08-03T12:00:00Z");
        var domain = new DocumentTemplate(
                UUID.randomUUID(),
                "contrat",
                null,
                null,
                "label",
                1,
                TemplateStatus.ACTIVE,
                "<p>ok</p>",
                "tenant_dev",
                now,
                now,
                "admin");

        DocumentTemplateJpaEntity entity = DocumentTemplateJpaEntity.fromDomain(domain);
        DocumentTemplate back = entity.toDomain();

        assertThat(back.variantKey()).isNull();
        assertThat(back.locale()).isNull();
        assertThat(back.status()).isEqualTo(TemplateStatus.ACTIVE);
        assertThat(back.templateKey()).isEqualTo("contrat");
        assertThat(back.htmlContent()).isEqualTo("<p>ok</p>");
    }

    @Test
    void status_isStoredAsEnumString() {
        Instant now = Instant.parse("2026-08-03T12:00:00Z");
        var domain = new DocumentTemplate(
                UUID.randomUUID(),
                "facture",
                "B2B",
                "nl",
                "label",
                3,
                TemplateStatus.DRAFT,
                "<html></html>",
                "tenant_dev",
                now,
                now,
                "system");

        DocumentTemplateJpaEntity entity = DocumentTemplateJpaEntity.fromDomain(domain);
        assertThat(entity.getStatus()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(entity.toDomain().version()).isEqualTo(3);
        assertThat(entity.toDomain().variantKey()).isEqualTo("B2B");
        assertThat(entity.toDomain().locale()).isEqualTo("nl");
    }
}
