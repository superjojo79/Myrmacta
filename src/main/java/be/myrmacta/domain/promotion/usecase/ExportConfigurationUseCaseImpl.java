package be.myrmacta.domain.promotion.usecase;

import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.connector.usecase.ManageConnectorInstanceUseCaseImpl;
import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.ConfigurationBundle;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.ConnectorDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.DossierTypeDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.TaskQuestionSchemaDoc;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.WorkflowConfigDoc;
import be.myrmacta.domain.promotion.port.in.ExportConfigurationUseCase;
import be.myrmacta.domain.promotion.port.out.ArtifactCodec;
import be.myrmacta.domain.workflow.model.TaskQuestionSchema;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * TODO §83 (D4) — export de la configuration du tenant en bundle. <b>Jamais de secret</b> : les champs de connecteur de type
 * mot de passe / secret sont retirés (leurs noms sont conservés pour que l'import sache quoi demander). Les processus et tables de
 * décision exportés sont la dernière version <b>déployée</b>, pas un brouillon. TODO §117 — tranche 2 : groupes d'utilisateurs
 * (sans membres).
 */
public final class ExportConfigurationUseCaseImpl implements ExportConfigurationUseCase {

    private final String tenantId;
    private final String appVersion;
    private final Clock clock;
    private final TenantAdminGuard guard;
    private final ArtifactCodec codec;
    private final DossierTypeRepository types;
    private final TaskQuestionSchemaRepository schemas;
    private final ConnectorInstanceRepository connectors;
    private final ConnectorCatalog catalog;
    private final DmnDeploymentPort dmnDeployment;
    private final WorkflowTypeConfigRepository workflowConfigs;
    private final WorkflowDeploymentPort workflowDeployment;
    private final AdditionalArtifacts additional;

    public ExportConfigurationUseCaseImpl(
            String tenantId,
            String appVersion,
            Clock clock,
            TenantAdminGuard guard,
            ArtifactCodec codec,
            DossierTypeRepository types,
            TaskQuestionSchemaRepository schemas,
            ConnectorInstanceRepository connectors,
            ConnectorCatalog catalog,
            DmnDeploymentPort dmnDeployment,
            WorkflowTypeConfigRepository workflowConfigs,
            WorkflowDeploymentPort workflowDeployment,
            AdditionalArtifacts additional) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.appVersion = appVersion == null ? "" : appVersion;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.types = Objects.requireNonNull(types, "types");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
        this.connectors = Objects.requireNonNull(connectors, "connectors");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.dmnDeployment = Objects.requireNonNull(dmnDeployment, "dmnDeployment");
        this.workflowConfigs = Objects.requireNonNull(workflowConfigs, "workflowConfigs");
        this.workflowDeployment = Objects.requireNonNull(workflowDeployment, "workflowDeployment");
        this.additional = Objects.requireNonNull(additional, "additional");
    }

    @Override
    public ConfigurationBundle export(AccessContext.User actor, String sourceLabel) {
        guard.requireTenantAdminOrApiService(actor);
        List<BundleEntry> entries = new ArrayList<>(additional.groups().export());

        types.findAll(tenantId).stream().sorted(Comparator.comparing(DossierType::code)).forEach(t ->
                entries.add(new BundleEntry(ArtifactType.DOSSIER_TYPE, t.code(), codec.encode(typeDoc(t)))));

        schemas.findAll(tenantId).stream().sorted(Comparator.comparing(TaskQuestionSchema::schemaKey)).forEach(s ->
                entries.add(new BundleEntry(ArtifactType.TASK_QUESTION_SCHEMA, s.schemaKey(), codec.encode(schemaDoc(s)))));

        Set<String> usedKeys = new HashSet<>();
        connectors.listByTenantId(tenantId).stream()
                .sorted(Comparator.comparing(c -> c.name().toLowerCase(Locale.ROOT)))
                .forEach(c -> {
                    ConnectorDoc doc = connectorDoc(c.name(), c.connectorType(), c.enabled(), c.config(), catalog);
                    entries.add(new BundleEntry(ArtifactType.CONNECTOR_INSTANCE, connectorKey(c.name(), usedKeys), codec.encode(doc)));
                });

        dmnDeployment.findAllLatestDeployments(tenantId).stream()
                .sorted(Comparator.comparing(i -> i.decisionKey()))
                .forEach(info -> dmnDeployment.findDeployedXml(tenantId, info.decisionKey()).ifPresent(xml ->
                        entries.add(new BundleEntry(ArtifactType.DMN, info.decisionKey(), xml))));

        List<WorkflowTypeConfig> configs = workflowConfigs.findAll(tenantId).stream()
                .sorted(Comparator.comparing(WorkflowTypeConfig::workflowKey))
                .toList();
        for (WorkflowTypeConfig config : configs) {
            workflowDeployment.findDeployedXml(tenantId, config.workflowKey()).ifPresent(xml ->
                    entries.add(new BundleEntry(ArtifactType.BPMN, config.workflowKey(), xml)));
        }
        for (WorkflowTypeConfig config : configs) {
            entries.add(new BundleEntry(ArtifactType.WORKFLOW_CONFIG, config.workflowKey(), codec.encode(workflowDoc(config))));
        }
        return new ConfigurationBundle(ConfigurationBundle.FORMAT_VERSION, clock.instant(), sourceLabel, appVersion, entries);
    }

    // ---- représentations portables (partagées avec l'import, qui compare les octets) ----

    static DossierTypeDoc typeDoc(DossierType t) {
        return new DossierTypeDoc(t.code(), t.label(), t.workflowKey(), t.active(), t.allowUnknownFields(),
                t.fieldDefinitions(), t.retentionDays(), t.retentionNever(), t.slaTargetHours(), t.documentTemplateKeys(),
                t.pieceTypes());
    }

    static TaskQuestionSchemaDoc schemaDoc(TaskQuestionSchema s) {
        return new TaskQuestionSchemaDoc(s.schemaKey(), s.label(), s.fields());
    }

    static WorkflowConfigDoc workflowDoc(WorkflowTypeConfig c) {
        return new WorkflowConfigDoc(c.workflowKey(), c.label(), c.statusDefinitions(), c.allowedDossierStatuses(),
                c.onStartDossierStatus(), c.observerUserIds());
    }

    /**
     * Retire toute valeur de champ secret — au premier niveau comme dans les structures imbriquées (revue D4 L1) — et le userinfo
     * des URL ({@code https://user:mdp@hôte}) ; ne garde que les noms des champs secrets de premier niveau.
     */
    static ConnectorDoc connectorDoc(
            String name, String type, boolean enabled, Map<String, Object> config, ConnectorCatalog catalog) {
        Map<String, Object> clean = new LinkedHashMap<>();
        List<String> secretFields = new ArrayList<>();
        config.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            if (isSecret(type, e.getKey(), catalog)) {
                secretFields.add(e.getKey());
            } else if (e.getValue() != null) {
                clean.put(e.getKey(), stripNested(e.getValue()));
            }
        });
        return new ConnectorDoc(name, type, enabled, clean, secretFields);
    }

    /** Même normalisation que l'export (null retirés, identifiants d'URL retirés) pour comparer à armes égales. */
    static Object normalizeValue(Object value) {
        return stripNested(value);
    }

    @SuppressWarnings("unchecked")
    private static Object stripNested(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            ((Map<String, Object>) map).forEach((k, v) -> {
                if (!ManageConnectorInstanceUseCaseImpl.isSecretKey(k) && v != null) {
                    out.put(k, stripNested(v));
                }
            });
            return out;
        }
        if (value instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull).map(ExportConfigurationUseCaseImpl::stripNested).toList();
        }
        if (value instanceof String text) {
            return URL_USERINFO.matcher(text).replaceAll("$1");
        }
        return value;
    }

    /** {@code scheme://utilisateur:motdepasse@hôte} → {@code scheme://hôte}. */
    /**
     * Des identifiants véritables : {@code utilisateur:motdepasse@} dans l'autorité de l'URL. Ni {@code git@hôte} (SSH, pas de
     * mot de passe), ni un {@code @} dans le chemin, la requête ou le fragment ({@code ?email=a@b}) : ce sont des URL valides.
     */
    static final java.util.regex.Pattern URL_USERINFO =
            java.util.regex.Pattern.compile("([A-Za-z][A-Za-z0-9+.-]*://)[^/\\s@:?#]*:[^/\\s@?#]+@");

    /** Vrai si une valeur porte des identifiants dans une URL ou un champ secret imbriqué (refusé à l'import). */
    @SuppressWarnings("unchecked")
    static boolean carriesSecret(Object value) {
        if (value instanceof Map<?, ?> map) {
            return ((Map<String, Object>) map).entrySet().stream().anyMatch(e ->
                    (ManageConnectorInstanceUseCaseImpl.isSecretKey(e.getKey()) && e.getValue() != null
                            && !String.valueOf(e.getValue()).isBlank())
                            || carriesSecret(e.getValue()));
        }
        if (value instanceof List<?> list) {
            return list.stream().anyMatch(ExportConfigurationUseCaseImpl::carriesSecret);
        }
        return value instanceof String text && URL_USERINFO.matcher(text).find();
    }

    static boolean isSecret(String connectorType, String key, ConnectorCatalog catalog) {
        return ManageConnectorInstanceUseCaseImpl.isSecretKey(key) || catalog.isPasswordField(connectorType, key);
    }

    /** Clé de fichier stable dérivée du nom (le nom d'origine reste dans le fichier) ; unique dans le bundle. */
    static String connectorKey(String name, Set<String> used) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            slug = "connector";
        }
        if (slug.length() > 100) {
            slug = slug.substring(0, 100);
        }
        String candidate = slug;
        for (int i = 2; !used.add(candidate); i++) {
            candidate = slug + "-" + i;
        }
        return candidate;
    }
}
