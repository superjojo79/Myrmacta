package be.myrmacta.domain.promotion.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateLimits;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateChecker;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import be.myrmacta.domain.notification.template.usecase.CreateNotificationTemplateUseCaseImpl;
import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.PlanAction;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.NotificationTemplateDoc;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.promotion.JsonArtifactCodec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §123 (H1, CS10) — artefact de promotion des gabarits de notification : export, plan, import. */
class NotificationTemplateArtifactsTest {

    private static final String TENANT = "tenant_dev";
    private static final JsonArtifactCodec CODEC = new JsonArtifactCodec();
    private static final NotificationTemplateLimits LIMITS = new NotificationTemplateLimits(50, 200, 300);

    /** Dépôt en mémoire : assez fidèle pour un export puis un import. */
    private static final class InMemoryRepository implements NotificationTemplateRepository {
        final List<NotificationTemplate> rows = new ArrayList<>();

        @Override
        public NotificationTemplate save(NotificationTemplate template) {
            rows.removeIf(t -> t.id().equals(template.id()));
            rows.add(template);
            return template;
        }

        @Override
        public Optional<NotificationTemplate> findByIdAndTenantId(UUID id, String tenantId) {
            return rows.stream().filter(t -> t.id().equals(id)).findFirst();
        }

        @Override
        public PageResult<NotificationTemplate> pageByTenantId(String tenantId, String templateKey, PageQuery query) {
            List<NotificationTemplate> matching = rows.stream()
                    .filter(t -> templateKey == null || t.templateKey().equals(templateKey))
                    .sorted(Comparator.comparing(NotificationTemplate::templateKey).thenComparingInt(NotificationTemplate::version))
                    .toList();
            int from = Math.min(query.offset(), matching.size());
            int to = Math.min(from + query.size(), matching.size());
            return PageResult.of(matching.subList(from, to), query.page(), query.size(), matching.size());
        }

        @Override
        public int nextVersion(String tenantId, String templateKey, String variantKey, String locale) {
            return rows.stream()
                            .filter(t -> t.templateKey().equals(templateKey)
                                    && java.util.Objects.equals(t.variantKey(), variantKey)
                                    && java.util.Objects.equals(t.locale(), locale))
                            .mapToInt(NotificationTemplate::version)
                            .max()
                            .orElse(0)
                    + 1;
        }

        @Override
        public Optional<NotificationTemplate> findBestActive(String tenantId, String templateKey, String variantKey, String locale) {
            return findActiveExact(tenantId, templateKey, variantKey, locale);
        }

        @Override
        public Optional<NotificationTemplate> findActiveExact(String tenantId, String templateKey, String variantKey, String locale) {
            return rows.stream()
                    .filter(t -> t.status() == TemplateStatus.ACTIVE
                            && t.templateKey().equals(templateKey)
                            && java.util.Objects.equals(t.variantKey(), variantKey)
                            && java.util.Objects.equals(t.locale(), locale))
                    .findFirst();
        }
    }

    private InMemoryRepository source;
    private InMemoryRepository target;
    private NotificationTemplateChecker checker;
    private NotificationTemplateArtifacts fromSource;
    private NotificationTemplateArtifacts intoTarget;

    @BeforeEach
    void setUp() {
        source = new InMemoryRepository();
        target = new InMemoryRepository();
        checker = template -> {
            if (template.textBody().contains("FORBIDDEN")) {
                throw new NotificationTemplateValidationException("textBody", "construct not allowed");
            }
        };
        fromSource = artifacts(source);
        intoTarget = artifacts(target);
    }

    private NotificationTemplateArtifacts artifacts(InMemoryRepository repository) {
        return new NotificationTemplateArtifacts(
                TENANT, CODEC, repository, new CreateNotificationTemplateUseCaseImpl(TENANT, repository, LIMITS), checker, LIMITS);
    }

    private static NotificationTemplate row(
            String key, String variant, String locale, int version, TemplateStatus status, String subject, String text, String html) {
        Instant now = Instant.parse("2026-11-01T10:00:00Z");
        return new NotificationTemplate(
                UUID.randomUUID(), key, variant, locale, "Label " + key, version, status, subject, text, html, TENANT, now, now, "u");
    }

    private static BundleEntry entry(NotificationTemplateDoc doc) {
        return new BundleEntry(ArtifactType.NOTIFICATION_TEMPLATE, NotificationTemplateArtifacts.entryKey(doc), CODEC.encode(doc));
    }

    @Test
    void exportCarriesOnlyTheActiveVersions_withTheirThreeParts_sortedByFileName() {
        source.save(row("relance", null, null, 1, TemplateStatus.ACTIVE, "S1", "T1", null));
        source.save(row("relance", null, null, 2, TemplateStatus.DRAFT, "S2", "T2", null));
        source.save(row("accuse", "court", "fr", 3, TemplateStatus.ACTIVE, "Sujet", "Texte", "<p>Html</p>"));
        source.save(row("ancien", null, null, 1, TemplateStatus.ARCHIVED, "S", "T", null));

        List<BundleEntry> exported = fromSource.export();

        assertThat(exported).extracting(BundleEntry::key).containsExactly("accuse.court.fr", "relance._._");
        assertThat(exported).extracting(BundleEntry::type).containsOnly(ArtifactType.NOTIFICATION_TEMPLATE);
        NotificationTemplateDoc doc = CODEC.decode(exported.getFirst().content(), NotificationTemplateDoc.class);
        assertThat(doc.subject()).isEqualTo("Sujet");
        assertThat(doc.textBody()).isEqualTo("Texte");
        assertThat(doc.htmlBody()).isEqualTo("<p>Html</p>");
        assertThat(exported.get(1).text()).doesNotContain("T2");
    }

    @Test
    void exportThenImport_roundTripsAsDraftsOnly_andReplayingConverges() {
        source.save(row("accuse", "court", "fr", 1, TemplateStatus.ACTIVE, "Sujet", "Texte\nsur deux lignes", "<p>Html</p>"));
        source.save(row("relance", null, null, 1, TemplateStatus.ACTIVE, "Relance", "Texte", null));

        List<BundleEntry> bundle = fromSource.export();
        for (BundleEntry e : bundle) {
            assertThat(intoTarget.plan(e).action()).isEqualTo(PlanAction.CREATE);
            assertThat(intoTarget.apply(e, "alice")).contains("draft version 1").contains("activate it");
        }

        assertThat(target.rows).hasSize(2).allSatisfy(t -> {
            assertThat(t.status()).isEqualTo(TemplateStatus.DRAFT);
            assertThat(t.updatedBy()).isEqualTo("alice");
        });
        NotificationTemplate imported = target.rows.stream().filter(t -> t.templateKey().equals("accuse")).findFirst().orElseThrow();
        assertThat(imported.variantKey()).isEqualTo("court");
        assertThat(imported.locale()).isEqualTo("fr");
        assertThat(imported.subject()).isEqualTo("Sujet");
        assertThat(imported.textBody()).isEqualTo("Texte\nsur deux lignes");
        assertThat(imported.htmlBody()).isEqualTo("<p>Html</p>");
        // rejouer : le brouillon identique suffit
        for (BundleEntry e : bundle) {
            var replay = intoTarget.plan(e);
            assertThat(replay.action()).isEqualTo(PlanAction.UNCHANGED);
            assertThat(replay.reason()).contains("draft version 1");
        }
    }

    @Test
    void anIdenticalActiveVersionIsUnchanged_aDifferentOneIsAnUpdateAsANewDraft() {
        target.save(row("accuse", null, null, 1, TemplateStatus.ACTIVE, "S", "T", null));

        assertThat(intoTarget.plan(entry(new NotificationTemplateDoc("accuse", null, null, "Label accuse", "S", "T", null))).action())
                .isEqualTo(PlanAction.UNCHANGED);
        var changed = intoTarget.plan(entry(new NotificationTemplateDoc("accuse", null, null, "Label accuse", "S", "T2", null)));
        assertThat(changed.action()).isEqualTo(PlanAction.UPDATE);

        intoTarget.apply(entry(new NotificationTemplateDoc("accuse", null, null, "Label accuse", "S", "T2", null)), "alice");

        assertThat(target.rows).extracting(NotificationTemplate::version, NotificationTemplate::status)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1, TemplateStatus.ACTIVE),
                        org.assertj.core.groups.Tuple.tuple(2, TemplateStatus.DRAFT));
    }

    @Test
    void aTemplateRefusedByTheCheckerOrBeyondTheLimitsOrWithAnInvalidKeyIsBlockedWithItsReason() {
        var refused = intoTarget.plan(entry(new NotificationTemplateDoc("faux", null, null, "F", "S", "FORBIDDEN", null)));
        var tooLarge = intoTarget.plan(entry(new NotificationTemplateDoc("gros", null, null, "G", "S", "x".repeat(201), null)));
        var badKey = intoTarget.plan(entry(new NotificationTemplateDoc("Pas Valide", null, null, "K", "S", "T", null)));
        var noBody = intoTarget.plan(entry(new NotificationTemplateDoc("vide", null, null, "V", "S", " ", null)));

        assertThat(refused.action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(refused.reason()).contains("refused at activation").contains("textBody").contains("construct not allowed");
        assertThat(tooLarge.action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(tooLarge.reason()).contains("exceeds 200 bytes");
        assertThat(badKey.action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(badKey.reason()).contains("templateKey");
        assertThat(noBody.action()).isEqualTo(PlanAction.BLOCKED);
        assertThat(target.rows).isEmpty();
    }

    @Test
    void theFileNameMustAgreeWithItsContent() {
        var forged = new BundleEntry(ArtifactType.NOTIFICATION_TEMPLATE, "autre._._",
                CODEC.encode(new NotificationTemplateDoc("accuse", null, null, "A", "S", "T", null)));

        assertThatThrownBy(() -> intoTarget.plan(forged)).hasMessageContaining("agree");
    }

    @Test
    void theFileNameIsTheSameAsADocumentTemplateOfTheSameCombination() {
        assertThat(NotificationTemplateArtifacts.entryKey(new NotificationTemplateDoc("accuse", null, "fr", "", "", "", null)))
                .isEqualTo("accuse._.fr");
        assertThat(NotificationTemplateArtifacts.entryKey(new NotificationTemplateDoc("accuse", "court", null, "", "", "", null)))
                .isEqualTo("accuse.court._");
    }

    @Test
    void everyVersionIsReadAcrossThePages_soAStagedDraftBeyondTheFirstHundredRowsIsFound() {
        for (int i = 1; i <= 120; i++) {
            target.save(row("lourd", null, null, i, i == 1 ? TemplateStatus.ACTIVE : TemplateStatus.ARCHIVED, "S" + i, "T" + i, null));
        }
        target.save(row("lourd", null, null, 121, TemplateStatus.DRAFT, "S", "brouillon", null));

        var plan = intoTarget.plan(entry(new NotificationTemplateDoc("lourd", null, null, "Label lourd", "S", "brouillon", null)));

        assertThat(plan.action()).isEqualTo(PlanAction.UNCHANGED);
        assertThat(plan.reason()).contains("draft version 121");
    }
}
