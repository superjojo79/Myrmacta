package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.signature.model.SignatureBlockDefinition;
import com.flowforge.domain.signature.port.in.RequestSignatureUseCase;
import java.util.Objects;
import java.util.UUID;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;

/**
 * TODO §72 D-SIGNATURE-TRIGGER — delegate allowlisté {@code ${signatureRequestTask}}. Relit le bloc
 * dans le BPMN <em>déployé</em> (validé au déploiement, jamais depuis des variables de process),
 * appelle {@link RequestSignatureUseCase} et pose {@code signatureRequestId}. Ne bloque jamais le
 * process : une erreur métier ou fournisseur donne une demande {@code FAILED} reprise par le
 * répartiteur d'issue (D-SIGNATURE-DISPATCH).
 */
public class SignatureRequestTask implements JavaDelegate {

    public static final String REQUEST_ID_VARIABLE = "signatureRequestId";

    private final RequestSignatureUseCase useCase;

    public SignatureRequestTask(RequestSignatureUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }

    @Override
    public void execute(DelegateExecution execution) {
        Object dossierIdVar = execution.getVariable("dossierId");
        if (dossierIdVar == null) {
            throw new IllegalArgumentException("dossierId process variable is required");
        }
        BpmnModel model = ProcessDefinitionUtil.getBpmnModel(execution.getProcessDefinitionId());
        FlowElement element = model.getFlowElement(execution.getCurrentActivityId());
        SignatureBlockDefinition block = element instanceof ServiceTask serviceTask
                ? FlowableBpmnParserAdapter.signatureDefinition(serviceTask)
                        .orElseThrow(() -> new IllegalStateException(
                                "activity " + element.getId() + " is not a signature block"))
                : null;
        if (block == null) {
            throw new IllegalStateException("activity " + execution.getCurrentActivityId() + " is not a serviceTask");
        }
        RequestSignatureUseCase.Result result = useCase.request(new RequestSignatureUseCase.Command(
                UUID.fromString(dossierIdVar.toString()), execution.getProcessInstanceId(), block));
        // Locale à l'exécution : deux blocs en branches parallèles ne s'écrasent pas (revue adversariale n°6) ;
        // la corrélation du message se fait sur cette variable locale.
        execution.setVariableLocal(REQUEST_ID_VARIABLE, result.requestId().toString());
    }
}
