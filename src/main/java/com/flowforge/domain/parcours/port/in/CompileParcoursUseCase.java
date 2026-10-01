package com.flowforge.domain.parcours.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.parcours.model.CompiledParcours;
import com.flowforge.domain.parcours.model.ParcoursDefinition;
import com.flowforge.domain.workflow.model.Provenance;
import com.flowforge.domain.workflow.port.in.ProposeDraftUseCase;

/** TODO §88 (LC5) — compiler un parcours en BPMN et le déposer en BROUILLON (jamais publié par ce cas d'usage). */
public interface CompileParcoursUseCase {

    /**
     * @param dryRun valide (mêmes contrôles qu'un déploiement : parseur, liste blanche, existence des références) sans rien écrire
     * @param replaceDraft autorise l'écrasement d'un brouillon BPMN d'un autre auteur
     */
    Result compile(AccessContext.User actor, Provenance provenance, String key, boolean dryRun, boolean replaceDraft);

    /**
     * Compile une définition non enregistrée (aperçu du BPMN et des avertissements) : aucune écriture. Le BPMN passe aussi par le
     * parseur et les contrôles de références du déploiement ; leur refus n'est pas levé mais rendu dans {@link Preview#problem()}
     * (on veut voir le BPMN et savoir pourquoi il ne serait pas déployable).
     */
    Preview preview(AccessContext.User actor, String key, String label, ParcoursDefinition definition);

    /** {@code problem} est {@code null} si le BPMN serait accepté tel quel par le déploiement. */
    record Preview(CompiledParcours compiled, String problem) {}

    /** {@code proposal} est {@code null} en mode à blanc. */
    record Result(CompiledParcours compiled, ProposeDraftUseCase.Proposal proposal) {}
}
