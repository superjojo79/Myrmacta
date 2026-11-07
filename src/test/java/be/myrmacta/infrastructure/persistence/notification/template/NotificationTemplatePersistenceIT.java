package be.myrmacta.infrastructure.persistence.notification.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;
import be.myrmacta.domain.notification.template.model.RenderedNotification;
import be.myrmacta.domain.notification.template.model.TemplateResolutionKey;
import be.myrmacta.domain.notification.template.port.in.ActivateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.CreateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.in.CreateNotificationTemplateUseCase.CreateCommand;
import be.myrmacta.domain.notification.template.port.in.RenderNotificationUseCase;
import be.myrmacta.domain.notification.template.port.in.ResolveNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Persistance JPA des gabarits de notification sur PostgreSQL : cascade, unicité de l'active, colonnes longues. */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class NotificationTemplatePersistenceIT {

    private static final String TENANT = "tenant_dev";

    @Autowired
    private NotificationTemplateRepository repository;

    @Autowired
    private CreateNotificationTemplateUseCase createUseCase;

    @Autowired
    private ActivateNotificationTemplateUseCase activateUseCase;

    @Autowired
    private ResolveNotificationTemplateUseCase resolveUseCase;

    @Autowired
    private RenderNotificationUseCase renderUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    private NotificationTemplate activeTemplate(String key, String variant, String locale, String subject) {
        NotificationTemplate draft = createUseCase.create(
                new CreateCommand(key, variant, locale, "L", subject, "Corps " + subject, null, "test"));
        return activateUseCase.activate(draft.id(), "test");
    }

    private static String newKey(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    @Test
    void liquibase_createsTheTableWithItsColumnsAndIndexes() {
        Integer columns = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE lower(table_name) = 'notification_template'
                  AND lower(column_name) IN (
                    'id', 'tenant_id', 'template_key', 'variant_key', 'locale', 'label', 'version', 'status',
                    'subject', 'text_body', 'html_body', 'created_at', 'updated_at', 'updated_by')
                """,
                Integer.class);
        Integer indexes = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM pg_indexes
                WHERE lower(tablename) = 'notification_template'
                  AND indexname IN ('uq_notification_template_active', 'uq_notification_template_version')
                """,
                Integer.class);

        assertThat(columns).isEqualTo(14);
        assertThat(indexes).isEqualTo(2);
    }

    @Test
    void save_thenRead_roundTripsLongBodiesAndANullHtmlPart() {
        String key = newKey("long");
        String longText = "ligne é\n".repeat(5_000);
        NotificationTemplate created = createUseCase.create(
                new CreateCommand(key, null, null, "L", "Objet " + "x".repeat(400), longText, "<p>" + "h".repeat(9_000) + "</p>", "t"));
        NotificationTemplate noHtml =
                createUseCase.create(new CreateCommand(newKey("nohtml"), null, null, null, "S", "T", null, "t"));

        NotificationTemplate read = repository.findByIdAndTenantId(created.id(), TENANT).orElseThrow();
        NotificationTemplate readNoHtml = repository.findByIdAndTenantId(noHtml.id(), TENANT).orElseThrow();

        assertThat(read.textBody()).isEqualTo(longText);
        assertThat(read.htmlBody()).hasSize("<p></p>".length() + 9_000);
        assertThat(read.subject()).hasSize(406);
        assertThat(readNoHtml.htmlBody()).isNull();
        assertThat(readNoHtml.label()).isNull();
    }

    @Test
    void findByIdAndTenantId_isScopedToTheTenant() {
        NotificationTemplate created = createUseCase.create(
                new CreateCommand(newKey("scope"), null, null, "L", "S", "T", null, "t"));

        assertThat(repository.findByIdAndTenantId(created.id(), TENANT)).isPresent();
        assertThat(repository.findByIdAndTenantId(created.id(), "autre_tenant")).isEmpty();
    }

    @Test
    void save_ofAnExistingTemplate_updatesInPlace() {
        NotificationTemplate created = createUseCase.create(
                new CreateCommand(newKey("upd"), null, null, "L", "S", "T", null, "t"));

        NotificationTemplate saved = repository.save(new NotificationTemplate(
                created.id(), created.templateKey(), created.variantKey(), created.locale(), "L2", created.version(),
                TemplateStatus.DRAFT, "S2", "T2", "<p>h</p>", created.tenantId(), created.createdAt(), Instant.now(), "bob"));

        assertThat(saved.id()).isEqualTo(created.id());
        NotificationTemplate read = repository.findByIdAndTenantId(created.id(), TENANT).orElseThrow();
        assertThat(read.subject()).isEqualTo("S2");
        assertThat(read.htmlBody()).isEqualTo("<p>h</p>");
        assertThat(read.updatedBy()).isEqualTo("bob");
        assertThat(repository.pageByTenantId(TENANT, created.templateKey(), PageQuery.of(0, 20)).totalElements())
                .isEqualTo(1);
    }

    @Test
    void nextVersion_countsPerCombination_includingNullVariantAndLocale() {
        String key = newKey("ver");
        assertThat(repository.nextVersion(TENANT, key, null, null)).isEqualTo(1);

        createUseCase.create(new CreateCommand(key, null, null, "L", "S", "T", null, "t"));
        createUseCase.create(new CreateCommand(key, null, null, "L", "S", "T", null, "t"));
        createUseCase.create(new CreateCommand(key, "premium", "fr", "L", "S", "T", null, "t"));

        assertThat(repository.nextVersion(TENANT, key, null, null)).isEqualTo(3);
        assertThat(repository.nextVersion(TENANT, key, "premium", "fr")).isEqualTo(2);
        assertThat(repository.nextVersion(TENANT, key, "premium", null)).isEqualTo(1);
    }

    @Test
    void activate_archivesThePreviousActiveOfTheSameCombinationOnly() {
        String key = newKey("act");
        NotificationTemplate first = activeTemplate(key, null, null, "v1");
        NotificationTemplate french = activeTemplate(key, null, "fr", "fr1");

        NotificationTemplate second = activeTemplate(key, null, null, "v2");

        assertThat(second.version()).isEqualTo(2);
        assertThat(repository.findByIdAndTenantId(first.id(), TENANT).orElseThrow().status())
                .isEqualTo(TemplateStatus.ARCHIVED);
        assertThat(repository.findByIdAndTenantId(french.id(), TENANT).orElseThrow().status())
                .isEqualTo(TemplateStatus.ACTIVE);
        assertThat(repository.findActiveExact(TENANT, key, null, null)).get().extracting(NotificationTemplate::id)
                .isEqualTo(second.id());
    }

    @Test
    void resolve_followsTheFourLevelCascade() {
        String key = newKey("cascade");
        NotificationTemplate def = activeTemplate(key, null, null, "defaut");
        NotificationTemplate fr = activeTemplate(key, null, "fr", "fr");
        NotificationTemplate premium = activeTemplate(key, "premium", null, "premium");
        NotificationTemplate premiumFr = activeTemplate(key, "premium", "fr", "premium-fr");

        assertThat(resolveId(key, "premium", "fr")).isEqualTo(premiumFr.id());
        assertThat(resolveId(key, "premium", "nl")).isEqualTo(premium.id());
        assertThat(resolveId(key, "premium", null)).isEqualTo(premium.id());
        assertThat(resolveId(key, "gold", "fr")).isEqualTo(fr.id());
        assertThat(resolveId(key, null, "fr")).isEqualTo(fr.id());
        assertThat(resolveId(key, "gold", "nl")).isEqualTo(def.id());
        assertThat(resolveId(key, null, null)).isEqualTo(def.id());
    }

    @Test
    void resolve_ignoresDraftsAndArchived_andOtherKeys() {
        String key = newKey("states");
        NotificationTemplate draftOnly = createUseCase.create(
                new CreateCommand(key, null, null, "L", "S", "T", null, "t"));
        assertThat(repository.findBestActive(TENANT, key, null, null)).isEmpty();

        activateUseCase.activate(draftOnly.id(), "t");
        assertThat(repository.findBestActive(TENANT, key, null, null)).isPresent();
        assertThat(repository.findBestActive(TENANT, key + "-other", null, null)).isEmpty();
        assertThat(repository.findBestActive("autre_tenant", key, null, null)).isEmpty();
    }

    @Test
    void render_resolvesThenRendersTheActiveVersion() {
        String key = newKey("render");
        NotificationTemplate draft = createUseCase.create(new CreateCommand(
                key, null, null, "L", "Accusé : [[${dossier.reference}]]", "Dossier [[${dossier.reference}]]",
                "<p th:text=\"${dossier.reference}\">x</p>", "t"));
        activateUseCase.activate(draft.id(), "t");

        RenderedNotification rendered = renderUseCase.render(
                new TemplateResolutionKey(key, null, "fr"), Map.of("dossier", Map.of("reference", "DOS-1")));

        assertThat(rendered.subject()).isEqualTo("Accusé : DOS-1");
        assertThat(rendered.textBody()).isEqualTo("Dossier DOS-1");
        assertThat(rendered.htmlBody()).contains("<p>DOS-1</p>");
    }

    @Test
    void activate_ofAHtmlOutsideThePolicy_isRefused_andTheDraftStaysADraft() {
        NotificationTemplate draft = createUseCase.create(new CreateCommand(
                newKey("policy"), null, null, "L", "S", "T", "<p>x</p><img src=\"https://t.example/p.gif\">", "t"));

        assertThatThrownBy(() -> activateUseCase.activate(draft.id(), "t"))
                .isInstanceOfSatisfying(NotificationTemplateValidationException.class, e -> {
                    assertThat(e.part()).isEqualTo("htmlBody");
                    assertThat(e.getMessage()).contains("<img>");
                });

        assertThat(repository.findByIdAndTenantId(draft.id(), TENANT).orElseThrow().status())
                .isEqualTo(TemplateStatus.DRAFT);
    }

    @Test
    void pageByTenantId_filtersByKey_andPages() {
        String key = newKey("page");
        for (int i = 0; i < 3; i++) {
            createUseCase.create(new CreateCommand(key, null, null, "L", "S", "T", null, "t"));
        }

        var all = repository.pageByTenantId(TENANT, key, PageQuery.of(0, 2));
        var rest = repository.pageByTenantId(TENANT, key, PageQuery.of(1, 2));
        var blankFilter = repository.pageByTenantId(TENANT, "  ", PageQuery.of(0, 5));

        assertThat(all.totalElements()).isEqualTo(3);
        assertThat(all.content()).hasSize(2);
        assertThat(rest.content()).hasSize(1);
        assertThat(blankFilter.totalElements()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void theDatabase_refusesASecondActiveAndADuplicateVersion() {
        String key = newKey("uq");
        insert(UUID.randomUUID(), key, null, null, 1, "ACTIVE");

        assertThatThrownBy(() -> insert(UUID.randomUUID(), key, null, null, 2, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(UUID.randomUUID(), key, null, null, 1, "ARCHIVED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Une autre langue est une autre combinaison.
        insert(UUID.randomUUID(), key, null, "fr", 1, "ACTIVE");
    }

    private UUID resolveId(String key, String variant, String locale) {
        return resolveUseCase.resolve(new TemplateResolutionKey(key, variant, locale)).id();
    }

    private void insert(UUID id, String key, String variant, String locale, int version, String status) {
        jdbc.update(
                """
                INSERT INTO notification_template (
                  id, tenant_id, template_key, variant_key, locale, label, version, status,
                  subject, text_body, created_at, updated_at, updated_by)
                VALUES (?, 'tenant_dev', ?, ?, ?, 'label', ?, ?, 's', 't', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'test')
                """,
                id, key, variant, locale, version, status);
    }
}
