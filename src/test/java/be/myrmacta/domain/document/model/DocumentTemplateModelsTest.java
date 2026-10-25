package be.myrmacta.domain.document.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentTemplateModelsTest {

    @Test
    void templateStatus_hasDraftActiveArchived() {
        assertThat(TemplateStatus.values())
                .containsExactly(TemplateStatus.DRAFT, TemplateStatus.ACTIVE, TemplateStatus.ARCHIVED);
    }

    @Test
    void documentTemplate_exposesResolutionTripletAndLifecycle() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-03T12:00:00Z");
        var template = new DocumentTemplate(
                id,
                "contrat",
                "premium",
                "fr",
                "Contrat premium FR",
                2,
                TemplateStatus.DRAFT,
                "<html th:text=\"${title}\"></html>",
                "tenant_dev",
                now,
                now,
                "admin");

        assertThat(template.id()).isEqualTo(id);
        assertThat(template.templateKey()).isEqualTo("contrat");
        assertThat(template.variantKey()).isEqualTo("premium");
        assertThat(template.locale()).isEqualTo("fr");
        assertThat(template.label()).isEqualTo("Contrat premium FR");
        assertThat(template.version()).isEqualTo(2);
        assertThat(template.status()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(template.htmlContent()).contains("th:text");
        assertThat(template.tenantId()).isEqualTo("tenant_dev");
        assertThat(template.updatedBy()).isEqualTo("admin");
    }

    @Test
    void documentTemplate_allowsNullVariantAndLocaleAsDefaults() {
        var template = sample(null, null, TemplateStatus.ACTIVE);
        assertThat(template.variantKey()).isNull();
        assertThat(template.locale()).isNull();
    }

    @Test
    void documentTemplate_requiresTemplateKeyAndHtmlContent() {
        assertThatThrownBy(() -> sampleWithKey(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("templateKey");
        assertThatThrownBy(() -> sampleWithHtml(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("htmlContent");
    }

    @Test
    void resolutionRequest_requiresTemplateKey_allowsOptionalVariantLocale() {
        var req = new TemplateResolutionRequest("contrat", "premium", "nl");
        assertThat(req.templateKey()).isEqualTo("contrat");
        assertThat(req.variantKey()).isEqualTo("premium");
        assertThat(req.locale()).isEqualTo("nl");

        var defaults = new TemplateResolutionRequest("facture", null, null);
        assertThat(defaults.variantKey()).isNull();
        assertThat(defaults.locale()).isNull();

        assertThatThrownBy(() -> new TemplateResolutionRequest(null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("templateKey");
    }

    @Test
    void templateNotFoundException_mentionsResolutionRequest() {
        var req = new TemplateResolutionRequest("contrat", "premium", "nl");
        var ex = new TemplateNotFoundException(req);
        assertThat(ex.getMessage()).contains("contrat").contains("premium").contains("nl");
    }

    @Test
    void invalidTemplateStateException_carriesMessage() {
        var ex = new InvalidTemplateStateException("Cannot activate ARCHIVED");
        assertThat(ex.getMessage()).isEqualTo("Cannot activate ARCHIVED");
    }

    @Test
    void invalidTemplateSyntaxException_carriesMessage() {
        var ex = new InvalidTemplateSyntaxException("bad th:block");
        assertThat(ex.getMessage()).contains("bad th:block");
    }

    @Test
    void templateValidationException_carriesMessage() {
        var ex = new TemplateValidationException("htmlContent exceeds 5 MiB");
        assertThat(ex.getMessage()).contains("5 MiB");
    }

    private static DocumentTemplate sample(String variant, String locale, TemplateStatus status) {
        Instant now = Instant.parse("2026-08-03T12:00:00Z");
        return new DocumentTemplate(
                UUID.randomUUID(),
                "contrat",
                variant,
                locale,
                "label",
                1,
                status,
                "<p>ok</p>",
                "tenant_dev",
                now,
                now,
                "system");
    }

    private static DocumentTemplate sampleWithKey(String key) {
        Instant now = Instant.parse("2026-08-03T12:00:00Z");
        return new DocumentTemplate(
                UUID.randomUUID(),
                key,
                null,
                null,
                "label",
                1,
                TemplateStatus.DRAFT,
                "<p>ok</p>",
                "tenant_dev",
                now,
                now,
                "system");
    }

    private static DocumentTemplate sampleWithHtml(String html) {
        Instant now = Instant.parse("2026-08-03T12:00:00Z");
        return new DocumentTemplate(
                UUID.randomUUID(),
                "contrat",
                null,
                null,
                "label",
                1,
                TemplateStatus.DRAFT,
                html,
                "tenant_dev",
                now,
                now,
                "system");
    }
}
