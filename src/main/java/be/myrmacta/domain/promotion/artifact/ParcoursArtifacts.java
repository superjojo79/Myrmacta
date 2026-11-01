package be.myrmacta.domain.promotion.artifact;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.parcours.ParcoursCompiler;
import be.myrmacta.domain.parcours.model.Parcours;
import be.myrmacta.domain.parcours.port.in.ManageParcoursUseCase;
import be.myrmacta.domain.parcours.port.out.ParcoursRepository;
import be.myrmacta.domain.promotion.model.ArtifactType;
import be.myrmacta.domain.promotion.model.BundleEntry;
import be.myrmacta.domain.promotion.model.PlanAction;
import be.myrmacta.domain.promotion.model.PlanEntry;
import be.myrmacta.domain.promotion.model.doc.ConfigDocs.ParcoursDoc;
import be.myrmacta.domain.promotion.port.out.ArtifactCodec;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * TODO §117 (D-PROMO-PARCOURS) — parcours dans un bundle : leur <b>définition</b>, enregistrée sans compilation (le BPMN du bundle
 * reste la référence et devient un brouillon). L'export note si la définition est à jour de sa dernière compilation et si le
 * processus déployé est ce dernier BPMN compilé ; sinon le plan avertit. Ce repère n'entre pas dans la comparaison.
 */
public final class ParcoursArtifacts {

    private final String tenantId;
    private final ArtifactCodec codec;
    private final ParcoursRepository parcours;
    private final ManageParcoursUseCase manage;
    private final WorkflowDeploymentPort deployment;

    public ParcoursArtifacts(
            String tenantId,
            ArtifactCodec codec,
            ParcoursRepository parcours,
            ManageParcoursUseCase manage,
            WorkflowDeploymentPort deployment) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.parcours = Objects.requireNonNull(parcours, "parcours");
        this.manage = Objects.requireNonNull(manage, "manage");
        this.deployment = Objects.requireNonNull(deployment, "deployment");
    }

    public List<BundleEntry> export() {
        return parcours.list(tenantId).stream()
                .sorted(Comparator.comparing(Parcours::key))
                .map(p -> new BundleEntry(ArtifactType.PARCOURS, p.key(),
                        codec.encode(new ParcoursDoc(p.key(), p.label(), p.definition(), inSync(p)))))
                .toList();
    }

    /** Vrai si la définition n'a pas changé depuis sa dernière compilation et que le processus déployé est ce BPMN compilé. */
    private boolean inSync(Parcours p) {
        if (p.lastCompiledAt() == null || p.lastCompiledSha256() == null || p.updatedAt().isAfter(p.lastCompiledAt())) {
            return false;
        }
        return deployment.findDeployedXml(tenantId, p.key())
                .map(xml -> BundleEntry.sha256(xml).equals(p.lastCompiledSha256()))
                .orElse(false);
    }

    public PlanEntry plan(BundleEntry entry) {
        ParcoursDoc doc = PromotionSupport.decode(codec, entry, ParcoursDoc.class);
        PromotionSupport.requireSameKey(entry, doc.key());
        try {
            // mêmes contrôles qu'un enregistrement : clé, libellé, graphe compilable
            ParcoursCompiler.compile(doc.key(), doc.label(), doc.definition(), key -> null);
        } catch (RuntimeException e) {
            return new PlanEntry(entry.type(), entry.key(), PlanAction.BLOCKED, PromotionSupport.cap(e.getMessage()));
        }
        Optional<Parcours> current = parcours.findByKey(tenantId, doc.key());
        PlanEntry planned;
        if (current.isEmpty()) {
            planned = new PlanEntry(entry.type(), entry.key(), PlanAction.CREATE, "definition saved, not compiled");
        } else if (sameDefinition(current.get(), doc)) {
            planned = new PlanEntry(entry.type(), entry.key(), PlanAction.UNCHANGED, "");
        } else {
            planned = new PlanEntry(entry.type(), entry.key(), PlanAction.UPDATE,
                    "replaces the definition of this instance (version " + current.get().version() + "), not compiled");
        }
        return Boolean.FALSE.equals(doc.inSync())
                ? PromotionSupport.warn(planned, "on the source, this definition changed after its last compilation or its "
                        + "deployed process is not that compilation: the BPMN of this bundle may not match it.")
                : planned;
    }

    public String apply(AccessContext.User actor, BundleEntry entry, PlanAction action) {
        ParcoursDoc doc = PromotionSupport.decode(codec, entry, ParcoursDoc.class);
        Integer expectedVersion = action == PlanAction.CREATE
                ? null
                : parcours.findByKey(tenantId, doc.key()).map(Parcours::version).orElse(null);
        Parcours saved = manage.save(actor, new ManageParcoursUseCase.SaveCommand(doc.key(), doc.label(), doc.definition(),
                expectedVersion));
        return (action == PlanAction.CREATE ? "created" : "updated") + " (version " + saved.version() + "), not compiled";
    }

    private boolean sameDefinition(Parcours current, ParcoursDoc doc) {
        return Arrays.equals(codec.encode(new ParcoursDoc(current.key(), current.label(), current.definition(), null)),
                codec.encode(new ParcoursDoc(doc.key(), doc.label(), doc.definition(), null)));
    }
}
