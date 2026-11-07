package be.myrmacta.domain.promotion.artifact;

import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.DeadlineSetting;
import be.myrmacta.domain.deadline.model.TenantCalendar;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.in.ManageDeadlineUseCase;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository.StoredTenantCalendar;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.FieldType;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting;
import be.myrmacta.domain.mailroom.port.in.ManageReplyWorkflowUseCase;
import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.ConfigurationBundle;
import be.myrmacta.domain.promotion.model.PlanAction;
import be.myrmacta.domain.promotion.model.PlanEntry;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.CalendarDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.CalendarEntryDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.CalendarSourceDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.DeadlineDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.DossierTypeDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.ReplyWorkflowDoc;
import be.myrmacta.domain.promotion.port.out.ArtifactCodec;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * TODO §117 (D-PROMO-TYPE-SETTINGS) — workflow « réponse » (D-MAILROOM-REPLY-WORKFLOW) et échéance (D-DEADLINE-FIELD) d'un type de
 * dossier : deux artefacts à part, de clé = code du type, appliqués après les configurations de workflow et les groupes. Une
 * dépendance absente de la cible et du bundle bloque le réglage (D-PROMO-ORPHAN) ; un réglage absent du bundle n'est jamais retiré
 * de la cible.
 */
public final class TypeSettingsArtifacts {

    private final String tenantId;
    private final ArtifactCodec codec;
    private final DossierTypeRepository types;
    private final WorkflowTypeConfigRepository workflowConfigs;
    private final ManageReplyWorkflowUseCase replyWorkflows;
    private final ManageDeadlineUseCase deadlines;
    private final TenantCalendarRepository calendars;

    public TypeSettingsArtifacts(
            String tenantId,
            ArtifactCodec codec,
            DossierTypeRepository types,
            WorkflowTypeConfigRepository workflowConfigs,
            ManageReplyWorkflowUseCase replyWorkflows,
            ManageDeadlineUseCase deadlines,
            TenantCalendarRepository calendars) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.types = Objects.requireNonNull(types, "types");
        this.workflowConfigs = Objects.requireNonNull(workflowConfigs, "workflowConfigs");
        this.calendars = Objects.requireNonNull(calendars, "calendars");
        this.replyWorkflows = Objects.requireNonNull(replyWorkflows, "replyWorkflows");
        this.deadlines = Objects.requireNonNull(deadlines, "deadlines");
    }

    public List<BundleEntry> export() {
        List<BundleEntry> entries = new ArrayList<>();
        List<DossierType> all = types.findAll(tenantId).stream().sorted(Comparator.comparing(DossierType::code)).toList();
        for (DossierType type : all) {
            replyWorkflows.get(type.code()).ifPresent(r -> entries.add(new BundleEntry(ArtifactType.REPLY_WORKFLOW, type.code(),
                    codec.encode(replyDoc(r)))));
        }
        for (DossierType type : all) {
            deadlines.get(type.code()).ifPresent(d -> entries.add(new BundleEntry(ArtifactType.DEADLINE, type.code(),
                    codec.encode(deadlineDoc(d)))));
        }
        entries.add(new BundleEntry(ArtifactType.CALENDAR, "tenant", codec.encode(calendarDoc(currentCalendar()))));
        return entries;
    }

    private TenantCalendar currentCalendar() {
        return calendars.find(tenantId).map(StoredTenantCalendar::calendar).orElseGet(TenantCalendar::defaults);
    }

    /** TODO §121 (D-CAL-PROMO-2) — l'identifiant d'une source et son auteur ne voyagent pas ; la clé est le nom. */
    static CalendarDoc calendarDoc(TenantCalendar calendar) {
        return new CalendarDoc(calendar.zoneId(), calendar.weekend().name(), null,
                calendar.manual().stream().map(TypeSettingsArtifacts::entryDoc).toList(),
                calendar.sources().stream().sorted(Comparator.comparing(CalendarSource::nameKey))
                        .map(source -> new CalendarSourceDoc(source.name(), source.kind().name(),
                                source.loadedAt() == null ? null : source.loadedAt().toString(),
                                source.closedDays().stream().map(TypeSettingsArtifacts::entryDoc).toList()))
                        .toList());
    }

    private static CalendarEntryDoc entryDoc(CalendarEntry entry) {
        return new CalendarEntryDoc(entry.start().toString(), entry.end().toString(), entry.nature().name(), entry.label());
    }

    private static CalendarEntry entry(CalendarEntryDoc doc) {
        LocalDate start = LocalDate.parse(doc.start());
        LocalDate end = doc.end() == null || doc.end().isBlank() ? start : LocalDate.parse(doc.end());
        return new CalendarEntry(start, end, CalendarEntry.Nature.parse(doc.nature()), doc.label());
    }

    /**
     * Le calendrier d'un bundle. Une source de même nom que l'une des sources d'ici en garde l'identifiant ; l'ancien
     * format ({@code holidays}) donne des saisies manuelles {@code CLOSED}. Lève {@link IllegalArgumentException}
     * ou {@link DateTimeParseException} si le contenu est invalide.
     */
    private TenantCalendar incomingCalendar(CalendarDoc doc) {
        List<CalendarEntry> manual = new ArrayList<>(doc.entries().stream().map(TypeSettingsArtifacts::entry).toList());
        if (doc.holidays() != null) {
            if (!doc.entries().isEmpty()) {
                throw new IllegalArgumentException("calendar holidays and entries are mutually exclusive");
            }
            doc.holidays().forEach(day -> manual.add(CalendarEntry.day(LocalDate.parse(day), CalendarEntry.Nature.CLOSED, null)));
        }
        Map<String, CalendarSource> here = new java.util.HashMap<>();
        currentCalendar().sources().forEach(source -> here.put(source.nameKey(), source));
        List<CalendarSource> sources = new ArrayList<>();
        for (CalendarSourceDoc source : doc.sources()) {
            CalendarSource existing = source.name() == null ? null : here.get(source.name().strip().toLowerCase(java.util.Locale.ROOT));
            CalendarSource.Kind kind = CalendarSource.Kind.parse(source.kind());
            // L'URL ne voyage pas (D-CAL-PROMO-2) : une source par URL déjà ici garde la sienne, donc sa forme masquée.
            String maskedUrl = existing != null && existing.kind() == kind ? existing.maskedUrl() : null;
            sources.add(new CalendarSource(existing != null ? existing.id() : java.util.UUID.randomUUID(), source.name(), kind,
                    source.entries().stream().map(TypeSettingsArtifacts::entry).toList(),
                    source.loadedAt() == null ? null : Instant.parse(source.loadedAt()), "promotion", null, null, maskedUrl));
        }
        return new TenantCalendar(doc.zoneId(), WeekendPattern.parse(doc.weekend()), manual, sources);
    }

    static ReplyWorkflowDoc replyDoc(ReplyWorkflowSetting s) {
        return new ReplyWorkflowDoc(s.dossierTypeCode(), s.mode(), s.workflowKey(), s.childTypeCode());
    }

    static DeadlineDoc deadlineDoc(DeadlineSetting s) {
        return new DeadlineDoc(s.dossierTypeCode(), s.fieldName(), s.reminderDays(), s.escalationGroup(), s.reminderDayCount());
    }

    // ------------------------------------------------------------------ workflow « réponse »

    public PlanEntry planReplyWorkflow(BundleEntry entry, Map<String, PlanEntry> planned) {
        ReplyWorkflowDoc doc = PromotionSupport.decode(codec, entry, ReplyWorkflowDoc.class);
        PromotionSupport.requireSameKey(entry, doc.dossierTypeCode());
        ReplyWorkflowSetting setting;
        try {
            setting = new ReplyWorkflowSetting(tenantId, doc.dossierTypeCode(), doc.mode(), doc.workflowKey(), doc.childTypeCode());
        } catch (IllegalArgumentException e) {
            return blocked(entry, e.getMessage());
        }
        String missingType = missingType(setting.dossierTypeCode(), planned);
        if (missingType != null) {
            return blocked(entry, missingType);
        }
        String missingConfig = missingWorkflowConfig(setting.workflowKey(), planned);
        if (missingConfig != null) {
            return blocked(entry, missingConfig);
        }
        if (setting.childTypeCode() != null) {
            String missingChild = missingType(setting.childTypeCode(), planned);
            if (missingChild != null) {
                return blocked(entry, "child " + missingChild);
            }
        }
        Optional<ReplyWorkflowSetting> current = existsHere(setting.dossierTypeCode())
                ? replyWorkflows.get(setting.dossierTypeCode())
                : Optional.empty();
        if (current.isEmpty()) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.CREATE, "");
        }
        return same(replyDoc(current.get()), replyDoc(setting))
                ? new PlanEntry(entry.type(), entry.key(), PlanAction.UNCHANGED, "")
                : new PlanEntry(entry.type(), entry.key(), PlanAction.UPDATE, "");
    }

    public String applyReplyWorkflow(BundleEntry entry, PlanAction action) {
        ReplyWorkflowDoc doc = PromotionSupport.decode(codec, entry, ReplyWorkflowDoc.class);
        replyWorkflows.set(doc.dossierTypeCode(), doc.mode(), doc.workflowKey(), doc.childTypeCode());
        return action == PlanAction.CREATE ? "created" : "updated";
    }

    // ------------------------------------------------------------------ échéance

    public PlanEntry planDeadline(
            BundleEntry entry, ConfigurationBundle bundle, Map<String, PlanEntry> planned, Set<String> targetGroups) {
        DeadlineDoc doc = PromotionSupport.decode(codec, entry, DeadlineDoc.class);
        PromotionSupport.requireSameKey(entry, doc.dossierTypeCode());
        DeadlineSetting setting;
        try {
            setting = new DeadlineSetting(tenantId, doc.dossierTypeCode(), doc.fieldName(), doc.reminderDays(), doc.escalationGroup(),
                    doc.reminderDayCount());
        } catch (IllegalArgumentException e) {
            return blocked(entry, e.getMessage());
        }
        String missingType = missingType(setting.dossierTypeCode(), planned);
        if (missingType != null) {
            return blocked(entry, missingType);
        }
        List<FieldDefinition> fields = fieldsAfterImport(setting.dossierTypeCode(), bundle, planned);
        boolean dateField = fields.stream().anyMatch(f -> f.name().equals(setting.fieldName()) && f.type() == FieldType.DATE);
        if (!dateField) {
            return blocked(entry, "'" + setting.fieldName() + "' is not a DATE field of the dossier type '"
                    + setting.dossierTypeCode() + "'");
        }
        if (setting.escalationGroup() != null && !targetGroups.contains(setting.escalationGroup())
                && !applicable(planned.get(ArtifactType.USER_GROUP + "/" + setting.escalationGroup()))) {
            return blocked(entry, "the escalation group '" + setting.escalationGroup()
                    + "' is neither on this instance nor applicable in the bundle");
        }
        Optional<DeadlineSetting> current = existsHere(setting.dossierTypeCode())
                ? deadlines.get(setting.dossierTypeCode())
                : Optional.empty();
        if (current.isEmpty()) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.CREATE, "");
        }
        return same(deadlineDoc(current.get()), deadlineDoc(setting))
                ? new PlanEntry(entry.type(), entry.key(), PlanAction.UNCHANGED, "")
                : new PlanEntry(entry.type(), entry.key(), PlanAction.UPDATE, "");
    }

    public String applyDeadline(BundleEntry entry, PlanAction action) {
        DeadlineDoc doc = PromotionSupport.decode(codec, entry, DeadlineDoc.class);
        deadlines.set(doc.dossierTypeCode(), doc.fieldName(), doc.reminderDays(), doc.escalationGroup(), doc.reminderDayCount());
        return action == PlanAction.CREATE ? "created" : "updated";
    }

    public PlanEntry planCalendar(BundleEntry entry) {
        CalendarDoc doc = PromotionSupport.decode(codec, entry, CalendarDoc.class);
        TenantCalendar incoming;
        try {
            incoming = incomingCalendar(doc);
        } catch (IllegalArgumentException | DateTimeParseException invalid) {
            return blocked(entry, invalid.getMessage() == null ? "invalid calendar" : invalid.getMessage());
        }
        boolean stored = calendars.find(tenantId).isPresent();
        return same(calendarDoc(currentCalendar()), calendarDoc(incoming))
                ? new PlanEntry(entry.type(), entry.key(), PlanAction.UNCHANGED, "")
                : new PlanEntry(entry.type(), entry.key(), stored ? PlanAction.UPDATE : PlanAction.CREATE, "");
    }

    public String applyCalendar(BundleEntry entry, PlanAction action) {
        if (action == PlanAction.UNCHANGED) {
            return "unchanged";
        }
        CalendarDoc doc = PromotionSupport.decode(codec, entry, CalendarDoc.class);
        // Une seule transaction sous verrou : un calendrier promu à moitié (réglage nouveau, sources anciennes) n'existe pas.
        calendars.locked(tenantId, () -> {
            TenantCalendar incoming = incomingCalendar(doc);
            calendars.save(new StoredTenantCalendar(tenantId, incoming, Instant.now(), "promotion"));
            Set<java.util.UUID> kept = new java.util.HashSet<>();
            incoming.sources().forEach(source -> kept.add(source.id()));
            currentCalendar().sources().stream().filter(source -> !kept.contains(source.id()))
                    .forEach(source -> calendars.deleteSource(tenantId, source.id()));
            incoming.sources().forEach(source -> calendars.saveSource(tenantId, source, null));
            return null;
        });
        return action == PlanAction.CREATE ? "created" : "updated";
    }

    // ------------------------------------------------------------------ dépendances

    private boolean existsHere(String typeCode) {
        return types.findByCode(tenantId, typeCode).isPresent();
    }

    /** {@code null} si le type existe ici ou sera créé par ce bundle ; sinon la raison du blocage. */
    private String missingType(String typeCode, Map<String, PlanEntry> planned) {
        if (existsHere(typeCode) || applicable(planned.get(ArtifactType.DOSSIER_TYPE + "/" + typeCode))) {
            return null;
        }
        return "dossier type '" + typeCode + "' is neither on this instance nor applicable in the bundle";
    }

    /**
     * La configuration du workflow doit exister au moment d'appliquer le réglage : déjà ici, ou créée directement par ce bundle. Jointe
     * à un brouillon BPMN, elle n'existera qu'à sa publication.
     */
    private String missingWorkflowConfig(String workflowKey, Map<String, PlanEntry> planned) {
        if (workflowConfigs.findByWorkflowKey(tenantId, workflowKey).isPresent()) {
            return null;
        }
        PlanEntry config = planned.get(ArtifactType.WORKFLOW_CONFIG + "/" + workflowKey);
        if (applicable(config) && !config.reason().startsWith(PromotionSupport.JOINED_TO_DRAFT)) {
            return null;
        }
        if (applicable(config)) {
            return "the workflow configuration '" + workflowKey + "' only exists once its BPMN draft is published: publish it, "
                    + "then run the import again";
        }
        return "no workflow configuration '" + workflowKey + "' on this instance or applicable in the bundle";
    }

    /** Champs du type après l'import : ceux du bundle s'il les applique, sinon ceux de la cible. */
    private List<FieldDefinition> fieldsAfterImport(String typeCode, ConfigurationBundle bundle, Map<String, PlanEntry> planned) {
        if (applicable(planned.get(ArtifactType.DOSSIER_TYPE + "/" + typeCode))) {
            return bundle.find(ArtifactType.DOSSIER_TYPE, typeCode)
                    .map(e -> PromotionSupport.decode(codec, e, DossierTypeDoc.class).fieldDefinitions())
                    .orElse(List.of());
        }
        return types.findByCode(tenantId, typeCode).map(DossierType::fieldDefinitions).orElse(List.of());
    }

    private static boolean applicable(PlanEntry entry) {
        return entry != null && entry.applicable();
    }

    private boolean same(Object a, Object b) {
        return Arrays.equals(codec.encode(a), codec.encode(b));
    }

    private static PlanEntry blocked(BundleEntry entry, String reason) {
        return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED, PromotionSupport.cap(reason));
    }
}
