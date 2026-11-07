package be.myrmacta.domain.promotion.artifact;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.NotificationTemplateSupport;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.NotificationTemplateLimits;
import be.myrmacta.domain.notification.template.port.in.CreateNotificationTemplateUseCase;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateChecker;
import be.myrmacta.domain.notification.template.port.out.NotificationTemplateRepository;
import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.PlanAction;
import be.myrmacta.domain.promotion.model.PlanEntry;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.NotificationTemplateDoc;
import be.myrmacta.domain.promotion.port.out.ArtifactCodec;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * TODO §123 (hypothèse H1) — gabarits de notification dans un bundle, sur le modèle de
 * {@link DocumentTemplateArtifacts} : la <b>version active</b> de chaque combinaison (clé, variante, langue). L'import crée une
 * nouvelle version <b>DRAFT</b>, jamais activée : un humain l'active dans l'écran d'administration. Rejouer converge : une version
 * active ou un brouillon déjà identique rend l'artefact « inchangé ». Le fichier est nommé comme celui d'un template de document
 * ({@link DocumentTemplateArtifacts#entryKey(String, String, String)}).
 */
public final class NotificationTemplateArtifacts {

    private static final String TRANSIENT_BY = "promotion";

    private final String tenantId;
    private final ArtifactCodec codec;
    private final NotificationTemplateRepository templates;
    private final CreateNotificationTemplateUseCase create;
    private final NotificationTemplateChecker checker;
    private final NotificationTemplateLimits limits;

    public NotificationTemplateArtifacts(
            String tenantId,
            ArtifactCodec codec,
            NotificationTemplateRepository templates,
            CreateNotificationTemplateUseCase create,
            NotificationTemplateChecker checker,
            NotificationTemplateLimits limits) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.templates = Objects.requireNonNull(templates, "templates");
        this.create = Objects.requireNonNull(create, "create");
        this.checker = Objects.requireNonNull(checker, "checker");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public List<BundleEntry> export() {
        return all(null).stream()
                .filter(t -> t.status() == TemplateStatus.ACTIVE)
                .map(NotificationTemplateArtifacts::doc)
                .sorted(Comparator.comparing(NotificationTemplateArtifacts::entryKey))
                .map(d -> new BundleEntry(ArtifactType.NOTIFICATION_TEMPLATE, entryKey(d), codec.encode(d)))
                .toList();
    }

    private static NotificationTemplateDoc doc(NotificationTemplate t) {
        return new NotificationTemplateDoc(
                t.templateKey(), t.variantKey(), t.locale(), t.label(), t.subject(), t.textBody(), t.htmlBody());
    }

    public PlanEntry plan(BundleEntry entry) {
        NotificationTemplateDoc doc = PromotionSupport.decode(codec, entry, NotificationTemplateDoc.class);
        PromotionSupport.requireSameKey(entry, entryKey(doc));
        NotificationTemplate candidate;
        try {
            candidate = candidate(doc);
            checker.check(candidate);
        } catch (RuntimeException e) {
            return blocked(entry, "it would be refused at activation: " + e.getMessage());
        }
        List<NotificationTemplate> versions = all(doc.templateKey()).stream()
                .filter(t -> Objects.equals(t.variantKey(), candidate.variantKey())
                        && Objects.equals(t.locale(), candidate.locale()))
                .toList();
        Optional<NotificationTemplate> active =
                versions.stream().filter(t -> t.status() == TemplateStatus.ACTIVE).findFirst();
        if (active.isPresent() && sameContent(active.get(), candidate)) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.UNCHANGED, "");
        }
        Optional<NotificationTemplate> stagedDraft = versions.stream()
                .filter(t -> t.status() == TemplateStatus.DRAFT && sameContent(t, candidate))
                .max(Comparator.comparingInt(NotificationTemplate::version));
        if (stagedDraft.isPresent()) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.UNCHANGED, "already saved as draft version "
                    + stagedDraft.get().version() + ": activate it in the notification templates screen");
        }
        return new PlanEntry(entry.type(), entry.key(), active.isPresent() ? PlanAction.UPDATE : PlanAction.CREATE,
                "saved as a new draft version: activate it in the notification templates screen");
    }

    public String apply(BundleEntry entry, String actorId) {
        NotificationTemplateDoc doc = PromotionSupport.decode(codec, entry, NotificationTemplateDoc.class);
        NotificationTemplate saved = create.create(new CreateNotificationTemplateUseCase.CreateCommand(
                doc.templateKey(), doc.variantKey(), doc.locale(), doc.label(), doc.subject(), doc.textBody(),
                doc.htmlBody(), actorId));
        return "draft version " + saved.version() + " saved: activate it in the notification templates screen";
    }

    /** Le gabarit tel qu'il serait créé : mêmes normalisations et mêmes bornes que {@link CreateNotificationTemplateUseCase}. */
    private NotificationTemplate candidate(NotificationTemplateDoc doc) {
        Instant now = Instant.now();
        return new NotificationTemplate(
                UUID.randomUUID(),
                NotificationTemplateSupport.requireKey(doc.templateKey()),
                NotificationTemplateSupport.optionalVariant(doc.variantKey()),
                NotificationTemplateSupport.optionalLocale(doc.locale()),
                NotificationTemplateSupport.optionalLabel(doc.label()),
                0,
                TemplateStatus.DRAFT,
                NotificationTemplateSupport.requireSubject(doc.subject(), limits),
                NotificationTemplateSupport.requireTextBody(doc.textBody(), limits),
                NotificationTemplateSupport.optionalHtmlBody(doc.htmlBody(), limits),
                tenantId,
                now,
                now,
                TRANSIENT_BY);
    }

    /** Toutes les versions du tenant (d'une clé si elle est donnée), page après page : le port ne pagine qu'au plus 100 lignes. */
    private List<NotificationTemplate> all(String templateKey) {
        List<NotificationTemplate> out = new ArrayList<>();
        int page = 0;
        PageResult<NotificationTemplate> result;
        do {
            result = templates.pageByTenantId(tenantId, templateKey, PageQuery.of(page, PageQuery.MAX_SIZE));
            out.addAll(result.content());
            page++;
        } while (page < result.totalPages());
        return out;
    }

    private static boolean sameContent(NotificationTemplate t, NotificationTemplate candidate) {
        return Objects.equals(t.label(), candidate.label())
                && t.subject().equals(candidate.subject())
                && t.textBody().equals(candidate.textBody())
                && Objects.equals(t.htmlBody(), candidate.htmlBody());
    }

    /** Nom de fichier stable d'une combinaison : {@code clé.variante.langue}, comme pour les templates de document. */
    public static String entryKey(NotificationTemplateDoc doc) {
        return DocumentTemplateArtifacts.entryKey(doc.templateKey(), doc.variantKey(), doc.locale());
    }

    private static PlanEntry blocked(BundleEntry entry, String reason) {
        return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED, PromotionSupport.cap(reason));
    }
}
