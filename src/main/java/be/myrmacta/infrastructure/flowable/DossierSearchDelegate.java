package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.dossier.port.in.SearchSimilarDossiersUseCase;
import be.myrmacta.domain.dossier.port.in.SearchSimilarDossiersUseCase.Criterion;
import be.myrmacta.domain.dossier.port.out.DossierSearchPort.MatchSummary;
import be.myrmacta.domain.workflow.palette.BlockAttributeRules;
import be.myrmacta.domain.workflow.palette.DossierSearchSpec;
import be.myrmacta.domain.workflow.portal.model.PortalRecipientSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TODO §102 (issue #184, lot B) — brique « Rechercher des dossiers » : compte les dossiers ouverts du même type que le dossier
 * courant (lui exclu) dont les champs cherchables valent les valeurs des critères, et pose ce nombre et la référence du plus
 * ancien dans les deux variables nommées par le BPMN (D-SEARCH-BLOCK-OUTPUT). Attributs littéraux seulement : une variable
 * posée à la complétion d'une tâche ne remplace ni les critères ni les noms de sortie.
 */
@Component("dossierSearchTask")
class DossierSearchDelegate implements JavaDelegate {

    private static final Logger LOG = LoggerFactory.getLogger(DossierSearchDelegate.class);
    private static final String DELEGATE = "dossierSearchTask";

    private final SearchSimilarDossiersUseCase searchSimilarDossiers;

    DossierSearchDelegate(SearchSimilarDossiersUseCase searchSimilarDossiers) {
        this.searchSimilarDossiers = searchSimilarDossiers;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String activity = execution.getCurrentActivityId();
        String criteria = DeployedActivityParameters.getLiteral(execution, "searchCriteria");
        String countResult = DeployedActivityParameters.getLiteral(execution, "searchCountResult");
        String referenceResult = DeployedActivityParameters.getLiteral(execution, "searchFirstReferenceResult");
        if (criteria == null || countResult == null || referenceResult == null) {
            throw new IllegalStateException("dossierSearchTask requires the literal attributes flowable:searchCriteria, "
                    + "flowable:searchCountResult and flowable:searchFirstReferenceResult");
        }
        // Défense en profondeur : même validation qu'au déploiement (un BPMN peut arriver sans passer par le parseur).
        BlockAttributeRules.validate(activity, DELEGATE, Map.of("searchCriteria", criteria,
                "searchCountResult", countResult, "searchFirstReferenceResult", referenceResult));
        List<DossierSearchSpec.Criterion> spec =
                DossierSearchSpec.parse(criteria, "BPMN attribute flowable:searchCriteria on " + activity);

        Object dossierId = execution.getVariable("dossierId");
        if (dossierId == null) {
            throw new IllegalStateException("dossierSearchTask needs a dossier (process variable dossierId)");
        }
        List<Criterion> resolved = spec.stream()
                .map(criterion -> criterion.source().kind() == PortalRecipientSource.Kind.DOSSIER_FIELD
                        ? (Criterion) new Criterion.FromDossierField(criterion.field(), criterion.source().name())
                        : new Criterion.FromValue(criterion.field(), execution.getVariable(criterion.source().name())))
                .toList();
        MatchSummary summary = searchSimilarDossiers.search(UUID.fromString(dossierId.toString()), resolved);

        execution.setVariable(countResult, summary.count());
        execution.setVariable(referenceResult, summary.firstReference());
        LOG.debug("Dossier search {} found {} open dossier(s)", activity, summary.count());
    }
}
