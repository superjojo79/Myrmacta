package be.myrmacta.domain.promotion.artifact;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.document.port.in.CreateDocumentTemplateUseCase;
import be.myrmacta.domain.document.port.out.DocumentTemplateRepository;
import be.myrmacta.domain.document.port.out.TemplateSyntaxChecker;
import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.ConfigurationBundle;
import be.myrmacta.domain.promotion.model.PlanAction;
import be.myrmacta.domain.promotion.model.PlanEntry;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.DocumentTemplateDoc;
import be.myrmacta.domain.promotion.port.out.ArtifactCodec;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TODO §117 (D-PROMO-TEMPLATE) — templates de documents dans un bundle : la <b>version active</b> de chaque combinaison (clé,
 * variante, langue). L'import crée une nouvelle version <b>DRAFT</b>, jamais activée : un humain l'active dans l'écran Templates.
 * Rejouer converge : une version active ou un brouillon déjà identique rend l'artefact « inchangé ».
 */
public final class DocumentTemplateArtifacts {

    private static final int MAX_PART_CHARS = 36;

    private final String tenantId;
    private final ArtifactCodec codec;
    private final DocumentTemplateRepository templates;
    private final CreateDocumentTemplateUseCase create;
    private final TemplateSyntaxChecker syntax;
    private final int maxHtmlBytes;

    public DocumentTemplateArtifacts(
            String tenantId,
            ArtifactCodec codec,
            DocumentTemplateRepository templates,
            CreateDocumentTemplateUseCase create,
            TemplateSyntaxChecker syntax,
            int maxHtmlBytes) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.templates = Objects.requireNonNull(templates, "templates");
        this.create = Objects.requireNonNull(create, "create");
        this.syntax = Objects.requireNonNull(syntax, "syntax");
        this.maxHtmlBytes = maxHtmlBytes;
    }

    public List<BundleEntry> export() {
        return templates.listByTenantId(tenantId, null).stream()
                .filter(t -> t.status() == TemplateStatus.ACTIVE)
                .map(DocumentTemplateArtifacts::doc)
                .sorted(Comparator.comparing(DocumentTemplateArtifacts::entryKey))
                .map(d -> new BundleEntry(ArtifactType.DOCUMENT_TEMPLATE, entryKey(d), codec.encode(d)))
                .toList();
    }

    private static DocumentTemplateDoc doc(DocumentTemplate t) {
        return new DocumentTemplateDoc(t.templateKey(), t.variantKey(), t.locale(), t.label(), t.htmlContent());
    }

    /** Clés de template portées par le bundle, toutes variantes et langues confondues. */
    public Set<String> templateKeysIn(ConfigurationBundle bundle) {
        return bundle.of(ArtifactType.DOCUMENT_TEMPLATE).stream()
                .map(e -> PromotionSupport.decode(codec, e, DocumentTemplateDoc.class).templateKey())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Clé de template d'un fichier du bundle. */
    public String templateKeyOf(BundleEntry entry) {
        return PromotionSupport.decode(codec, entry, DocumentTemplateDoc.class).templateKey();
    }

    /** Clés qui ont une version active sur la cible. */
    public Set<String> activeKeys(List<String> keys) {
        if (keys.isEmpty()) {
            return Set.of();
        }
        return templates.findActiveByTemplateKeysIn(tenantId, keys).stream()
                .map(DocumentTemplate::templateKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    public PlanEntry plan(BundleEntry entry) {
        DocumentTemplateDoc doc = PromotionSupport.decode(codec, entry, DocumentTemplateDoc.class);
        PromotionSupport.requireSameKey(entry, entryKey(doc));
        if (doc.htmlContent().isBlank()) {
            return blocked(entry, "the template has no content");
        }
        if (doc.htmlContent().getBytes(StandardCharsets.UTF_8).length > maxHtmlBytes) {
            return blocked(entry, "the template exceeds " + maxHtmlBytes + " bytes");
        }
        try {
            syntax.check(doc.htmlContent());
        } catch (RuntimeException e) {
            return blocked(entry, "its syntax would be refused at activation: " + e.getMessage());
        }
        List<DocumentTemplate> versions = templates.listByTenantId(tenantId, doc.templateKey()).stream()
                .filter(t -> Objects.equals(t.variantKey(), doc.variantKey()) && Objects.equals(t.locale(), doc.locale()))
                .toList();
        Optional<DocumentTemplate> active = versions.stream().filter(t -> t.status() == TemplateStatus.ACTIVE).findFirst();
        if (active.isPresent() && sameContent(active.get(), doc)) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.UNCHANGED, "");
        }
        Optional<DocumentTemplate> stagedDraft = versions.stream()
                .filter(t -> t.status() == TemplateStatus.DRAFT && sameContent(t, doc))
                .max(Comparator.comparingInt(DocumentTemplate::version));
        if (stagedDraft.isPresent()) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.UNCHANGED, "already saved as draft version "
                    + stagedDraft.get().version() + ": activate it in 'Templates'");
        }
        return new PlanEntry(entry.type(), entry.key(), active.isPresent() ? PlanAction.UPDATE : PlanAction.CREATE,
                "saved as a new draft version: activate it in 'Templates'");
    }

    public String apply(BundleEntry entry, String actorId) {
        DocumentTemplateDoc doc = PromotionSupport.decode(codec, entry, DocumentTemplateDoc.class);
        DocumentTemplate saved = create.create(new CreateDocumentTemplateUseCase.CreateCommand(
                doc.templateKey(), doc.variantKey(), doc.locale(), doc.label(), doc.htmlContent(), actorId));
        return "draft version " + saved.version() + " saved: activate it in 'Templates'";
    }

    private static boolean sameContent(DocumentTemplate t, DocumentTemplateDoc doc) {
        return Objects.equals(t.label(), doc.label()) && t.htmlContent().equals(doc.htmlContent());
    }

    /**
     * Nom de fichier stable d'une combinaison : {@code clé.variante.langue} ({@code _} pour la valeur par défaut). Une partie qui
     * n'est pas déjà un nom sûr est simplifiée, et une empreinte de la combinaison d'origine garantit l'unicité.
     */
    public static String entryKey(DocumentTemplateDoc doc) {
        return entryKey(doc.templateKey(), doc.variantKey(), doc.locale());
    }

    /** Même règle pour les gabarits de notification (TODO §123) : un seul nommage des fichiers de template dans un bundle. */
    public static String entryKey(String templateKey, String variantKey, String locale) {
        String key = part(templateKey);
        String variant = variantKey == null ? "_" : part(variantKey);
        String loc = locale == null ? "_" : part(locale);
        String plain = key + "." + variant + "." + loc;
        String original = templateKey + "." + (variantKey == null ? "_" : variantKey) + "." + (locale == null ? "_" : locale);
        // Revue de clôture #279 (B5) : une variante ou une langue littérale « _ » se distingue de la valeur par défaut
        boolean literalDefault = "_".equals(variantKey) || "_".equals(locale);
        if (plain.equals(original) && !literalDefault) {
            return plain;
        }
        return plain + "-" + hash(templateKey + "\u0000" + variantKey + "\u0000" + locale);
    }

    private static String part(String value) {
        String slug = value.replaceAll("[^A-Za-z0-9_-]", "-");
        if (slug.isEmpty() || slug.startsWith("-")) {
            slug = "t" + slug;
        }
        return slug.length() > MAX_PART_CHARS ? slug.substring(0, MAX_PART_CHARS) : slug;
    }

    private static String hash(String value) {
        return BundleEntry.sha256(value.getBytes(StandardCharsets.UTF_8)).substring(0, 10);
    }

    private static PlanEntry blocked(BundleEntry entry, String reason) {
        return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED, PromotionSupport.cap(reason));
    }
}
