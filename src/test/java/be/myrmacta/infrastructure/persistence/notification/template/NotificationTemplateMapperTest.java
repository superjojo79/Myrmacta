package be.myrmacta.infrastructure.persistence.notification.template;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTemplateMapperTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    private static NotificationTemplate template(String variant, String locale, String html) {
        return new NotificationTemplate(
                UUID.randomUUID(), "accuse-reception", variant, locale, "Accusé", 3, TemplateStatus.ACTIVE, "Objet",
                "Corps", html, "tenant_dev", NOW, NOW.plusSeconds(5), "admin");
    }

    @Test
    void roundTrip_preservesEveryField_nullVariantLocaleAndHtmlIncluded() {
        NotificationTemplate domain = template(null, null, null);

        NotificationTemplate back = NotificationTemplateJpaEntity.fromDomain(domain).toDomain();

        assertThat(back).isEqualTo(domain);
        assertThat(back.variantKey()).isNull();
        assertThat(back.locale()).isNull();
        assertThat(back.htmlBody()).isNull();
    }

    @Test
    void roundTrip_withVariantLocaleAndHtml() {
        NotificationTemplate domain = template("premium", "fr-BE", "<p>x</p>");

        assertThat(NotificationTemplateJpaEntity.fromDomain(domain).toDomain()).isEqualTo(domain);
        assertThat(NotificationTemplateJpaEntity.fromDomain(domain).getStatus()).isEqualTo(TemplateStatus.ACTIVE);
    }

    @Test
    void applyDomain_updatesContentAndStatus_butNeverTheKeyOrCreationDate() {
        NotificationTemplateJpaEntity entity = NotificationTemplateJpaEntity.fromDomain(template("v", "fr", "<p>a</p>"));
        NotificationTemplate changed = new NotificationTemplate(
                UUID.randomUUID(), "autre-cle", "autre", "nl", "Nouveau", 4, TemplateStatus.ARCHIVED, "Objet 2",
                "Corps 2", null, "autre-tenant", NOW.plusSeconds(100), NOW.plusSeconds(200), "bob");

        entity.applyDomain(changed);
        NotificationTemplate back = entity.toDomain();

        assertThat(back.label()).isEqualTo("Nouveau");
        assertThat(back.version()).isEqualTo(4);
        assertThat(back.status()).isEqualTo(TemplateStatus.ARCHIVED);
        assertThat(back.subject()).isEqualTo("Objet 2");
        assertThat(back.textBody()).isEqualTo("Corps 2");
        assertThat(back.htmlBody()).isNull();
        assertThat(back.updatedBy()).isEqualTo("bob");
        assertThat(back.updatedAt()).isEqualTo(NOW.plusSeconds(200));
        assertThat(back.templateKey()).isEqualTo("accuse-reception");
        assertThat(back.variantKey()).isEqualTo("v");
        assertThat(back.locale()).isEqualTo("fr");
        assertThat(back.tenantId()).isEqualTo("tenant_dev");
        assertThat(back.createdAt()).isEqualTo(NOW);
    }
}
