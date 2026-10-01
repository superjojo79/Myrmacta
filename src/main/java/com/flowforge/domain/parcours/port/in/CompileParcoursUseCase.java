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

    /** Compile une définition non enregistrée (aperçu du BPMN et des avertissements) : aucune écriture. */
    CompiledParcours preview(AccessContext.User actor, String key, String label, ParcoursDefinition definition);

    /** {@code proposal} est {@code null} en mode à blanc. */
    record Result(CompiledParcours compiled, ProposeDraftUseCase.Proposal proposal) {}
}
