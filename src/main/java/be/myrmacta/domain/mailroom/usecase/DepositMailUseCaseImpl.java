package be.myrmacta.domain.mailroom.usecase;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.model.ConnectorInstanceNotFoundException;
import be.myrmacta.domain.connector.model.InboundAttachment;
import be.myrmacta.domain.connector.model.WorkflowTriggerEvent;
import be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.mailroom.model.DepositFile;
import be.myrmacta.domain.mailroom.model.DepositPoint;
import be.myrmacta.domain.mailroom.model.DepositRefusedException;
import be.myrmacta.domain.mailroom.model.DepositResult;
import be.myrmacta.domain.mailroom.port.in.DepositMailUseCase;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §113 (D-MAILROOM-DEPOSIT) — chaque PDF déposé devient un courrier par le même chemin qu'un connecteur d'entrée
 * ({@link ConnectorTriggerRuntime#deliver}) : pièce contrôlée (types, signature, antivirus), process mis en file. Le déposant est
 * tracé ({@code courrierDeposePar}) ; le dossier est créé par le connecteur, comme pour l'e-mail.
 */
public final class DepositMailUseCaseImpl implements DepositMailUseCase {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_NAME = 200;

    private final String tenantId;
    private final ConnectorInstanceRepository instances;
    private final DossierAccessPolicy accessPolicy;
    private final ConnectorTriggerRuntime runtime;
    private final DossierRepository dossiers;
    private final long maxBytes;

    public DepositMailUseCaseImpl(String tenantId, ConnectorInstanceRepository instances, DossierAccessPolicy accessPolicy,
            ConnectorTriggerRuntime runtime, DossierRepository dossiers, long maxBytes) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.instances = Objects.requireNonNull(instances, "instances");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.dossiers = Objects.requireNonNull(dossiers, "dossiers");
        this.maxBytes = maxBytes;
    }

    @Override
    public List<DepositPoint> points(AccessContext.User actor) {
        Objects.requireNonNull(actor, "actor");
        return instances.listByTenantId(tenantId).stream().filter(DepositMailUseCaseImpl::isActiveDepositPoint)
                .map(DepositMailUseCaseImpl::toPoint)
                .filter(point -> accessPolicy.canEditType(tenantId, point.dossierTypeCode(), actor))
                .sorted(Comparator.comparing(point -> point.name() == null ? "" : point.name()))
                .toList();
    }

    @Override
    public DepositPoint point(UUID pointId, AccessContext.User actor) {
        DepositPoint point = toPoint(instance(pointId));
        accessPolicy.assertCanEditType(tenantId, point.dossierTypeCode(), actor);
        return point;
    }

    @Override
    public DepositResult deposit(UUID pointId, DepositFile file, String sender, String subject, AccessContext.User actor) {
        ConnectorInstanceData instance = instance(pointId);
        DepositPoint point = toPoint(instance);
        accessPolicy.assertCanEditType(tenantId, point.dossierTypeCode(), actor);
        String name = file.filename();
        if (name.isEmpty() || name.length() > MAX_NAME || name.chars().anyMatch(Character::isISOControl)) {
            throw new DepositRefusedException("nom de fichier invalide");
        }
        if (file.content().length > maxBytes) {
            throw new DepositRefusedException("trop volumineux");
        }
        if (file.content().length < PDF_MAGIC.length
                || !Arrays.equals(Arrays.copyOf(file.content(), PDF_MAGIC.length), PDF_MAGIC)) {
            throw new DepositRefusedException("pas un PDF");
        }
        Map<String, Object> config = instance.config();
        Map<String, Object> fields = new LinkedHashMap<>(defaults(config));
        if (sender != null && !sender.isBlank()) {
            fields.put(text(config.get("senderField"), "expediteur"), sender.strip());
        }
        fields.put(text(config.get("subjectField"), "objet"), subject == null || subject.isBlank() ? baseName(name) : subject.strip());
        Map<String, Object> variables = Map.of("courrierFichier", name, "courrierDeposePar", actor.userId());
        UUID dossierId = runtime.deliver(instance, new WorkflowTriggerEvent(text(config.get("processKey"), null), point.dossierTypeCode(),
                fields, variables, null, List.of(new InboundAttachment(name, "application/pdf", file.content())), true));
        String reference = dossiers.findById(dossierId).map(dossier -> dossier.reference()).orElse(null);
        return DepositResult.created(name, dossierId, reference);
    }

    private ConnectorInstanceData instance(UUID pointId) {
        return instances.findByIdAndTenantId(pointId, tenantId).filter(DepositMailUseCaseImpl::isActiveDepositPoint)
                .orElseThrow(() -> new ConnectorInstanceNotFoundException(pointId));
    }

    private static boolean isActiveDepositPoint(ConnectorInstanceData instance) {
        return DepositPoint.CONNECTOR_TYPE.equals(instance.connectorType()) && instance.enabled()
                && text(instance.config().get("dossierTypeCode"), null) != null;
    }

    private static DepositPoint toPoint(ConnectorInstanceData instance) {
        return new DepositPoint(instance.id(), instance.name(), text(instance.config().get("dossierTypeCode"), null));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> defaults(Map<String, Object> config) {
        return config.get("defaultFields") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).strip();
    }

    private static String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }
}
