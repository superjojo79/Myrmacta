package be.myrmacta.domain.notification.template.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.document.model.TemplateProcessingException;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateLimits;
import be.myrmacta.domain.notification.template.model.NotificationTemplateNotFoundException;
import be.myrmacta.domain.notification.template.model.NotificationTemplateStateException;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;
import be.myrmacta.domain.notification.template.model.RenderedNotification;
import be.myrmacta.domain.notification.template.model.TemplateResolutionKey;
import be.myrmacta.domain.notification.template.port.in.CreateNotificationTemplateUseCase.CreateCommand;
import be.myrmacta.domain.notification.template.port.in.PreviewNotificationTemplateUseCase.PreviewCommand;
import be.myrmacta.domain.notification.template.port.in.UpdateNotificationTemplateUseCase.UpdateCommand;
import be.myrmacta.domain.shared.PageQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationTemplateUseCasesTest {

    private static final String TENANT = "tenant_dev";
    private static final NotificationTemplateLimits LIMITS = new NotificationTemplateLimits(40, 100, 200);

    private InMemoryNotificationTemplateRepository repository;
    private final List<NotificationTemplate> checked = new ArrayList<>();
    private boolean checkerRejects;
    private final List<NotificationTemplate> rendered = new ArrayList<>();

    private CreateNotificationTemplateUseCaseImpl create;
    private UpdateNotificationTemplateUseCaseImpl update;
    private ActivateNotificationTemplateUseCaseImpl activate;
    private ArchiveNotificationTemplateUseCaseImpl archive;
    private NewNotificationTemplateVersionUseCaseImpl newVersion;
    private GetNotificationTemplateUseCaseImpl get;
    private ListNotificationTemplateUseCaseImpl list;
    private ResolveNotificationTemplateUseCaseImpl resolve;
    private RenderNotificationUseCaseImpl render;
    private PreviewNotificationTemplateUseCaseImpl preview;

    @BeforeEach
    void setUp() {
        repository = new InMemoryNotificationTemplateRepository();
        checked.clear();
        rendered.clear();
        checkerRejects = false;
        create = new CreateNotificationTemplateUseCaseImpl(TENANT, repository, LIMITS);
        update = new UpdateNotificationTemplateUseCaseImpl(TENANT, repository, LIMITS);
        activate = new ActivateNotificationTemplateUseCaseImpl(TENANT, repository, template -> {
            checked.add(template);
            if (checkerRejects) {
                throw new NotificationTemplateValidationException("htmlBody", "tag <img> is not allowed");
            }
        });
        archive = new ArchiveNotificationTemplateUseCaseImpl(TENANT, repository);
        newVersion = new NewNotificationTemplateVersionUseCaseImpl(TENANT, repository);
        get = new GetNotificationTemplateUseCaseImpl(TENANT, repository);
        list = new ListNotificationTemplateUseCaseImpl(TENANT, repository);
        resolve = new ResolveNotificationTemplateUseCaseImpl(TENANT, repository);
        render = new RenderNotificationUseCaseImpl(resolve, (template, model) -> {
            rendered.add(template);
            return new RenderedNotification(template.subject(), template.textBody(), template.htmlBody());
        });
        preview = new PreviewNotificationTemplateUseCaseImpl(
                TENANT,
                repository,
                template -> {
                    checked.add(template);
                    if (checkerRejects) {
                        throw new NotificationTemplateValidationException("htmlBody", "tag <img> is not allowed");
                    }
                },
                (template, model) -> {
                    rendered.add(template);
                    return new RenderedNotification("S", "T", template.htmlBody());
                },
                LIMITS);
    }

    private NotificationTemplate draft(String key, String variant, String locale) {
        return create.create(new CreateCommand(key, variant, locale, "Label", "Objet", "Corps", null, "alice"));
    }

    @Test
    void create_makesADraftVersionOne_withNormalizedOptionalParts() {
        NotificationTemplate created = create.create(
                new CreateCommand("accuse-reception", "  ", " fr ", "Accusé", " Objet ", "Corps", "  ", "alice"));

        assertThat(created.status()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(created.version()).isEqualTo(1);
        assertThat(created.variantKey()).isNull();
        assertThat(created.locale()).isEqualTo("fr");
        assertThat(created.subject()).isEqualTo("Objet");
        assertThat(created.htmlBody()).isNull();
        assertThat(created.tenantId()).isEqualTo(TENANT);
        assertThat(created.updatedBy()).isEqualTo("alice");
    }

    @Test
    void create_secondDraftOfTheSameKey_getsTheNextVersion() {
        draft("k", null, null);
        assertThat(draft("k", null, null).version()).isEqualTo(2);
        assertThat(draft("k", "premium", null).version()).isEqualTo(1);
    }

    @Test
    void create_withoutUpdatedBy_recordsSystem() {
        NotificationTemplate created = create.create(new CreateCommand("k", null, null, null, "S", "B", null, null));
        assertThat(created.updatedBy()).isEqualTo("system");
    }

    @Test
    void create_rejectsInvalidParts_namingThePart() {
        assertThatThrownBy(() -> create.create(new CreateCommand(" ", null, null, "L", "S", "B", null, "a")))
                .isInstanceOfSatisfying(
                        NotificationTemplateValidationException.class, e -> assertThat(e.part()).isEqualTo("templateKey"));
        assertThatThrownBy(() -> create.create(new CreateCommand("Bad Key", null, null, "L", "S", "B", null, "a")))
                .isInstanceOfSatisfying(
                        NotificationTemplateValidationException.class, e -> assertThat(e.part()).isEqualTo("templateKey"));
        assertThatThrownBy(() -> create.create(new CreateCommand("k", "BAD", null, "L", "S", "B", null, "a")))
                .isInstanceOfSatisfying(
                        NotificationTemplateValidationException.class, e -> assertThat(e.part()).isEqualTo("variantKey"));
        assertThatThrownBy(() -> create.create(new CreateCommand("k", null, "fr_BE!", "L", "S", "B", null, "a")))
                .isInstanceOfSatisfying(
                        NotificationTemplateValidationException.class, e -> assertThat(e.part()).isEqualTo("locale"));
        assertThatThrownBy(() -> create.create(new CreateCommand("k", null, null, "L", " ", "B", null, "a")))
                .isInstanceOfSatisfying(
                        NotificationTemplateValidationException.class, e -> assertThat(e.part()).isEqualTo("subject"));
        assertThatThrownBy(() -> create.create(new CreateCommand("k", null, null, "L", "S", null, null, "a")))
                .isInstanceOfSatisfying(
                        NotificationTemplateValidationException.class, e -> assertThat(e.part()).isEqualTo("textBody"));
        assertThatThrownBy(() -> create.create(new CreateCommand("k", null, null, "x".repeat(201), "S", "B", null, "a")))
                .isInstanceOfSatisfying(
                        NotificationTemplateValidationException.class, e -> assertThat(e.part()).isEqualTo("label"));
    }

    @Test
    void create_enforcesTheSizeLimits() {
        assertThatThrownBy(() -> create.create(new CreateCommand("k", null, null, "L", "s".repeat(41), "B", null, "a")))
                .isInstanceOfSatisfying(
                        NotificationTemplateValidationException.class, e -> assertThat(e.part()).isEqualTo("subject"));
        assertThatThrownBy(() -> create.create(new CreateCommand("k", null, null, "L", "S", "b".repeat(101), null, "a")))
                .isInstanceOfSatisfying(
                        NotificationTemplateValidationException.class, e -> assertThat(e.part()).isEqualTo("textBody"));
        assertThatThrownBy(() -> create.create(new CreateCommand("k", null, null, "L", "S", "B", "h".repeat(201), "a")))
                .isInstanceOfSatisfying(
                        NotificationTemplateValidationException.class, e -> assertThat(e.part()).isEqualTo("htmlBody"));
        // La taille du corps se mesure en octets UTF-8 : 51 caractères de 2 octets dépassent 100 octets.
        assertThatThrownBy(() -> create.create(new CreateCommand("k", null, null, "L", "S", "é".repeat(51), null, "a")))
                .isInstanceOf(NotificationTemplateValidationException.class);
        assertThat(create.create(new CreateCommand("k", null, null, "L", "s".repeat(40), "b".repeat(100), "h".repeat(200), "a")))
                .isNotNull();
    }

    @Test
    void update_patchesOnlyTheGivenFields_ofADraft() {
        NotificationTemplate created = create.create(new CreateCommand("k", null, null, "L", "S", "B", "<p>h</p>", "a"));

        NotificationTemplate updated =
                update.update(new UpdateCommand(created.id(), null, "Nouvel objet", null, null, "bob"));

        assertThat(updated.subject()).isEqualTo("Nouvel objet");
        assertThat(updated.textBody()).isEqualTo("B");
        assertThat(updated.htmlBody()).isEqualTo("<p>h</p>");
        assertThat(updated.label()).isEqualTo("L");
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.status()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(updated.updatedBy()).isEqualTo("bob");
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
    }

    @Test
    void update_blankHtmlBody_removesTheHtmlPart() {
        NotificationTemplate created = create.create(new CreateCommand("k", null, null, "L", "S", "B", "<p>h</p>", "a"));

        assertThat(update.update(new UpdateCommand(created.id(), null, null, null, "  ", "a")).htmlBody()).isNull();
    }

    @Test
    void update_revalidatesTheChangedParts() {
        NotificationTemplate created = draft("k", null, null);

        assertThatThrownBy(() -> update.update(new UpdateCommand(created.id(), null, null, " ", null, "a")))
                .isInstanceOfSatisfying(
                        NotificationTemplateValidationException.class, e -> assertThat(e.part()).isEqualTo("textBody"));
        assertThatThrownBy(() -> update.update(new UpdateCommand(created.id(), null, "s".repeat(41), null, null, "a")))
                .isInstanceOf(NotificationTemplateValidationException.class);
    }

    @Test
    void update_refusesANonDraft_andAnUnknownId() {
        NotificationTemplate created = draft("k", null, null);
        activate.activate(created.id(), "a");

        assertThatThrownBy(() -> update.update(new UpdateCommand(created.id(), "x", null, null, null, "a")))
                .isInstanceOf(NotificationTemplateStateException.class);
        assertThatThrownBy(() -> update.update(new UpdateCommand(UUID.randomUUID(), "x", null, null, null, "a")))
                .isInstanceOf(NotificationTemplateNotFoundException.class);
    }

    @Test
    void activate_runsTheFullCheck_thenArchivesThePreviousActive() {
        NotificationTemplate first = draft("k", null, null);
        activate.activate(first.id(), "a");
        NotificationTemplate second = draft("k", null, null);

        NotificationTemplate activated = activate.activate(second.id(), "bob");

        assertThat(activated.status()).isEqualTo(TemplateStatus.ACTIVE);
        assertThat(activated.updatedBy()).isEqualTo("bob");
        assertThat(repository.stored(first.id()).status()).isEqualTo(TemplateStatus.ARCHIVED);
        assertThat(checked).extracting(NotificationTemplate::id).containsExactly(first.id(), second.id());
    }

    @Test
    void activate_aRejectedTemplate_staysADraftAndKeepsThePreviousActive() {
        NotificationTemplate first = draft("k", null, null);
        activate.activate(first.id(), "a");
        NotificationTemplate second = draft("k", null, null);
        checkerRejects = true;

        assertThatThrownBy(() -> activate.activate(second.id(), "a"))
                .isInstanceOfSatisfying(NotificationTemplateValidationException.class, e -> {
                    assertThat(e.part()).isEqualTo("htmlBody");
                    assertThat(e.getMessage()).contains("<img>");
                });

        assertThat(repository.stored(second.id()).status()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(repository.stored(first.id()).status()).isEqualTo(TemplateStatus.ACTIVE);
    }

    @Test
    void activate_onlyADraft() {
        NotificationTemplate created = draft("k", null, null);
        activate.activate(created.id(), "a");

        assertThatThrownBy(() -> activate.activate(created.id(), "a"))
                .isInstanceOf(NotificationTemplateStateException.class);
        assertThatThrownBy(() -> activate.activate(UUID.randomUUID(), "a"))
                .isInstanceOf(NotificationTemplateNotFoundException.class);
    }

    @Test
    void activate_doesNotTouchAnotherVariantOrLocale() {
        NotificationTemplate base = draft("k", null, null);
        activate.activate(base.id(), "a");
        NotificationTemplate french = draft("k", null, "fr");
        activate.activate(french.id(), "a");

        assertThat(repository.stored(base.id()).status()).isEqualTo(TemplateStatus.ACTIVE);
        assertThat(repository.stored(french.id()).status()).isEqualTo(TemplateStatus.ACTIVE);
    }

    @Test
    void archive_onlyAnActive() {
        NotificationTemplate created = draft("k", null, null);
        assertThatThrownBy(() -> archive.archive(created.id(), "a"))
                .isInstanceOf(NotificationTemplateStateException.class);

        activate.activate(created.id(), "a");
        assertThat(archive.archive(created.id(), "bob").status()).isEqualTo(TemplateStatus.ARCHIVED);
        assertThatThrownBy(() -> archive.archive(UUID.randomUUID(), "a"))
                .isInstanceOf(NotificationTemplateNotFoundException.class);
    }

    @Test
    void newVersion_copiesTheContentIntoADraftOfTheNextVersion() {
        NotificationTemplate created = create.create(new CreateCommand("k", "v", "fr", "L", "S", "B", "<p>h</p>", "a"));
        activate.activate(created.id(), "a");

        NotificationTemplate next = newVersion.newVersion(created.id(), "bob");

        assertThat(next.id()).isNotEqualTo(created.id());
        assertThat(next.status()).isEqualTo(TemplateStatus.DRAFT);
        assertThat(next.version()).isEqualTo(2);
        assertThat(next.subject()).isEqualTo("S");
        assertThat(next.textBody()).isEqualTo("B");
        assertThat(next.htmlBody()).isEqualTo("<p>h</p>");
        assertThat(next.variantKey()).isEqualTo("v");
        assertThat(next.locale()).isEqualTo("fr");
        assertThat(next.updatedBy()).isEqualTo("bob");
        assertThatThrownBy(() -> newVersion.newVersion(UUID.randomUUID(), "a"))
                .isInstanceOf(NotificationTemplateNotFoundException.class);
    }

    @Test
    void get_isScopedToTheTenant() {
        NotificationTemplate created = draft("k", null, null);
        assertThat(get.get(created.id()).id()).isEqualTo(created.id());

        GetNotificationTemplateUseCaseImpl otherTenant = new GetNotificationTemplateUseCaseImpl("other", repository);
        assertThatThrownBy(() -> otherTenant.get(created.id()))
                .isInstanceOf(NotificationTemplateNotFoundException.class);
    }

    @Test
    void list_stripsTheBodies_andFiltersByKey() {
        draft("a", null, null);
        create.create(new CreateCommand("b", null, null, "L", "Objet B", "Corps B", "<p>h</p>", "a"));

        var all = list.list(null, PageQuery.of(0, 20));
        var onlyB = list.list(" b ", PageQuery.of(0, 20));

        assertThat(all.totalElements()).isEqualTo(2);
        assertThat(onlyB.content()).hasSize(1);
        NotificationTemplate item = onlyB.content().getFirst();
        assertThat(item.subject()).isEqualTo("Objet B");
        assertThat(item.textBody()).isEmpty();
        assertThat(item.htmlBody()).isNull();
    }

    @Test
    void resolve_returnsOnlyActiveVersions_withFourLevelFallback() {
        NotificationTemplate def = draft("k", null, null);
        NotificationTemplate fr = draft("k", null, "fr");
        NotificationTemplate premium = draft("k", "premium", null);
        NotificationTemplate premiumFr = draft("k", "premium", "fr");
        draft("k", "never", null);

        assertThatThrownBy(() -> resolve.resolve(new TemplateResolutionKey("k", null, null)))
                .isInstanceOf(NotificationTemplateNotFoundException.class);

        for (NotificationTemplate t : List.of(def, fr, premium, premiumFr)) {
            activate.activate(t.id(), "a");
        }

        assertThat(resolve.resolve(new TemplateResolutionKey("k", "premium", "fr")).id()).isEqualTo(premiumFr.id());
        assertThat(resolve.resolve(new TemplateResolutionKey("k", "premium", "nl")).id()).isEqualTo(premium.id());
        assertThat(resolve.resolve(new TemplateResolutionKey("k", "gold", "fr")).id()).isEqualTo(fr.id());
        assertThat(resolve.resolve(new TemplateResolutionKey("k", "gold", "nl")).id()).isEqualTo(def.id());
        assertThat(resolve.resolve(new TemplateResolutionKey(" k ", "  ", null)).id()).isEqualTo(def.id());
        assertThatThrownBy(() -> resolve.resolve(new TemplateResolutionKey("absent", null, null)))
                .isInstanceOf(NotificationTemplateNotFoundException.class);
        assertThatThrownBy(() -> resolve.resolve(new TemplateResolutionKey(" ", null, null)))
                .isInstanceOf(NotificationTemplateValidationException.class);
    }

    @Test
    void render_resolvesTheActiveVersion_thenRendersIt() {
        NotificationTemplate created = draft("k", null, null);
        activate.activate(created.id(), "a");

        RenderedNotification result =
                render.render(new TemplateResolutionKey("k", null, null), Map.of("dossier", Map.of()));

        assertThat(result.subject()).isEqualTo("Objet");
        assertThat(rendered).extracting(NotificationTemplate::id).containsExactly(created.id());
    }

    @Test
    void render_withoutActiveVersion_isNotFound_andNeverRenders() {
        draft("k", null, null);

        assertThatThrownBy(() -> render.render(new TemplateResolutionKey("k", null, null), Map.of()))
                .isInstanceOf(NotificationTemplateNotFoundException.class);
        assertThat(rendered).isEmpty();
    }

    @Test
    void render_propagatesTheRendererFailure() {
        NotificationTemplate created = draft("k", null, null);
        activate.activate(created.id(), "a");
        RenderNotificationUseCaseImpl failing = new RenderNotificationUseCaseImpl(resolve, (template, model) -> {
            throw new TemplateProcessingException("textBody: unknown property dossier.x");
        });

        assertThatThrownBy(() -> failing.render(new TemplateResolutionKey("k", null, null), Map.of()))
                .isInstanceOf(TemplateProcessingException.class)
                .hasMessageContaining("textBody");
    }

    @Test
    void preview_byId_rendersADraft_afterTheFullCheck() {
        NotificationTemplate created = create.create(new CreateCommand("k", null, null, "L", "S", "B", "<p>h</p>", "a"));

        RenderedNotification result =
                preview.preview(new PreviewCommand(created.id(), null, null, null, Map.of("variables", Map.of())));

        assertThat(result.htmlBody()).isEqualTo("<p>h</p>");
        assertThat(checked).extracting(NotificationTemplate::id).containsExactly(created.id());
        assertThat(repository.stored(created.id()).status()).isEqualTo(TemplateStatus.DRAFT);
    }

    @Test
    void preview_byContent_rendersATransientTemplate_withoutStoringIt() {
        RenderedNotification result =
                preview.preview(new PreviewCommand(null, "Objet", "Corps", "<p>h</p>", Map.of()));

        assertThat(result.htmlBody()).isEqualTo("<p>h</p>");
        assertThat(list.list(null, PageQuery.of(0, 20)).totalElements()).isZero();
    }

    @Test
    void preview_refusesAnInvalidTemplate_andAnIncompleteRequest() {
        checkerRejects = true;
        assertThatThrownBy(() -> preview.preview(new PreviewCommand(null, "S", "B", "<img>", Map.of())))
                .isInstanceOf(NotificationTemplateValidationException.class);
        checkerRejects = false;

        assertThatThrownBy(() -> preview.preview(new PreviewCommand(null, "S", null, null, Map.of())))
                .isInstanceOfSatisfying(
                        NotificationTemplateValidationException.class, e -> assertThat(e.part()).isEqualTo("textBody"));
        assertThatThrownBy(() -> preview.preview(new PreviewCommand(null, "S", "b".repeat(101), null, Map.of())))
                .isInstanceOf(NotificationTemplateValidationException.class);
        assertThatThrownBy(() -> preview.preview(new PreviewCommand(UUID.randomUUID(), null, null, null, Map.of())))
                .isInstanceOf(NotificationTemplateNotFoundException.class);
    }

    @Test
    void preview_byId_refusesCombinedContent() {
        NotificationTemplate created = draft("k", null, null);

        assertThatThrownBy(() -> preview.preview(new PreviewCommand(created.id(), "S", null, null, Map.of())))
                .isInstanceOfSatisfying(
                        NotificationTemplateValidationException.class, e -> assertThat(e.part()).isEqualTo("id"));
    }

    @Test
    void resolve_validatesVariantAndLocale_andNeverEchoesThem() {
        for (String bad : List.of("{a=b, c=SECRET}", "x\nINFO forged line", "Upper Case", "a".repeat(200))) {
            assertThatThrownBy(() -> resolve.resolve(new TemplateResolutionKey("k", bad, null)))
                    .isInstanceOf(NotificationTemplateValidationException.class)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("SECRET").doesNotContain("forged"));
            assertThatThrownBy(() -> resolve.resolve(new TemplateResolutionKey("k", null, bad)))
                    .isInstanceOf(NotificationTemplateValidationException.class)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("SECRET").doesNotContain("forged"));
        }
    }

    @Test
    void resolve_notFound_doesNotEchoVariantOrLocale() {
        assertThatThrownBy(() -> resolve.resolve(new TemplateResolutionKey("absent", "vip", "fr-BE")))
                .isInstanceOf(NotificationTemplateNotFoundException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("absent").doesNotContain("vip").doesNotContain("fr-BE"));
    }
}
